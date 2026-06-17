import Foundation

// SleepLogHints — portable, Foundation-only live validation hints for the manual sleep-log sheet.
//
// Platform-agnostic port of the Android sleep log sheet logic
// (`feature/daydial/.../SleepLogSheet.kt`: duration math, window label with overnight tag,
// vs-target delta, short/long-night warnings, and the synthesized restfulness label). Pure and
// deterministic — no clock reads, no SwiftUI / UIKit — so it unit-tests off-device on the
// Windows/Linux CI that builds ChronosCore.

public enum SleepLogHints {

    /// Minutes in a full day.
    public static let minutesPerDay = 24 * 60
    /// The healthy-night target the live delta compares against (8 hours).
    public static let recommendedSleepMinutes = 8 * 60
    /// Below this, a night is flagged "short". (< 6h)
    public static let shortNightMinutes = 6 * 60
    /// Above this, a night is flagged "long". (> 9h)
    public static let longNightMinutes = 9 * 60

    // MARK: Duration

    /// Minutes asleep between `bedMinute` and `wakeMinute`, wrapping past midnight when the wake
    /// time is earlier in the day than bedtime. Equal times mean nothing meaningful was logged, so
    /// this returns 0 rather than a full 24-hour span. Mirrors `sleepDurationMinutes`.
    public static func durationMinutes(bedMinute: Int, wakeMinute: Int) -> Int {
        let raw = wakeMinute - bedMinute
        return raw < 0 ? raw + minutesPerDay : raw
    }

    /// Compact human label for a span of minutes, e.g. "7h 30m", "8h", or "45m".
    /// Mirrors `formatSleepDuration`.
    public static func formatDuration(_ minutes: Int) -> String {
        let hours = minutes / 60
        let mins = minutes % 60
        switch (hours, mins) {
        case (0, _): return "\(mins)m"
        case (_, 0): return "\(hours)h"
        default: return "\(hours)h \(mins)m"
        }
    }

    /// Format a minute-of-day as a 12-hour clock string, e.g. 1380 -> "11:00 PM".
    public static func formatClock(_ minute: Int) -> String {
        let m = ((minute % minutesPerDay) + minutesPerDay) % minutesPerDay
        let hour24 = m / 60
        let min = m % 60
        let suffix = hour24 >= 12 ? "PM" : "AM"
        var hour12 = hour24 % 12
        if hour12 == 0 { hour12 = 12 }
        return String(format: "%d:%02d %@", hour12, min, suffix)
    }

    // MARK: Window + tags

    /// "11:00 PM → 7:00 AM (next day)" confirmation of the entered window, or nil until both ends
    /// are set. The "(next day)" tag — the iOS analogue of Android's overnight tag — appears when
    /// the wake time falls past midnight, so a mistyped AM/PM is easy to spot. Mirrors
    /// `sleepWindowLabel`.
    public static func windowLabel(bedMinute: Int?, wakeMinute: Int?) -> String? {
        guard let bed = bedMinute, let wake = wakeMinute else { return nil }
        let overnight = wake < bed ? " (next day)" : ""
        return "\(formatClock(bed)) → \(formatClock(wake))\(overnight)"
    }

    /// True when the window crosses midnight (wake earlier in the day than bed). Drives the
    /// "Overnight" badge in the sheet.
    public static func isOvernight(bedMinute: Int?, wakeMinute: Int?) -> Bool {
        guard let bed = bedMinute, let wake = wakeMinute else { return false }
        return wake < bed
    }

    /// One-line "time in bed" summary, or nil when either end is unset or the span collapses to
    /// zero. Mirrors `sleepDurationSummary`.
    public static func durationSummary(bedMinute: Int?, wakeMinute: Int?) -> String? {
        guard let bed = bedMinute, let wake = wakeMinute else { return nil }
        let duration = durationMinutes(bedMinute: bed, wakeMinute: wake)
        guard duration > 0 else { return nil }
        return "\(formatDuration(duration)) in bed"
    }

    // MARK: vs-target delta

    /// Neutral comparison of the logged span against the 8h target, e.g. "1h 30m short of 8 hours"
    /// / "On target for 8 hours" / "45m over 8 hours". Nil when either end is unset or the span is
    /// zero. Mirrors `sleepVsRecommendedLabel`.
    public static func vsTargetLabel(
        bedMinute: Int?,
        wakeMinute: Int?,
        recommendedMinutes: Int = recommendedSleepMinutes
    ) -> String? {
        guard let bed = bedMinute, let wake = wakeMinute else { return nil }
        let duration = durationMinutes(bedMinute: bed, wakeMinute: wake)
        guard duration > 0 else { return nil }
        let recommendedHours = recommendedMinutes / 60
        let diff = duration - recommendedMinutes
        switch diff {
        case 0: return "On target for \(recommendedHours) hours"
        case ..<0: return "\(formatDuration(-diff)) short of \(recommendedHours) hours"
        default: return "\(formatDuration(diff)) over \(recommendedHours) hours"
        }
    }

    // MARK: Short / long-night warnings

    /// The severity of a duration warning: short (< 6h), long (> 9h), or none. `warning` returns
    /// nil for normal nights and a clear nudge otherwise — also catching the bed==wake mistype.
    public enum DurationWarning: Equatable, Sendable {
        case short
        case long
    }

    /// Classify the logged span as a short / long-night warning, or nil when it's within a healthy
    /// range (or unset). Tighter than Android's mistype hint: this drives the live short/long
    /// coaching the prompt asks for. Bed==wake is treated as "short" (nothing meaningful logged).
    public static func durationWarning(bedMinute: Int?, wakeMinute: Int?) -> DurationWarning? {
        guard let bed = bedMinute, let wake = wakeMinute else { return nil }
        if bed == wake { return .short }
        let duration = durationMinutes(bedMinute: bed, wakeMinute: wake)
        if duration < shortNightMinutes { return .short }
        if duration > longNightMinutes { return .long }
        return nil
    }

    /// Human warning text for the live hint, or nil for a healthy night. Mirrors the spirit of
    /// `sleepDurationHint` but uses the 6h/9h short/long thresholds from the iOS spec.
    public static func warningLabel(bedMinute: Int?, wakeMinute: Int?) -> String? {
        guard let bed = bedMinute, let wake = wakeMinute else { return nil }
        if bed == wake { return "Bed and wake times match — set different times." }
        switch durationWarning(bedMinute: bed, wakeMinute: wake) {
        case .short: return "Short night — under 6 hours. Check the times if that's not right."
        case .long: return "Long night — over 9 hours. Double-check the AM/PM on your times."
        case .none: return nil
        }
    }

    // MARK: Quality + restfulness

    /// Descriptive label for each 1–5 sleep-quality level, so the rating isn't a bare number.
    /// Mirrors `sleepQualityLabel`.
    public static func qualityLabel(_ level: Int) -> String {
        switch min(max(level, 1), 5) {
        case 1: return "Poor"
        case 2: return "Restless"
        case 3: return "Okay"
        case 4: return "Good"
        default: return "Great"
        }
    }

    /// One-word synthesis of self-rated `quality` and `interruptions` into an overall restfulness
    /// read. Mirrors `sleepRestfulnessLabel`.
    public static func restfulnessLabel(quality: Int, interruptions: Int) -> String {
        let q = min(max(quality, 1), 5)
        let safeInterruptions = max(interruptions, 0)
        switch true {
        case safeInterruptions >= 3: return "Disrupted night"
        case q <= 2: return "Rough night"
        case q >= 4 && safeInterruptions <= 1: return "Restful night"
        default: return "Average night"
        }
    }
}
