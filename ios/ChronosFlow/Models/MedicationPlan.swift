import Foundation
import SwiftData

/// A medication schedule with reminders, dose acknowledgement, and refill tracking.
/// Ported from `MedicationPlan.kt`. ChronosFlow helps track and remind — it does not give
/// medical advice (carried over verbatim from the Android app description).
@Model
final class MedicationPlan {
    @Attribute(.unique) var id: String
    var name: String
    var dosage: String
    var unit: String
    var notes: String?
    var startAt: Date?
    var endAt: Date?
    var reminderMinuteOfDay: Int
    var takeWithFood: Bool
    var missedCount: Int
    var refillNeededAfterDoses: Int?
    /// Remaining doses in the current supply; decremented on each acknowledged dose for refill ETA.
    var remainingDoses: Int?
    var isActive: Bool

    /// Days/times the dose recurs (minute-of-day list), flattening `MedicationSchedule`.
    var reminderMinutes: [Int]
    /// Logged dose acknowledgements (timestamps), flattening `recentDoseEvents`.
    var takenAt: [Date]

    init(
        id: String = UUID().uuidString,
        name: String,
        dosage: String = "",
        unit: String = "mg",
        notes: String? = nil,
        startAt: Date? = nil,
        endAt: Date? = nil,
        reminderMinuteOfDay: Int = 8 * 60,
        takeWithFood: Bool = false,
        missedCount: Int = 0,
        refillNeededAfterDoses: Int? = nil,
        remainingDoses: Int? = nil,
        isActive: Bool = true,
        reminderMinutes: [Int]? = nil,
        takenAt: [Date] = []
    ) {
        self.id = id
        self.name = name
        self.dosage = dosage
        self.unit = unit
        self.notes = notes
        self.startAt = startAt
        self.endAt = endAt
        self.reminderMinuteOfDay = reminderMinuteOfDay
        self.takeWithFood = takeWithFood
        self.missedCount = missedCount
        self.refillNeededAfterDoses = refillNeededAfterDoses
        self.remainingDoses = remainingDoses
        self.isActive = isActive
        self.reminderMinutes = reminderMinutes ?? [reminderMinuteOfDay]
        self.takenAt = takenAt
    }

    func acknowledgeDose(at date: Date = .now) {
        takenAt.append(date)
        if let remaining = remainingDoses { remainingDoses = max(remaining - 1, 0) }
    }

    /// Days until refill is needed, given remaining supply and doses per day. Mirrors
    /// ChronosCore.daysUntilRefill. Nil when supply isn't tracked.
    var daysUntilRefill: Int? {
        guard let remaining = remainingDoses else { return nil }
        let perDay = max(reminderMinutes.count, 1)
        return remaining / perDay
    }

    func isTaken(on day: Date) -> Bool {
        takenAt.contains { Calendar.current.isDate($0, inSameDayAs: day) }
    }
}
