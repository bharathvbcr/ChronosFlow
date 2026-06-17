import Foundation
import EventKit

/// A calendar event reduced to dial coordinates, drawn as a ghost arc the plan can be checked against.
struct CalendarOverlayEvent: Identifiable, Equatable {
    let id: String
    let title: String
    let startMinute: Int
    let durationMinutes: Int
}

/// Reads the system calendar via EventKit — the iOS-native equivalent of the Android calendar import.
/// Read-only overlay: events are shown on the dial as fixed commitments, never silently written as
/// blocks (the user imports them deliberately). Mirrors the classify/skip rules in ChronosCore's
/// `classify` (all-day and free-availability events are skipped).
@MainActor
final class CalendarOverlayProvider {
    private let store = EKEventStore()

    func requestAccess() async -> Bool {
        do { return try await store.requestFullAccessToEvents() }
        catch { return false }
    }

    /// Timed, busy events for `date`, as overlay arcs. All-day / free events are skipped.
    func events(on date: Date) async -> [CalendarOverlayEvent] {
        let granted = await requestAccess()
        guard granted else { return [] }

        let cal = Calendar.current
        let start = cal.startOfDay(for: date)
        guard let end = cal.date(byAdding: .day, value: 1, to: start) else { return [] }
        let predicate = store.predicateForEvents(withStart: start, end: end, calendars: nil)

        return store.events(matching: predicate).compactMap { event in
            guard !event.isAllDay, event.availability != .free else { return nil }
            let startMinute = minuteOfDay(event.startDate, in: cal)
            let duration = max(Int(event.endDate.timeIntervalSince(event.startDate) / 60), 1)
            return CalendarOverlayEvent(
                id: event.eventIdentifier ?? UUID().uuidString,
                title: event.title ?? "Event",
                startMinute: startMinute,
                durationMinutes: min(duration, 1440))
        }
    }

    private func minuteOfDay(_ date: Date, in cal: Calendar) -> Int {
        let c = cal.dateComponents([.hour, .minute], from: date)
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }
}
