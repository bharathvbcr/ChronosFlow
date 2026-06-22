import SwiftUI
import SwiftData
import PhotosUI
import HealthKit
import FoundationModels
import ChronosCore

// MARK: - JournalView
//
// Full Android-parity journal screen. Ports, in addition to the original emoji mood picker /
// prompt shuffle / photo attachments / history:
//   • Multi-entry per day — a primary reflection plus subtask-style "points", each with an
//     optional time-of-day. Sub-notes inside the primary entry are serialized as bullet lines
//     via ChronosCore (journalParseBody / journalSerializeBody). (J01, J05, J09)
//   • A calendar month overview painting mood emoji + workout badges per day, with month nav and
//     mood/workout filter pills (journalMoodByDate / journalWorkoutDates / journalWrittenDates). (J02)
//   • HealthKit workout import as journal points (HKSampleQuery over .workoutType()). (J04)
//   • An on-device AI insight card (Foundation Models) with a deterministic offline heuristic
//     fallback built from buildJournalInsightPrompt + the recent entries. (J03)
//   • Optional time-of-day per entry via parseFlexibleMinute / formatDisplayMinute. (J05)
//   • Deterministic daily prompt (journalPromptOfTheDay) + manual shuffle. (J06)
//   • Progressive "Add details" disclosure with a live summary line + search/filter over history.
//
// Model note: `JournalEntry` (Wellbeing.swift) has no `entryMinuteOfDay` column, so a point's time
// is carried on `createdAt` (a real instant on the entry's day) — `entryDate` stays start-of-day for
// keying. `minuteOfDay(of:)` derives the display/sort time from `createdAt`. Workout points are
// stamped with the `journalWorkoutIdPrefix` id convention so `isJournalWorkoutEntry` recognises them.

struct JournalView: View {
    @Environment(\.modelContext) private var context
    @Query(sort: \JournalEntry.entryDate, order: .reverse) private var entries: [JournalEntry]

    // Composer state
    @State private var draft = ""
    @State private var highlights: [String] = []
    @State private var selectedMood: JournalMood = .unset
    @State private var timeText = ""
    @State private var dictation = DictationController()
    @State private var pendingPhotos: [PhotosPickerItem] = []
    @State private var pendingPhotoData: [Data] = []
    @State private var showDetails = false
    @State private var showCamera = false
    @State private var showAIInsights = false

    // Inline point adder
    @State private var pointDraft = ""
    @State private var pointTimeText = ""

    // Prompt state (deterministic daily base + manual shuffle offset)
    @State private var promptOffset = 0

    // Calendar overview
    @State private var visibleMonth: Date = Calendar.current.startOfDay(for: .now)
    @State private var showMoodLayer = true
    @State private var showWorkoutLayer = true

    // History search
    @State private var searchText = ""

    // AI insight
    @State private var insight = JournalInsightEngine()

    // HealthKit workout import
    @State private var workouts = JournalWorkoutImporter()

    private let cal = Calendar.current

    // MARK: Derived collections

    private var todayEntry: JournalEntry? {
        entries.first { cal.isDateInToday($0.entryDate) && $0.isPrimary }
    }

    /// Value-typed snapshot of all entries for the ChronosCore overview helpers.
    private var records: [JournalEntryRecord] {
        entries.map { entry in
            JournalEntryRecord(
                id: entry.id,
                entryDate: entry.entryDate,
                body: entry.body,
                isPrimary: entry.isPrimary,
                dayRating: entry.moodRating > 0 ? entry.moodRating : nil,
                entryMinuteOfDay: minuteOfDay(of: entry),
                createdAt: entry.createdAt)
        }
    }

    private var moodByDate: [Date: ChronosCore.JournalMood] { journalMoodByDate(records, calendar: cal) }
    private var workoutDates: Set<Date> { journalWorkoutDates(records, calendar: cal) }
    private var writtenDates: Set<Date> { journalWrittenDates(records, calendar: cal) }

    // MARK: Streak

    private var currentStreak: Int {
        let today = cal.startOfDay(for: .now)
        let entryDates = Set(entries.map { cal.startOfDay(for: $0.entryDate) })
        var streak = 0
        var cursor = today
        if entryDates.contains(today) {
            streak = 1
            cursor = cal.date(byAdding: .day, value: -1, to: today) ?? today
        } else {
            cursor = cal.date(byAdding: .day, value: -1, to: today) ?? today
        }
        while entryDates.contains(cursor) {
            streak += 1
            cursor = cal.date(byAdding: .day, value: -1, to: cursor) ?? cursor
        }
        return streak
    }

    // MARK: Composer helpers

    private var serializedDraft: String { journalSerializeBody(mainNote: draft, subNotes: highlights) }
    private var canSave: Bool { journalCanSave(serializedDraft) }
    private var isOnlyPrompts: Bool { journalIsOnlyPrompts(serializedDraft) }

    /// The reflection prompt to show: deterministic daily base shifted by the manual shuffle offset.
    private var activePrompt: String {
        journalPrompt(date: .now, calendar: cal, offset: promptOffset)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: ChronosSpacing.medium) {
                    headerRow
                    composerCard
                    pointsSection
                    workoutImportCard
                    aiInsightCard
                    calendarOverview
                    historySection
                }
                .padding(ChronosSpacing.standard)
            }
            .background { ChronosBackdrop() }
            .navigationTitle("Journal")
            .chronosScrollMinimizedBar()
            .toolbar { aiInsightsToolbarItem }
            .onAppear {
                prefillFromExistingEntry()
                workouts.refreshAvailability()
            }
            .onChange(of: dictation.transcript) { _, new in
                guard !new.isEmpty else { return }
                appendDictation(new)
                dictation.clearTranscript()
            }
            .onDisappear { dictation.stop() }
            .onChange(of: pendingPhotos) { _, items in
                Task { await loadPickedPhotos(items) }
            }
            .sheet(isPresented: $showAIInsights) {
                AssistantSheet()
            }
            .sheet(isPresented: $showCamera) {
                CameraPickerView { data in
                    if let data { pendingPhotoData.append(data) }
                }
            }
        }
    }

    // MARK: Header

    private var headerRow: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Today's reflection")
                    .font(.chronosTitle)
                if let saved = todayEntry {
                    Text(journalLastSavedLabel(updatedAt: saved.updatedAt, now: .now))
                        .font(.chronosCaption).foregroundStyle(.secondary)
                }
            }
            Spacer()
            if currentStreak > 0 {
                streakBadge
            }
        }
    }

    private var streakBadge: some View {
        Label("\(currentStreak) day streak", systemImage: "flame.fill")
            .font(.chronosCaption)
            .foregroundStyle(ChronosColors.brandAccent)
            .padding(.horizontal, ChronosSpacing.compact)
            .padding(.vertical, ChronosSpacing.micro)
            .background(ChronosColors.brandAccent.opacity(0.15), in: Capsule())
    }

    @ToolbarContentBuilder
    private var aiInsightsToolbarItem: some ToolbarContent {
        ToolbarItem(placement: .primaryAction) {
            Button {
                showAIInsights = true
            } label: {
                Label("Assistant", systemImage: "bubble.left.and.text.bubble.right")
                    .font(.chronosCaption)
            }
        }
    }

    // MARK: Composer card

    private var composerCard: some View {
        ChronosGlassPanel {
            VStack(alignment: .leading, spacing: ChronosSpacing.standard) {

                shufflePromptRow

                // Main text field (placeholder = the day's prompt, mirroring Android).
                TextField(activePrompt.isEmpty ? "What shaped your day?" : activePrompt,
                          text: $draft, axis: .vertical)
                    .lineLimit(4...14)
                    .font(.chronosBody)

                HStack {
                    dictationButton
                    Spacer()
                    Text(journalMeterLabel(serializedDraft))
                        .font(.chronosCaption).foregroundStyle(.secondary)
                }

                if isOnlyPrompts {
                    Text("Add your own words below the prompts to save.")
                        .font(.chronosCaption).foregroundStyle(.secondary)
                }

                // Progressive "Add details" disclosure with a live summary.
                DisclosureGroup(isExpanded: $showDetails) {
                    VStack(alignment: .leading, spacing: ChronosSpacing.medium) {
                        highlightsEditor
                        timeOfDayField
                        moodPicker
                        photoAttachmentSection
                    }
                    .padding(.top, ChronosSpacing.small)
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Label("Add details", systemImage: "chevron.down.circle")
                            .font(.chronosLabel)
                            .foregroundStyle(ChronosColors.brandPrimary)
                        if !detailsSummary.isEmpty {
                            Text(detailsSummary)
                                .font(.chronosCaption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .tint(ChronosColors.brandPrimary)

                if !pendingPhotoData.isEmpty {
                    photoThumbnailStrip
                }

                Button(action: save) {
                    Text("Save entry")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(!canSave)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    /// Live "1 highlight · 8:00 AM · 2 photos" summary under the collapsed "Add details" label.
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
        if selectedMood != .unset {
            parts.append(selectedMood.label)
        }
        return parts.joined(separator: " · ")
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
                Image(systemName: "dice")
                    .font(.chronosBody)
                    .foregroundStyle(ChronosColors.brandSecondary)
                    .padding(ChronosSpacing.small)
                    .background(ChronosColors.brandSecondary.opacity(0.12), in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Shuffle prompt")
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
                    TextField("Highlight", text: Binding(
                        get: { idx < highlights.count ? highlights[idx] : "" },
                        set: { if idx < highlights.count { highlights[idx] = $0 } }))
                        .font(.chronosBody)
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
                Label("Add highlight", systemImage: "plus.circle")
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
                TextField("e.g. 9:00 AM", text: $timeText)
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
        }
    }

    // MARK: Inline point adder ("Add a point")

    private var pointsSection: some View {
        ChronosGlassCard {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                Text("Add a point")
                    .font(.chronosHeadline)
                    .foregroundStyle(.secondary)
                Text("Quick timed notes for today — a moment, a thought, a win.")
                    .font(.chronosCaption)
                    .foregroundStyle(.secondary)

                HStack(alignment: .top, spacing: ChronosSpacing.small) {
                    TextField("What happened?", text: $pointDraft, axis: .vertical)
                        .lineLimit(1...4)
                        .font(.chronosBody)
                    TextField("9:00 AM", text: $pointTimeText)
                        .font(.chronosCaption)
                        .frame(width: 88)
                        .textInputAutocapitalization(.characters)
                }

                Button {
                    addPoint()
                } label: {
                    Label("Add point", systemImage: "plus")
                        .font(.chronosCaption)
                }
                .buttonStyle(.bordered)
                .disabled(pointDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: Mood picker

    private var moodPicker: some View {
        VStack(alignment: .leading, spacing: ChronosSpacing.small) {
            Text(selectedMood == .unset ? "How are you feeling?" : selectedMood.label)
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
                    Label("Library", systemImage: "photo.on.rectangle")
                        .font(.chronosCaption)
                        .padding(.horizontal, ChronosSpacing.compact)
                        .padding(.vertical, ChronosSpacing.small)
                        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: ChronosRadius.extraSmall))
                }
                .buttonStyle(.plain)

                Button {
                    showCamera = true
                } label: {
                    Label("Camera", systemImage: "camera")
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

    // MARK: Workout import card (HealthKit)

    @ViewBuilder
    private var workoutImportCard: some View {
        switch workouts.availability {
        case .unavailable:
            EmptyView()
        default:
            ChronosGlassCard(tint: ChronosColors.brandSecondary) {
                VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                    Label("Import workouts", systemImage: "figure.run")
                        .font(.chronosHeadline)
                    Text("Add your recent HealthKit workouts as journal points.")
                        .font(.chronosCaption)
                        .foregroundStyle(.secondary)

                    if workouts.isImporting {
                        HStack(spacing: ChronosSpacing.small) {
                            ProgressView()
                            Text("Importing…").font(.chronosCaption).foregroundStyle(.secondary)
                        }
                    } else if let result = workouts.lastResult {
                        Text(result.inserted == 0
                             ? "Up to date — no new workouts."
                             : "Imported \(result.inserted) workout\(result.inserted == 1 ? "" : "s").")
                            .font(.chronosCaption)
                            .foregroundStyle(ChronosColors.brandSecondary)
                    }

                    Button {
                        Task {
                            if workouts.availability == .needsAuthorization {
                                await workouts.requestAuthorization()
                            }
                            await workouts.importRecent(into: context)
                        }
                    } label: {
                        Label(workouts.availability == .needsAuthorization ? "Connect HealthKit" : "Import",
                              systemImage: "square.and.arrow.down")
                            .font(.chronosCaption)
                    }
                    .buttonStyle(.bordered)
                    .disabled(workouts.isImporting)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    // MARK: AI insight card

    @ViewBuilder
    private var aiInsightCard: some View {
        if !records.isEmpty {
            ChronosGlassCard(tint: ChronosColors.brandPrimary) {
                VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                    HStack {
                        Label("Reflection insight", systemImage: "sparkles")
                            .font(.chronosHeadline)
                        Spacer()
                        if case .ready(_, let fromAi) = insight.state {
                            Text(fromAi ? "On-device AI" : "Summary")
                                .font(.chronosCaption)
                                .foregroundStyle(.secondary)
                        }
                    }

                    switch insight.state {
                    case .idle:
                        Text("Notice patterns in your mood and recurring themes from recent entries.")
                            .font(.chronosCaption)
                            .foregroundStyle(.secondary)
                        Button {
                            generateInsight()
                        } label: {
                            Label("Reflect on recent entries", systemImage: "wand.and.stars")
                                .font(.chronosCaption)
                        }
                        .buttonStyle(.bordered)

                    case .loading:
                        HStack(spacing: ChronosSpacing.small) {
                            ProgressView()
                            Text("Reflecting…").font(.chronosCaption).foregroundStyle(.secondary)
                        }

                    case .ready(let text, _):
                        Text(text).font(.chronosBody)
                        HStack(spacing: ChronosSpacing.standard) {
                            Button {
                                generateInsight()
                            } label: {
                                Label("Regenerate", systemImage: "arrow.clockwise").font(.chronosCaption)
                            }
                            .buttonStyle(.plain)
                            .foregroundStyle(ChronosColors.brandPrimary)
                            Button {
                                withAnimation(ChronosMotion.smooth) { insight.dismiss() }
                            } label: {
                                Text("Dismiss").font(.chronosCaption)
                            }
                            .buttonStyle(.plain)
                            .foregroundStyle(.secondary)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private func generateInsight() {
        let snapshot = journalSortedPoints(records.filter { !isJournalWorkoutEntry($0) })
            .filter { $0.isPrimary || $0.dayRating != nil }
        // Newest first for the prompt builder.
        let ordered = snapshot.sorted { $0.entryDate > $1.entryDate }
        Task { await insight.generate(from: ordered, today: .now, calendar: cal) }
    }

    // MARK: Calendar overview

    private var calendarOverview: some View {
        ChronosGlassCard {
            VStack(alignment: .leading, spacing: ChronosSpacing.compact) {
                HStack {
                    Button {
                        withAnimation(ChronosMotion.snappy) { shiftMonth(by: -1) }
                    } label: { Image(systemName: "chevron.left") }
                        .buttonStyle(.plain)
                    Spacer()
                    Text(visibleMonth.formatted(.dateTime.month(.wide).year()))
                        .font(.chronosHeadline)
                    Spacer()
                    Button {
                        withAnimation(ChronosMotion.snappy) { shiftMonth(by: 1) }
                    } label: { Image(systemName: "chevron.right") }
                        .buttonStyle(.plain)
                        .disabled(isCurrentMonth)
                }

                HStack(spacing: ChronosSpacing.small) {
                    filterPill("Mood", systemImage: "face.smiling", on: showMoodLayer) {
                        withAnimation(ChronosMotion.snappy) { showMoodLayer.toggle() }
                    }
                    filterPill("Workouts", systemImage: "figure.run", on: showWorkoutLayer) {
                        withAnimation(ChronosMotion.snappy) { showWorkoutLayer.toggle() }
                    }
                }

                weekdayHeader
                monthGrid
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func filterPill(_ title: String, systemImage: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(title, systemImage: systemImage)
                .font(.chronosCaption)
                .foregroundStyle(on ? ChronosColors.brandPrimary : .secondary)
                .padding(.horizontal, ChronosSpacing.compact)
                .padding(.vertical, ChronosSpacing.micro)
                .background((on ? ChronosColors.brandPrimary : Color.secondary).opacity(0.14), in: Capsule())
        }
        .buttonStyle(.plain)
    }

    private var weekdayHeader: some View {
        let symbols = cal.veryShortStandaloneWeekdaySymbols
        let ordered = Array(symbols[(cal.firstWeekday - 1)...] + symbols[..<(cal.firstWeekday - 1)])
        return HStack(spacing: 0) {
            ForEach(Array(ordered.enumerated()), id: \.offset) { _, sym in
                Text(sym)
                    .font(.chronosCaption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    private var monthGrid: some View {
        let days = monthDays()
        let columns = Array(repeating: GridItem(.flexible(), spacing: 4), count: 7)
        return LazyVGrid(columns: columns, spacing: 4) {
            ForEach(Array(days.enumerated()), id: \.offset) { _, day in
                calendarCell(day)
            }
        }
    }

    @ViewBuilder
    private func calendarCell(_ day: Date?) -> some View {
        if let day {
            let key = cal.startOfDay(for: day)
            let mood = showMoodLayer ? moodByDate[key] : nil
            let hasWorkout = showWorkoutLayer && workoutDates.contains(key)
            let wrote = writtenDates.contains(key)
            let isToday = cal.isDateInToday(day)
            VStack(spacing: 1) {
                ZStack {
                    if let mood {
                        Text(mood.emoji).font(.system(size: 18))
                    } else {
                        Text("\(cal.component(.day, from: day))")
                            .font(.chronosCaption)
                            .foregroundStyle(wrote ? .primary : .secondary)
                    }
                    if hasWorkout {
                        Image(systemName: "figure.run")
                            .font(.system(size: 8))
                            .foregroundStyle(ChronosColors.brandSecondary)
                            .offset(x: 11, y: -11)
                    }
                }
                .frame(height: 24)
                Circle()
                    .fill(wrote ? ChronosColors.brandPrimary : .clear)
                    .frame(width: 4, height: 4)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 2)
            .background(isToday ? ChronosColors.brandPrimary.opacity(0.12) : .clear,
                        in: RoundedRectangle(cornerRadius: ChronosRadius.extraSmall, style: .continuous))
        } else {
            Color.clear.frame(height: 32)
        }
    }

    private var isCurrentMonth: Bool {
        cal.isDate(visibleMonth, equalTo: .now, toGranularity: .month)
    }

    private func shiftMonth(by months: Int) {
        if let next = cal.date(byAdding: .month, value: months, to: visibleMonth) {
            visibleMonth = cal.startOfDay(for: next)
        }
    }

    /// The visible month's day cells, padded with leading nils so the 1st lands under its weekday.
    private func monthDays() -> [Date?] {
        guard let monthInterval = cal.dateInterval(of: .month, for: visibleMonth),
              let range = cal.range(of: .day, in: .month, for: visibleMonth) else { return [] }
        let firstWeekday = cal.component(.weekday, from: monthInterval.start)
        let leading = (firstWeekday - cal.firstWeekday + 7) % 7
        var cells: [Date?] = Array(repeating: nil, count: leading)
        for offset in 0..<range.count {
            if let day = cal.date(byAdding: .day, value: offset, to: monthInterval.start) {
                cells.append(day)
            }
        }
        return cells
    }

    // MARK: Actions

    private func prefillFromExistingEntry() {
        guard let saved = todayEntry else { return }
        let parts = journalParseBody(saved.body)
        draft = parts.mainNote
        highlights = parts.subNotes
        selectedMood = JournalMood(rawValue: saved.moodRating) ?? .unset
        // Auto-expand "Add details" when the entry already carries any details (Android parity).
        if !highlights.isEmpty || selectedMood != .unset || !saved.photoUris.isEmpty {
            showDetails = true
        }
        pendingPhotoData = saved.photoUris.compactMap { path -> Data? in
            guard let url = URL(string: path) else { return nil }
            return try? Data(contentsOf: url)
        }
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
        // Drop empty highlight rows, then serialize main note + bullets the Android way.
        highlights = highlights.filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        let cleaned = journalCleanForSave(serializedDraft)
        guard !cleaned.isEmpty else { return }

        let streak = currentStreak
        let savedPaths = savePhotosToFiles(pendingPhotoData)
        // Record which shuffled prompt was active (parity with Android's shuffledPromptKey).
        let promptKey = "offset-\(promptOffset)"
        // Optional time-of-day → a real instant on today carried via createdAt.
        let timestamp = composedTimestamp(on: .now)

        if let existing = todayEntry {
            existing.body = cleaned
            existing.updatedAt = .now
            existing.createdAt = timestamp
            existing.moodRating = selectedMood.rawValue
            existing.photoUris = savedPaths
            existing.shuffledPromptKey = promptKey
            existing.streakDay = streak
        } else {
            let entry = JournalEntry(
                entryDate: .now,
                createdAt: timestamp,
                updatedAt: .now,
                body: cleaned,
                promptType: promptKey,
                moodRating: selectedMood.rawValue,
                photoUris: savedPaths,
                shuffledPromptKey: promptKey,
                streakDay: streak + 1)
            context.insert(entry)
        }
        try? context.save()
    }

    /// Create a secondary, timed journal point for today from the inline adder.
    private func addPoint() {
        let body = pointDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { return }
        let timestamp = composedTimestamp(on: .now, timeText: pointTimeText)
        let entry = JournalEntry(
            entryDate: .now,
            createdAt: timestamp,
            updatedAt: .now,
            body: body,
            isPrimary: false,
            moodRating: 0)
        context.insert(entry)
        try? context.save()
        withAnimation(ChronosMotion.snappy) {
            pointDraft = ""
            pointTimeText = ""
        }
    }

    /// Resolve the entry's stored instant: the day's start-of-day plus the parsed minute-of-day when a
    /// time was given, otherwise `now`. Lets `minuteOfDay(of:)` recover the display/sort time.
    private func composedTimestamp(on day: Date, timeText text: String? = nil) -> Date {
        let source = text ?? timeText
        if let minute = parseFlexibleMinute(source) {
            let start = cal.startOfDay(for: day)
            return cal.date(byAdding: .minute, value: minute, to: start) ?? day
        }
        return .now
    }

    /// Minute-of-day for an entry, derived from its `createdAt` instant (0–1439), or nil for the
    /// auto-stamped epoch placeholder.
    private func minuteOfDay(of entry: JournalEntry) -> Int? {
        let comps = cal.dateComponents([.hour, .minute], from: entry.createdAt)
        guard let hour = comps.hour, let minute = comps.minute else { return nil }
        return hour * 60 + minute
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

    // MARK: History

    private var filteredEntries: [JournalEntry] {
        guard !searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return entries }
        return entries.filter { $0.body.range(of: searchText, options: .caseInsensitive) != nil }
    }

    private var groupedHistory: [(key: Date, entries: [JournalEntry])] {
        let dict = Dictionary(grouping: filteredEntries) { cal.startOfDay(for: $0.entryDate) }
        return dict
            .map { (key: $0.key, entries: sortedDayEntries($0.value)) }
            .sorted { $0.key > $1.key }
    }

    /// Day entries ordered like Android's list: primary first, then by time-of-day, then creation.
    private func sortedDayEntries(_ dayEntries: [JournalEntry]) -> [JournalEntry] {
        dayEntries.sorted { lhs, rhs in
            if lhs.isPrimary != rhs.isPrimary { return lhs.isPrimary }
            let lm = minuteOfDay(of: lhs) ?? Int.max
            let rm = minuteOfDay(of: rhs) ?? Int.max
            if lm != rm { return lm < rm }
            return lhs.createdAt < rhs.createdAt
        }
    }

    @ViewBuilder
    private var historySection: some View {
        if entries.isEmpty {
            ContentUnavailableView(
                "No reflections yet",
                systemImage: "book.closed",
                description: Text("Your saved reflections will appear here."))
                .padding(.top, ChronosSpacing.large)
        } else {
            HStack {
                Text("History").font(.chronosTitle)
                Spacer()
                if !searchText.isEmpty {
                    Text("\(filteredEntries.count) of \(entries.count)")
                        .font(.chronosCaption)
                        .foregroundStyle(.secondary)
                }
            }

            HStack(spacing: ChronosSpacing.small) {
                Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                TextField("Search reflections", text: $searchText)
                    .font(.chronosBody)
                if !searchText.isEmpty {
                    Button {
                        searchText = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(ChronosSpacing.compact)
            .background(.thinMaterial, in: RoundedRectangle(cornerRadius: ChronosRadius.small, style: .continuous))

            ForEach(groupedHistory, id: \.key) { group in
                let dayLabel = cal.isDateInToday(group.key)
                    ? "Today"
                    : group.key.formatted(.dateTime.weekday(.wide).month().day())

                VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                    Text(dayLabel)
                        .font(.chronosHeadline)
                        .foregroundStyle(.secondary)
                        .padding(.top, ChronosSpacing.small)

                    ForEach(group.entries) { entry in
                        JournalHistoryCard(entry: entry, minuteOfDay: minuteOfDay(of: entry)) {
                            context.delete(entry)
                            try? context.save()
                        }
                    }
                }
            }
        }
    }
}

// MARK: - JournalHistoryCard

private struct JournalHistoryCard: View {
    let entry: JournalEntry
    let minuteOfDay: Int?
    let onDelete: () -> Void
    @State private var expanded = false

    private var mood: JournalMood { JournalMood(rawValue: entry.moodRating) ?? .unset }
    private var isWorkout: Bool { entry.id.hasPrefix(journalWorkoutIdPrefix) }
    private var parts: JournalBodyParts { journalParseBody(entry.body) }

    var body: some View {
        ChronosGlassCard(tint: isWorkout ? ChronosColors.brandSecondary : nil) {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                HStack(spacing: ChronosSpacing.small) {
                    if isWorkout {
                        Image(systemName: "figure.run").foregroundStyle(ChronosColors.brandSecondary)
                    }
                    if let minute = minuteOfDay {
                        Text(formatDisplayMinute(minute))
                            .font(.chronosCaption).foregroundStyle(.secondary)
                    } else {
                        Text(entry.entryDate.formatted(.dateTime.hour().minute()))
                            .font(.chronosCaption).foregroundStyle(.secondary)
                    }
                    if mood != .unset {
                        Text(mood.emoji).font(.chronosCaption)
                    }
                    if !entry.isPrimary && !isWorkout {
                        Text("point")
                            .font(.chronosCaption)
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 6).padding(.vertical, 1)
                            .background(Color.secondary.opacity(0.14), in: Capsule())
                    }
                    Spacer()
                    if entry.streakDay > 0 && entry.isPrimary {
                        Label("\(entry.streakDay)", systemImage: "flame.fill")
                            .font(.chronosCaption)
                            .foregroundStyle(ChronosColors.brandAccent)
                    }
                }

                if !parts.mainNote.isEmpty {
                    Text(parts.mainNote)
                        .font(.chronosBody)
                        .lineLimit(expanded ? nil : 4)
                }

                // Sub-note highlights as a bullet list.
                if !parts.subNotes.isEmpty {
                    VStack(alignment: .leading, spacing: 2) {
                        ForEach(Array(parts.subNotes.enumerated()), id: \.offset) { _, note in
                            HStack(alignment: .top, spacing: ChronosSpacing.small) {
                                Text("•").foregroundStyle(.secondary)
                                Text(note).font(.chronosBody)
                            }
                        }
                    }
                }

                if isLong {
                    Button(expanded ? "Show less" : "Show more") {
                        withAnimation(ChronosMotion.smooth) { expanded.toggle() }
                    }
                    .font(.chronosCaption)
                    .buttonStyle(.plain)
                    .foregroundStyle(ChronosColors.brandPrimary)
                }

                if !entry.photoUris.isEmpty {
                    savedPhotoStrip
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        // The history list lives in a ScrollView (not a List), so `.swipeActions` wouldn't fire here;
        // a context menu is the portable delete affordance.
        .contextMenu {
            Button(role: .destructive, action: onDelete) {
                Label("Delete entry", systemImage: "trash")
            }
        }
    }

    private var isLong: Bool {
        parts.mainNote.count > 220 || parts.mainNote.filter { $0 == "\n" }.count >= 4
    }

    private var savedPhotoStrip: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: ChronosSpacing.small) {
                ForEach(entry.photoUris, id: \.self) { path in
                    if let url = URL(string: path),
                       let data = try? Data(contentsOf: url),
                       let uiImage = UIImage(data: data) {
                        Image(uiImage: uiImage)
                            .resizable()
                            .scaledToFill()
                            .frame(width: 56, height: 56)
                            .clipShape(RoundedRectangle(cornerRadius: ChronosRadius.extraSmall, style: .continuous))
                    }
                }
            }
        }
    }
}

// MARK: - JournalInsightEngine
//
// On-device AI reflection over recent journal entries with a deterministic offline fallback.
// Uses Foundation Models (iOS 27 `SystemLanguageModel` / `LanguageModelSession`) when Apple
// Intelligence is available; otherwise it falls back to a local heuristic (average mood + recurring
// word themes) so the card is always useful. The AI prompt is built by ChronosCore's
// `buildJournalInsightPrompt` for parity with Android.

@MainActor
@Observable
final class JournalInsightEngine {
    enum State: Equatable {
        case idle
        case loading
        /// `fromAi` is false when the deterministic offline heuristic produced the text.
        case ready(String, fromAi: Bool)
    }

    private(set) var state: State = .idle

    private var canUseModel: Bool {
        if case .available = SystemLanguageModel.default.availability { return true }
        return false
    }

    func dismiss() { state = .idle }

    func generate(from entries: [JournalEntryRecord], today: Date, calendar: Calendar) async {
        guard !entries.isEmpty else { return }
        state = .loading

        if canUseModel {
            let prompt = buildJournalInsightPrompt(entries: entries, today: today, calendar: calendar)
            let session = LanguageModelSession(
                instructions: Instructions {
                    "You are a warm, concise journaling companion. From the supplied reflections only, "
                    + "note any patterns in mood or recurring themes in 2-3 short sentences, then offer "
                    + "one gentle, encouraging suggestion. Avoid clinical or therapy language."
                })
            do {
                let response = try await session.respond(
                    to: prompt,
                    options: GenerationOptions(temperature: GenerationProfile.balanced.temperature))
                let text = response.content.trimmingCharacters(in: .whitespacesAndNewlines)
                if !text.isEmpty {
                    state = .ready(text, fromAi: true)
                    return
                }
            } catch {
                // fall through to the offline heuristic
            }
        }

        state = .ready(Self.offlineInsight(from: entries), fromAi: false)
    }

    /// Deterministic, no-network reflection: average mood band + the most-recurring meaningful word.
    static func offlineInsight(from entries: [JournalEntryRecord]) -> String {
        let recent = Array(entries.prefix(14))
        let ratings = recent.compactMap(\.dayRating)
        var lines: [String] = []

        if !ratings.isEmpty {
            let avg = Double(ratings.reduce(0, +)) / Double(ratings.count)
            let band: String
            switch avg {
            case ..<2.0: band = "These days have felt heavy"
            case ..<3.0: band = "Things have felt a bit low lately"
            case ..<4.0: band = "Your days have felt steady"
            default: band = "Your days have felt good"
            }
            lines.append("\(band) — average mood \(String(format: "%.1f", avg))/5 across \(ratings.count) day\(ratings.count == 1 ? "" : "s").")
        }

        if let theme = topTheme(in: recent) {
            lines.append("\"\(theme)\" keeps coming up in what you write.")
        }

        lines.append("Take a moment for one small thing that recharges you tonight.")
        return lines.joined(separator: " ")
    }

    /// The most frequent meaningful (4+ letter, non-stopword) lowercased word across the entries.
    private static func topTheme(in entries: [JournalEntryRecord]) -> String? {
        let stop: Set<String> = ["that", "this", "with", "have", "today", "from", "were", "your",
                                 "about", "they", "what", "when", "just", "really", "could", "would",
                                 "there", "their", "them", "then", "than", "been", "felt", "feel",
                                 "more", "some", "much", "very", "into", "over", "after", "still"]
        var counts: [String: Int] = [:]
        for entry in entries {
            let words = entry.body.lowercased()
                .components(separatedBy: CharacterSet.alphanumerics.inverted)
                .filter { $0.count >= 4 && !stop.contains($0) }
            for word in words { counts[word, default: 0] += 1 }
        }
        return counts.filter { $0.value >= 2 }.max { $0.value < $1.value }?.key
    }
}

// MARK: - JournalWorkoutImporter
//
// The iOS-native, read-only HealthKit workout importer — the journal analogue of Android's
// JournalViewModel.importWorkouts (reads Health Connect exercises and writes `hc-workout-` journal
// points). It queries `.workoutType()` over the trailing 30 days and upserts one secondary, timed
// `JournalEntry` per workout, stamping the `journalWorkoutIdPrefix` id so `isJournalWorkoutEntry`
// recognises it and re-runs never duplicate. The point's time-of-day is carried on `createdAt`.

@Observable
@MainActor
final class JournalWorkoutImporter {
    enum Availability: Equatable {
        case available
        case needsAuthorization
        case unavailable
    }

    struct ImportResult: Equatable {
        var inserted = 0
        var skipped = 0
    }

    private let store = HKHealthStore()
    private(set) var availability: Availability = .unavailable
    private(set) var isImporting = false
    private(set) var lastResult: ImportResult?

    private var workoutType: HKWorkoutType { HKObjectType.workoutType() }

    func refreshAvailability() {
        guard HKHealthStore.isHealthDataAvailable() else {
            availability = .unavailable
            return
        }
        switch store.authorizationStatus(for: workoutType) {
        case .sharingAuthorized: availability = .available
        case .notDetermined, .sharingDenied: availability = .needsAuthorization
        @unknown default: availability = .needsAuthorization
        }
    }

    func requestAuthorization() async {
        guard HKHealthStore.isHealthDataAvailable() else {
            availability = .unavailable
            return
        }
        do {
            try await store.requestAuthorization(toShare: [], read: [workoutType])
        } catch {
            // Leave as-is; the import will simply read nothing if access was refused.
        }
        refreshAvailability()
        // Reads keep status `.notDetermined` even after a grant — treat a completed prompt as ready.
        if availability == .needsAuthorization { availability = .available }
    }

    @discardableResult
    func importRecent(into context: ModelContext, days: Int = 30) async -> ImportResult {
        guard HKHealthStore.isHealthDataAvailable() else { return ImportResult() }
        isImporting = true
        defer { isImporting = false }

        let now = Date()
        let start = Calendar.current.date(byAdding: .day, value: -max(days, 1), to: now) ?? now
        let predicate = HKQuery.predicateForSamples(withStart: start, end: now, options: [])
        let workouts = await queryWorkouts(predicate: predicate)

        // Existing workout-point ids to avoid duplicates on re-run.
        let existing = ((try? context.fetch(FetchDescriptor<JournalEntry>())) ?? [])
            .filter { $0.id.hasPrefix(journalWorkoutIdPrefix) }
            .map(\.id)
        let existingIDs = Set(existing)

        var result = ImportResult()
        for workout in workouts {
            let id = journalWorkoutIdPrefix + workout.uuid.uuidString
            if existingIDs.contains(id) { result.skipped += 1; continue }
            let entry = JournalEntry(
                id: id,
                entryDate: workout.startDate,
                createdAt: workout.startDate,
                updatedAt: now,
                body: workoutLabel(workout),
                isPrimary: false,
                moodRating: 0,
                isWorkoutEntry: true)
            context.insert(entry)
            result.inserted += 1
        }
        try? context.save()
        lastResult = result
        return result
    }

    private func queryWorkouts(predicate: NSPredicate) async -> [HKWorkout] {
        await withCheckedContinuation { continuation in
            let sort = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: true)
            let query = HKSampleQuery(
                sampleType: workoutType, predicate: predicate,
                limit: HKObjectQueryNoLimit, sortDescriptors: [sort]
            ) { _, samples, _ in
                continuation.resume(returning: (samples as? [HKWorkout]) ?? [])
            }
            store.execute(query)
        }
    }

    /// A short human label for a workout point, e.g. "Running · 32 min".
    private func workoutLabel(_ workout: HKWorkout) -> String {
        let minutes = Int(workout.duration / 60)
        return "\(workout.workoutActivityType.displayName) · \(minutes) min"
    }
}

// MARK: - HKWorkoutActivityType labels

private extension HKWorkoutActivityType {
    var displayName: String {
        switch self {
        case .running: "Running"
        case .walking: "Walking"
        case .cycling: "Cycling"
        case .swimming: "Swimming"
        case .hiking: "Hiking"
        case .yoga: "Yoga"
        case .functionalStrengthTraining, .traditionalStrengthTraining: "Strength training"
        case .highIntensityIntervalTraining: "HIIT"
        case .coreTraining: "Core training"
        case .pilates: "Pilates"
        case .dance, .cardioDance: "Dance"
        case .elliptical: "Elliptical"
        case .rowing: "Rowing"
        case .stairClimbing, .stairs: "Stair climbing"
        case .tennis: "Tennis"
        case .basketball: "Basketball"
        case .soccer: "Soccer"
        case .golf: "Golf"
        default: "Workout"
        }
    }
}

// MARK: - CameraPickerView (UIViewControllerRepresentable)

/// Wraps UIImagePickerController with camera source for in-app photo capture.
/// Falls back gracefully on simulator (no camera hardware).
private struct CameraPickerView: UIViewControllerRepresentable {
    @Environment(\.dismiss) private var dismiss
    var onCapture: (Data?) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = UIImagePickerController.isSourceTypeAvailable(.camera) ? .camera : .photoLibrary
        picker.allowsEditing = false
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: CameraPickerView

        init(parent: CameraPickerView) { self.parent = parent }

        func imagePickerController(_ picker: UIImagePickerController,
                                   didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            let image = info[.originalImage] as? UIImage
            let data = image?.jpegData(compressionQuality: 0.85)
            parent.onCapture(data)
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.onCapture(nil)
            parent.dismiss()
        }
    }
}

#Preview { JournalView().modelContainer(ChronosStore.previewContainer()) }
