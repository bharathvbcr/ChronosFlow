import SwiftUI
import SwiftData
import ChronosCore

/// The Tasks tab — the iOS port of Android `feature/tasks/TaskScreen.kt`. Brought to parity with the
/// Android "Task command center": a page-header card, a row of Open/Urgent/Done metric tiles, an
/// "Add task" button, an assistant-triage card, urgent-task permission alerts, Open/All/Done filter
/// chips with a DayDial shortcut, and rich task rows (priority badge, description, metadata badges,
/// an action cue, and Schedule / Duplicate / Edit / Delete footer buttons). Tapping a row opens the
/// `TaskContextCommandSheet` of quick actions, mirroring Android's `TaskContextCommandSheet`.
struct TasksView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.scenePhase) private var scenePhase
    @Query(sort: [SortDescriptor(\TaskItem.priority, order: .reverse),
                  SortDescriptor(\TaskItem.createdAt)]) private var tasks: [TaskItem]
    @Query(sort: \Goal.title) private var goals: [Goal]
    @State private var activeSheet: TaskSheet?
    /// The task whose quick-action context sheet is open (Android `commandSheetTask`).
    @State private var contextTask: TaskItem?
    /// Open/All/Done list filter (Android `TaskFilter`, default Open).
    @State private var filter: TaskFilter = .open
    /// Dismissed-permission state so the alerts behave like Android's dismissible attention cards.
    @State private var notificationAlertDismissed = false
    @State private var alarmAlertDismissed = false
    /// Candidates drained from a multi-line / .txt / .ics share (written by ChronosShareExtension to
    /// the App Group under `share.pendingBulkTasks`). Non-nil drives the bulk-import review sheet.
    @State private var bulkImportCandidates: [String]?

    /// App Group the Share Extension writes pending shares into.
    private static let appGroup = "group.com.chronosflow.shared"

    /// Optional jump to the day-planning dial, injected by the shell when Tasks is presented as a
    /// destination (Android `onOpenDayDial`). When `nil`, the DayDial shortcut is hidden.
    var onOpenDayDial: (() -> Void)?

    init(onOpenDayDial: (() -> Void)? = nil) {
        self.onOpenDayDial = onOpenDayDial
    }

    private enum TaskFilter: String, CaseIterable, Identifiable {
        case open = "Open", all = "All", done = "Done"
        var id: String { rawValue }
    }

    private enum TaskSheet: Identifiable {
        case edit(TaskItem)
        case new
        var id: String { if case .edit(let t) = self { "edit-\(t.id)" } else { "new" } }
    }

    // MARK: Derived metrics (reactive — recomputed as the @Query results change).

    private var openCount: Int { tasks.filter { !$0.isCompleted }.count }
    private var doneCount: Int { tasks.count - openCount }
    private var urgentCount: Int { tasks.filter { !$0.isCompleted && $0.isUrgent }.count }

    private var visibleTasks: [TaskItem] {
        switch filter {
        case .open: tasks.filter { !$0.isCompleted }
        case .all: tasks
        case .done: tasks.filter(\.isCompleted)
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: ChronosSpacing.compact) {
                    header
                    if !tasks.isEmpty { metricTiles }
                    addButton
                    if !tasks.isEmpty {
                        TaskStatsCard(tasks: tasks)
                        assistantTriageCard
                    }
                    permissionAlerts
                    filterRow
                    if visibleTasks.isEmpty {
                        emptyState
                    } else {
                        ForEach(visibleTasks) { task in
                            TaskRow(
                                task: task,
                                onToggle: { complete(task) },
                                onTap: { contextTask = task },
                                onSchedule: { scheduleToday(task) },
                                onDuplicate: { duplicate(task) },
                                onEdit: { activeSheet = .edit(task) },
                                onDelete: { delete(task) }
                            )
                        }
                    }
                }
                .padding(ChronosSpacing.standard)
            }
            .background { ChronosBackdrop() }
            .navigationTitle("Tasks")
            .chronosScrollMinimizedBar()
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { activeSheet = .new } label: { Image(systemName: "plus") }
                }
            }
            .sheet(item: $activeSheet) { sheet in
                switch sheet {
                case .edit(let task): TaskEditorSheet(task: task)
                case .new:
                    TaskEditorSheet(task: nil, onOpenExistingTask: { id in
                        if let existing = tasks.first(where: { $0.id == id }) {
                            // Re-present the matched task for editing after the add sheet dismisses.
                            DispatchQueue.main.async { activeSheet = .edit(existing) }
                        }
                    })
                }
            }
            .sheet(item: $contextTask) { task in
                TaskContextCommandSheet(
                    task: task,
                    onSchedule: { scheduleToday(task); contextTask = nil },
                    onEdit: { activeSheet = .edit(task); contextTask = nil },
                    onComplete: { complete(task); contextTask = nil }
                )
            }
            // Bulk-import review for a multi-line / .txt / .ics share routed here by the Share
            // Extension. Mirrors Android TaskBulkImportSheet.
            .sheet(item: bulkSheetBinding) { box in
                TaskBulkImportSheet(candidates: box.candidates)
            }
            .onAppear { drainPendingBulkImport() }
            .onChange(of: scenePhase) { _, phase in
                if phase == .active { drainPendingBulkImport() }
            }
        }
    }

    // MARK: - Header & metrics

    /// Page-header card (Android `ChronosPageHeader`): icon, title, subtitle.
    private var header: some View {
        ChronosGlassCard(tone: .standard, tint: ChronosColors.brandPrimary) {
            HStack(spacing: ChronosSpacing.compact) {
                Image(systemName: "checklist")
                    .font(.title2)
                    .foregroundStyle(ChronosColors.brandPrimary)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Task command center")
                        .font(.chronosTitle)
                    Text("Capture commitments, schedule priority work, and arm urgent tasks.")
                        .font(.chronosCaption)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
        }
    }

    /// Open / Urgent / Done metric tiles (Android row of `ChronosMetricTile`).
    private var metricTiles: some View {
        HStack(spacing: ChronosSpacing.compact) {
            MetricTile(label: "Open", value: "\(openCount)",
                       systemImage: "tray.full.fill", tint: ChronosColors.brandPrimary)
            MetricTile(label: "Urgent", value: "\(urgentCount)",
                       systemImage: "exclamationmark.triangle.fill", tint: ChronosColors.brandAccent)
            MetricTile(label: "Done", value: "\(doneCount)",
                       systemImage: "checkmark.circle.fill", tint: ChronosColors.brandSecondary)
        }
    }

    /// Full-width "Add task" button (Android `ChronosFilledTonalButton`).
    private var addButton: some View {
        Button { activeSheet = .new } label: {
            Label("Add task", systemImage: "plus")
                .font(.chronosLabel)
                .frame(maxWidth: .infinity)
                .padding(.vertical, ChronosSpacing.small)
        }
        .buttonStyle(.borderedProminent)
        .tint(ChronosColors.brandPrimary)
    }

    /// Assistant-triage card (Android "Assistant triage" `ChronosListCard`): a headline + next step
    /// computed from the open/urgent task mix via `buildTaskAssistantSummary`.
    private var assistantTriageCard: some View {
        let summary = buildTaskAssistantSummary(tasks: tasks)
        return ChronosGlassCard(tone: .quiet) {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                Label("Assistant triage", systemImage: "sparkles")
                    .font(.chronosLabel)
                    .foregroundStyle(ChronosColors.brandPrimary)
                Text(summary.headline).font(.chronosBody)
                Text(summary.nextStep)
                    .font(.chronosCaption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: - Permission alerts

    /// Urgent-task permission attention cards (Android `TaskAttentionCard`). iOS surfaces notification
    /// authorization; exact-alarm scheduling is the OS's job here (no exact-alarm permission to grant),
    /// so the alarm card explains the iOS notification-time fallback instead.
    @ViewBuilder private var permissionAlerts: some View {
        if urgentCount > 0 && !notificationAlertDismissed {
            TaskAttentionCard(
                title: "Notifications are off",
                message: "Urgent task reminders need notification access.",
                actionLabel: "Enable",
                onAction: { requestNotifications() },
                onDismiss: { withAnimation(ChronosMotion.snappy) { notificationAlertDismissed = true } }
            )
        }
        if urgentCount > 0 && !alarmAlertDismissed {
            TaskAttentionCard(
                title: "Exact reminders are limited",
                message: "Urgent tasks fire at their reminder time; iOS doesn't allow exact alarms.",
                actionLabel: "OK",
                onAction: { withAnimation(ChronosMotion.snappy) { alarmAlertDismissed = true } },
                onDismiss: { withAnimation(ChronosMotion.snappy) { alarmAlertDismissed = true } }
            )
        }
    }

    // MARK: - Filter row

    /// Open/All/Done filter chips with the DayDial shortcut (Android `ChronosFilterChip` row + DayDial).
    private var filterRow: some View {
        HStack(spacing: ChronosSpacing.small) {
            ForEach(TaskFilter.allCases) { option in
                FilterChip(label: option.rawValue, selected: filter == option) {
                    withAnimation(ChronosMotion.snappy) { filter = option }
                }
            }
            Spacer(minLength: 0)
            if let onOpenDayDial {
                Button(action: onOpenDayDial) {
                    Label("DayDial", systemImage: "calendar.day.timeline.left")
                        .font(.chronosCaption)
                }
                .buttonStyle(.bordered)
                .tint(ChronosColors.brandSecondary)
            }
        }
    }

    private var emptyState: some View {
        ContentUnavailableView {
            Label(filter == .done ? "No completed tasks" : "No tasks here",
                  systemImage: "checklist")
        } description: {
            Text(filter == .done
                 ? "Finished work will collect here for review."
                 : "Add the next concrete commitment, then protect time for it on the dial.")
        } actions: {
            Button("Add task") { activeSheet = .new }
                .buttonStyle(.borderedProminent)
                .tint(ChronosColors.brandPrimary)
        }
        .padding(.top, ChronosSpacing.large)
    }

    // MARK: - Actions

    /// Toggle completion; when completing a recurring task, spawn its next occurrence.
    private func complete(_ task: TaskItem) {
        withAnimation(ChronosMotion.snappy) {
            let wasCompleted = task.isCompleted
            task.isCompleted.toggle()
            if !wasCompleted, let rec = task.recurrence {
                let anchor = task.dueDate ?? task.targetDate ?? .now
                if let next = rec.nextDate(after: anchor) {
                    context.insert(TaskItem(
                        title: task.title, detail: task.detail, priority: task.priority,
                        dueDate: task.dueDate != nil ? next : nil,
                        targetDate: task.targetDate != nil ? next : nil,
                        goalID: task.goalID, recurrence: rec,
                        checklist: task.checklist.map { ChecklistItem(text: $0.text, isDone: false, order: $0.order) }))
                }
            }
            try? context.save()
        }
    }

    /// Slot the task onto today's dial by setting its target date to today (Android `scheduleTaskToday`).
    /// iOS has no TaskSchedule entity, so "scheduling" pins the target day used by the schedule badge.
    private func scheduleToday(_ task: TaskItem) {
        withAnimation(ChronosMotion.snappy) {
            task.targetDate = Calendar.current.startOfDay(for: .now)
            if task.preferredDurationMinutes == nil { task.preferredDurationMinutes = 60 }
            task.updatedAt = .now
            try? context.save()
        }
    }

    /// Duplicate a task with reset completion / checklist progress (Android `duplicateTask`).
    private func duplicate(_ task: TaskItem) {
        let copy = TaskItem(
            title: task.title + " (copy)", detail: task.detail, priority: task.priority,
            dueDate: task.dueDate, preferredDurationMinutes: task.preferredDurationMinutes,
            preferredStartMinuteOfDay: task.preferredStartMinuteOfDay, targetDate: task.targetDate,
            goalID: task.goalID, recurrence: task.recurrence,
            checklist: task.checklist.map { ChecklistItem(text: $0.text, isDone: false, order: $0.order) })
        context.insert(copy)
        try? context.save()
    }

    private func delete(_ task: TaskItem) {
        withAnimation(ChronosMotion.snappy) {
            context.delete(task)
            try? context.save()
        }
    }

    /// Ask for notification authorization so urgent reminders can fire. The alert dismisses either way.
    private func requestNotifications() {
        Task {
            await ChronosNotifications.shared.requestAuthorization()
            await MainActor.run {
                withAnimation(ChronosMotion.snappy) { notificationAlertDismissed = true }
            }
        }
    }

    // MARK: - Bulk import plumbing

    /// Wraps the optional candidate list in an `Identifiable` box so `.sheet(item:)` presents it,
    /// and clears the state when the sheet is dismissed.
    private var bulkSheetBinding: Binding<BulkImportBox?> {
        Binding(
            get: { bulkImportCandidates.map(BulkImportBox.init) },
            set: { if $0 == nil { bulkImportCandidates = nil } }
        )
    }

    private struct BulkImportBox: Identifiable {
        let candidates: [String]
        var id: String { candidates.joined(separator: "\u{1f}") }
    }

    /// Pull any pending bulk-import candidates the Share Extension left in the App Group (recent
    /// only, mirroring the 30s freshness window the add-task single-share path uses) and surface the
    /// review sheet. Consumed once so it doesn't re-fire on the next foreground.
    private func drainPendingBulkImport() {
        let defaults = UserDefaults(suiteName: Self.appGroup)
        guard let candidates = defaults?.stringArray(forKey: "share.pendingBulkTasks"),
              !candidates.isEmpty,
              let ts = defaults?.double(forKey: "share.pendingTimestamp"),
              Date().timeIntervalSince1970 - ts < 30 else { return }
        defaults?.removeObject(forKey: "share.pendingBulkTasks")
        bulkImportCandidates = candidates
    }
}

// MARK: - Task row

/// A rich task card mirroring Android's `TaskItem` composable: a completion checkbox, the title with
/// a priority badge, a description preview, a flowing row of metadata badges (alarm / dial-gap /
/// schedule / connections), an action cue, and a footer of Schedule / Duplicate / Edit / Delete
/// actions. Tapping the body opens the quick-action context sheet.
private struct TaskRow: View {
    let task: TaskItem
    let onToggle: () -> Void
    let onTap: () -> Void
    let onSchedule: () -> Void
    let onDuplicate: () -> Void
    let onEdit: () -> Void
    let onDelete: () -> Void

    var body: some View {
        ChronosGlassCard(tone: task.isUrgent && !task.isCompleted ? .standard : .quiet,
                         tint: rowTint) {
            VStack(alignment: .leading, spacing: ChronosSpacing.compact) {
                HStack(alignment: .top, spacing: ChronosSpacing.compact) {
                    Button(action: onToggle) {
                        Image(systemName: task.isCompleted ? "checkmark.circle.fill" : "circle")
                            .font(.title3)
                            .foregroundStyle(task.isCompleted ? ChronosColors.brandSecondary : .secondary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(task.isCompleted ? "Mark \(task.title) incomplete" : "Mark \(task.title) complete")

                    VStack(alignment: .leading, spacing: ChronosSpacing.micro) {
                        HStack(alignment: .firstTextBaseline) {
                            Text(task.title)
                                .font(.chronosHeadline)
                                .strikethrough(task.isCompleted)
                                .foregroundStyle(task.isCompleted ? .secondary : .primary)
                            Spacer(minLength: ChronosSpacing.small)
                            PriorityBadge(priority: task.priority)
                        }

                        if let detail = task.detail, !detail.isEmpty {
                            Text(detail)
                                .font(.chronosCaption)
                                .foregroundStyle(.secondary)
                                .lineLimit(3)
                        }

                        metadataBadges

                        Text(actionCue)
                            .font(.chronosCaption)
                            .fontWeight(.semibold)
                            .foregroundStyle(ChronosColors.brandPrimary)
                            .padding(.top, 2)
                    }
                }
                .contentShape(Rectangle())
                .onTapGesture(perform: onTap)

                footer
            }
        }
    }

    private var rowTint: Color? {
        if task.isCompleted { return nil }
        if task.isUrgent { return ChronosColors.brandAccent }
        return nil
    }

    /// Flowing metadata badges (Android `TaskMetadataBadge` row): alarm state, dial-gap, schedule
    /// summary, and connections.
    @ViewBuilder private var metadataBadges: some View {
        let badges = buildBadges()
        if !badges.isEmpty {
            BadgeFlow(spacing: ChronosSpacing.small) {
                ForEach(Array(badges.enumerated()), id: \.offset) { _, badge in
                    MetadataBadge(text: badge.text, systemImage: badge.icon, color: badge.color)
                }
            }
            .padding(.top, 2)
        }
    }

    private struct Badge { let text: String; let icon: String?; let color: Color }

    private func buildBadges() -> [Badge] {
        var badges: [Badge] = []
        if task.isUrgent && !task.isCompleted {
            let label = task.dueDate.map { "Reminder \($0.formatted(.dateTime.month().day().hour().minute()))" }
                ?? "Reminder not set"
            badges.append(Badge(text: label, icon: "alarm", color: ChronosColors.brandPrimary))
        }
        if !task.isCompleted && task.targetDate == nil && task.recurrence == nil {
            badges.append(Badge(text: "Not on dial", icon: "calendar.badge.exclamationmark",
                                color: ChronosColors.brandAccent))
        }
        if let summary = task.scheduleSummary() {
            badges.append(Badge(text: summary, icon: "calendar", color: ChronosColors.brandSecondary))
        }
        if let connection = task.connectionSummary {
            badges.append(Badge(text: connection, icon: "link", color: ChronosColors.brandSecondary))
        }
        return badges
    }

    /// Human prompt for what tapping the row does (Android `taskContextCue`).
    private var actionCue: String {
        if task.isCompleted { return "Tap for actions · Reopen" }
        if task.targetDate != nil || task.recurrence != nil { return "Tap for actions · Edit and add occurrence" }
        return "Tap for actions · Edit and schedule"
    }

    /// Schedule (open-only) on the left; Duplicate / Edit / Delete on the right (Android footer row).
    private var footer: some View {
        HStack(spacing: ChronosSpacing.small) {
            if !task.isCompleted {
                Button(action: onSchedule) {
                    Label(scheduleLabel, systemImage: "calendar.badge.plus")
                        .font(.chronosCaption)
                }
                .buttonStyle(.borderedProminent)
                .tint(ChronosColors.brandSecondary)
            }
            Spacer(minLength: 0)
            Button(action: onDuplicate) {
                Image(systemName: "doc.on.doc")
            }
            .buttonStyle(.plain)
            .foregroundStyle(.secondary)
            .accessibilityLabel("Duplicate \(task.title)")

            Button(action: onEdit) {
                Image(systemName: "pencil")
            }
            .buttonStyle(.plain)
            .foregroundStyle(.secondary)
            .accessibilityLabel("Edit \(task.title)")

            Button(action: onDelete) {
                Image(systemName: "trash")
            }
            .buttonStyle(.plain)
            .foregroundStyle(ChronosColors.brandAccent)
            .accessibilityLabel("Delete \(task.title)")
        }
    }

    private var scheduleLabel: String {
        (task.targetDate != nil || task.recurrence != nil) ? "Add occurrence" : "Schedule"
    }
}

// MARK: - Context command sheet

/// Quick-action sheet for a task (Android `TaskContextCommandSheet`): shows the best next action
/// emphasized, then the remaining internal options (Schedule / Edit / Complete). The iOS task model
/// carries no external contact/app commands at runtime, so this surfaces the internal actions Android
/// always includes.
private struct TaskContextCommandSheet: View {
    @Environment(\.dismiss) private var dismiss
    let task: TaskItem
    let onSchedule: () -> Void
    let onEdit: () -> Void
    let onComplete: () -> Void

    private struct Command: Identifiable {
        let id = UUID()
        let label: String
        let description: String
        let shortLabel: String
        let icon: String
        let action: () -> Void
    }

    private var commands: [Command] {
        var list: [Command] = []
        if !task.isCompleted {
            list.append(Command(
                label: (task.targetDate != nil || task.recurrence != nil) ? "Add DayDial occurrence" : "Schedule on DayDial",
                description: "Protect time on today's DayDial",
                shortLabel: (task.targetDate != nil || task.recurrence != nil) ? "Add occurrence" : "Schedule",
                icon: "calendar.badge.plus", action: onSchedule))
        }
        list.append(Command(
            label: task.isCompleted ? "Reopen task" : "Mark complete",
            description: task.isCompleted ? "Move this task back to Open" : "Move this task to Done",
            shortLabel: task.isCompleted ? "Reopen" : "Complete",
            icon: task.isCompleted ? "arrow.uturn.backward.circle" : "checkmark.circle",
            action: onComplete))
        list.append(Command(
            label: "Edit task",
            description: "Change title, timing, checklist, or goal link",
            shortLabel: "Edit", icon: "pencil", action: onEdit))
        return list
    }

    var body: some View {
        let all = commands
        let primary = all.first
        let remaining = Array(all.dropFirst())

        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: ChronosSpacing.compact) {
                    Text(countText(all.count))
                        .font(.chronosCaption)
                        .foregroundStyle(.secondary)

                    if let primary {
                        Text(all.count == 1 ? "Best quick action" : "Best next action")
                            .font(.chronosLabel)
                            .foregroundStyle(ChronosColors.brandPrimary)
                        CommandRow(command: primary, emphasized: true) { primary.action() }
                    }
                    if !remaining.isEmpty {
                        Text("\(remaining.count) more option\(remaining.count == 1 ? "" : "s")")
                            .font(.chronosLabel)
                            .foregroundStyle(.secondary)
                        ForEach(remaining) { command in
                            CommandRow(command: command, emphasized: false) { command.action() }
                        }
                    }
                }
                .padding(ChronosSpacing.standard)
            }
            .background { ChronosBackdrop() }
            .navigationTitle(task.title)
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func countText(_ count: Int) -> String {
        switch count {
        case 0: "No quick actions"
        case 1: "1 quick action for this task."
        default: "\(count) quick actions for this task."
        }
    }

    private struct CommandRow: View {
        let command: Command
        let emphasized: Bool
        let action: () -> Void

        var body: some View {
            Button(action: action) {
                HStack(spacing: ChronosSpacing.compact) {
                    Image(systemName: command.icon)
                        .foregroundStyle(emphasized ? ChronosColors.brandPrimary : .secondary)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(command.label)
                            .font(.chronosLabel)
                            .foregroundStyle(.primary)
                        Text(command.description)
                            .font(.chronosCaption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer(minLength: 0)
                    Text(command.shortLabel)
                        .font(.chronosCaption)
                        .foregroundStyle(emphasized ? ChronosColors.brandPrimary : .secondary)
                }
                .padding(ChronosSpacing.compact)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: ChronosRadius.medium, style: .continuous)
                        .fill(emphasized ? ChronosColors.brandPrimary.opacity(0.12) : Color(.tertiarySystemFill).opacity(0.5))
                )
            }
            .buttonStyle(.plain)
        }
    }
}

// MARK: - Attention card

/// Dismissible permission / status banner (Android `TaskAttentionCard`).
private struct TaskAttentionCard: View {
    let title: String
    let message: String
    let actionLabel: String
    let onAction: () -> Void
    var onDismiss: (() -> Void)?

    var body: some View {
        ChronosGlassCard(tone: .quiet, tint: ChronosColors.brandAccent) {
            HStack(alignment: .center, spacing: ChronosSpacing.compact) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.chronosLabel)
                        .foregroundStyle(ChronosColors.brandAccent)
                    Text(message)
                        .font(.chronosCaption)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
                Button(actionLabel, action: onAction)
                    .font(.chronosCaption.weight(.semibold))
                    .buttonStyle(.borderless)
                if let onDismiss {
                    Button("Dismiss", action: onDismiss)
                        .font(.chronosCaption)
                        .buttonStyle(.borderless)
                        .foregroundStyle(.secondary)
                        .accessibilityLabel("Dismiss \(title) alert")
                }
            }
        }
    }
}

// MARK: - Reusable visual atoms

/// A compact card-like metric (label + big value + accent), mirroring Android's `ChronosMetricTile`.
private struct MetricTile: View {
    let label: String
    let value: String
    let systemImage: String
    let tint: Color

    var body: some View {
        ChronosGlassCard(tone: .quiet, tint: tint) {
            VStack(alignment: .leading, spacing: ChronosSpacing.micro) {
                Label(label, systemImage: systemImage)
                    .font(.chronosCaption).foregroundStyle(.secondary)
                    .labelStyle(.titleAndIcon)
                Text(value).font(.chronosTitle).foregroundStyle(tint)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label): \(value)")
    }
}

/// A pill-shaped filter chip (Android `ChronosFilterChip`).
private struct FilterChip: View {
    let label: String
    let selected: Bool
    var tint: Color = ChronosColors.brandPrimary
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.chronosCaption)
                .padding(.horizontal, ChronosSpacing.compact)
                .padding(.vertical, ChronosSpacing.small)
                .background(selected ? tint : Color(.tertiarySystemFill), in: Capsule())
                .foregroundStyle(selected ? .white : .primary)
        }
        .buttonStyle(.plain)
    }
}

/// The priority capsule shown beside a task title (Android `PriorityBadge`).
private struct PriorityBadge: View {
    let priority: Int

    var body: some View {
        Text(label)
            .font(.chronosCaption)
            .padding(.horizontal, ChronosSpacing.small)
            .padding(.vertical, 3)
            .background(color.opacity(0.18), in: Capsule())
            .foregroundStyle(color)
    }

    private var label: String {
        switch priority { case 3, 2: "Urgent"; case 1: "High"; default: "Normal" }
    }

    private var color: Color {
        switch priority { case 3, 2: ChronosColors.brandAccent; case 1: ChronosColors.brandPrimary; default: .secondary }
    }
}

/// A small icon+text metadata badge with a tinted container (Android `TaskMetadataBadge`).
private struct MetadataBadge: View {
    let text: String
    let systemImage: String?
    let color: Color

    var body: some View {
        HStack(spacing: ChronosSpacing.micro) {
            if let systemImage {
                Image(systemName: systemImage).font(.caption2).foregroundStyle(color)
            }
            Text(text).font(.chronosCaption).foregroundStyle(color)
        }
        .padding(.horizontal, ChronosSpacing.small)
        .padding(.vertical, 3)
        .background(color.opacity(0.10), in: RoundedRectangle(cornerRadius: ChronosRadius.extraSmall, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: ChronosRadius.extraSmall, style: .continuous)
                .strokeBorder(color.opacity(0.18), lineWidth: 0.5)
        )
    }
}

/// A minimal flowing wrap layout for the metadata badges (mirrors Android `FlowRow`).
private struct BadgeFlow: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var rowWidth: CGFloat = 0, rowHeight: CGFloat = 0
        var totalHeight: CGFloat = 0, totalWidth: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if rowWidth + size.width > maxWidth, rowWidth > 0 {
                totalHeight += rowHeight + spacing
                totalWidth = max(totalWidth, rowWidth - spacing)
                rowWidth = 0; rowHeight = 0
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
        var x = bounds.minX, y = bounds.minY, rowHeight: CGFloat = 0
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

#Preview {
    TasksView().modelContainer(ChronosStore.previewContainer())
}
