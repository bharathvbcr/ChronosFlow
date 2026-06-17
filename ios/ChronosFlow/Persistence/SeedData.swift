import Foundation
import SwiftData

/// Seeds a believable day so the app, previews, and widgets show something real on first run.
enum SeedData {
    @MainActor
    static func populate(_ context: ModelContext) {
        // Only seed an empty store.
        let existing = try? context.fetch(FetchDescriptor<TimeBlock>())
        guard (existing?.isEmpty ?? true) else { return }

        let today = Calendar.current.startOfDay(for: .now)

        let blocks: [TimeBlock] = [
            TimeBlock(date: today, title: "Sleep", category: "SLEEP",
                      startMinuteOfDay: 0, durationMinutes: 6 * 60 + 30,
                      provenance: .routine, flexibility: .fixed, energyLevel: .low),
            TimeBlock(date: today, title: "Morning routine", category: "ROUTINE",
                      startMinuteOfDay: 6 * 60 + 30, durationMinutes: 45,
                      provenance: .routine, energyLevel: .moderate),
            TimeBlock(date: today, title: "Deep work", category: "FOCUS",
                      startMinuteOfDay: 9 * 60, durationMinutes: 120,
                      provenance: .manual, flexibility: .movable, energyLevel: .high),
            TimeBlock(date: today, title: "Lunch", category: "MEAL",
                      startMinuteOfDay: 12 * 60 + 30, durationMinutes: 45,
                      provenance: .routine, energyLevel: .low),
            TimeBlock(date: today, title: "Study", category: "STUDY",
                      startMinuteOfDay: 14 * 60, durationMinutes: 90,
                      provenance: .manual, energyLevel: .high),
            TimeBlock(date: today, title: "Workout", category: "EXERCISE",
                      startMinuteOfDay: 18 * 60, durationMinutes: 60,
                      provenance: .habit, energyLevel: .intense),
        ]
        blocks.forEach(context.insert)

        let tasks = [
            TaskItem(title: "Finish quarterly report", priority: 3, targetDate: today,
                     preferredDurationMinutes: 90),
            TaskItem(title: "Reply to mentor email", priority: 2, targetDate: today),
            TaskItem(title: "Plan weekend trip", priority: 1),
        ]
        tasks.forEach(context.insert)

        let habits = [
            Habit(title: "Read 20 minutes", cadence: "DAILY", difficulty: 2, streakCount: 4),
            Habit(title: "Meditate", cadence: "DAILY", difficulty: 1, streakCount: 12),
            Habit(title: "Gym", cadence: "3x/week", difficulty: 3, streakCount: 2),
        ]
        habits.forEach(context.insert)

        context.insert(Goal(title: "Run a half marathon", category: "FITNESS",
                            targetValue: 30, progressValue: 11))
        context.insert(Goal(title: "Read 12 books this year", category: "LEARNING",
                            targetValue: 12, progressValue: 5))

        context.insert(MedicationPlan(name: "Vitamin D", dosage: "1000", unit: "IU",
                                      reminderMinuteOfDay: 8 * 60, takeWithFood: true,
                                      reminderMinutes: [8 * 60]))

        context.insert(SleepTrack(date: today, actualStartMinute: 23 * 60, actualEndMinute: 6 * 60 + 30,
                                  sleepQuality: 4, interruptedCount: 1))

        try? context.save()
    }
}
