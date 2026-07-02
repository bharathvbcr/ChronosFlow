import SwiftUI
import SwiftData
import PhotosUI
import ChronosCore

// MARK: - JournalEditorSheet
//
// The unified journal composer surfaced as a modal sheet — the iOS analogue of Android's
// `JournalComposerSheet` / `JournalEntryComposer` (feature/daydial JournalScreen.kt). Brought to
// parity with the Android source-of-truth: the rich composer (mood, prompt-shuffled reflection,
// word count, "Add details" disclosure carrying highlights / time-of-day / a multi-day range / photos
// / dictation) lives here as a sheet, NOT inline in the page. `JournalView` (the page) presents it for
// three flows, mirroring Android's `openNew(date)` / `openEdit(entry)`:
//   • New entry for a chosen day (defaults to today).
//   • New entry pinned to a specific day (tapping a calendar day / a history day's "+").
//   • Editing the day's existing entry (tapping a history row).
//
// Parity points ported from Android `JournalEntryComposer`:
//   • Multi-day range creation for NEW entries — a "Apply to a range of days" toggle unlocks an
//     end-date picker and a "Adds N entries, one per day." summary; the save button reads
//     "Save N entries". On save we insert one `JournalEntry` per day in [start … end]. (J-range)
//   • Save-button text adapts: "Update entry" when editing, "Save N entries" for a range, else
//     "Save entry" — matching Android's `when { !isNew … dates.size > 1 … }`.
//   • Editing pins the entry's day (no date picker); a new entry can target any day.
//   • The "Add details" disclosure auto-expands when an edited entry already carries details.
//
// Model note: a point's time-of-day has no column on `JournalEntry`, so it is carried on `createdAt`
// (a real instant on the entry's day); `entryDate` stays start-of-day for keying. This matches the
// convention used by `JournalView`.

struct JournalEditorSheet: View {
    /// The entry being edited, or nil to compose a brand-new entry.
    let editing: JournalEntry?
    /// The day a new entry targets (ignored when editing — the entry keeps its own day).
    let initialDate: Date
    /// Current streak, shown as a badge in the header (Android parity).
    let streak: Int

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    // Composer state
    @State private var draft = ""
    @State private var highlights: [String] = []
    @State private var selectedMood: JournalMood = .unset
    @State private var timeText = ""
    @State private var dictation = DictationController()
    /// On-device text tools backing the per-field "✨" polish menus (Android JournalAiAssistButton).
    @State private var textTools = ChronosTextTools()
    /// Transient outcome line for the last AI action ("Updated by AI…" / "Reverted"), auto-cleared.
    @State private var aiMessage: String?
    @State private var pendingPhotos: [PhotosPickerItem] = []
    @State private var pendingPhotoData: [Data] = []
    @State private var showDetails = false
    @State private var showCamera = false
    @State private var promptOffset = 0

    // Multi-day range (new entries only)
    @State private var applyRange = false
    @State private var startDate: Date = .now
    @State private var endDate: Date = .now

    @State private var didPrefill = false

    /// Toggled in `save()` to fire a success haptic on commit (mirrors SleepView's save feedback).
    @State private var saveFeedback = false

    private let cal = Calendar.current

    /// Public init so the page (and any future caller) can present the sheet for an edit or a new entry.
    /// `editing == nil` composes a new entry for `initialDate`.
    init(editing: JournalEntry? = nil, initialDate: Date = .now, streak: Int = 0) {
        self.editing = editing
        self.initialDate = initialDate
        self.streak = streak
    }

    private var isNew: Bool { editing == nil }

    private var serializedDraft: String { journalSerializeBody(mainNote: draft, subNotes: highlights) }
    private var canSave: Bool { journalCanSave(serializedDraft) || selectedMood != .unset }
    private var isOnlyPrompts: Bool { journalIsOnlyPrompts(serializedDraft) }

    /// The reflection prompt to show: deterministic daily base shifted by the manual shuffle offset.
    private var activePrompt: String {
        journalPrompt(date: startDate, calendar: cal, offset: promptOffset)
    }

    /// The list of days this save targets — one per day across [start … end] for a new range,
    /// otherwise just the single (start) day. Mirrors Android's `dates` derivation.
    private var targetDays: [Date] {
        guard isNew, applyRange else { return [cal.startOfDay(for: startDate)] }
        let start = cal.startOfDay(for: startDate)
        let end = cal.startOfDay(for: max(endDate, startDate))
        var days: [Date] = []
        var cursor = start
        while cursor <= end {
            days.append(cursor)
            guard let next = cal.date(byAdding: .day, value: 1, to: cursor) else { break }
            cursor = next
        }
        return days
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: ChronosSpacing.standard) {
                    composerCard
                }
                .padding(ChronosSpacing.standard)
            }
            .background { ChronosBackdrop() }
            .navigationTitle(isNew ? "New entry" : "Edit entry")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                if let editing {
                    ToolbarItem(placement: .destructiveAction) {
                        Button(role: .destructive) {
                            context.delete(editing)
                            try? context.save()
                            dismiss()
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                }
            }
            .sensoryFeedback(.success, trigger: saveFeedback)
            // Auto-clear the AI outcome line after a few seconds (Android shows it as a snackbar).
            .task(id: aiMessage) {
                guard aiMessage != nil else { return }
                try? await Task.sleep(for: .seconds(4))
                withAnimation(ChronosMotion.smooth) { aiMessage = nil }
            }
            .onAppear { prefill() }
            .onChange(of: dictation.transcript) { _, new in
                guard !new.isEmpty else { return }
                appendDictation(new)
                dictation.clearTranscript()
            }
            .onDisappear { dictation.stop() }
            .onChange(of: pendingPhotos) { _, items in
                Task { await loadPickedPhotos(items) }
            }
            .sheet(isPresented: $showCamera) {
                CameraPickerView { data in
                    if let data { pendingPhotoData.append(data) }
                }
            }
        }
    }

    // MARK: Composer card

    private var composerCard: some View {
        ChronosGlassPanel {
            VStack(alignment: .leading, spacing: ChronosSpacing.standard) {

                // Header: target day + live streak badge (Android parity).
                HStack(alignment: .firstTextBaseline) {
                    dateRow
                    Spacer()
                    if streak > 0 {
                        Label("\(streak) day streak", systemImage: "flame.fill")
                            .font(.chronosCaption)
                            .foregroundStyle(ChronosColors.brandAccent)
                    }
                }

                // Mood leads — the quickest, most inviting thing to log.
                moodPicker

                shufflePromptRow

                TextField(activePrompt.isEmpty ? "What shaped your day?" : activePrompt,
                          text: $draft, axis: .vertical)
                    .lineLimit(4...14)
                    .font(.chronosBody)

                HStack {
                    dictationButton
                    Spacer()
                    Text(journalMeterLabel(serializedDraft))
                        .font(.chronosCaption).foregroundStyle(.secondary)
                    if textTools.isAvailable {
                        JournalAiPolishMenu(
                            text: draft, tools: textTools,
                            onApply: { polished in withAnimation(ChronosMotion.smooth) { draft = polished } },
                            onMessage: showAiMessage)
                    }
                }

                if let aiMessage {
                    Text(aiMessage)
                        .font(.chronosCaption).foregroundStyle(.secondary)
                        .transition(.opacity)
                }

                if isOnlyPrompts {
                    Text("Add your own words below the prompts to save.")
                        .font(.chronosCaption).foregroundStyle(.secondary)
                }

                DisclosureGroup(isExpanded: $showDetails) {
                    VStack(alignment: .leading, spacing: ChronosSpacing.medium) {
                        highlightsEditor
                        timeOfDayField
                        if isNew { rangeSection }
                        photoAttachmentSection
                    }
                    .padding(.top, ChronosSpacing.small)
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Label("Add details", systemImage: "chevron.down.circle")
                            .font(.chronosLabel)
                            .foregroundStyle(ChronosColors.brandPrimary)
                        Text(detailsSummary)
                            .font(.chronosCaption)
                            .foregroundStyle(.secondary)
                    }
                }
                .tint(ChronosColors.brandPrimary)

                if !pendingPhotoData.isEmpty {
                    photoThumbnailStrip
                }

                Button(action: save) {
                    Text(saveButtonTitle)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(!canSave)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var saveButtonTitle: String {
        if !isNew { return "Update entry" }
        let count = targetDays.count
        return count > 1 ? "Save \(count) entries" : "Save entry"
    }

    /// Live "1 highlight · 8:00 AM · 2 photos · 5 days" summary under the collapsed disclosure.
    private var detailsSummary: String {
        var parts: [String] = []
        if !highlights.isEmpty {
            parts.append(highlights.count == 1 ? "1 highlight" : "\(highlights.count) highlights")
        }
        if let minute = parseFlexibleMinute(timeText) {
            parts.append(formatDisplayMinute(minute))
        }
        if !pendingPhotoData.isEmpty {
            parts.append(pendingPhotoData.count == 1 ? "1 photo" : "\(pendingPhotoData.count) photos")
        }
        if isNew && applyRange && targetDays.count > 1 {
            parts.append("\(targetDays.count) days")
        }
        return parts.isEmpty ? "Highlights, time of day, photos" : parts.joined(separator: " · ")
    }

    // MARK: Date row

    @ViewBuilder
    private var dateRow: some View {
        if isNew {
            // A new entry can target any day; editing keeps the entry's day fixed.
            HStack(spacing: ChronosSpacing.small) {
                Image(systemName: "calendar")
                    .font(.chronosBody)
                    .foregroundStyle(ChronosColors.brandPrimary)
                DatePicker("",
                           selection: $startDate,
                           displayedComponents: .date)
                    .labelsHidden()
                    .onChange(of: startDate) { _, new in
                        if endDate < new { endDate = new }
                    }
            }
        } else {
            Text(startDate.formatted(.dateTime.weekday(.wide).month().day()))
                .font(.chronosHeadline)
                .foregroundStyle(.secondary)
        }
    }

    // MARK: Range section (new entries only)

    private var rangeSection: some View {
        VStack(alignment: .leading, spacing: ChronosSpacing.small) {
            Toggle(isOn: $applyRange.animation(ChronosMotion.snappy)) {
                Text("Apply to a range of days")
                    .font(.chronosBody)
            }
            .tint(ChronosColors.brandPrimary)

            if applyRange {
                HStack(spacing: ChronosSpacing.small) {
                    Image(systemName: "calendar")
                        .font(.chronosBody)
                        .foregroundStyle(.secondary)
                    Text("To")
                        .font(.chronosLabel)
                        .foregroundStyle(.secondary)
                    DatePicker("",
                               selection: $endDate,
                               in: startDate...,
                               displayedComponents: .date)
                        .labelsHidden()
                }
                Text(journalRangeSummary(targetDays.count))
                    .font(.chronosCaption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    // MARK: Shuffle prompt

    private var shufflePromptRow: some View {
        HStack(spacing: ChronosSpacing.small) {
            Button {
                withAnimation(ChronosMotion.smooth) {
                    draft = journalBodyWithPrompt(draft, question: activePrompt)
                }
            } label: {
                Text(activePrompt)
                    .font(.chronosLabel)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)

            Button {
                withAnimation(ChronosMotion.bouncy) { promptOffset += 1 }
            } label: {
                Image(systemName: "shuffle")
                    .font(.chronosBody)
                    .foregroundStyle(ChronosColors.brandSecondary)
                    .padding(ChronosSpacing.small)
                    .background(ChronosColors.brandSecondary.opacity(0.12), in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Show a different prompt")
        }
    }

    // MARK: Highlights (sub-notes / bullets)

    private var highlightsEditor: some View {
        VStack(alignment: .leading, spacing: ChronosSpacing.small) {
            Text("Highlights")
                .font(.chronosLabel)
                .foregroundStyle(.secondary)

            ForEach(Array(highlights.enumerated()), id: \.offset) { idx, _ in
                HStack(spacing: ChronosSpacing.small) {
                    Image(systemName: "circle.fill")
                        .font(.system(size: 5))
                        .foregroundStyle(.secondary)
                    TextField("Sub-note", text: Binding(
                        get: { idx < highlights.count ? highlights[idx] : "" },
                        set: { if idx < highlights.count { highlights[idx] = $0 } }))
                        .font(.chronosBody)
                    if textTools.isAvailable {
                        JournalAiPolishMenu(
                            text: idx < highlights.count ? highlights[idx] : "",
                            tools: textTools,
                            onApply: { if idx < highlights.count { highlights[idx] = $0 } },
                            onMessage: showAiMessage)
                    }
                    Button {
                        withAnimation(ChronosMotion.snappy) {
                            if idx < highlights.count { highlights.remove(at: idx) }
                        }
                    } label: {
                        Image(systemName: "minus.circle.fill").foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                }
            }

            Button {
                withAnimation(ChronosMotion.snappy) { highlights.append("") }
            } label: {
                Label("Add a sub-note", systemImage: "plus.circle")
                    .font(.chronosCaption)
                    .foregroundStyle(ChronosColors.brandPrimary)
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: Time of day

    private var timeOfDayField: some View {
        VStack(alignment: .leading, spacing: ChronosSpacing.small) {
            Text("Time of day (optional)")
                .font(.chronosLabel)
                .foregroundStyle(.secondary)
            HStack(spacing: ChronosSpacing.small) {
                TextField("e.g. 2:30 PM", text: $timeText)
                    .font(.chronosBody)
                    .textInputAutocapitalization(.characters)
                if let minute = parseFlexibleMinute(timeText) {
                    Text(formatDisplayMinute(minute))
                        .font(.chronosCaption)
                        .foregroundStyle(ChronosColors.brandSecondary)
                } else if !timeText.isEmpty {
                    Image(systemName: "exclamationmark.triangle")
                        .font(.chronosCaption)
                        .foregroundStyle(ChronosColors.brandAccent)
                }
            }
            if !timeText.isEmpty && parseFlexibleMinute(timeText) == nil {
                Text("Use a time like 2:30 PM or 14:30.")
                    .font(.chronosCaption)
                    .foregroundStyle(ChronosColors.brandAccent)
            }
        }
    }

    // MARK: Mood picker

    private var moodPicker: some View {
        VStack(alignment: .leading, spacing: ChronosSpacing.small) {
            Text(selectedMood == .unset ? "How was your day?" : selectedMood.label)
                .font(.chronosLabel)
                .foregroundStyle(.secondary)
                .animation(ChronosMotion.snappy, value: selectedMood)

            HStack(spacing: ChronosSpacing.standard) {
                ForEach(JournalMood.allCases.filter { $0 != .unset }, id: \.self) { mood in
                    Button {
                        withAnimation(ChronosMotion.bouncy) {
                            selectedMood = (selectedMood == mood) ? .unset : mood
                        }
                    } label: {
                        Text(mood.emoji)
                            .font(.system(size: 40))
                            .scaleEffect(selectedMood == mood ? 1.25 : 1.0)
                            .animation(ChronosMotion.bouncy, value: selectedMood)
                            .shadow(
                                color: selectedMood == mood ? ChronosColors.brandPrimary.opacity(0.4) : .clear,
                                radius: 8, x: 0, y: 4)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(mood.label)
                }
            }
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(.vertical, ChronosSpacing.micro)
        }
    }

    // MARK: Photos

    private var photoAttachmentSection: some View {
        VStack(alignment: .leading, spacing: ChronosSpacing.small) {
            Text("Photos")
                .font(.chronosLabel)
                .foregroundStyle(.secondary)

            HStack(spacing: ChronosSpacing.small) {
                PhotosPicker(
                    selection: $pendingPhotos,
                    maxSelectionCount: 6,
                    matching: .images,
                    photoLibrary: .shared()
                ) {
                    Label("Add photos", systemImage: "photo.on.rectangle")
                        .font(.chronosCaption)
                        .padding(.horizontal, ChronosSpacing.compact)
                        .padding(.vertical, ChronosSpacing.small)
                        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: ChronosRadius.extraSmall))
                }
                .buttonStyle(.plain)

                Button {
                    showCamera = true
                } label: {
                    Label("Take photo", systemImage: "camera")
                        .font(.chronosCaption)
                        .padding(.horizontal, ChronosSpacing.compact)
                        .padding(.vertical, ChronosSpacing.small)
                        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: ChronosRadius.extraSmall))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var photoThumbnailStrip: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: ChronosSpacing.small) {
                ForEach(Array(pendingPhotoData.enumerated()), id: \.offset) { idx, data in
                    if let uiImage = UIImage(data: data) {
                        ZStack(alignment: .topTrailing) {
                            Image(uiImage: uiImage)
                                .resizable()
                                .scaledToFill()
                                .frame(width: 72, height: 72)
                                .clipShape(RoundedRectangle(cornerRadius: ChronosRadius.extraSmall, style: .continuous))
                            Button {
                                withAnimation(ChronosMotion.snappy) {
                                    if idx < pendingPhotoData.count { pendingPhotoData.remove(at: idx) }
                                }
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.caption)
                                    .foregroundStyle(.white)
                                    .shadow(radius: 2)
                            }
                            .offset(x: 4, y: -4)
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .padding(.vertical, ChronosSpacing.micro)
        }
    }

    // MARK: Dictation

    @ViewBuilder
    private var dictationButton: some View {
        switch dictation.availability {
        case .unsupported:
            EmptyView()
        case .available, .denied:
            Button {
                dictation.toggle()
            } label: {
                Label(dictation.isRecording ? "Listening…" : "Dictate",
                      systemImage: dictation.isRecording ? "waveform" : "mic.fill")
                    .font(.chronosCaption)
                    .foregroundStyle(dictation.isRecording ? ChronosColors.brandAccent : .secondary)
                    .symbolEffect(.variableColor, isActive: dictation.isRecording)
            }
            .buttonStyle(.plain)
            .disabled(dictation.availability == .denied)
        }
    }

    // MARK: Prefill / actions

    private func prefill() {
        guard !didPrefill else { return }
        didPrefill = true
        startDate = cal.startOfDay(for: editing?.entryDate ?? initialDate)
        endDate = startDate
        guard let saved = editing else { return }
        let parts = journalParseBody(saved.body)
        draft = parts.mainNote
        highlights = parts.subNotes
        selectedMood = JournalMood(rawValue: saved.moodRating) ?? .unset
        timeText = minuteOfDay(of: saved).map(formatDisplayMinute) ?? ""
        if !highlights.isEmpty || selectedMood != .unset || !saved.photoUris.isEmpty || !timeText.isEmpty {
            showDetails = true
        }
        pendingPhotoData = saved.photoUris.compactMap { path -> Data? in
            guard let url = URL(string: path) else { return nil }
            return try? Data(contentsOf: url)
        }
    }

    /// Surface (and animate in) the transient AI outcome line; `.task(id:)` clears it later.
    private func showAiMessage(_ message: String) {
        withAnimation(ChronosMotion.snappy) { aiMessage = message }
    }

    private func appendDictation(_ transcript: String) {
        let pieces = [draft.trimmingCharacters(in: .whitespacesAndNewlines),
                      transcript.trimmingCharacters(in: .whitespacesAndNewlines)]
            .filter { !$0.isEmpty }
        draft = pieces.joined(separator: " ")
    }

    private func loadPickedPhotos(_ items: [PhotosPickerItem]) async {
        var loaded: [Data] = []
        for item in items {
            if let data = try? await item.loadTransferable(type: Data.self) {
                loaded.append(data)
            }
        }
        await MainActor.run { pendingPhotoData.append(contentsOf: loaded) }
    }

    private func save() {
        highlights = highlights.filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        let cleaned = journalCleanForSave(serializedDraft)
        guard !cleaned.isEmpty || selectedMood != .unset else { return }
        let body = cleaned.isEmpty ? "" : cleaned

        let savedPaths = savePhotosToFiles(pendingPhotoData)
        let promptKey = "offset-\(promptOffset)"

        if let existing = editing {
            // prefill() loaded the old photos back as Data, so savePhotosToFiles() just wrote fresh
            // copies under new UUIDs — delete the previous files now replaced, or every edit orphans
            // (and effectively duplicates) the entry's images on disk.
            deletePhotoFiles(existing.photoUris.filter { !savedPaths.contains($0) })
            existing.body = body
            existing.updatedAt = .now
            existing.createdAt = composedTimestamp(on: existing.entryDate)
            existing.moodRating = selectedMood.rawValue
            existing.photoUris = savedPaths
            existing.shuffledPromptKey = promptKey
        } else {
            // One entry per targeted day for a multi-day range; otherwise a single entry. (Android parity.)
            for day in targetDays {
                let timestamp = composedTimestamp(on: day)
                let entry = JournalEntry(
                    entryDate: cal.startOfDay(for: day),
                    createdAt: timestamp,
                    updatedAt: .now,
                    body: body,
                    promptType: promptKey,
                    moodRating: selectedMood.rawValue,
                    photoUris: savedPaths,
                    shuffledPromptKey: promptKey,
                    streakDay: streak + 1)
                context.insert(entry)
            }
        }
        saveFeedback.toggle()
        try? context.save()
        dismiss()
    }

    /// Resolve the entry's stored instant: the day's start-of-day plus the parsed minute-of-day when a
    /// time was given, otherwise the day at noon (a stable placeholder distinct from the epoch).
    private func composedTimestamp(on day: Date) -> Date {
        let start = cal.startOfDay(for: day)
        if let minute = parseFlexibleMinute(timeText) {
            return cal.date(byAdding: .minute, value: minute, to: start) ?? day
        }
        // No explicit time: stamp "now" when targeting today, else the day's noon, so sorting is stable.
        if cal.isDateInToday(day) { return .now }
        return cal.date(byAdding: .hour, value: 12, to: start) ?? start
    }

    private func minuteOfDay(of entry: JournalEntry) -> Int? {
        let comps = cal.dateComponents([.hour, .minute], from: entry.createdAt)
        guard let hour = comps.hour, let minute = comps.minute else { return nil }
        let total = hour * 60 + minute
        return total == 0 ? nil : total
    }

    private func savePhotosToFiles(_ dataList: [Data]) -> [String] {
        guard let docsURL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first
        else { return [] }
        let journalDir = docsURL.appendingPathComponent("JournalPhotos", isDirectory: true)
        try? FileManager.default.createDirectory(at: journalDir, withIntermediateDirectories: true)
        return dataList.compactMap { data -> String? in
            let file = journalDir.appendingPathComponent(UUID().uuidString + ".jpg")
            guard (try? data.write(to: file)) != nil else { return nil }
            return file.absoluteString
        }
    }

    /// Remove on-disk photo files for the given saved URIs (used to clean up images an edit replaced).
    private func deletePhotoFiles(_ uris: [String]) {
        for uri in uris {
            guard let url = URL(string: uri) else { continue }
            try? FileManager.default.removeItem(at: url)
        }
    }
}

// MARK: - Per-field AI polish menu

/// A "✨" affordance for a single text field: a menu offering on-device grammar fix and
/// expand/rewrite, plus one-tap undo of the last AI apply so the edit is non-destructive.
/// Ports Android's `JournalAiAssistButton` (JournalScreen.kt), backed by the shared
/// Foundation Models text tools instead of ML Kit.
private struct JournalAiPolishMenu: View {
    let text: String
    let tools: ChronosTextTools
    let onApply: (String) -> Void
    let onMessage: (String) -> Void

    @State private var busy = false
    /// Field text from before the last AI apply, offered as "Undo AI edit".
    @State private var undoTo: String?

    var body: some View {
        Menu {
            Button {
                run { await tools.run(.proofread, on: $0) }
            } label: {
                Label("Fix grammar", systemImage: "text.badge.checkmark")
            }
            Button {
                run { await tools.expandReflection($0) }
            } label: {
                Label("Expand / rewrite", systemImage: "text.append")
            }
            if let original = undoTo {
                Button {
                    onApply(original)
                    undoTo = nil
                    onMessage("Reverted")
                } label: {
                    Label("Undo AI edit", systemImage: "arrow.uturn.backward")
                }
            }
        } label: {
            if busy {
                ProgressView().controlSize(.small)
            } else {
                Image(systemName: "sparkles")
                    .font(.chronosBody)
                    .foregroundStyle(ChronosColors.brandPrimary)
            }
        }
        .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || busy)
        .accessibilityLabel("Polish with AI")
    }

    /// Run a transform on the field's current text and apply/report the outcome
    /// (Android `JournalAiAssistButton.handle`).
    private func run(_ transform: @escaping (String) async -> String?) {
        let original = text
        busy = true
        Task { @MainActor in
            defer { busy = false }
            guard let result = await transform(original) else {
                onMessage("On-device AI isn't available right now")
                return
            }
            if result == original {
                onMessage("AI had no changes to suggest")
            } else {
                onApply(result)
                undoTo = original
                onMessage("Updated by AI — undo from the ✨ menu")
            }
        }
    }
}

// MARK: - Journal page parity labels
//
// Small string helpers ported verbatim from Android `JournalScreen.kt` (they have no ChronosCore
// home yet). Kept here, alongside the editor, so the page and editor share one source of truth.

/// Subtitle for the page history section, reflecting an active `query` filter. Mirrors Android's
/// `journalHistoryPageSummary` ("$shown of $total reflections match").
func journalHistoryPageSummary(total: Int, shown: Int, query: String) -> String {
    let label = total == 1 ? "reflection" : "reflections"
    if query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
        return "\(total) \(label)"
    }
    return "\(shown) of \(total) \(label) match"
}

/// Header label for a day group in the history list, e.g. "Mon, Jun 16 · 2 entries". Mirrors
/// Android's `journalDayGroupLabel`.
func journalDayGroupLabel(_ date: Date, count: Int, calendar: Calendar = .current) -> String {
    let day = calendar.isDateInToday(date)
        ? "Today"
        : date.formatted(.dateTime.weekday(.abbreviated).month(.abbreviated).day())
    let suffix = count == 1 ? "1 entry" : "\(count) entries"
    return "\(day) · \(suffix)"
}

/// Summary line for a multiday save, e.g. "Adds 5 entries, one per day." Mirrors Android's
/// `journalRangeSummary`.
func journalRangeSummary(_ dayCount: Int) -> String {
    dayCount <= 1 ? "Adds 1 entry." : "Adds \(dayCount) entries, one per day."
}
