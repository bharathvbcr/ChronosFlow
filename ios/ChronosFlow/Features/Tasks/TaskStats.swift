import SwiftUI
import Charts

// MARK: - Assistant triage
//
// iOS port of Android `feature/tasks/TaskAssistantSummary.kt`. Derives a short headline + a concrete
// next step from the open/urgent task mix, used by the Tasks "Assistant triage" card. Pure (no I/O)
// so it stays testable and matches the Android logic line for line.

/// A two-line triage summary for the task list (Android `TaskAssistantSummary`).
struct TaskAssistantSummary {
    let headline: String
    let nextStep: String
}

/// Build the triage summary from the current task list. Mirrors Android `buildTaskAssistantSummary`:
/// an iOS task counts as "having protected time" when it has a target date plus a preferred duration
/// (the iOS equivalent of Android's TaskSchedule presence).
func buildTaskAssistantSummary(tasks: [TaskItem]) -> TaskAssistantSummary {
    let open = tasks.filter { !$0.isCompleted }
    let urgent = open.filter { $0.isUrgent }
    let unscheduledUrgent = urgent.filter { !taskHasProtectedTime($0) }

    let headline: String
    if !unscheduledUrgent.isEmpty {
        headline = "\(unscheduledUrgent.count) urgent task(s) still need protected time."
    } else if !urgent.isEmpty {
        headline = "Urgent tasks are captured and ready for DayDial placement."
    } else if open.isEmpty {
        headline = "Your task inbox is clear."
    } else {
        headline = "\(open.count) open task(s) are waiting for prioritization."
    }

    let nextStep: String
    if !unscheduledUrgent.isEmpty {
        nextStep = "Schedule the top urgent task into DayDial before adding new commitments."
    } else if !urgent.isEmpty {
        nextStep = "Review the protected urgent work before pulling in lower-priority tasks."
    } else if open.count >= 5 {
        nextStep = "Batch the backlog into one planning pass and schedule only the next few concrete tasks."
    } else {
        nextStep = "Keep the next task concrete, then schedule it once the rest of the day is stable."
    }
    return TaskAssistantSummary(headline: headline, nextStep: nextStep)
}

private func taskHasProtectedTime(_ task: TaskItem) -> Bool {
    task.targetDate != nil && task.preferredDurationMinutes != nil
}

// MARK: - Stats snapshot
//
// iOS port of Android `feature/tasks/TaskStatsCard.kt`. A completion ring, this-week progress,
// due-today / overdue pills, an open-by-priority breakdown bar, and a 7-day completion trend — all
// derived from the in-memory task list (no extra persistence), matching the Android card.

/// Engagement snapshot derived from the in-memory task list (Android `TaskStatsSnapshot`).
struct TaskStatsSnapshot {
    let total: Int
    let done: Int
    let completedThisWeek: Int
    let dueToday: Int
    let overdue: Int
    let openLow: Int
    let openMedium: Int
    let openHigh: Int

    var completionRate: Double { total == 0 ? 0 : Double(done) / Double(total) }
    var openCount: Int { openLow + openMedium + openHigh }
}

func buildTaskStatsSnapshot(tasks: [TaskItem], now: Date = .now, calendar: Calendar = .current) -> TaskStatsSnapshot {
    let today = calendar.startOfDay(for: now)
    let weekAgo = calendar.date(byAdding: .day, value: -7, to: now) ?? now
    let open = tasks.filter { !$0.isCompleted }
    return TaskStatsSnapshot(
        total: tasks.count,
        done: tasks.filter(\.isCompleted).count,
        completedThisWeek: tasks.filter { $0.isCompleted && $0.updatedAt >= weekAgo }.count,
        dueToday: open.filter { task in
            guard let due = task.dueDate else { return false }
            return calendar.isDate(due, inSameDayAs: today)
        }.count,
        overdue: open.filter { ($0.dueDate.map { $0 < now }) ?? false }.count,
        openLow: open.filter { $0.priority <= 0 }.count,
        openMedium: open.filter { $0.priority == 1 }.count,
        openHigh: open.filter { $0.priority >= 2 }.count
    )
}

/// Tasks completed on each of the trailing `days` days (oldest first), bucketed by `updatedAt`.
/// Mirrors Android `taskCompletionByDay`.
func taskCompletionByDay(tasks: [TaskItem], today: Date = .now, calendar: Calendar = .current, days: Int = 7) -> [Int] {
    guard days > 0 else { return [] }
    let base = calendar.startOfDay(for: today)
    let start = calendar.date(byAdding: .day, value: -(days - 1), to: base) ?? base
    let completedDays = tasks.filter(\.isCompleted).map { calendar.startOfDay(for: $0.updatedAt) }
    return (0..<days).map { offset in
        let date = calendar.date(byAdding: .day, value: offset, to: start) ?? start
        return completedDays.filter { calendar.isDate($0, inSameDayAs: date) }.count
    }
}

func taskStatsHeadline(_ stats: TaskStatsSnapshot) -> String {
    if stats.total == 0 { return "Add a task to get started." }
    if stats.openCount == 0 { return "All clear — every task is done!" }
    if stats.completionRate >= 0.75 { return "Almost there. Finish strong." }
    if stats.completedThisWeek > 0 { return "Nice momentum this week." }
    return "Knock out your first task today."
}

/// Motivating overview card for the Tasks page (Android `TaskStatsCard`): a completion ring with
/// this-week progress, due-today / overdue pills, an open-work priority breakdown, and a 7-day trend.
struct TaskStatsCard: View {
    let tasks: [TaskItem]

    private var stats: TaskStatsSnapshot { buildTaskStatsSnapshot(tasks: tasks) }

    var body: some View {
        let s = stats
        if s.total > 0 {
            ChronosGlassCard {
                VStack(alignment: .leading, spacing: ChronosSpacing.compact) {
                    HStack(spacing: ChronosSpacing.standard) {
                        CompletionRing(progress: s.completionRate)
                        VStack(alignment: .leading, spacing: ChronosSpacing.micro) {
                            Text("Progress").font(.chronosHeadline)
                            Text(taskStatsHeadline(s))
                                .font(.chronosCaption).foregroundStyle(.secondary)
                            Text("\(s.completedThisWeek) completed this week")
                                .font(.chronosCaption).foregroundStyle(ChronosColors.brandPrimary)
                        }
                        Spacer(minLength: 0)
                    }

                    if s.dueToday > 0 || s.overdue > 0 {
                        HStack(spacing: ChronosSpacing.small) {
                            if s.dueToday > 0 {
                                StatPill(label: "\(s.dueToday) due today", color: ChronosColors.brandPrimary)
                            }
                            if s.overdue > 0 {
                                StatPill(label: "\(s.overdue) overdue", color: ChronosColors.brandAccent)
                            }
                        }
                    }

                    if s.openCount > 0 {
                        PriorityBreakdownBar(stats: s)
                    }

                    let trend = taskCompletionByDay(tasks: tasks)
                    if trend.reduce(0, +) > 0 {
                        VStack(alignment: .leading, spacing: ChronosSpacing.micro) {
                            Text("Completed · last 7 days")
                                .font(.chronosCaption).foregroundStyle(.secondary)
                            Chart(Array(trend.enumerated()), id: \.offset) { index, count in
                                BarMark(x: .value("Day", index), y: .value("Completed", count))
                                    .foregroundStyle(count > 0 ? ChronosColors.brandSecondary : Color.secondary.opacity(0.25))
                                    .cornerRadius(3)
                            }
                            .chartXAxis(.hidden)
                            .chartYAxis { AxisMarks(position: .leading, values: .automatic(desiredCount: 3)) }
                            .frame(height: 56)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
}

/// Circular completion ring with a centered percentage (Android `CompletionRing`).
private struct CompletionRing: View {
    let progress: Double
    @State private var sweep: Double = 0

    var body: some View {
        ZStack {
            Circle()
                .stroke(Color.secondary.opacity(0.18), style: StrokeStyle(lineWidth: 9, lineCap: .round))
            Circle()
                .trim(from: 0, to: max(0, min(sweep, 1)))
                .stroke(ChronosColors.brandPrimary, style: StrokeStyle(lineWidth: 9, lineCap: .round))
                .rotationEffect(.degrees(-90))
            Text("\(Int((max(0, min(sweep, 1))) * 100))%")
                .font(.chronosHeadline)
        }
        .frame(width: 84, height: 84)
        .onAppear { withAnimation(ChronosMotion.smooth) { sweep = progress } }
        .onChange(of: progress) { _, new in withAnimation(ChronosMotion.smooth) { sweep = new } }
        .accessibilityLabel("\(Int(progress * 100)) percent of tasks complete")
    }
}

/// A pill summarizing a due/overdue count (Android `TaskStatPill`).
private struct StatPill: View {
    let label: String
    let color: Color

    var body: some View {
        Text(label)
            .font(.chronosCaption.weight(.semibold))
            .padding(.horizontal, ChronosSpacing.compact)
            .padding(.vertical, ChronosSpacing.micro + 2)
            .background(color.opacity(0.18), in: Capsule())
            .foregroundStyle(color)
    }
}

/// A stacked bar of open work split by priority, with a legend (Android `PriorityBreakdownBar`).
private struct PriorityBreakdownBar: View {
    let stats: TaskStatsSnapshot

    private let high = ChronosColors.brandAccent
    private let medium = Color(red: 0.95, green: 0.62, blue: 0.24)
    private let low = ChronosColors.brandSecondary

    var body: some View {
        VStack(alignment: .leading, spacing: ChronosSpacing.micro + 2) {
            Text("\(stats.openCount) open by priority")
                .font(.chronosCaption).foregroundStyle(.secondary)
            GeometryReader { geo in
                let total = max(CGFloat(stats.openCount), 1)
                HStack(spacing: 0) {
                    if stats.openHigh > 0 {
                        Rectangle().fill(high).frame(width: geo.size.width * CGFloat(stats.openHigh) / total)
                    }
                    if stats.openMedium > 0 {
                        Rectangle().fill(medium).frame(width: geo.size.width * CGFloat(stats.openMedium) / total)
                    }
                    if stats.openLow > 0 {
                        Rectangle().fill(low).frame(width: geo.size.width * CGFloat(stats.openLow) / total)
                    }
                }
                .clipShape(Capsule())
            }
            .frame(height: 10)
            .background(Color.secondary.opacity(0.15), in: Capsule())

            HStack(spacing: ChronosSpacing.compact) {
                if stats.openHigh > 0 { legend("Urgent \(stats.openHigh)", high) }
                if stats.openMedium > 0 { legend("Medium \(stats.openMedium)", medium) }
                if stats.openLow > 0 { legend("Normal \(stats.openLow)", low) }
            }
        }
    }

    private func legend(_ label: String, _ color: Color) -> some View {
        HStack(spacing: ChronosSpacing.micro) {
            Circle().fill(color).frame(width: 8, height: 8)
            Text(label).font(.chronosCaption).foregroundStyle(.secondary)
        }
    }
}
