import SwiftUI
import SwiftData
import ChronosCore

/// Create / edit a task, including a reorderable checklist and an offline smart-fill title
/// parser (the iOS port of the Android add-task modal's headline parser). As the title is typed,
/// `ChronosCore.parseSmartFill` extracts due date / time / duration / priority / recurrence and a
/// contact action; the detections surface in a glassy "Detected in your text" card the user can
/// apply (all or per-chip) before they're folded into the draft.
struct TaskEditorSheet: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    /// All tasks — used to flag a gentle duplicate-title warning (Android `existingTaskTitles`) and
    /// to jump to an existing match.
    @Query private var allTasks: [TaskItem]
    /// Goals available to link, for the goal-detection prompt + picker (Android `goalOptions`).
    @Query(sort: \Goal.title) private var goals: [Goal]

    let existing: TaskItem?
    @State private var title: String
    @State private var detail: String
    @State private var priority: Int
    @State private var hasDueDate: Bool
    @State private var dueDate: Date
    @State private var repeats: Bool
    @State private var frequency: RecurrenceSpec.Frequency
    @State private var interval: Int
    /// For a weekly rule: the selected weekday set (1=Sun … 7=Sat). Empty = "same day each week".
    @State private var weekdays: Set<Int>
    /// For a monthly-ordinal rule: which occurrence (1…4, 5 = last) and the target weekday.
    @State private var ordinal: Int
    @State private var ordinalWeekday: Int
    /// Optional recurrence start/end window (Android `startsOn` / `endsOn`).
    @State private var hasRecurrenceEnd: Bool
    @State private var recurrenceEnd: Date
    /// Linked goal id (Android `goalId`); empty = unlinked.
    @State private var goalID: String
    @State private var checklist: [ChecklistItem]
    @State private var newChecklistItem = ""

    /// Invoked with an existing task's id when the user taps "Open the existing task instead" on the
    /// duplicate warning, so the presenter can re-present that task for editing (Android
    /// `onOpenExistingTask`). Optional with a default so existing call sites compile unchanged.
    private let onOpenExistingTask: ((String) -> Void)?

    /// Reusable templates (App-Group backed; no SwiftData). Created once per sheet.
    @State private var templateStore = TaskTemplateStore()
    /// Prompt state for "Save as template" (asks for a name, defaulting to the title).
    @State private var savingTemplate = false
    @State private var templateName = ""

    /// On-device text tools (proofread / rewrite / summarize) for the notes field.
    @State private var textTools = ChronosTextTools()

    /// The most recent smart-fill detection for the typed title, shown in the preview card.
    @State private var smartFill: SmartFillResult?
    /// A detected one-tap action (email / phone / url) kept on the draft after applying.
    @State private var actionHint: ActionHint?

    /// Source-app provenance label ("Shared from …") when this draft was pre-filled from another
    /// app's share. Read once from the App Group for a fresh single-share pre-fill; nil otherwise.
    private let sourceAppLabel: String?

    init(task: TaskItem?, initialTitle: String? = nil, onOpenExistingTask: ((String) -> Void)? = nil) {
        self.existing = task
        self.onOpenExistingTask = onOpenExistingTask
        // Surface the share provenance only for a fresh share-driven new-task pre-fill.
        if task == nil, initialTitle != nil {
            self.sourceAppLabel = TaskEditorSheet.pendingShareSourceApp()
        } else {
            self.sourceAppLabel = nil
        }
        _title = State(initialValue: initialTitle ?? task?.title ?? "")
        _detail = State(initialValue: task?.detail ?? "")
        _priority = State(initialValue: task?.priority ?? 0)
        _hasDueDate = State(initialValue: task?.dueDate != nil)
        _dueDate = State(initialValue: task?.dueDate ?? .now)
        _repeats = State(initialValue: task?.recurrence != nil)
        _frequency = State(initialValue: task?.recurrence?.frequency ?? .daily)
        _interval = State(initialValue: task?.recurrence?.interval ?? 1)
        _weekdays = State(initialValue: task?.recurrence?.weekdays ?? [])
        _ordinal = State(initialValue: task?.recurrence?.ordinal ?? 1)
        _ordinalWeekday = State(initialValue: task?.recurrence?.ordinalWeekday ?? 2)
        _hasRecurrenceEnd = State(initialValue: task?.recurrence?.endsOn != nil)
        _recurrenceEnd = State(initialValue: task?.recurrence?.endsOn
            ?? Calendar.current.date(byAdding: .month, value: 3, to: .now) ?? .now)
        _goalID = State(initialValue: task?.goalID ?? "")
        _checklist = State(initialValue: task?.checklist.sorted { $0.order < $1.order } ?? [])
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Task title", text: $title)
                        .onChange(of: title) { _, newValue in detectSmartFill(newValue) }
                        // Pressing Return/Done submits if the form is valid (Android keyboardActions
                        // onDone). A quick-capture win so a one-field task needs no toolbar tap.
                        .onSubmit {
                            if !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { save() }
                        }
                    if let source = sourceAppLabel {
                        // "Shared from [App]" provenance chip (Android TaskFormSheet ~1128), shown when
                        // the draft was pre-filled from another app's share — styled as a pill to match.
                        HStack(spacing: ChronosSpacing.micro) {
                            Image(systemName: "square.and.arrow.up").font(.caption2)
                            Text("Shared from \(source)")
                        }
                        .font(.chronosCaption)
                        .padding(.horizontal, ChronosSpacing.small)
                        .padding(.vertical, 4)
                        .background(ChronosColors.brandSecondary.opacity(0.15), in: Capsule())
                        .foregroundStyle(ChronosColors.brandSecondary)
                        .listRowSeparator(.hidden)
                    }
                    if let duplicate = duplicateTask {
                        // Gentle duplicate-title warning + jump to the existing task (Android ~1108).
                        VStack(alignment: .leading, spacing: ChronosSpacing.micro) {
                            Label("A task named “\(duplicate.title)” already exists.", systemImage: "exclamationmark.circle")
                                .font(.chronosCaption)
                                .foregroundStyle(ChronosColors.brandAccent)
                            Button("Open the existing task instead") {
                                let id = duplicate.id
                                dismiss()
                                onOpenExistingTask?(id)
                            }
                            .font(.chronosCaption)
                        }
                        .listRowSeparator(.hidden)
                    }
                    TextField("Notes", text: $detail, axis: .vertical)
                    if textTools.isAvailable && !detail.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        Menu {
                            ForEach(ChronosTextOp.allCases) { op in
                                Button { rewriteNotes(op) } label: { Label(op.label, systemImage: op.systemImage) }
                            }
                        } label: {
                            Label(textTools.isWorking ? "Rewriting…" : "AI rewrite", systemImage: "wand.and.sparkles")
                                .font(.chronosCaption)
                        }
                        .disabled(textTools.isWorking)
                    }
                }

                if let fill = smartFill, fill.hasDetection {
                    Section {
                        SmartFillPreviewCard(
                            fill: fill,
                            onApplyAll: { applyAll(fill) },
                            onDismiss: { withAnimation(ChronosMotion.snappy) { smartFill = nil } }
                        )
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                    }
                }

                // "Suggest details with AI" (Android `onRequestAssist` button + suggestion chips).
                // On-device assist surfaces an offline smart-fill pass over the title; tapping the
                // button re-runs detection so the preview card above can be applied.
                Section {
                    Button {
                        requestAssistSuggestions()
                    } label: {
                        Label(smartFill?.hasDetection == true ? "Refresh suggestions" : "Suggest details with AI",
                              systemImage: "wand.and.sparkles")
                            .font(.chronosLabel)
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .tint(ChronosColors.brandPrimary)
                    .disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
                }

                // Goal detection + linking (Android `detectGoalIdFromText` + goal picker). A detection
                // prompt appears when the typed title/notes name an existing goal that isn't linked yet.
                if !goals.isEmpty {
                    Section("Goal") {
                        if let detected = detectedGoal, detected.id != goalID {
                            Button {
                                withAnimation(ChronosMotion.snappy) { goalID = detected.id }
                            } label: {
                                Label("Link to goal: \(detected.title)", systemImage: "link.badge.plus")
                                    .font(.chronosCaption)
                                    .foregroundStyle(ChronosColors.brandPrimary)
                            }
                        }
                        Picker("Linked goal", selection: $goalID) {
                            Text("None").tag("")
                            ForEach(goals) { goal in Text(goal.title).tag(goal.id) }
                        }
                    }
                }

                if let action = actionHint {
                    Section("Action") {
                        Button {
                            open(action)
                        } label: {
                            Label(action.label, systemImage: actionIcon(action.kind))
                        }
                    }
                }

                Section("Priority") {
                    Picker("Priority", selection: $priority) {
                        Text("None").tag(0); Text("Low").tag(1)
                        Text("Medium").tag(2); Text("High").tag(3)
                    }
                    .pickerStyle(.segmented)
                }
                Section("Due") {
                    Toggle("Has due date", isOn: $hasDueDate.animation(ChronosMotion.snappy))
                    if hasDueDate {
                        DatePicker("Due", selection: $dueDate, displayedComponents: [.date, .hourAndMinute])
                    }
                }
                Section("Repeat") {
                    Toggle("Repeats", isOn: $repeats.animation(ChronosMotion.snappy))
                    if repeats {
                        Picker("Frequency", selection: $frequency) {
                            Text("Daily").tag(RecurrenceSpec.Frequency.daily)
                            Text("Weekly").tag(RecurrenceSpec.Frequency.weekly)
                            Text("Monthly").tag(RecurrenceSpec.Frequency.monthly)
                            Text("Monthly (weekday)").tag(RecurrenceSpec.Frequency.monthlyOrdinal)
                        }
                        Stepper("Every \(interval) \(unitLabel)", value: $interval, in: 1...30)
                        if frequency == .weekly {
                            WeekdaySelector(selection: $weekdays)
                            if !weekdays.isEmpty {
                                Text("Repeats on \(RecurrenceSpec.weekdayLabel(for: weekdays)).")
                                    .font(.chronosCaption).foregroundStyle(.secondary)
                            }
                        }
                        if frequency == .monthlyOrdinal {
                            // Ordinal (1st…4th / Last) + target weekday (Android monthly-ordinal UI).
                            Picker("Occurrence", selection: $ordinal) {
                                ForEach(1...5, id: \.self) { n in
                                    Text(RecurrenceSpec.ordinalLabel(n)).tag(n)
                                }
                            }
                            Picker("Weekday", selection: $ordinalWeekday) {
                                ForEach([2, 3, 4, 5, 6, 7, 1], id: \.self) { wd in
                                    Text(RecurrenceSpec.weekdayName(wd)).tag(wd)
                                }
                            }
                            Text("Repeats on the \(RecurrenceSpec.ordinalLabel(ordinal)) \(RecurrenceSpec.weekdayName(ordinalWeekday)) each month.")
                                .font(.chronosCaption).foregroundStyle(.secondary)
                        }
                        // Optional end date for the series (Android `endsOn`). `startsOn` defaults to
                        // the task's anchor day so it isn't surfaced as a separate field here.
                        Toggle("Ends on a date", isOn: $hasRecurrenceEnd.animation(ChronosMotion.snappy))
                        if hasRecurrenceEnd {
                            DatePicker("End date", selection: $recurrenceEnd, displayedComponents: [.date])
                        }
                    }
                }
                Section("Checklist") {
                    ForEach($checklist) { $item in
                        HStack {
                            Button { item.isDone.toggle() } label: {
                                Image(systemName: item.isDone ? "checkmark.circle.fill" : "circle")
                            }.buttonStyle(.plain)
                            TextField("Item", text: $item.text)
                        }
                    }
                    .onMove { checklist.move(fromOffsets: $0, toOffset: $1); reindex() }
                    .onDelete { checklist.remove(atOffsets: $0); reindex() }
                    HStack {
                        // Pasting a list (newlines or "a; b; c") splits into multiple steps on Add.
                        TextField("Add a step — or paste a list", text: $newChecklistItem, axis: .vertical)
                        Button("Add") { addChecklistItem() }
                            .disabled(newChecklistItem.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                    if !descriptionChecklistCandidates.isEmpty {
                        Button {
                            extractChecklistFromDescription()
                        } label: {
                            Label("Add \(descriptionChecklistCandidates.count) step\(descriptionChecklistCandidates.count == 1 ? "" : "s") from notes",
                                  systemImage: "text.badge.plus")
                                .font(.chronosCaption)
                        }
                    }
                }
                if let existing {
                    Section {
                        Button("Delete task", role: .destructive) {
                            context.delete(existing); try? context.save(); dismiss()
                        }
                    }
                }
            }
            .navigationTitle(existing == nil ? "New task" : "Edit task")
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .topBarTrailing) { templatesMenu }
                if existing == nil {
                    ToolbarItem(placement: .bottomBar) {
                        Button {
                            saveDraft(); resetDraft()
                        } label: {
                            Label("Add & new", systemImage: "plus.circle")
                        }
                        .disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", action: save)
                        .disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .alert("Save as template", isPresented: $savingTemplate) {
                TextField("Template name", text: $templateName)
                Button("Cancel", role: .cancel) {}
                Button("Save") { commitTemplate() }
                    .disabled(templateName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            } message: {
                Text("Reuse this task's fields later from the Templates menu.")
            }
        }
    }

    // MARK: - Templates

    @ViewBuilder private var templatesMenu: some View {
        Menu {
            if !templateStore.templates.isEmpty {
                Section("Apply") {
                    ForEach(templateStore.templates) { template in
                        Button { apply(template) } label: { Text(template.name) }
                    }
                }
            }
            Button {
                templateName = title.trimmingCharacters(in: .whitespacesAndNewlines)
                savingTemplate = true
            } label: {
                Label("Save as template", systemImage: "square.and.arrow.down")
            }
            .disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        } label: {
            Label("Templates", systemImage: "doc.on.doc")
        }
    }

    /// Fill the draft from a template (additively replaces the authoring fields; one-off context
    /// like a specific due date / contact is intentionally NOT carried).
    private func apply(_ template: TaskTemplate) {
        withAnimation(ChronosMotion.snappy) {
            title = template.name
            detail = template.detail ?? ""
            priority = template.priority
            checklist = template.checklist.enumerated().map { ChecklistItem(text: $1, order: $0) }
            if let rec = template.recurrence {
                repeats = true
                frequency = rec.frequency
                interval = rec.interval
                weekdays = rec.weekdays
                ordinal = rec.ordinal
                ordinalWeekday = rec.ordinalWeekday
            } else {
                repeats = false
            }
            smartFill = nil
        }
    }

    private func commitTemplate() {
        let template = TaskTemplate.from(
            name: templateName,
            detail: detail.isEmpty ? nil : detail,
            priority: priority,
            durationMinutes: nil,
            checklist: checklist,
            recurrence: recurrenceSpec)
        templateStore.save(template)
    }

    /// Reset the sheet to a fresh draft (used by "Add & new" to keep adding tasks).
    private func resetDraft() {
        withAnimation(ChronosMotion.snappy) {
            title = ""; detail = ""; priority = 0
            hasDueDate = false; dueDate = .now
            repeats = false; frequency = .daily; interval = 1; weekdays = []
            ordinal = 1; ordinalWeekday = 2; hasRecurrenceEnd = false
            goalID = ""
            checklist = []; newChecklistItem = ""
            smartFill = nil; actionHint = nil
        }
    }

    private var unitLabel: String {
        let base = switch frequency {
            case .daily: "day"; case .weekly: "week"; case .monthly, .monthlyOrdinal: "month"
        }
        return interval == 1 ? base : base + "s"
    }

    private var recurrenceSpec: RecurrenceSpec? {
        guard repeats else { return nil }
        return RecurrenceSpec(
            frequency: frequency,
            interval: interval,
            weekdays: frequency == .weekly ? weekdays : [],
            ordinal: ordinal,
            ordinalWeekday: ordinalWeekday,
            endsOn: hasRecurrenceEnd ? Calendar.current.startOfDay(for: recurrenceEnd) : nil)
    }

    // MARK: - Duplicate detection & goal linking

    /// An existing OTHER task whose title matches the typed one (case-insensitive). Mirrors Android's
    /// gentle duplicate warning (only while creating a new task, where a clash is unintended).
    private var duplicateTask: TaskItem? {
        guard existing == nil else { return nil }
        let key = title.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard key.count >= 2 else { return nil }
        return allTasks.first { $0.title.trimmingCharacters(in: .whitespaces).lowercased() == key }
    }

    /// A goal whose title appears in the typed title/notes (Android `detectGoalIdFromText`): a simple
    /// case-insensitive substring scan, longest goal title first so the most specific match wins.
    private var detectedGoal: Goal? {
        let haystack = (title + " " + detail).lowercased()
        guard !haystack.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
        return goals
            .sorted { $0.title.count > $1.title.count }
            .first { goal in
                let needle = goal.title.trimmingCharacters(in: .whitespaces).lowercased()
                return needle.count >= 3 && haystack.contains(needle)
            }
    }

    /// Re-run the offline smart-fill pass so the "Detected in your text" preview card refreshes with
    /// the latest title (the on-device stand-in for Android's networked `onRequestAssist`).
    private func requestAssistSuggestions() {
        detectSmartFill(title)
    }

    /// Reads (and consumes) the "Shared from [App]" provenance label the Share Extension wrote into
    /// the App Group alongside `share.pendingText`, recent-only. Consuming it here keeps a stale
    /// label from leaking onto a later, unrelated new-task draft.
    private static func pendingShareSourceApp() -> String? {
        let defaults = UserDefaults(suiteName: "group.com.chronosflow.shared")
        guard let source = defaults?.string(forKey: "share.sourceApp"),
              !source.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              let ts = defaults?.double(forKey: "share.pendingTimestamp"),
              Date().timeIntervalSince1970 - ts < 30 else { return nil }
        defaults?.removeObject(forKey: "share.sourceApp")
        return source
    }

    // MARK: - Smart fill

    /// Run the offline parser over the current title and stash the result for the preview card.
    /// Pure detection — nothing is applied to the draft until the user taps "Apply all".
    private func detectSmartFill(_ text: String) {
        let result = parseSmartFill(text, now: .now)
        withAnimation(ChronosMotion.snappy) {
            smartFill = (result.hasDetection || result.actionHint != nil) ? result : nil
        }
    }

    /// Map every detected field onto the draft and tidy the title, then collapse the card.
    private func applyAll(_ fill: SmartFillResult) {
        withAnimation(ChronosMotion.snappy) {
            title = fill.cleanedTitle

            if let due = fill.dueDate {
                hasDueDate = true
                dueDate = due
            } else if let minute = fill.timeMinuteOfDay {
                // Time-only: fold the time into today's date.
                hasDueDate = true
                dueDate = Calendar.current.date(
                    bySettingHour: minute / 60, minute: minute % 60, second: 0,
                    of: Calendar.current.startOfDay(for: .now)) ?? dueDate
            }

            if let p = fill.priority {
                priority = priorityInt(p)
            }
            if let rule = fill.recurrence {
                repeats = true
                frequency = mapFrequency(rule.frequency)
                interval = max(rule.interval, 1)
                weekdays = rule.frequency == .weekly ? rule.weekdays : []
            }
            if let action = fill.actionHint {
                actionHint = action
            }
            smartFill = nil
        }
    }

    private func priorityInt(_ p: TaskPriorityHint) -> Int {
        switch p { case .high: 3; case .medium: 2; case .low: 1 }
    }

    private func mapFrequency(_ f: RecurrenceFrequency) -> RecurrenceSpec.Frequency {
        switch f { case .daily: .daily; case .weekly: .weekly; case .monthly: .monthly }
    }

    private func actionIcon(_ kind: ActionHint.Kind) -> String {
        switch kind { case .email: "envelope"; case .phone: "phone"; case .url: "safari" }
    }

    /// One-tap: open the detected mail / tel / web target.
    private func open(_ action: ActionHint) {
        let raw = action.value
        let urlString: String
        switch action.kind {
        case .email: urlString = "mailto:\(raw)"
        case .phone: urlString = "tel:\(raw.filter { $0.isNumber || $0 == "+" })"
        case .url: urlString = raw.contains("://") ? raw : "https://\(raw)"
        }
        if let url = URL(string: urlString) { openURL(url) }
    }

    // MARK: - Checklist

    /// Add one or more steps. A pasted/typed multi-line entry (or a `; `-separated list) becomes
    /// several checklist items. Mirrors Android `splitChecklistInput`.
    private func addChecklistItem() {
        let parts = TaskEditorSheet.splitChecklistInput(newChecklistItem)
        guard !parts.isEmpty else { return }
        withAnimation(ChronosMotion.snappy) {
            for part in parts {
                checklist.append(ChecklistItem(text: part, order: checklist.count))
            }
        }
        newChecklistItem = ""
    }

    /// Split a checklist-entry field into steps on newlines or semicolons.
    static func splitChecklistInput(_ raw: String) -> [String] {
        raw.split(whereSeparator: { $0 == "\n" || $0 == ";" })
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
    }

    /// Candidate steps pulled from a multi-line notes body (bullet/number markers stripped),
    /// excluding anything already present. Mirrors Android `descriptionChecklistCandidates`.
    private var descriptionChecklistCandidates: [String] {
        let existing = Set(checklist.map { $0.text.trimmingCharacters(in: .whitespaces).lowercased() })
        var seen = Set<String>()
        var result: [String] = []
        for line in detail.split(separator: "\n", omittingEmptySubsequences: false) {
            let stripped = line.trimmingCharacters(in: .whitespaces)
                .replacingOccurrences(of: #"^(?:[-*•·]|\d+[.)])\s+"#, with: "", options: .regularExpression)
                .trimmingCharacters(in: .whitespaces)
            let key = stripped.lowercased()
            guard (2...120).contains(stripped.count), !existing.contains(key), !seen.contains(key)
            else { continue }
            seen.insert(key)
            result.append(stripped)
        }
        return result
    }

    private func extractChecklistFromDescription() {
        let candidates = descriptionChecklistCandidates
        withAnimation(ChronosMotion.snappy) {
            for c in candidates {
                checklist.append(ChecklistItem(text: c, order: checklist.count))
            }
        }
    }

    private func reindex() {
        for i in checklist.indices { checklist[i].order = i }
    }

    /// Run an on-device text tool over the notes and replace them with the result.
    private func rewriteNotes(_ op: ChronosTextOp) {
        let input = detail
        Task {
            if let result = await textTools.run(op, on: input) {
                withAnimation(ChronosMotion.snappy) { detail = result }
            }
        }
    }

    /// Persist the current draft (insert or update) without dismissing. Returns nothing — used by
    /// both `save()` and the "Add & new" flow.
    private func saveDraft() {
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanTitle.isEmpty else { return }
        let task: TaskItem
        let linkedGoal = goalID.isEmpty ? nil : goalID
        if let existing {
            existing.title = cleanTitle
            existing.detail = detail.isEmpty ? nil : detail
            existing.priority = priority
            existing.dueDate = hasDueDate ? dueDate : nil
            existing.recurrence = recurrenceSpec
            existing.goalID = linkedGoal
            existing.checklist = checklist
            existing.updatedAt = .now
            task = existing
        } else {
            let newTask = TaskItem(
                title: cleanTitle, detail: detail.isEmpty ? nil : detail,
                priority: priority, dueDate: hasDueDate ? dueDate : nil,
                targetDate: hasDueDate ? dueDate : nil, goalID: linkedGoal,
                recurrence: recurrenceSpec, checklist: checklist)
            context.insert(newTask)
            task = newTask
        }
        try? context.save()
        // Schedule (or refresh) the reminder for this task — gated on settings inside scheduleTask.
        let scheduled = task
        Task { await ChronosNotifications.shared.scheduleTask(scheduled) }
    }

    private func save() {
        saveDraft()
        dismiss()
    }
}

// MARK: - Preview card

/// The glassy "Detected in your text" card. Shows the detection chips in a flowing layout and
/// offers a one-tap "Apply all" plus a dismiss. Mirrors the Android `TaskTitleSmartFillCard`.
private struct SmartFillPreviewCard: View {
    let fill: SmartFillResult
    let onApplyAll: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        ChronosGlassCard(tone: .standard, tint: ChronosColors.brandPrimary) {
            VStack(alignment: .leading, spacing: ChronosSpacing.compact) {
                Text("Detected in your text")
                    .font(.chronosLabel)
                    .foregroundStyle(.secondary)

                ChipFlowLayout(spacing: ChronosSpacing.small) {
                    ForEach(Array(fill.detections.enumerated()), id: \.offset) { _, chip in
                        Text(chip)
                            .font(.chronosCaption)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(.thinMaterial, in: Capsule())
                    }
                }

                Text("Tidy the title to “\(fill.cleanedTitle)” and fill in the detected details.")
                    .font(.chronosCaption)
                    .foregroundStyle(.secondary)

                HStack(spacing: ChronosSpacing.small) {
                    Button(action: onApplyAll) {
                        Text("Apply all").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)

                    Button(action: onDismiss) {
                        Text("Dismiss").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }
                .padding(.top, 2)
            }
        }
        .padding(.horizontal)
        .transition(.scale(scale: 0.96).combined(with: .opacity))
    }
}

// MARK: - Weekday selector

/// A compact row of toggleable weekday chips (Mon-first) for a weekly recurrence rule. Stores
/// `Calendar.component(.weekday)` numbers (1=Sun … 7=Sat) so it round-trips with ChronosCore.
private struct WeekdaySelector: View {
    @Binding var selection: Set<Int>

    /// Display order Mon…Sun, paired with the Calendar weekday number.
    private let days: [(label: String, weekday: Int)] = [
        ("M", 2), ("T", 3), ("W", 4), ("T", 5), ("F", 6), ("S", 7), ("S", 1)
    ]

    var body: some View {
        HStack(spacing: ChronosSpacing.small) {
            ForEach(days, id: \.weekday) { day in
                let on = selection.contains(day.weekday)
                Button {
                    withAnimation(ChronosMotion.snappy) {
                        if on { selection.remove(day.weekday) } else { selection.insert(day.weekday) }
                    }
                } label: {
                    Text(day.label)
                        .font(.chronosCaption)
                        .frame(width: 30, height: 30)
                        .background(on ? ChronosColors.brandPrimary : Color(.tertiarySystemFill),
                                    in: Circle())
                        .foregroundStyle(on ? .white : .primary)
                }
                .buttonStyle(.plain)
            }
        }
        .accessibilityLabel("Repeat on weekdays")
    }
}

// MARK: - Flow layout

/// A minimal flowing wrap layout for the detection chips (iOS 16+ `Layout`). Lays children out
/// left-to-right, wrapping to a new row when the proposed width is exceeded.
private struct ChipFlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var rowWidth: CGFloat = 0
        var rowHeight: CGFloat = 0
        var totalHeight: CGFloat = 0
        var totalWidth: CGFloat = 0

        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if rowWidth + size.width > maxWidth, rowWidth > 0 {
                totalHeight += rowHeight + spacing
                totalWidth = max(totalWidth, rowWidth - spacing)
                rowWidth = 0
                rowHeight = 0
            }
            rowWidth += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        totalHeight += rowHeight
        totalWidth = max(totalWidth, rowWidth - spacing)
        return CGSize(width: min(totalWidth, maxWidth), height: totalHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) {
        let maxWidth = bounds.width
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0

        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x + size.width > bounds.minX + maxWidth, x > bounds.minX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
