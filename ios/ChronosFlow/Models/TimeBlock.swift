import Foundation
import SwiftData

/// A planned (or actual) span of the day, rendered as an arc on the Chronos Dial.
///
/// Ported from `TimeBlock.kt`. Times are stored as **minute-of-day** (0..1439) plus a
/// `durationMinutes` (1..1440), exactly like the Android model, so the dial geometry and
/// all planner maths port over unchanged. `date` is stored as the start-of-day `Date`.
@Model
final class TimeBlock {
    @Attribute(.unique) var id: String
    var date: Date
    var title: String
    var category: String
    var startMinuteOfDay: Int
    var durationMinutes: Int
    var timezoneIdentifier: String
    var provenance: BlockProvenance
    var flexibility: BlockFlexibility
    var energyLevel: EnergyIntensity
    var source: String

    // Cross-entity links (kept as ids to match the Android relational model).
    var taskID: String?
    var calendarEventID: Int64?
    var medicationPlanID: String?
    var habitID: String?
    var goalID: String?
    var routineID: String?
    var recurrenceRuleID: String?

    var isLocked: Bool
    var isProtected: Bool

    // Actual (logged) execution window, for planned-vs-actual review.
    var actualStartMinuteOfDay: Int?
    var actualEndMinuteOfDay: Int?

    var createdAt: Date
    var updatedAt: Date

    init(
        id: String = UUID().uuidString,
        date: Date,
        title: String,
        category: String = "FOCUS",
        startMinuteOfDay: Int,
        durationMinutes: Int,
        timezoneIdentifier: String = TimeZone.current.identifier,
        provenance: BlockProvenance = .manual,
        flexibility: BlockFlexibility = .movable,
        energyLevel: EnergyIntensity = .moderate,
        source: String = "user",
        taskID: String? = nil,
        calendarEventID: Int64? = nil,
        medicationPlanID: String? = nil,
        habitID: String? = nil,
        goalID: String? = nil,
        routineID: String? = nil,
        recurrenceRuleID: String? = nil,
        isLocked: Bool = false,
        isProtected: Bool = false,
        actualStartMinuteOfDay: Int? = nil,
        actualEndMinuteOfDay: Int? = nil,
        createdAt: Date = .now,
        updatedAt: Date = .now
    ) {
        precondition((0...1439).contains(startMinuteOfDay), "startMinuteOfDay must be 0..1439")
        precondition((1...1440).contains(durationMinutes), "durationMinutes must be 1..1440")
        self.id = id
        self.date = Calendar.current.startOfDay(for: date)
        self.title = title
        self.category = category
        self.startMinuteOfDay = startMinuteOfDay
        self.durationMinutes = durationMinutes
        self.timezoneIdentifier = timezoneIdentifier
        self.provenance = provenance
        self.flexibility = flexibility
        self.energyLevel = energyLevel
        self.source = source
        self.taskID = taskID
        self.calendarEventID = calendarEventID
        self.medicationPlanID = medicationPlanID
        self.habitID = habitID
        self.goalID = goalID
        self.routineID = routineID
        self.recurrenceRuleID = recurrenceRuleID
        self.isLocked = isLocked
        self.isProtected = isProtected
        self.actualStartMinuteOfDay = actualStartMinuteOfDay
        self.actualEndMinuteOfDay = actualEndMinuteOfDay
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }

    /// Mirrors the Kotlin `plannedEndMinuteOfDay` computed property (wraps past midnight).
    var plannedEndMinuteOfDay: Int { (startMinuteOfDay + durationMinutes) % 1440 }

    var crossesMidnight: Bool { startMinuteOfDay + durationMinutes > 1440 }

    func updateStart(_ minute: Int) {
        startMinuteOfDay = ((minute % 1440) + 1440) % 1440
        updatedAt = .now
    }

    func updateDuration(_ minutes: Int) {
        durationMinutes = min(max(minutes, 1), 1440)
        updatedAt = .now
    }
}
