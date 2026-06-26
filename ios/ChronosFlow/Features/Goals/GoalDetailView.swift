import SwiftUI
import SwiftData
import ChronosCore

/// Full goal detail with linked-work visualization. Ports Android's `GoalDetailSheet.kt`:
/// a live progress header, optional description, then the tasks and habits that carry this
/// goal's `goalID` grouped into sections (each row shows completion / streak state). An
/// "Edit goal" button hands off to the shared `GoalEditorSheet` in edit mode.
///
/// Linked work is read live via `@Query` filtered on `goalID` so completing a task or logging
/// a habit elsewhere moves the header forward without any manual refresh — matching the Android
/// `observeDerivedProgress` flow.
struct GoalDetailView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @Bindable var goal: Goal

    /// Linked tasks/habits filtered to this goal. The predicate is built from the goal id so the
    /// query re-runs whenever the underlying rows change.
    @Query private var linkedTasks: [TaskItem]
    @Query private var linkedHabits: [Habit]

    @State private var editing = false

    init(goal: Goal) {
        self.goal = goal
        let goalID = goal.id
        _linkedTasks = Query(
            filter: #Predicate<TaskItem> { $0.goalID == goalID },
            sort: [SortDescriptor(\TaskItem.priority, order: .reverse),
                   SortDescriptor(\TaskItem.createdAt)]
        )
        _linkedHabits = Query(
            filter: #Predicate<Habit> { $0.goalID == goalID },
            sort: \Habit.title
        )
    }

    private var completedTaskCount: Int { linkedTasks.filter(\.isCompleted).count }
    private var habitCompletionCount: Int { linkedHabits.reduce(0) { $0 + $1.completionDates.count } }

    private var derived: GoalDerivedProgress {
        GoalDerivedProgress(completedTaskCount: completedTaskCount, habitCompletionCount: habitCompletionCount)
    }
    private var total: Int { goal.totalProgress(completedTasks: completedTaskCount, habitCompletions: habitCompletionCount) }
    private var fraction: Double { goal.progressFraction(completedTasks: completedTaskCount, habitCompletions: habitCompletionCount) }

    private var tint: Color { ChronosColors.category(goal.category) }
    private var isEmpty: Bool { linkedTasks.isEmpty && linkedHabits.isEmpty }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: ChronosSpacing.medium) {
                    header
                    if isEmpty { emptyState }
                    if !linkedTasks.isEmpty { tasksSection }
                    if !linkedHabits.isEmpty { habitsSection }
                }
                .padding(ChronosSpacing.standard)
            }
            .background { ChronosBackdrop() }
            .navigationTitle(goal.title)
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Edit goal") { editing = true } }
            }
            .sheet(isPresented: $editing) { GoalEditorSheet(goal: goal) }
        }
    }

    // MARK: Header

    private var header: some View {
        ChronosGlassCard(tone: .prominent, tint: tint) {
            VStack(alignment: .leading, spacing: ChronosSpacing.compact) {
                Text(goalProgressSummary(progress: total, target: goal.targetValue))
                    .font(.chronosHeadline)
                ProgressView(value: fraction).tint(tint)
                if let label = GoalLabels.goalLinkedWorkLabel(derived) {
                    Text(label).font(.chronosCaption).foregroundStyle(.secondary)
                }
                if let detail = goal.detail, !detail.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Text(detail).font(.chronosBody).foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: Empty state

    /// Centered title + message empty state, mirroring Android's `ChronosEmptyState` in
    /// GoalDetailSheet (shown when `linkedWork.isEmpty`).
    private var emptyState: some View {
        ContentUnavailableView(
            "No linked work yet",
            systemImage: "link",
            description: Text("Link tasks and habits to this goal from their edit screens — completing them then moves this goal forward automatically.")
        )
        .frame(maxWidth: .infinity)
        .padding(.vertical, ChronosSpacing.medium)
    }

    // MARK: Tasks

    private var tasksSection: some View {
        VStack(alignment: .leading, spacing: ChronosSpacing.small) {
            sectionHeader(title: "Tasks", subtitle: "\(completedTaskCount) of \(linkedTasks.count) complete")
            ForEach(linkedTasks) { task in
                GoalLinkedRow(
                    iconName: task.isCompleted ? "checkmark.circle.fill" : "circle",
                    iconTint: task.isCompleted ? tint : .secondary,
                    title: task.title,
                    struck: task.isCompleted,
                    subtitle: taskSubtitle(task)
                )
            }
        }
    }

    private func taskSubtitle(_ task: TaskItem) -> String? {
        var parts: [String] = []
        if let priority = taskPriorityLabel(task.priority) { parts.append(priority) }
        if let due = GoalLabels.goalDueLabel(targetDate: task.targetDate ?? task.dueDate,
                                             isCompleted: task.isCompleted, today: .now) {
            parts.append(due.text)
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    /// Mirrors Android's `taskPriorityLabel`: priority >= 2 → Urgent, == 1 → High, else none.
    private func taskPriorityLabel(_ priority: Int) -> String? {
        switch priority {
        case let p where p >= 2: "Urgent"
        case 1: "High"
        default: nil
        }
    }

    // MARK: Habits

    private var habitsSection: some View {
        VStack(alignment: .leading, spacing: ChronosSpacing.small) {
            sectionHeader(title: "Habits", subtitle: "\(linkedHabits.count) linked")
            ForEach(linkedHabits) { habit in
                GoalLinkedRow(
                    iconName: "repeat.circle.fill",
                    iconTint: habit.isActive ? tint : .secondary,
                    title: habit.title,
                    struck: false,
                    subtitle: habitSubtitle(habit)
                )
            }
        }
    }

    /// Mirrors Android's `GoalLinkedHabitRow` streak text rules.
    private func habitSubtitle(_ habit: Habit) -> String {
        let cadence = GoalLabels.cadenceLabel(habit.cadence)
        if !habit.isActive { return "Paused" }
        if habit.streakCount > 0 { return "\(habit.streakCount)-day streak · \(cadence)" }
        return cadence
    }

    // MARK: Helpers

    private func sectionHeader(title: String, subtitle: String) -> some View {
        HStack {
            Text(title).font(.chronosTitle)
            Spacer()
            Text(subtitle).font(.chronosCaption).foregroundStyle(.secondary)
        }
    }
}

/// A single linked-work row (leading status icon + title/subtitle column). Mirrors Android's
/// `GoalLinkedRow`.
private struct GoalLinkedRow: View {
    let iconName: String
    let iconTint: Color
    let title: String
    let struck: Bool
    let subtitle: String?

    var body: some View {
        HStack(spacing: ChronosSpacing.compact) {
            Image(systemName: iconName)
                .font(.title3)
                .foregroundStyle(iconTint)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.chronosBody).strikethrough(struck)
                if let subtitle { Text(subtitle).font(.chronosCaption).foregroundStyle(.secondary) }
            }
            Spacer()
        }
        .padding(.vertical, ChronosSpacing.micro)
    }
}
