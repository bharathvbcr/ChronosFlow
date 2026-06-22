import XCTest
@testable import ChronosCore

/// Tests for the portable watch formatting helpers (`WearFormat`), the `WearLinkStatus` model + its
/// settings labels, and the sensitive-title redaction helpers. All pure / deterministic (clock and
/// minute-of-day injected) so they run on the Windows/Linux CI that builds ChronosCore. Mirrors the
/// cases Android's WearFormat / WearDaySummaryEntries / RelativeTimeFormat tests cover.
final class WearFormatTests: XCTestCase {

    // 60 minutes, in millis — the staleness threshold, hoisted as a `let` for the sync-age cases.
    private let oneHourMillis: Int64 = 60 * 60_000

    // MARK: - minuteOfDay

    func testMinuteOfDayBasic() {
        XCTAssertEqual(WearFormat.minuteOfDay(0), "00:00")
        XCTAssertEqual(WearFormat.minuteOfDay(9 * 60), "09:00")
        XCTAssertEqual(WearFormat.minuteOfDay(9 * 60 + 5), "09:05")
        XCTAssertEqual(WearFormat.minuteOfDay(23 * 60 + 59), "23:59")
    }

    func testMinuteOfDayWrapsAcrossMidnightAndNegatives() {
        // 1440 (next midnight) wraps to 00:00; past-midnight minutes wrap into the day.
        XCTAssertEqual(WearFormat.minuteOfDay(1440), "00:00")
        XCTAssertEqual(WearFormat.minuteOfDay(1440 + 90), "01:30")
        // Negative minutes wrap back from the end of the day.
        XCTAssertEqual(WearFormat.minuteOfDay(-1), "23:59")
        XCTAssertEqual(WearFormat.minuteOfDay(-60), "23:00")
    }

    // MARK: - windowLabel

    func testWindowLabelUsesEnDash() {
        XCTAssertEqual(WearFormat.windowLabel(startMinute: 9 * 60, endMinute: 9 * 60 + 30),
                       "09:00–09:30")
        // Crossing midnight wraps the end.
        XCTAssertEqual(WearFormat.windowLabel(startMinute: 23 * 60 + 30, endMinute: 24 * 60 + 15),
                       "23:30–00:15")
    }

    // MARK: - mmss

    func testMmss() {
        XCTAssertEqual(WearFormat.mmss(0), "0:00")
        XCTAssertEqual(WearFormat.mmss(5), "0:05")
        XCTAssertEqual(WearFormat.mmss(65), "1:05")
        XCTAssertEqual(WearFormat.mmss(25 * 60), "25:00")
        // Past an hour switches to h:mm:ss.
        XCTAssertEqual(WearFormat.mmss(3600), "1:00:00")
        XCTAssertEqual(WearFormat.mmss(3661), "1:01:01")
        // Negative clamps to 0.
        XCTAssertEqual(WearFormat.mmss(-10), "0:00")
    }

    // MARK: - minutesWords

    func testMinutesWords() {
        XCTAssertEqual(WearFormat.minutesWords(0), "0m")
        XCTAssertEqual(WearFormat.minutesWords(45), "45m")
        XCTAssertEqual(WearFormat.minutesWords(59), "59m")
        XCTAssertEqual(WearFormat.minutesWords(60), "1h")          // exact hour, no minutes
        XCTAssertEqual(WearFormat.minutesWords(90), "1h 30m")
        XCTAssertEqual(WearFormat.minutesWords(120), "2h")
        XCTAssertEqual(WearFormat.minutesWords(125), "2h 5m")
        // Negative clamps to 0.
        XCTAssertEqual(WearFormat.minutesWords(-30), "0m")
    }

    // MARK: - remainingLabel ("Xm left")

    func testRemainingLabel() {
        XCTAssertEqual(WearFormat.remainingLabel(endMinute: 600, nowMinute: 577), "23m left")
        XCTAssertEqual(WearFormat.remainingLabel(endMinute: 600, nowMinute: 535), "1h 5m left")
        // At or past the end reads "ending" — never a zero/negative countdown.
        XCTAssertEqual(WearFormat.remainingLabel(endMinute: 600, nowMinute: 600), "ending")
        XCTAssertEqual(WearFormat.remainingLabel(endMinute: 600, nowMinute: 700), "ending")
    }

    // MARK: - startsInLabel ("Next in Xm")

    func testStartsInLabel() {
        XCTAssertEqual(WearFormat.startsInLabel(startMinute: 600, nowMinute: 585), "in 15m")
        XCTAssertEqual(WearFormat.startsInLabel(startMinute: 600, nowMinute: 535), "in 1h 5m")
        // Due now or overdue reads "now".
        XCTAssertEqual(WearFormat.startsInLabel(startMinute: 600, nowMinute: 600), "now")
        XCTAssertEqual(WearFormat.startsInLabel(startMinute: 600, nowMinute: 650), "now")
    }

    // MARK: - syncAgeLabel (staleness warning)

    func testSyncAgeLabelNilWhenNeverSynced() {
        XCTAssertNil(WearFormat.syncAgeLabel(receivedAtMillis: 0, nowMillis: 5_000_000))
        XCTAssertNil(WearFormat.syncAgeLabel(receivedAtMillis: -1, nowMillis: 5_000_000))
    }

    func testSyncAgeLabelNilWhenFresh() {
        let now: Int64 = 10_000_000
        // Just synced.
        XCTAssertNil(WearFormat.syncAgeLabel(receivedAtMillis: now, nowMillis: now))
        // 59 minutes ago — still under the 60-minute threshold.
        XCTAssertNil(WearFormat.syncAgeLabel(receivedAtMillis: now - 59 * 60_000, nowMillis: now))
        // Exactly at the threshold boundary is NOT yet stale (strict <).
        XCTAssertNil(WearFormat.syncAgeLabel(receivedAtMillis: now - (oneHourMillis - 1),
                                             nowMillis: now))
    }

    func testSyncAgeLabelStaleMinutesThenHours() {
        let now: Int64 = 100_000_000
        // Exactly 60 minutes → stale, reported in minutes.
        XCTAssertEqual(WearFormat.syncAgeLabel(receivedAtMillis: now - oneHourMillis, nowMillis: now),
                       "Synced 60m ago")
        // 90 minutes → still under 120, reported in minutes.
        XCTAssertEqual(WearFormat.syncAgeLabel(receivedAtMillis: now - 90 * 60_000, nowMillis: now),
                       "Synced 90m ago")
        // 120 minutes → switches to hours.
        XCTAssertEqual(WearFormat.syncAgeLabel(receivedAtMillis: now - 120 * 60_000, nowMillis: now),
                       "Synced 2h ago")
        // 3h 30m → integer-floored to 3h.
        XCTAssertEqual(WearFormat.syncAgeLabel(receivedAtMillis: now - 210 * 60_000, nowMillis: now),
                       "Synced 3h ago")
    }

    // MARK: - WearLinkStatus model

    func testUnknownStatus() {
        let u = WearLinkStatus.unknown
        XCTAssertFalse(u.watchPaired)
        XCTAssertFalse(u.watchConnected)
        XCTAssertFalse(u.watchAppInstalled)
        XCTAssertNil(u.connectedNodeName)
        XCTAssertEqual(u.lastPublishedAtMillis, 0)
    }

    // MARK: - wearLinkConnectionSummary

    func testConnectionSummaryChecking() {
        XCTAssertEqual(wearLinkConnectionSummary(nil), "Checking watch…")
    }

    func testConnectionSummaryConnectedWithAndWithoutName() {
        let named = WearLinkStatus(watchPaired: true, watchConnected: true, watchAppInstalled: true,
                                   connectedNodeName: "Pixel Watch", lastPublishedAtMillis: 0)
        XCTAssertEqual(wearLinkConnectionSummary(named), "Connected: Pixel Watch")

        let unnamed = WearLinkStatus(watchPaired: true, watchConnected: true, watchAppInstalled: true,
                                     connectedNodeName: nil, lastPublishedAtMillis: 0)
        XCTAssertEqual(wearLinkConnectionSummary(unnamed), "Connected")
    }

    func testConnectionSummaryDegradedStates() {
        let appMissing = WearLinkStatus(watchPaired: true, watchConnected: true,
                                        watchAppInstalled: false, connectedNodeName: nil,
                                        lastPublishedAtMillis: 0)
        XCTAssertEqual(wearLinkConnectionSummary(appMissing), "Watch connected — app not installed")

        let unreachable = WearLinkStatus(watchPaired: true, watchConnected: false,
                                         watchAppInstalled: false, connectedNodeName: nil,
                                         lastPublishedAtMillis: 0)
        XCTAssertEqual(wearLinkConnectionSummary(unreachable), "Watch paired but not reachable")

        XCTAssertEqual(wearLinkConnectionSummary(.unknown), "No watch connected")
    }

    // MARK: - formatLastSyncedLabel (phone-side caption)

    func testLastSyncedNeverAndSkew() {
        XCTAssertEqual(formatLastSyncedLabel(lastSyncAtMillis: nil, nowMillis: 5_000_000),
                       "Not synced yet")
        // Future timestamp (clock skew) clamps to just now.
        XCTAssertEqual(formatLastSyncedLabel(lastSyncAtMillis: 6_000_000, nowMillis: 5_000_000),
                       "Synced just now")
    }

    func testLastSyncedRanges() {
        let now: Int64 = 1_000_000_000
        func ago(_ millis: Int64) -> String {
            formatLastSyncedLabel(lastSyncAtMillis: now - millis, nowMillis: now)
        }
        XCTAssertEqual(ago(0), "Synced just now")
        XCTAssertEqual(ago(30_000), "Synced just now")          // <1 min
        XCTAssertEqual(ago(60_000), "Synced 1 min ago")
        XCTAssertEqual(ago(45 * 60_000), "Synced 45 min ago")
        XCTAssertEqual(ago(60 * 60_000), "Synced 1 hr ago")
        XCTAssertEqual(ago(5 * 60 * 60_000), "Synced 5 hr ago")
        XCTAssertEqual(ago(24 * 60 * 60_000), "Synced yesterday")
        XCTAssertEqual(ago(3 * 24 * 60 * 60_000), "Synced 3 days ago")
    }

    // MARK: - Redaction helpers

    func testRedactedTitle() {
        XCTAssertEqual(wearRedactedTitle("Deep work", sensitiveTitlesRedacted: false), "Deep work")
        XCTAssertEqual(wearRedactedTitle("Deep work", sensitiveTitlesRedacted: true),
                       "Scheduled block")
        XCTAssertEqual(redactedBlockTitle, "Scheduled block")
    }

    func testRedactedEntries() {
        let titles = ["Email", "Standup", "Review PR"]
        XCTAssertEqual(wearRedactedEntries(titles, sensitiveTitlesRedacted: false), titles)
        // Redacted → array dropped entirely (counts still flow via other fields).
        XCTAssertEqual(wearRedactedEntries(titles, sensitiveTitlesRedacted: true), [])
    }
}
