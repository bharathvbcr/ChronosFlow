import Foundation
import SwiftData
import ChronosCore

/// SwiftData snapshot of an in-flight SPLIT focus session, so a session survives an app kill (F03).
/// The iOS analogue of the Android `FocusSplitSessionStore` (SharedPreferences-backed). Persisted
/// only while a split session is RUNNING/PAUSED and cleared otherwise. All stored properties are
/// optional or default-initialized (CloudKit-safe); the phase list is stored as a Codable JSON blob.
///
/// Lives in `Models/` (not `Features/Focus/`) so it's compiled into BOTH the app and the widget
/// extension targets — `ChronosStore.schema` references it, and that schema is shared with the
/// WidgetKit extension.
@Model
final class FocusSessionSnapshot {
    /// Coarse lifecycle status of the snapshot. Only RUNNING/PAUSED snapshots are resumed.
    enum Status: String, Codable, Sendable {
        case running
        case paused
        case completed
    }

    var blockID: String?
    var blockTitle: String?
    /// `FocusSplitPreset.rawValue`; resolved via `presetValue`.
    var presetRaw: String?
    var plannedBlockMinutes: Int = 25
    /// The planned phase sequence, JSON-encoded `[FocusPhase]`; read via `phaseList`.
    var phasesData: Data?
    var currentIndex: Int = 0
    /// `Status.rawValue`; read via `statusValue`.
    var statusRaw: String?
    var awaitingAdvance: Bool = false
    /// Seconds left in the current phase at the moment of the last save (for PAUSED/RUNNING resume).
    var remainingSeconds: Int = 0
    var completedWorkSessions: Int = 0
    var interruptions: Int = 0
    var startedAt: Date?
    var updatedAt: Date = Date.now

    init() {}

    /// Decoded phase plan (empty if missing/corrupt).
    var phaseList: [FocusPhase] {
        guard let phasesData,
              let decoded = try? JSONDecoder().decode([FocusPhase].self, from: phasesData) else { return [] }
        return decoded
    }

    /// Store the phase plan as a JSON blob.
    func encodePhases(_ phases: [FocusPhase]) {
        phasesData = try? JSONEncoder().encode(phases)
    }

    /// Resolved status (defaults to `.running` for legacy/missing values).
    var statusValue: Status { statusRaw.flatMap(Status.init(rawValue:)) ?? .running }

    /// Resolved preset (defaults to the 25·5 default to match `FocusTimerModel.preset`).
    var presetValue: FocusSplitPreset { presetRaw.flatMap(FocusSplitPreset.init(rawValue:)) ?? .p25_5 }

    /// Newest-first fetch descriptor (the live session, if any).
    static var fetchDescriptor: FetchDescriptor<FocusSessionSnapshot> {
        FetchDescriptor<FocusSessionSnapshot>(sortBy: [SortDescriptor(\.updatedAt, order: .reverse)])
    }
}
