import SwiftUI
import SwiftData
import EventKit
import ChronosCore

// MARK: - CalendarManagementView
//
// The iOS analogue of Android's CALENDARS sidebar page (SidebarPageContent.kt lines 537–908,
// 1533–1557). Consolidates the calendar surface that was previously scattered across the dial
// overlay toggle into one screen, mirroring Android's dedicated page:
//   1. A month picker for date navigation (Android: the month-grid card).
//   2. A calendar sync-status card with an EventKit permission-request flow + rationale, modelled
//      on Android's CalendarPermissionStatus / collapsible "Calendar sync" section. Mirrors the
//      rationale copy in DayDialCalendarPermissions.kt / SidebarPageContent.kt.
//   3. A source-filtered "Detailed calendar" timeline with the three segmented presets
//      (All / Schedule / Calendar — CalendarTimelinePreset) and a per-source filter menu, rendering
//      all six sources grouped like Android's buildSidebarTimelineItems: EventKit events (Calendar /
//      All-day calendar) plus the SwiftData schedule-side sources (Task / Habit / Medication / Plan)
//      with per-row quick actions (Complete for tasks/habits, Take/Missed for medication doses).
//
// Calendar events are read read-only via the shared `CalendarOverlayProvider` (EventKit). The
// schedule-side rows reuse the write patterns of TasksView / HabitsView / MedicationView (recurring
// tasks spawn their next occurrence, doses go through acknowledgeDose/recordDose) without touching
// those views.

struct CalendarManagementView: View {
    @State private var model = CalendarManagementModel()
    @Environment(\.modelContext) private var context

    // Schedule-side sources for the timeline (Android: DayQuickItemsUiState + the day's TimeBlocks).
    // Queried unfiltered and narrowed to the selected date at build time, since the date is dynamic.
    @Query private var blocks: [TimeBlock]
    @Query private var tasks: [TaskItem]
    @Query private var habits: [Habit]
    @Query private var medications: [MedicationPlan]

    /// Bumps after each quick action to fire the success haptic.
    @State private var actionTick = 0

    var body: some View {
        Form {
            monthPickerSection
            syncStatusSection
            timelineSection
        }
        .navigationTitle("Calendars")
        .navigationBarTitleDisplayMode(.inline)
        .task { await model.refresh() }
        .sensoryFeedback(.success, trigger: actionTick)
    }

    // MARK: Month picker (Android: the month-grid navigation card)

    private var monthPickerSection: some View {
        Section {
            DatePicker("Date",
                       selection: $model.selectedDate,
                       displayedComponents: .date)
                .datePickerStyle(.graphical)
                .onChange(of: model.selectedDate) { _, _ in
                    Task { await model.reloadEvents() }
                }
        } header: {
            Text(model.selectedDate.formatted(.dateTime.month(.wide).year()))
        }
    }

    // MARK: Sync status + permission flow (Android: the "Calendar sync" collapsible)

    private var syncStatusSection: some View {
        Section {
            LabeledContent("Status") {
                HStack(spacing: ChronosSpacing.micro) {
                    Circle()
                        .fill(model.statusColor)
                        .frame(width: ChronosSpacing.small, height: ChronosSpacing.small)
                    Text(model.connectionSummary)
                        .foregroundStyle(.secondary)
                }
            }
            Text(model.statusMessage)
                .font(.chronosCaption)
                .foregroundStyle(.secondary)

            // Last-refresh label, reusing the shared relative formatter (Android: "Synced X ago").
            Text(formatLastSyncedLabel(lastSyncAt: model.lastRefresh, now: .now))
                .font(.chronosCaption)
                .foregroundStyle(.secondary)

            // Actionable buttons mirror Android's status-card actions (Connect / Refresh / Open Settings).
            switch model.permission {
            case .notDetermined:
                Button("Connect calendar") { Task { await model.requestAccess() } }
            case .denied, .restricted:
                Button("Open Settings") { model.openSystemSettings() }
            case .writeOnly:
                Button("Enable read access in Settings") { model.openSystemSettings() }
            case .fullAccess:
                Button {
                    Task { await model.reloadEvents() }
                } label: {
                    Label("Refresh device events", systemImage: "arrow.clockwise")
                }
            }
        } header: {
            Text("Calendar sync")
        } footer: {
            Text(model.rationaleCopy)
        }
    }

    // MARK: Detailed calendar timeline (Android: "Detailed calendar")

    private var timelineSection: some View {
        Section {
            // Three segmented presets (Android: CalendarTimelinePreset All / Schedule / Calendar).
            Picker("Preset", selection: $model.preset) {
                ForEach(CalendarTimelinePreset.allCases) { Text($0.label).tag($0) }
            }
            .pickerStyle(.segmented)
            .onChange(of: model.preset) { _, newValue in
                withAnimation(ChronosMotion.snappy) { model.applyPreset(newValue) }
            }

            // Per-source filter + hide-completed menu (Android: the filter dropdown).
            filterMenu

            timelineContent
        } header: {
            Text("Detailed calendar")
        } footer: {
            Text("Calendar, tasks, habits, and medications for \(model.selectedDate.formatted(.dateTime.month().day())). Mirrors the Android Detailed Calendar timeline.")
        }
    }

    private var filterMenu: some View {
        Menu {
            Toggle("Hide completed", isOn: $model.hideCompleted)
            Toggle("Connected calendar only", isOn: $model.connectedCalendarOnly)
            Divider()
            ForEach(CalendarTimelineSource.allCases) { source in
                Toggle(source.label, isOn: model.sourceBinding(source))
            }
        } label: {
            Label("Filter", systemImage: "line.3.horizontal.decrease.circle")
        }
    }

    @ViewBuilder
    private var timelineContent: some View {
        let grouped = model.groupedTimeline(scheduleItems: scheduleTimelineItems())
        if grouped.isEmpty {
            ContentUnavailableView {
                Label(model.permission == .fullAccess ? "No timeline items" : "Calendar not connected",
                      systemImage: "calendar")
            } description: {
                Text(model.emptyStateMessage)
            } actions: {
                switch model.permission {
                case .notDetermined:
                    Button("Connect calendar") { Task { await model.requestAccess() } }
                        .buttonStyle(.borderedProminent)
                        .tint(ChronosColors.brandPrimary)
                case .denied, .restricted, .writeOnly:
                    Button("Open Settings") { model.openSystemSettings() }
                        .buttonStyle(.borderedProminent)
                        .tint(ChronosColors.brandPrimary)
                case .fullAccess:
                    EmptyView()
                }
            }
        } else {
            ForEach(grouped, id: \.source) { group in
                let sourceColor = ChronosColors.category(group.source.label)
                // Source header with count (Android: "{sourceLabel} ({count})").
                HStack(spacing: ChronosSpacing.small) {
                    Image(systemName: group.source.symbol)
                        .foregroundStyle(sourceColor)
                    Text("\(group.source.label) (\(group.items.count))")
                        .font(.chronosLabel)
                        .foregroundStyle(sourceColor)
                }
                ForEach(group.items) { item in
                    CalendarTimelineRow(item: item, sourceColor: sourceColor, onAction: perform)
                }
            }
        }
    }

    // MARK: Schedule-side timeline items (Android: buildSidebarTimelineItems' quick-item half)

    /// Builds the Task / Habit / Medication / Plan rows for the selected date. Each list is sorted
    /// like Android: scheduled minute first (unscheduled last), then title.
    private func scheduleTimelineItems() -> [CalendarTimelineSource: [CalendarTimelineItem]] {
        let cal = Calendar.current
        let day = cal.startOfDay(for: model.selectedDate)
        var items: [CalendarTimelineSource: [CalendarTimelineItem]] = [:]
        items[.plan] = blocks
            .filter { cal.isDate($0.date, inSameDayAs: day) && $0.calendarEventID == nil }
            .map(CalendarTimelineItem.init(block:))
            .sorted()
        items[.task] = tasks
            .filter { task in
                // Android `belongsToDate`: slotted onto this day or due this day.
                task.targetDate.map { cal.isDate($0, inSameDayAs: day) } ?? false
                    || task.dueDate.map { cal.isDate($0, inSameDayAs: day) } ?? false
            }
            .map(CalendarTimelineItem.init(task:))
            .sorted()
        items[.habit] = habits
            .filter(\.isActive)
            .map { CalendarTimelineItem(habit: $0, day: day) }
            .sorted()
        items[.medication] = medications
            .filter { plan in
                guard plan.isActive, !plan.isPaused(on: day) else { return false }
                if let start = plan.startAt, day < cal.startOfDay(for: start) { return false }
                if let end = plan.endAt, day > cal.startOfDay(for: end) { return false }
                return true
            }
            .flatMap { plan in
                plan.reminderMinutes.sorted().map { CalendarTimelineItem(plan: plan, minute: $0, day: day) }
            }
            .sorted()
        return items
    }

    /// Executes a row's quick action with the same model writes the owning feature screens use.
    private func perform(_ action: CalendarTimelineQuickAction) {
        let cal = Calendar.current
        let day = cal.startOfDay(for: model.selectedDate)
        withAnimation(ChronosMotion.snappy) {
            switch action {
            case .completeTask(let id):
                guard let task = tasks.first(where: { $0.id == id }), !task.isCompleted else { return }
                // Mirrors TasksView.complete: completing a recurring task spawns its next occurrence.
                task.isCompleted = true
                if let rec = task.recurrence {
                    let anchor = task.dueDate ?? task.targetDate ?? .now
                    if let next = rec.nextDate(after: anchor) {
                        context.insert(TaskItem(
                            title: task.title, detail: task.detail, priority: task.priority,
                            dueDate: task.dueDate != nil ? next : nil,
                            targetDate: task.targetDate != nil ? next : nil,
                            goalID: task.goalID, recurrence: rec,
                            checklist: task.checklist.map {
                                ChecklistItem(text: $0.text, isDone: false, order: $0.order)
                            }))
                    }
                }
            case .completeHabit(let id):
                guard let habit = habits.first(where: { $0.id == id }),
                      !habit.isCompleted(on: day) else { return }
                habit.toggleCompletion(on: day)
            case .takeDose(let id, let minute):
                guard let plan = medications.first(where: { $0.id == id }) else { return }
                plan.acknowledgeDose(at: doseTimestamp(day: day, minute: minute))
            case .missDose(let id, let minute):
                guard let plan = medications.first(where: { $0.id == id }) else { return }
                plan.recordDose(.missed, at: doseTimestamp(day: day, minute: minute), reason: "Marked missed")
            }
            try? context.save()
        }
        actionTick += 1
    }

    /// The scheduled dose instant on the viewed day, so history lands on the right date.
    private func doseTimestamp(day: Date, minute: Int) -> Date {
        Calendar.current.date(byAdding: .minute, value: minute, to: day) ?? day
    }
}

// MARK: - Timeline row

/// One timeline item, mirroring Android's SidebarTimelineItemRow rendering
/// (left accent bar, time, title, detail/status, quick-action buttons).
private struct CalendarTimelineRow: View {
    let item: CalendarTimelineItem
    let sourceColor: Color
    var onAction: (CalendarTimelineQuickAction) -> Void = { _ in }

    var body: some View {
        HStack(alignment: .top, spacing: ChronosSpacing.compact) {
            RoundedRectangle(cornerRadius: 2)
                .fill(sourceColor)
                .frame(width: ChronosSpacing.micro)
            VStack(alignment: .leading, spacing: 2) {
                Text(item.title)
                    .font(.chronosBody)
                Text(item.timeLabel)
                    .font(.chronosCaption)
                    .foregroundStyle(.secondary)
                if let detail = item.detail {
                    Text(detail)
                        .font(.chronosCaption)
                        .foregroundStyle(.secondary)
                }
                if let status = item.status {
                    Text(status)
                        .font(.chronosCaption.weight(.semibold))
                        .foregroundStyle(item.isDone ? ChronosColors.brandPrimary : .secondary)
                }
                if item.primaryAction != nil || item.secondaryAction != nil {
                    HStack(spacing: ChronosSpacing.small) {
                        if let action = item.primaryAction {
                            Button(action.label) { onAction(action) }
                                .buttonStyle(.borderedProminent)
                                .tint(ChronosColors.brandPrimary)
                        }
                        if let action = item.secondaryAction {
                            Button(action.label) { onAction(action) }
                                .buttonStyle(.bordered)
                        }
                    }
                    .buttonBorderShape(.capsule)
                    .controlSize(.small)
                    .font(.chronosCaption)
                    .padding(.top, 2)
                }
            }
            Spacer()
        }
        .padding(.vertical, 2)
    }
}

// MARK: - Model

/// EventKit-backed, source-filterable timeline state. Pure-ish view model: it owns the selected date,
/// the EventKit permission snapshot, the segmented preset + per-source filter set, and the events read
/// from the device calendar via the shared `CalendarOverlayProvider`. Marked `@MainActor` because it
/// touches `EKEventStore` and `@Observable` state the view binds to.
@Observable
@MainActor
final class CalendarManagementModel {
    /// Mirrors `EKAuthorizationStatus` collapsed to the states the status card branches on,
    /// modelled on Android's CalendarPermissionStatus (read/write granted, denied).
    enum Permission { case notDetermined, denied, restricted, writeOnly, fullAccess }

    var selectedDate: Date = .now
    var permission: Permission = .notDetermined
    var preset: CalendarTimelinePreset = .all
    var enabledSources: Set<CalendarTimelineSource> = Set(CalendarTimelineSource.allCases)
    var hideCompleted = false
    var connectedCalendarOnly = false
    var lastRefresh: Date?

    private var calendarEvents: [CalendarOverlayEvent] = []
    private let provider = CalendarOverlayProvider()

    // MARK: Permission

    func refresh() async {
        readPermission()
        if permission == .fullAccess { await reloadEvents() }
    }

    private func readPermission() {
        switch EKEventStore.authorizationStatus(for: .event) {
        case .notDetermined: permission = .notDetermined
        case .restricted: permission = .restricted
        case .denied: permission = .denied
        case .writeOnly: permission = .writeOnly
        case .fullAccess: permission = .fullAccess
        case .authorized: permission = .fullAccess   // legacy (< iOS 17) full access
        @unknown default: permission = .denied
        }
    }

    func requestAccess() async {
        _ = await provider.requestAccess()
        readPermission()
        if permission == .fullAccess { await reloadEvents() }
    }

    func openSystemSettings() {
        #if canImport(UIKit)
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
        #endif
    }

    // MARK: Events

    func reloadEvents() async {
        guard permission == .fullAccess else { calendarEvents = []; return }
        let timed = await provider.events(on: selectedDate)
        let allDay = await provider.allDayContext(on: selectedDate)
        withAnimation(ChronosMotion.snappy) { calendarEvents = timed + allDay }
        lastRefresh = .now
    }

    // MARK: Filtering

    func applyPreset(_ preset: CalendarTimelinePreset) {
        enabledSources = preset.sources
    }

    func sourceBinding(_ source: CalendarTimelineSource) -> Binding<Bool> {
        Binding(
            get: { self.enabledSources.contains(source) },
            set: { isOn in
                if isOn { self.enabledSources.insert(source) } else { self.enabledSources.remove(source) }
                // Drop to the matching preset, or to .all when the selection no longer matches one.
                self.preset = CalendarTimelinePreset.allCases.first { $0.sources == self.enabledSources } ?? .all
            }
        )
    }

    /// Build the source-grouped timeline. Calendar / All-day-calendar sources are populated from
    /// EventKit; the schedule-side sources (Task / Habit / Medication / Plan) come from the view's
    /// SwiftData queries via `scheduleItems`. Mirrors Android's buildSidebarTimelineItems filter:
    /// hide-completed drops done items, and "connected calendar only" limits to calendar sources.
    func groupedTimeline(
        scheduleItems: [CalendarTimelineSource: [CalendarTimelineItem]] = [:]
    ) -> [CalendarTimelineGroup] {
        var groups: [CalendarTimelineGroup] = []
        for source in CalendarTimelineSource.allCases where enabledSources.contains(source) {
            switch source {
            case .calendar:
                let items = calendarEvents.filter { !$0.isAllDay }.map(CalendarTimelineItem.init)
                if !items.isEmpty { groups.append(.init(source: source, items: items)) }
            case .allDayCalendar:
                if connectedCalendarOnly == false || permission == .fullAccess {
                    let items = calendarEvents.filter(\.isAllDay).map(CalendarTimelineItem.init)
                    if !items.isEmpty { groups.append(.init(source: source, items: items)) }
                }
            case .task, .habit, .medication, .plan:
                guard !connectedCalendarOnly else { break }
                var items = scheduleItems[source] ?? []
                if hideCompleted { items.removeAll(where: \.isDone) }
                if !items.isEmpty { groups.append(.init(source: source, items: items)) }
            }
        }
        return groups
    }

    var emptyStateMessage: String {
        if connectedCalendarOnly { return "No connected calendar items for this date." }
        if hideCompleted { return "No timeline items for this date with completed items hidden." }
        if enabledSources.isEmpty { return "No sections enabled for this timeline." }
        return "No timeline items for this date."
    }

    // MARK: Status copy (mirrors Android's CalendarPermissionStatus-driven messages)

    var connectionSummary: String {
        switch permission {
        case .fullAccess: "Connected"
        case .writeOnly: "Write-only"
        case .notDetermined: "Not connected"
        case .denied, .restricted: "Access off"
        }
    }

    var statusColor: Color {
        switch permission {
        case .fullAccess: .green
        case .writeOnly: .orange
        case .notDetermined: .secondary
        case .denied, .restricted: .red
        }
    }

    var statusMessage: String {
        switch permission {
        case .fullAccess:
            "Calendar connected. ChronosFlow reads your device events to avoid duplicate plans and shows them on the dial."
        case .writeOnly:
            "ChronosFlow can write calendar exports but can’t read your device events yet. Enable read access to import scheduled events."
        case .notDetermined:
            "Connect your calendar to import scheduled events and surface them on the dial."
        case .denied, .restricted:
            "Calendar access is off. iOS won’t let ChronosFlow import calendar items until you re-enable permission in Settings."
        }
    }

    /// Rationale copy shown in the section footer — mirrors Android's "Calendar access is needed"
    /// rationale (DayDialCalendarPermissions.kt / SidebarPageContent.kt).
    var rationaleCopy: String {
        "ChronosFlow reads your device calendar to avoid duplicate plans and to show your existing commitments on the dial. Calendar events are read-only — they’re never silently turned into blocks."
    }
}

// MARK: - Timeline value types

struct CalendarTimelineGroup: Identifiable {
    let source: CalendarTimelineSource
    let items: [CalendarTimelineItem]
    var id: String { source.rawValue }
}

/// A quick action a schedule-side timeline row exposes, mirroring Android's per-row
/// primary/secondary actions (Complete for tasks/habits, Take/Missed for medication doses).
enum CalendarTimelineQuickAction: Equatable {
    case completeTask(String)
    case completeHabit(String)
    case takeDose(planID: String, minute: Int)
    case missDose(planID: String, minute: Int)

    var label: String {
        switch self {
        case .completeTask, .completeHabit: "Complete"
        case .takeDose: "Take"
        case .missDose: "Missed"
        }
    }
}

struct CalendarTimelineItem: Identifiable, Equatable, Comparable {
    let id: String
    let title: String
    let timeLabel: String
    let isAllDay: Bool
    var detail: String?
    var status: String?
    var isDone = false
    var primaryAction: CalendarTimelineQuickAction?
    var secondaryAction: CalendarTimelineQuickAction?
    /// In-group sort key (Android sortMinute; unscheduled items sink to the bottom).
    var sortMinute = 0

    /// Android UNSCHEDULED_TIMELINE_MINUTE — items without a time sort after every scheduled one.
    private static let unscheduledMinute = 24 * 60 + 10

    init(_ event: CalendarOverlayEvent) {
        self.id = event.id
        self.title = event.title
        self.isAllDay = event.isAllDay
        if event.isAllDay {
            self.timeLabel = "All day"
        } else {
            let start = Self.clock(event.startMinute)
            let end = Self.clock(event.startMinute + event.durationMinutes)
            self.timeLabel = "\(start) – \(end)"
        }
        self.sortMinute = event.isAllDay ? 0 : event.startMinute
    }

    /// A planner block on the day (Android: the TimeBlock half of buildSidebarTimelineItems —
    /// no quick action, status "Planned").
    init(block: TimeBlock) {
        self.id = "block:\(block.id)"
        self.title = block.title.isEmpty ? "Focus block" : block.title
        self.isAllDay = false
        self.timeLabel = "\(Self.clock(block.startMinuteOfDay)) – \(Self.clock(block.startMinuteOfDay + block.durationMinutes))"
        self.detail = block.category
        self.status = "Planned"
        self.sortMinute = block.startMinuteOfDay
    }

    /// A task due on the day (Android: DayQuickItemUiModel TASK).
    init(task: TaskItem) {
        self.id = "task:\(task.id)"
        self.title = task.title.isEmpty ? "Untitled task" : task.title
        self.isAllDay = false
        self.timeLabel = task.preferredStartMinuteOfDay.map(Self.clock) ?? "Unscheduled"
        var parts: [String] = []
        if let duration = task.preferredDurationMinutes { parts.append("\(duration)m") }
        let stepsLeft = task.checklist.filter { !$0.isDone }.count
        if stepsLeft > 0 { parts.append("\(stepsLeft) steps left") }
        self.detail = parts.isEmpty ? "Task" : parts.joined(separator: " · ")
        self.isDone = task.isCompleted
        self.status = task.isCompleted ? "Done" : "Due today"
        self.primaryAction = task.isCompleted ? nil : .completeTask(task.id)
        self.sortMinute = task.preferredStartMinuteOfDay ?? Self.unscheduledMinute
    }

    /// An active habit on the day (Android: DayQuickItemUiModel HABIT).
    init(habit: Habit, day: Date) {
        self.id = "habit:\(habit.id)"
        self.title = habit.title.isEmpty ? "Untitled habit" : habit.title
        self.isAllDay = false
        self.timeLabel = Self.clock(habit.effectiveStartMinute)
        self.detail = "\(Self.clock(habit.windowStartMinute)) – \(Self.clock(habit.windowEndMinute))"
        let completed = habit.isCompleted(on: day)
        let skipped = habit.isSkipped(on: day)
        self.isDone = completed || skipped
        self.status = completed ? "Done"
            : skipped ? "Skipped"
            : habit.isPaused(on: day) ? "Paused"
            : "Due today"
        self.primaryAction = isDone ? nil : .completeHabit(habit.id)
        self.sortMinute = habit.effectiveStartMinute
    }

    /// One scheduled dose of a medication plan (Android: DayQuickItemUiModel MEDICATION).
    /// Doneness is day-level, matching MedicationView's `takenToday` convention: any terminal dose
    /// event on the day that carries no minute (the acknowledgeDose path) settles every dose row.
    init(plan: MedicationPlan, minute: Int, day: Date) {
        self.id = "med:\(plan.id):\(minute)"
        self.title = plan.name.isEmpty ? "Medication" : plan.name
        self.isAllDay = false
        self.timeLabel = Self.clock(minute)
        var parts = ["\(plan.dosage) \(plan.unit)".trimmingCharacters(in: .whitespaces)]
        if plan.takeWithFood { parts.append("with food") }
        self.detail = parts.filter { !$0.isEmpty }.joined(separator: " · ")
        let cal = Calendar.current
        let event = (plan.doseEvents ?? []).first { event in
            cal.isDate(event.date, inSameDayAs: day)
                && [.taken, .missed, .skipped].contains(event.status)
                && (event.scheduledMinuteOfDay == nil || event.scheduledMinuteOfDay == minute)
        }
        self.isDone = event != nil
        self.status = switch event?.status {
        case .taken: "Taken"
        case .missed: "Missed"
        case .skipped: "Skipped"
        default: "Due \(Self.clock(minute))"
        }
        self.primaryAction = isDone ? nil : .takeDose(planID: plan.id, minute: minute)
        self.secondaryAction = isDone ? nil : .missDose(planID: plan.id, minute: minute)
        self.sortMinute = minute
    }

    /// Android's in-source ordering: scheduled minute, then case-insensitive title.
    static func < (lhs: CalendarTimelineItem, rhs: CalendarTimelineItem) -> Bool {
        lhs.sortMinute != rhs.sortMinute
            ? lhs.sortMinute < rhs.sortMinute
            : lhs.title.lowercased() < rhs.title.lowercased()
    }

    /// Minute-of-day → "h:mm a" wall-clock label.
    private static func clock(_ minuteOfDay: Int) -> String {
        let m = ((minuteOfDay % 1440) + 1440) % 1440
        var comps = DateComponents()
        comps.hour = m / 60
        comps.minute = m % 60
        let date = Calendar.current.date(from: comps) ?? .now
        return date.formatted(.dateTime.hour().minute())
    }
}

#Preview {
    NavigationStack { CalendarManagementView() }
        .modelContainer(ChronosStore.previewContainer())
}
