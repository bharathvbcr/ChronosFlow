import Foundation

// Quiet-hours math — the pure, Foundation-only port of the Android sleep-window suppression
// (the "don't fire non-critical reminders while the user is asleep" rule). All inputs are
// minute-of-day values (0..1439); the window may wrap past midnight (e.g. 22:00 → 07:00).
//
// Mirrors the Android behaviour: a reminder whose fire time lands inside the quiet window is
// deferred to the window's end (the next allowed minute), rather than dropped.

public enum QuietHours {

    /// True when `minute` falls inside the quiet window `[startMinute, endMinute)`.
    ///
    /// - The window is half-open: `start` is quiet, `end` is the first allowed minute.
    /// - A window where `start == end` is treated as *empty* (never quiet), matching the
    ///   Android convention where an equal start/end disables suppression.
    /// - A window where `start > end` wraps past midnight (e.g. 22:00 → 07:00).
    public static func isQuiet(minute: Int, startMinute: Int, endMinute: Int) -> Bool {
        let m = normalize(minute)
        let start = normalize(startMinute)
        let end = normalize(endMinute)
        if start == end { return false }                 // empty window → never quiet
        if start < end { return m >= start && m < end }  // same-day window
        return m >= start || m < end                     // wraps past midnight
    }

    /// The next minute-of-day at which a non-critical reminder is allowed to fire.
    ///
    /// If `minute` is outside the quiet window it is returned unchanged. If it is inside the
    /// window the window's `endMinute` is returned (the first allowed minute). The result is
    /// always normalized to 0..1439.
    public static func nextAllowedMinute(minute: Int, startMinute: Int, endMinute: Int) -> Int {
        guard isQuiet(minute: minute, startMinute: startMinute, endMinute: endMinute) else {
            return normalize(minute)
        }
        return normalize(endMinute)
    }

    /// True when shifting `minute` out of the quiet window lands it on the *next* calendar day.
    ///
    /// Useful for callers scheduling a concrete fire date: when the original minute is in the
    /// pre-midnight portion of a wrapping window (e.g. 23:30 with a 22:00 → 07:00 window), the
    /// next allowed minute (07:00) is on the following day. Returns false when no shift happens.
    public static func nextAllowedRollsToNextDay(minute: Int, startMinute: Int, endMinute: Int) -> Bool {
        guard isQuiet(minute: minute, startMinute: startMinute, endMinute: endMinute) else { return false }
        let m = normalize(minute)
        let start = normalize(startMinute)
        let end = normalize(endMinute)
        // Only wrapping windows can push past midnight, and only for minutes at/after `start`
        // (the pre-midnight portion). Minutes before `end` are already on the target day.
        return start > end && m >= start
    }

    /// Clamp any (possibly negative or >1439) minute into 0..1439 by mod-1440 wrapping.
    public static func normalizeMinute(_ minute: Int) -> Int {
        ((minute % 1440) + 1440) % 1440
    }

    /// Internal shorthand used throughout this file.
    static func normalize(_ minute: Int) -> Int { normalizeMinute(minute) }
}
