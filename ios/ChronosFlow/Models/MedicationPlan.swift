import Foundation
import SwiftData

/// Status of a single recorded dose. Mirrors the relevant subset of Android's
/// `MedicationDoseEventType` (TAKEN / MISSED / SKIPPED / SNOOZED) — the
/// SCHEDULED/PAUSED/RESUMED lifecycle markers are not persisted as dose history on iOS.
enum DoseStatus: String, Codable, CaseIterable, Sendable {
    case taken
    case missed
    case skipped
    case snoozed
}

/// One recorded dose-history entry for a `MedicationPlan`. Ported from Android's
/// `MedicationDoseEvent` (core/domain `MedicationPlannerModels.kt`). Acts as the iOS store of
/// record for adherence: the old single `takenAt: [Date]` array is migrated into `taken` events.
/// Adherence math itself lives in ChronosCore (`adherenceStats`) — this only stores.
@Model
final class DoseEvent {
    @Attribute(.unique) var id: String
    /// When this dose outcome was recorded / occurred.
    var date: Date
    /// Stored as a raw string for SwiftData/CloudKit; read via `status`.
    var statusRaw: String
    /// Minute-of-day the dose was scheduled for, when known (mirrors `scheduledMinuteOfDay`).
    var scheduledMinuteOfDay: Int?
    /// Optional free-text reason (e.g. why skipped) — mirrors Android `reason`.
    var reason: String?
    /// Dose amount captured at record time, when it differs from the plan — mirrors `doseAmount`.
    var doseAmount: String?

    /// Owning plan. Optional + inverse for CloudKit compatibility.
    @Relationship(inverse: \MedicationPlan.doseEvents) var plan: MedicationPlan?

    var status: DoseStatus {
        get { DoseStatus(rawValue: statusRaw) ?? .taken }
        set { statusRaw = newValue.rawValue }
    }

    init(
        id: String = UUID().uuidString,
        date: Date = .now,
        status: DoseStatus = .taken,
        scheduledMinuteOfDay: Int? = nil,
        reason: String? = nil,
        doseAmount: String? = nil,
        plan: MedicationPlan? = nil
    ) {
        self.id = id
        self.date = date
        self.statusRaw = status.rawValue
        self.scheduledMinuteOfDay = scheduledMinuteOfDay
        self.reason = reason
        self.doseAmount = doseAmount
        self.plan = plan
    }
}

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

    // MARK: Pause / resume (mirrors MedicationSchedule.pausedUntil)
    /// When set and in the future, the plan is paused (reminders suppressed) until this date.
    var pausedUntil: Date?

    // MARK: Safety profile (mirrors MedicationSafetyProfile)
    /// Soft cap on doses recorded per day; informational guardrail. Nil when untracked.
    var maxDosesPerDay: Int?
    /// Free-text safety notes / cautions / instructions.
    var safetyNotes: String?

    // MARK: Dose history
    /// Recorded dose events (taken/missed/skipped/snoozed). Replaces the legacy `takenAt` array.
    @Relationship(deleteRule: .cascade) var doseEvents: [DoseEvent]?

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
        pausedUntil: Date? = nil,
        maxDosesPerDay: Int? = nil,
        safetyNotes: String? = nil,
        doseEvents: [DoseEvent]? = nil
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
        self.pausedUntil = pausedUntil
        self.maxDosesPerDay = maxDosesPerDay
        self.safetyNotes = safetyNotes
        self.doseEvents = doseEvents
    }

    /// Whether reminders are currently suppressed because the plan is paused.
    func isPaused(on date: Date = .now) -> Bool {
        guard let until = pausedUntil else { return false }
        return until > date
    }

    /// Timestamps of acknowledged ("taken") doses, oldest-to-newest. Convenience over `doseEvents`
    /// preserving the old `takenAt` read shape for adherence/refill consumers.
    var takenAt: [Date] {
        (doseEvents ?? [])
            .filter { $0.status == .taken }
            .map(\.date)
            .sorted()
    }

    /// Record an acknowledged dose and decrement remaining supply. Migrates the legacy
    /// `acknowledgeDose` behaviour onto the `DoseEvent` history.
    func acknowledgeDose(at date: Date = .now) {
        let event = DoseEvent(date: date, status: .taken, plan: self)
        if doseEvents == nil { doseEvents = [event] } else { doseEvents?.append(event) }
        if let remaining = remainingDoses { remainingDoses = max(remaining - 1, 0) }
    }

    /// Record a non-taken dose outcome (missed/skipped/snoozed) without touching supply.
    func recordDose(_ status: DoseStatus, at date: Date = .now, reason: String? = nil) {
        let event = DoseEvent(date: date, status: status, reason: reason, plan: self)
        if doseEvents == nil { doseEvents = [event] } else { doseEvents?.append(event) }
    }

    /// Days until refill is needed, given remaining supply and doses per day. Mirrors
    /// ChronosCore.daysUntilRefill. Nil when supply isn't tracked.
    var daysUntilRefill: Int? {
        guard let remaining = remainingDoses else { return nil }
        let perDay = max(reminderMinutes.count, 1)
        return remaining / perDay
    }

    func isTaken(on day: Date) -> Bool {
        (doseEvents ?? []).contains {
            $0.status == .taken && Calendar.current.isDate($0.date, inSameDayAs: day)
        }
    }
}
