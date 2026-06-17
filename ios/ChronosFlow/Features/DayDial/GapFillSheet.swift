import SwiftUI
import SwiftData
import ChronosCore

/// Fit unscheduled tasks into today's free windows, shown for review before applying (the same
/// review-before-apply contract as the AI planner). The packing mirrors ChronosCore.gapFill, which
/// is unit-tested off-device.
///
/// Sleep-readiness aware: after a DEPLETED night (derived from the most recently logged night up to
/// `date`), demanding (high-priority) tasks are floored past the grogginess window
/// (`ReadinessSchedule.demandingTaskEarliestStartMinute`) and recovery breaks lengthen 20→25.
struct GapFillSheet: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    let date: Date
    let existingBlocks: [TimeBlock]
    @Query private var allTasks: [TaskItem]
    @Query private var nights: [SleepTrack]

    private var freeWindows: [FreeWindow] { PlannerMath.freeWindows(in: existingBlocks) }

    /// Readiness from the most recently logged night up to `date` (last night only bears on today's
    /// plan, matching Android's ScheduleTaskIntoDayUseCase). Future dates ignore it → `.unknown`.
    /// Bridged to ChronosCore's `SleepReadiness` (same raw values) so the portable scheduling
    /// helpers can be applied.
    private var readiness: ChronosCore.SleepReadiness {
        guard Calendar.current.isDate(date, inSameDayAs: .now) else { return .unknown }
        let cal = Calendar.current
        let today = cal.startOfDay(for: date)
        guard let yesterday = cal.date(byAdding: .day, value: -1, to: today) else { return .unknown }
        let recent = nights
            .filter { $0.date >= yesterday && $0.date <= today }
            .max { $0.date < $1.date }
        // App-side deriveSleepReadiness(lastNight: SleepTrack?) → app SleepReadiness; map by rawValue.
        let appReadiness = deriveSleepReadiness(lastNight: recent)
        return ChronosCore.SleepReadiness(rawValue: appReadiness.rawValue) ?? .unknown
    }

    /// Incomplete tasks not already scheduled as a block today.
    private var candidates: [TaskItem] {
        let scheduledTaskIDs = Set(existingBlocks.compactMap(\.taskID))
        return allTasks.filter { !$0.isCompleted && !scheduledTaskIDs.contains($0.id) }
    }

    private var suggestions: [Suggestion] { pack() }

    var body: some View {
        NavigationStack {
            ZStack {
                ChronosBackdrop()
                content
            }
            .navigationTitle("Fill free time")
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } }
                if !suggestions.isEmpty {
                    ToolbarItem(placement: .confirmationAction) { Button("Apply", action: apply) }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    @ViewBuilder private var content: some View {
        if freeWindows.isEmpty {
            ContentUnavailableView("No free time", systemImage: "calendar.badge.exclamationmark",
                                   description: Text("Today's plan is full — nothing to fill."))
        } else if suggestions.isEmpty {
            ContentUnavailableView("Nothing to schedule", systemImage: "checklist",
                                   description: Text("No unscheduled tasks fit the open windows."))
        } else {
            ScrollView {
                VStack(alignment: .leading, spacing: ChronosSpacing.compact) {
                    let taskCount = suggestions.filter { !$0.isBreak }.count
                    Text("\(taskCount) task\(taskCount == 1 ? "" : "s") fit into \(freeWindows.count) open window\(freeWindows.count == 1 ? "" : "s").")
                        .font(.chronosCaption).foregroundStyle(.secondary)
                    if readiness == .depleted {
                        Text("After a poor night, demanding tasks start later and breaks run longer to help you recover.")
                            .font(.chronosCaption).foregroundStyle(ChronosColors.brandAccent)
                    }
                    ForEach(suggestions) { s in
                        ChronosGlassCard(tint: s.isBreak ? ChronosColors.brandAccent : ChronosColors.brandSecondary) {
                            HStack {
                                Text(s.title).font(.chronosLabel)
                                Spacer()
                                Text("\(s.startMinute.clockTime) · \(s.duration)m")
                                    .font(.chronosCaption).foregroundStyle(.secondary)
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }
                }
                .padding(ChronosSpacing.standard)
            }
        }
    }

    private struct Suggestion: Identifiable {
        let id = UUID()
        let taskID: String?
        let title: String
        let startMinute: Int
        let duration: Int
        let isBreak: Bool
        init(taskID: String?, title: String, startMinute: Int, duration: Int, isBreak: Bool = false) {
            self.taskID = taskID
            self.title = title
            self.startMinute = startMinute
            self.duration = duration
            self.isBreak = isBreak
        }
    }

    /// Priority desc, then duration desc; first-fit into windows. Mirrors ChronosCore.gapFill.
    ///
    /// Readiness-aware (Part B): after a DEPLETED night, high-priority ("demanding") tasks are
    /// floored to `ReadinessSchedule.demandingTaskEarliestStartMinute` (>= 11:00) so they slide past
    /// the morning grogginess window; lighter tasks keep first-fit. A long depleted-day focus stretch
    /// is broken up with a longer recovery break (`ReadinessSchedule.recoveryBreakMinutes`).
    private func pack() -> [Suggestion] {
        let currentReadiness = readiness
        let demandingFloor = ReadinessSchedule.demandingTaskEarliestStartMinute(currentReadiness)
        let focusStretch = ReadinessSchedule.focusStretchMinutes(currentReadiness)
        let breakMinutes = ReadinessSchedule.recoveryBreakMinutes(currentReadiness)

        var cursors = freeWindows
            .sorted { $0.startMinute < $1.startMinute }
            .map { (start: $0.startMinute, end: $0.startMinute + $0.durationMinutes) }
        let ordered = candidates.sorted {
            $0.priority != $1.priority ? $0.priority > $1.priority
                : (($0.preferredDurationMinutes ?? 30) > ($1.preferredDurationMinutes ?? 30))
        }
        var result: [Suggestion] = []
        // Per-window running focus minutes since the last break, to know when to offer one.
        var focusSinceBreak: [Int: Int] = [:]

        for task in ordered {
            let duration = task.preferredDurationMinutes ?? 30
            // A high-priority task (priority >= 2) is treated as demanding work.
            let isDemanding = task.priority >= 2
            for i in cursors.indices {
                // After a depleted night, demanding tasks may not start before the floor.
                let earliest = (isDemanding ? demandingFloor : nil).map { max(cursors[i].start, $0) }
                    ?? cursors[i].start
                guard earliest >= cursors[i].start, cursors[i].end - earliest >= duration else { continue }

                // Insert a recovery break first if this window has accumulated a full focus stretch.
                let accrued = focusSinceBreak[i] ?? 0
                if accrued >= focusStretch, cursors[i].end - earliest >= breakMinutes + duration {
                    result.append(Suggestion(taskID: nil, title: "Recovery break",
                                             startMinute: earliest, duration: breakMinutes, isBreak: true))
                    cursors[i].start = earliest + breakMinutes
                    focusSinceBreak[i] = 0
                }
                let start = max(cursors[i].start, earliest)
                result.append(Suggestion(taskID: task.id, title: task.title,
                                         startMinute: start, duration: duration))
                cursors[i].start = start + duration
                focusSinceBreak[i, default: 0] += duration
                break
            }
        }
        return result.sorted { $0.startMinute < $1.startMinute }
    }

    private func apply() {
        for s in suggestions {
            context.insert(TimeBlock(
                date: date, title: s.title, category: s.isBreak ? "RECOVERY" : "FOCUS",
                startMinuteOfDay: s.startMinute, durationMinutes: s.duration,
                provenance: .ai, flexibility: .movable, source: "gapfill", taskID: s.taskID))
        }
        try? context.save()
        dismiss()
    }
}
