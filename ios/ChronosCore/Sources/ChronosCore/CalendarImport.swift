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

// MARK: - Calendar import constants (mirror CalendarImportSemantics.kt)

/// Category string stamped on timed imported blocks. Mirrors `CALENDAR_EVENT_CATEGORY`.
public let calendarEventCategory = "CALENDAR"
/// Category string stamped on the 15-minute all-day context markers. Mirrors
/// `ALL_DAY_CALENDAR_EVENT_CATEGORY`.
public let allDayCalendarEventCategory = "CALENDAR_ALL_DAY"
/// Length (minutes) of the all-day context marker. Mirrors `ALL_DAY_CALENDAR_MARKER_MINUTES`.
public let allDayCalendarMarkerMinutes = 15
/// Provenance string used for every block imported from the device calendar. Compared
/// case-insensitively elsewhere (see `DialRings`/`RingBlockInput`).
public let calendarImportedProvenance = "calendar"

// MARK: - Instance-unique IDs (mirror CalendarEventRepositoryImpl.kt:243 / :291)

/// `yyyy-MM-dd` for the date component of an instance id, formatted in `calendar`'s zone so the id
/// keys on the *local* day the segment falls on (matches Android's `LocalDate.toString()`).
private func isoDayString(_ date: Date, calendar: Calendar) -> String {
    let c = calendar.dateComponents([.year, .month, .day], from: date)
    return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
}

/// Instance-unique id for a *timed* imported event segment:
/// `calendar-import-<eventId>-<yyyy-MM-dd>-<startMinute>`.
///
/// The device EVENT_ID is shared by every instance of a recurring event, so the local date plus
/// the start-minute-of-day is what keeps same-day recurring instances (and per-day midnight splits)
/// from colliding. Faithful port of `CalendarEventRepositoryImpl.kt` line 243.
public func instanceUniqueId(eventId: String, date: Date, startMinute: Int, calendar: Calendar = .current) -> String {
    "calendar-import-\(eventId)-\(isoDayString(date, calendar: calendar))-\(startMinute)"
}

/// Instance-unique id for an *all-day* imported event marker:
/// `calendar-import-<eventId>-<yyyy-MM-dd>` (no start-minute suffix, since all-day markers always
/// sit at minute 0). Faithful port of `CalendarEventRepositoryImpl.kt` line 291.
public func allDayInstanceUniqueId(eventId: String, date: Date, calendar: Calendar = .current) -> String {
    "calendar-import-\(eventId)-\(isoDayString(date, calendar: calendar))"
}

// MARK: - Provenance guard (mirror DayDialBlockDelegate guards)

/// True when a block originated from the device calendar. Editing or deleting such a block must
/// NEVER write back to the user's original device event (its link points at the user's event, not
/// an export ChronosFlow owns). Callers gate sync-back on `!isImportedCalendarBlock(...)`.
/// Comparison is case-insensitive to match `DialRings`' string handling.
public func isImportedCalendarBlock(provenance: String) -> Bool {
    provenance.lowercased() == calendarImportedProvenance
}

// MARK: - Imported block value type

/// A block produced from a device-calendar event, reduced to the calendar-relevant fields. Pure
/// value type so the import math is fully testable off-device; the app maps this onto its SwiftData
/// `TimeBlock`. Field semantics mirror the `TimeBlock(...)` constructed in
/// `CalendarEventRepositoryImpl.kt` (timed: lines 240-265, all-day: lines 290-313).
public struct ImportedCalendarBlock: Sendable, Equatable {
    public let id: String
    public let eventID: String
    public let date: Date                 // start-of-(local)-day this segment belongs to
    public let title: String
    public let category: String
    public let startMinute: Int
    public let durationMinutes: Int
    public let timezone: String
    public let provenance: String         // always `calendarImportedProvenance`
    public let flexibility: BlockFlexibility
    public let energy: EnergyIntensity
    public let isAllDay: Bool
    public let isLocked: Bool
    public let isProtected: Bool

    public init(
        id: String, eventID: String, date: Date, title: String, category: String,
        startMinute: Int, durationMinutes: Int, timezone: String,
        provenance: String, flexibility: BlockFlexibility, energy: EnergyIntensity,
        isAllDay: Bool, isLocked: Bool, isProtected: Bool
    ) {
        self.id = id
        self.eventID = eventID
        self.date = date
        self.title = title
        self.category = category
        self.startMinute = startMinute
        self.durationMinutes = durationMinutes
        self.timezone = timezone
        self.provenance = provenance
        self.flexibility = flexibility
        self.energy = energy
        self.isAllDay = isAllDay
        self.isLocked = isLocked
        self.isProtected = isProtected
    }
}

/// True for the 15-minute all-day context markers (vs real timed commitments). Mirrors
/// `TimeBlock.isAllDayCalendarImport()` in `CalendarImportSemantics.kt`: an imported block that is
/// either explicitly the all-day category or runs the full day (>= 1440 min).
public func isAllDayCalendarImport(_ block: ImportedCalendarBlock) -> Bool {
    isImportedCalendarBlock(provenance: block.provenance)
        && (block.category.caseInsensitiveCompare(allDayCalendarEventCategory) == .orderedSame
            || block.durationMinutes >= 1440)
}

/// Whether the block actually occupies schedule time (timed commitment) rather than being a
/// context-only marker. Mirrors `TimeBlock.occupiesScheduleTime()`.
public func occupiesScheduleTime(_ block: ImportedCalendarBlock) -> Bool {
    !isAllDayCalendarImport(block)
}

// MARK: - Energy classification (mirror CalendarImportClassifier.kt)

private let highEnergyKeywords = [
    "meeting", "interview", "presentation", "review", "1:1", "one-on-one",
    "standup", "stand-up", "sync", "demo", "planning", "workshop", "exam",
    "deadline", "negotiation", "pitch", "onsite", "on-site"
]
private let lowEnergyKeywords = [
    "lunch", "dinner", "breakfast", "brunch", "coffee", "break", "social",
    "party", "birthday", "holiday", "vacation", "travel", "flight", "commute",
    "walk", "errand", "appointment reminder", "out of office", "ooo"
]

/// Heuristic energy for an imported timed event from its title (+ optional description). Low-energy
/// keywords win over high-energy ones (Android checks LOW first). Faithful port of
/// `classifyImportedEventEnergy`.
public func classifyImportedEventEnergy(title: String, description: String? = nil) -> EnergyIntensity {
    var haystack = title.lowercased()
    if let description { haystack += " " + description.lowercased() }
    if lowEnergyKeywords.contains(where: { haystack.contains($0) }) { return .low }
    if highEnergyKeywords.contains(where: { haystack.contains($0) }) { return .high }
    return .moderate
}

// MARK: - Timed-event expansion (midnight split + recurrence)

/// A single device-calendar event occurrence with absolute start/end instants. Recurring events are
/// expanded into one `CalendarOccurrence` per instance *before* import math runs (EventKit exposes
/// no expanded-instances API, so the caller — or `expandOccurrences` below — enumerates them).
public struct CalendarOccurrence: Sendable, Equatable {
    public let eventID: String
    public let title: String
    public let description: String?
    public let start: Date
    public let end: Date
    public let timezone: String
    public let isAllDay: Bool

    public init(eventID: String, title: String, description: String? = nil,
                start: Date, end: Date, timezone: String, isAllDay: Bool = false) {
        self.eventID = eventID
        self.title = title
        self.description = description
        self.start = start
        self.end = end
        self.timezone = timezone
        self.isAllDay = isAllDay
    }
}

/// Expand one recurring (or single) device event into concrete `CalendarOccurrence`s that overlap
/// `[windowStart, windowEnd)`. The first occurrence starts at `firstStart`; subsequent ones repeat
/// per `rule`, each preserving the same wall-clock duration. Pure enumeration via `Calendar`
/// (matches the iOS approach note: no built-in expanded-instances API).
///
/// Passing `rule == nil` yields the single occurrence (clipped to the window check).
public func expandOccurrences(
    eventID: String,
    title: String,
    description: String? = nil,
    firstStart: Date,
    durationMinutes: Int,
    timezone: String,
    isAllDay: Bool = false,
    rule: RecurrenceRule?,
    windowStart: Date,
    windowEnd: Date,
    calendar: Calendar = .current
) -> [CalendarOccurrence] {
    guard windowStart < windowEnd, durationMinutes > 0 else { return [] }
    let duration = TimeInterval(durationMinutes * 60)

    func occurrence(at start: Date) -> CalendarOccurrence {
        CalendarOccurrence(eventID: eventID, title: title, description: description,
                           start: start, end: start.addingTimeInterval(duration),
                           timezone: timezone, isAllDay: isAllDay)
    }
    func overlapsWindow(_ start: Date) -> Bool {
        let end = start.addingTimeInterval(duration)
        return start < windowEnd && end > windowStart
    }

    guard let rule else {
        return overlapsWindow(firstStart) ? [occurrence(at: firstStart)] : []
    }

    // Reuse the date-recurrence engine to find which local *days* the series fires on, then
    // re-attach the original wall-clock time-of-day so durations/start times stay stable.
    let timeOfDay = calendar.dateComponents([.hour, .minute, .second], from: firstStart)
    let days = expandRecurrence(rule, from: firstStart, to: windowEnd, calendar: calendar)
    var result: [CalendarOccurrence] = []
    for day in days {
        guard let start = calendar.date(
            bySettingHour: timeOfDay.hour ?? 0,
            minute: timeOfDay.minute ?? 0,
            second: timeOfDay.second ?? 0,
            of: day
        ) else { continue }
        if start < firstStart { continue }          // never emit before the series anchor
        if overlapsWindow(start) { result.append(occurrence(at: start)) }
    }
    return result
}

/// Split a timed occurrence into one `ImportedCalendarBlock` per local day it spans, clipped to the
/// sync window. Cross-midnight events become a segment per day (matching the dial's per-day face).
/// Faithful port of `toImportedTimeBlocks` (`CalendarEventRepositoryImpl.kt` lines 202-267).
public func splitTimedOccurrence(
    _ occurrence: CalendarOccurrence,
    windowStart: Date,
    windowEnd: Date,
    calendar: Calendar = .current
) -> [ImportedCalendarBlock] {
    let clippedStart = max(occurrence.start, windowStart)
    let clippedEnd = min(occurrence.end, windowEnd)
    guard clippedStart < clippedEnd else { return [] }

    var blocks: [ImportedCalendarBlock] = []
    var dayStart = calendar.startOfDay(for: clippedStart)
    while dayStart < clippedEnd {
        guard let nextDay = calendar.date(byAdding: .day, value: 1, to: dayStart) else { break }
        let segmentStart = max(clippedStart, dayStart)
        let segmentEnd = min(clippedEnd, nextDay)
        if segmentStart < segmentEnd {
            let comps = calendar.dateComponents([.hour, .minute], from: segmentStart)
            let startMinute = (comps.hour ?? 0) * 60 + (comps.minute ?? 0)
            let rawMinutes = Int(segmentEnd.timeIntervalSince(segmentStart) / 60.0)
            let durationMinutes = min(max(rawMinutes, 1), 1440)
            let day = dayStart
            blocks.append(ImportedCalendarBlock(
                id: instanceUniqueId(eventId: occurrence.eventID, date: day,
                                     startMinute: startMinute, calendar: calendar),
                eventID: occurrence.eventID,
                date: day,
                title: occurrence.title,
                category: calendarEventCategory,
                startMinute: startMinute,
                durationMinutes: durationMinutes,
                timezone: occurrence.timezone,
                provenance: calendarImportedProvenance,
                flexibility: .fixed,
                energy: classifyImportedEventEnergy(title: occurrence.title, description: occurrence.description),
                isAllDay: false,
                isLocked: true,
                isProtected: true
            ))
        }
        dayStart = nextDay
    }
    return blocks
}

// MARK: - All-day markers

/// Build the inclusive last *local* date an all-day event covers. CalendarContract/EventKit model
/// all-day end as the *exclusive* next-midnight, so the inclusive last day is one day before the
/// end; never earlier than the start. Faithful port of `allDayEventLastDate`.
public func allDayEventLastDate(start: Date, exclusiveEnd: Date, calendar: Calendar = .current) -> Date {
    let startDay = calendar.startOfDay(for: start)
    let exclusiveEndDay = calendar.startOfDay(for: exclusiveEnd)
    let lastDay = calendar.date(byAdding: .day, value: -1, to: exclusiveEndDay) ?? startDay
    return lastDay < startDay ? startDay : lastDay
}

/// Turn an all-day occurrence into 15-minute OPTIONAL/LOW context markers — one per local day in
/// the event's inclusive range intersected with the sync window. These surface "vacation"/"holiday"
/// context on the dial without blocking schedule time. Faithful port of
/// `toImportedAllDayTimeBlocks` (`CalendarEventRepositoryImpl.kt` lines 269-315).
public func allDayContextMarkers(
    _ occurrence: CalendarOccurrence,
    windowStart: Date,
    windowEnd: Date,
    calendar: Calendar = .current
) -> [ImportedCalendarBlock] {
    let syncStartDate = calendar.startOfDay(for: windowStart)
    // windowEnd is exclusive; its last covered day is the day before it.
    let syncEndDate = allDayEventLastDate(start: windowStart, exclusiveEnd: windowEnd, calendar: calendar)
    let eventStartDate = calendar.startOfDay(for: occurrence.start)
    let eventLastDate = allDayEventLastDate(start: occurrence.start, exclusiveEnd: occurrence.end, calendar: calendar)

    let firstImportDate = max(syncStartDate, eventStartDate)
    let lastImportDate = min(syncEndDate, eventLastDate)
    guard firstImportDate <= lastImportDate else { return [] }

    var markers: [ImportedCalendarBlock] = []
    var day = firstImportDate
    while day <= lastImportDate {
        markers.append(ImportedCalendarBlock(
            id: allDayInstanceUniqueId(eventId: occurrence.eventID, date: day, calendar: calendar),
            eventID: occurrence.eventID,
            date: day,
            title: occurrence.title,
            category: allDayCalendarEventCategory,
            startMinute: 0,
            durationMinutes: allDayCalendarMarkerMinutes,
            timezone: occurrence.timezone,
            provenance: calendarImportedProvenance,
            flexibility: .optional,
            energy: .low,
            isAllDay: true,
            isLocked: false,
            isProtected: false
        ))
        guard let next = calendar.date(byAdding: .day, value: 1, to: day) else { break }
        day = next
    }
    return markers
}

/// One entry point that routes an occurrence to either timed midnight-split blocks or all-day
/// context markers. Mirrors `toImportedTimeBlocks`' top-level `if (isAllDay)` branch.
public func importedBlocks(
    for occurrence: CalendarOccurrence,
    windowStart: Date,
    windowEnd: Date,
    calendar: Calendar = .current
) -> [ImportedCalendarBlock] {
    occurrence.isAllDay
        ? allDayContextMarkers(occurrence, windowStart: windowStart, windowEnd: windowEnd, calendar: calendar)
        : splitTimedOccurrence(occurrence, windowStart: windowStart, windowEnd: windowEnd, calendar: calendar)
}

// MARK: - Sync window (mirror CalendarBackgroundSyncWorker.syncWindow)

/// Number of days ahead the background/foreground sync refreshes. Mirrors `SYNC_WINDOW_DAYS`.
public let calendarSyncWindowDays = 7
/// Hours between background refreshes. Mirrors `SYNC_INTERVAL_HOURS`.
public let calendarSyncIntervalHours = 6

/// Rolling refresh window: from the start of `now`'s local day through `calendarSyncWindowDays`
/// days ahead (exclusive end). Faithful port of `CalendarBackgroundSyncWorker.syncWindow`.
public func syncWindow(now: Date, calendar: Calendar = .current) -> (start: Date, end: Date) {
    let start = calendar.startOfDay(for: now)
    let end = calendar.date(byAdding: .day, value: calendarSyncWindowDays, to: start) ?? start
    return (start, end)
}

// MARK: - Last-synced label (mirror RelativeTimeFormat.formatLastSyncedLabel)

/// Compact "Synced X ago" hint for the last successful calendar sync. Clock-injected (`now` is
/// passed) so it is pure and re-derivable on every render. Faithful port of `formatLastSyncedLabel`
/// in `RelativeTimeFormat.kt`.
public func formatLastSyncedLabel(lastSyncAt: Date?, now: Date) -> String {
    guard let lastSyncAt else { return "Not synced yet" }
    let elapsed = now.timeIntervalSince(lastSyncAt)
    if elapsed < 0 { return "Synced just now" }   // guard against clock skew

    let minutes = Int(elapsed / 60.0)
    if minutes < 1 { return "Synced just now" }
    if minutes < 60 { return "Synced \(minutes) min ago" }

    let hours = Int(elapsed / 3600.0)
    if hours < 24 { return "Synced \(hours) hr ago" }

    let days = Int(elapsed / 86400.0)
    return days == 1 ? "Synced yesterday" : "Synced \(days) days ago"
}
