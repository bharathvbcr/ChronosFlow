import Foundation
import EventKit
import ChronosCore

/// A calendar event reduced to dial coordinates, drawn as a ghost arc the plan can be checked against.
/// `isAllDay` flags the 15-minute context markers (vacation/holiday/OOO) that surface as faint dots on
/// the dial rather than schedule commitments — mirroring Android's all-day calendar markers.
struct CalendarOverlayEvent: Identifiable, Equatable {
    let id: String
    let title: String
    let startMinute: Int
    let durationMinutes: Int
    let isAllDay: Bool

    init(id: String, title: String, startMinute: Int, durationMinutes: Int, isAllDay: Bool = false) {
        self.id = id
        self.title = title
        self.startMinute = startMinute
        self.durationMinutes = durationMinutes
        self.isAllDay = isAllDay
    }
}

/// Reads the system calendar via EventKit — the iOS-native equivalent of the Android calendar import.
/// Read-only overlay: events are shown on the dial as fixed commitments, never silently written as
/// blocks (the user imports them deliberately).
///
/// All the classification, recurrence-expansion, midnight-splitting, instance-unique-id and all-day
/// marker math is owned by ChronosCore (`expandOccurrences` / `importedBlocks` / `classify`), so the
/// overlay stays a thin EventKit adapter and shares semantics with the import path. EKEvent
/// `availability` is mapped to ChronosCore's `isBusy` so free/tentative events are treated as context.
@MainActor
final class CalendarOverlayProvider {
    private let store = EKEventStore()

    func requestAccess() async -> Bool {
        do { return try await store.requestFullAccessToEvents() }
        catch { return false }
    }

    /// Timed busy events for `date` as overlay arcs. Free-availability events are skipped (context,
    /// not commitments). Recurring events and midnight-crossing events are expanded/split via
    /// ChronosCore so each instance/segment gets its own arc with a stable instance-unique id.
    /// All-day events are excluded here — use `allDayContext(on:)` for those.
    func events(on date: Date) async -> [CalendarOverlayEvent] {
        let granted = await requestAccess()
        guard granted else { return [] }

        let cal = Calendar.current
        let dayStart = cal.startOfDay(for: date)
        guard let dayEnd = cal.date(byAdding: .day, value: 1, to: dayStart) else { return [] }
        let predicate = store.predicateForEvents(withStart: dayStart, end: dayEnd, calendars: nil)

        return store.events(matching: predicate).flatMap { event -> [CalendarOverlayEvent] in
            guard !event.isAllDay else { return [] }
            // Map EKEvent availability → ChronosCore busy semantics: only "busy" is a commitment.
            let isBusy = event.availability == .busy || event.availability == .notSupported
            guard let start = event.startDate, let end = event.endDate else { return [] }

            // EventKit already returns one EKEvent per recurring instance from a date-range query, so
            // no rule expansion is needed; we feed the single occurrence through ChronosCore's split.
            let occurrence = CalendarOccurrence(
                eventID: event.eventIdentifier ?? UUID().uuidString,
                title: event.title ?? "Event",
                start: start,
                end: end,
                timezone: (event.timeZone ?? cal.timeZone).identifier,
                isAllDay: false
            )
            // classify() guards busy/zero-duration; skip non-busy before splitting.
            let external = ExternalEvent(
                externalID: occurrence.eventID, title: occurrence.title,
                startMinute: 0, durationMinutes: max(Int(end.timeIntervalSince(start) / 60), 0),
                isAllDay: false, isBusy: isBusy
            )
            if case .skipped = classify(external) { return [] }

            return importedBlocks(for: occurrence, windowStart: dayStart, windowEnd: dayEnd, calendar: cal)
                .filter { !$0.isAllDay }
                .map {
                    CalendarOverlayEvent(
                        id: $0.id, title: $0.title,
                        startMinute: $0.startMinute, durationMinutes: $0.durationMinutes,
                        isAllDay: false
                    )
                }
        }
    }

    /// All-day events for `date` as faint 15-minute context markers (matching Android's all-day
    /// calendar markers). Surfaces vacation/holiday/OOO context without blocking schedule time.
    func allDayContext(on date: Date) async -> [CalendarOverlayEvent] {
        let granted = await requestAccess()
        guard granted else { return [] }

        let cal = Calendar.current
        let dayStart = cal.startOfDay(for: date)
        guard let dayEnd = cal.date(byAdding: .day, value: 1, to: dayStart) else { return [] }
        let predicate = store.predicateForEvents(withStart: dayStart, end: dayEnd, calendars: nil)

        return store.events(matching: predicate).flatMap { event -> [CalendarOverlayEvent] in
            guard event.isAllDay, let start = event.startDate, let end = event.endDate else { return [] }
            let occurrence = CalendarOccurrence(
                eventID: event.eventIdentifier ?? UUID().uuidString,
                title: event.title ?? "Event",
                start: start,
                end: end,
                timezone: (event.timeZone ?? cal.timeZone).identifier,
                isAllDay: true
            )
            return importedBlocks(for: occurrence, windowStart: dayStart, windowEnd: dayEnd, calendar: cal)
                .map {
                    CalendarOverlayEvent(
                        id: $0.id, title: $0.title,
                        startMinute: $0.startMinute, durationMinutes: $0.durationMinutes,
                        isAllDay: true
                    )
                }
        }
    }
}
