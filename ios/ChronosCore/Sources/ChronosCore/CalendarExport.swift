import Foundation

// Calendar export — a *pure contract* describing how a ChronosFlow block maps to a device-calendar
// event, and what intent (save / update / delete) a given edit should produce. EventKit is wired in
// the app layer (EKEventStore.save/remove); ChronosCore owns only the deterministic decision and
// the field mapping. This mirrors the Android export path:
//   - CalendarEventRepositoryImpl.exportTimeBlock / updateExportedTimeBlock / deleteExportedTimeBlock
//   - DeviceCalendarPlatform.buildEventValues (block -> ContentValues field mapping)
//   - DayDialBlockDelegate.syncLinkedCalendarExport / deleteExportedCalendarEventIfNeeded (the
//     provenance guard: imported blocks NEVER write back).
//
// Pure Foundation; fully testable off-device.

// MARK: - Input

/// The block fields the export contract reasons about, reduced to plain values. `eventID` is the
/// device-calendar event identifier this block was exported to (nil if never exported). `provenance`
/// is compared case-insensitively (mirrors the rest of the calendar code).
public struct ExportableBlock: Sendable, Equatable {
    public let title: String
    public let date: Date                 // start-of-day the block lives on
    public let startMinute: Int
    public let durationMinutes: Int
    public let timezone: String           // IANA id; falls back to UTC if unparseable
    public let provenance: String
    public let eventID: String?           // device event id, if already exported

    public init(
        title: String, date: Date, startMinute: Int, durationMinutes: Int,
        timezone: String, provenance: String, eventID: String? = nil
    ) {
        self.title = title
        self.date = date
        self.startMinute = startMinute
        self.durationMinutes = durationMinutes
        self.timezone = timezone
        self.provenance = provenance
        self.eventID = eventID
    }
}

// MARK: - Event field mapping

/// The fields the app should write onto an `EKEvent` when saving/updating. Mirrors the
/// `ContentValues` Android builds in `DeviceCalendarPlatform.buildEventValues`: title, an
/// "Exported from ChronosFlow" note, absolute start/end, the resolved timezone id, never all-day.
public struct CalendarEventFields: Sendable, Equatable {
    public let title: String
    public let notes: String
    public let start: Date
    public let end: Date
    public let timezoneID: String
    public let isAllDay: Bool

    public init(title: String, notes: String, start: Date, end: Date,
                timezoneID: String, isAllDay: Bool) {
        self.title = title
        self.notes = notes
        self.start = start
        self.end = end
        self.timezoneID = timezoneID
        self.isAllDay = isAllDay
    }
}

/// The note ChronosFlow stamps on every exported event (so users can tell where it came from).
/// Matches Android's `"Exported from ChronosFlow"`.
public let calendarExportNote = "Exported from ChronosFlow"

/// Resolve a block's IANA timezone, falling back to UTC when the id is unparseable. The app passes
/// this id to `EKEvent.timeZone`; UTC is the deterministic fallback for the portable core (the app
/// may substitute the device's current zone, matching Android's `ZoneId.systemDefault()` default).
public func resolvedTimeZone(_ id: String) -> TimeZone {
    TimeZone(identifier: id) ?? TimeZone(identifier: "UTC")!
}

/// The timezone *identifier string* the app stamps on the exported event: the requested id when it is
/// a parseable IANA zone, else `"UTC"`. This is deterministic across platforms — unlike
/// `resolvedTimeZone(_:).identifier`, which corelibs-Foundation (Linux/Windows) normalizes the UTC
/// fallback to `"GMT"`. The instant math is unaffected (UTC and GMT share the same offset).
public func resolvedTimeZoneID(_ id: String) -> String {
    TimeZone(identifier: id) != nil ? id : "UTC"
}

/// Map a block to the concrete calendar-event fields. `start` is the block's local start instant;
/// `end` is `start + duration`. Faithful port of `buildEventValues`.
public func calendarEventFields(for block: ExportableBlock) -> CalendarEventFields {
    let zone = resolvedTimeZone(block.timezone)
    var cal = Calendar(identifier: .gregorian)
    cal.timeZone = zone
    let dayStart = cal.startOfDay(for: block.date)
    let start = dayStart.addingTimeInterval(TimeInterval(block.startMinute * 60))
    let end = start.addingTimeInterval(TimeInterval(block.durationMinutes * 60))
    return CalendarEventFields(
        title: block.title,
        notes: calendarExportNote,
        start: start,
        end: end,
        timezoneID: resolvedTimeZoneID(block.timezone),
        isAllDay: false
    )
}

// MARK: - Export intent

/// The action the app should take on the device calendar for a given block edit. The app turns
/// these into EventKit calls; ChronosCore decides which one applies (and refuses to touch imported
/// events). `fields` carries the mapped event payload for save/update.
public enum CalendarExportIntent: Sendable, Equatable {
    /// Insert a brand-new event (block was never exported). App stores the returned eventID.
    case save(fields: CalendarEventFields)
    /// Update the existing event identified by `eventID` to match the block.
    case update(eventID: String, fields: CalendarEventFields)
    /// Remove the existing event identified by `eventID` (the block was deleted/un-exported).
    case delete(eventID: String)
    /// Do nothing: the block is an imported calendar event (writing back would corrupt the user's
    /// original event) or there is nothing to remove. Carries a reason for diagnostics/UI.
    case noOp(reason: NoOpReason)

    public enum NoOpReason: String, Sendable, Equatable {
        /// Block provenance is `calendar` — it links the user's event, not an export we own.
        case importedBlock
        /// A delete was requested but the block was never exported (no eventID to remove).
        case neverExported
    }
}

/// Intent for *exporting or syncing* a block to the device calendar (the user pressed
/// "Export to Calendar", or the block moved/resized/retitled). Mirrors
/// `exportTimeBlock` + `updateExportedTimeBlock` routed through the
/// `DayDialBlockDelegate.syncLinkedCalendarExport` provenance guard:
///   - imported (provenance == calendar) blocks → `.noOp(.importedBlock)` (NEVER write back);
///   - already-exported blocks (have an eventID) → `.update`;
///   - otherwise → `.save`.
public func exportIntent(for block: ExportableBlock) -> CalendarExportIntent {
    if isImportedCalendarBlock(provenance: block.provenance) {
        return .noOp(reason: .importedBlock)
    }
    let fields = calendarEventFields(for: block)
    if let eventID = block.eventID {
        return .update(eventID: eventID, fields: fields)
    }
    return .save(fields: fields)
}

/// Intent for *deleting* a block's exported event when the block itself is removed. Mirrors
/// `DayDialBlockDelegate.deleteExportedCalendarEventIfNeeded` + `deleteExportedTimeBlock`:
///   - imported blocks → `.noOp(.importedBlock)` (the linked event is the user's own);
///   - exported blocks → `.delete`;
///   - never-exported blocks → `.noOp(.neverExported)`.
public func deleteIntent(for block: ExportableBlock) -> CalendarExportIntent {
    if isImportedCalendarBlock(provenance: block.provenance) {
        return .noOp(reason: .importedBlock)
    }
    guard let eventID = block.eventID else {
        return .noOp(reason: .neverExported)
    }
    return .delete(eventID: eventID)
}
