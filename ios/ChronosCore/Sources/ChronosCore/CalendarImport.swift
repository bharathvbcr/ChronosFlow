import Foundation

// Calendar import — port of CalendarImportClassifier / CalendarImportSemantics and the provenance
// guards. Classifies external calendar events and reconciles them against already-imported blocks so
// re-importing is idempotent: new events are added, vanished events are removed, unchanged ones stay.

public struct ExternalEvent: Sendable, Equatable {
    public let externalID: String
    public let title: String
    public let startMinute: Int
    public let durationMinutes: Int
    public let isAllDay: Bool
    public let isBusy: Bool          // availability == busy
    public init(externalID: String, title: String, startMinute: Int, durationMinutes: Int,
                isAllDay: Bool = false, isBusy: Bool = true) {
        self.externalID = externalID
        self.title = title
        self.startMinute = startMinute
        self.durationMinutes = durationMinutes
        self.isAllDay = isAllDay
        self.isBusy = isBusy
    }
}

public enum ImportClassification: Sendable, Equatable {
    case fixedBlock        // a real timed commitment → becomes a fixed block on the dial
    case skipped(reason: String)
}

/// Decide whether an event should become a dial block. All-day and free-availability events are
/// context, not commitments, so they're skipped (matches CalendarImportSemantics).
public func classify(_ event: ExternalEvent) -> ImportClassification {
    if event.isAllDay { return .skipped(reason: "all-day") }
    if !event.isBusy { return .skipped(reason: "free availability") }
    if event.durationMinutes <= 0 { return .skipped(reason: "zero duration") }
    return .fixedBlock
}

public struct ImportPlan: Sendable, Equatable {
    public let toAdd: [ExternalEvent]        // events not yet represented as blocks
    public let staleExternalIDs: [String]    // previously-imported ids whose events are gone
}

/// Reconcile fetched events against the externalIDs of blocks already imported from the calendar.
/// Returns what to add and what to remove so the import is idempotent and self-cleaning.
public func planCalendarImport(
    fetched: [ExternalEvent], existingExternalIDs: Set<String>
) -> ImportPlan {
    let importable = fetched.filter { if case .fixedBlock = classify($0) { return true } else { return false } }
    let fetchedIDs = Set(importable.map(\.externalID))
    let toAdd = importable.filter { !existingExternalIDs.contains($0.externalID) }
    let stale = existingExternalIDs.subtracting(fetchedIDs)
    return ImportPlan(toAdd: toAdd, staleExternalIDs: Array(stale).sorted())
}
