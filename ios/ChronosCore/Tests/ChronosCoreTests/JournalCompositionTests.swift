import XCTest
@testable import ChronosCore

/// Tests for the portable journal composition helpers: body serialize/parse (main note + bullet
/// sub-notes), calendar mood-by-date, the deterministic daily prompt, flexible time-of-day parsing,
/// and the AI insight prompt builder. All pure / deterministic — a fixed UTC calendar is injected so
/// they run on the Windows/Linux CI that builds ChronosCore (never reads the wall clock).
final class JournalCompositionTests: XCTestCase {

    private let cal: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        return c
    }()

    /// A UTC date at midnight for `y-m-d`.
    private func day(_ y: Int, _ m: Int, _ d: Int) -> Date {
        var comps = DateComponents()
        comps.year = y; comps.month = m; comps.day = d
        comps.hour = 0; comps.minute = 0; comps.second = 0
        comps.timeZone = TimeZone(identifier: "UTC")!
        return cal.date(from: comps)!
    }

    private func entry(
        _ id: String,
        _ date: Date,
        body: String = "",
        primary: Bool = true,
        rating: Int? = nil,
        minute: Int? = nil,
        created: TimeInterval = 0
    ) -> JournalEntryRecord {
        JournalEntryRecord(
            id: id, entryDate: date, body: body, isPrimary: primary,
            dayRating: rating, entryMinuteOfDay: minute,
            createdAt: Date(timeIntervalSince1970: created)
        )
    }

    // MARK: - Mood lookup

    func testMoodForRating() {
        XCTAssertEqual(journalMoodFor(1)?.label, "Rough")
        XCTAssertEqual(journalMoodFor(3)?.label, "Okay")
        XCTAssertEqual(journalMoodFor(5)?.label, "Great")
        XCTAssertEqual(journalMoodFor(5)?.emoji, "😄")
        XCTAssertNil(journalMoodFor(nil))
        XCTAssertNil(journalMoodFor(0))
        XCTAssertNil(journalMoodFor(6))
    }

    func testMoodLabelsMatchAndroid() {
        XCTAssertEqual(journalMoods.map { $0.label },
                       ["Rough", "Low", "Okay", "Good", "Great"])
        XCTAssertEqual(journalMoods.map { $0.rating }, [1, 2, 3, 4, 5])
    }

    // MARK: - Mood by date

    func testMoodByDatePrefersPrimary() {
        let d = day(2026, 6, 20)
        let entries = [
            entry("a", d, primary: false, rating: 2),
            entry("b", d, primary: true, rating: 5),   // primary wins
        ]
        let map = journalMoodByDate(entries, calendar: cal)
        XCTAssertEqual(map[cal.startOfDay(for: d)]?.rating, 5)
    }

    func testMoodByDateFallsBackToFirstRated() {
        let d = day(2026, 6, 20)
        let entries = [
            entry("a", d, primary: true, rating: nil),  // primary unrated
            entry("b", d, primary: false, rating: 4),   // first rated
            entry("c", d, primary: false, rating: 2),
        ]
        let map = journalMoodByDate(entries, calendar: cal)
        XCTAssertEqual(map[cal.startOfDay(for: d)]?.rating, 4)
    }

    func testMoodByDateOmitsUnratedDays() {
        let d = day(2026, 6, 20)
        let entries = [entry("a", d, primary: true, rating: nil)]
        let map = journalMoodByDate(entries, calendar: cal)
        XCTAssertTrue(map.isEmpty)
    }

    func testMoodByDateGroupsAcrossDays() {
        let d1 = day(2026, 6, 19)
        let d2 = day(2026, 6, 20)
        let entries = [
            entry("a", d1, primary: true, rating: 3),
            entry("b", d2, primary: true, rating: 5),
            // entry with a non-midnight time on d2 still groups into d2.
            entry("c", d2.addingTimeInterval(13 * 3600), primary: false, rating: 1),
        ]
        let map = journalMoodByDate(entries, calendar: cal)
        XCTAssertEqual(map.count, 2)
        XCTAssertEqual(map[cal.startOfDay(for: d1)]?.rating, 3)
        XCTAssertEqual(map[cal.startOfDay(for: d2)]?.rating, 5)
    }

    // MARK: - Workout / written dates

    func testWorkoutAndWrittenDates() {
        let d1 = day(2026, 6, 19)
        let d2 = day(2026, 6, 20)
        let entries = [
            entry("hc-workout-1", d1, primary: false),     // workout point
            entry("reflect-1", d1, primary: true, rating: 4),
            entry("hc-workout-2", d2, primary: false),
        ]
        XCTAssertTrue(isJournalWorkoutEntry(entries[0]))
        XCTAssertFalse(isJournalWorkoutEntry(entries[1]))

        let workout = journalWorkoutDates(entries, calendar: cal)
        XCTAssertEqual(workout, [cal.startOfDay(for: d1), cal.startOfDay(for: d2)])

        let written = journalWrittenDates(entries, calendar: cal)
        XCTAssertEqual(written, [cal.startOfDay(for: d1)])  // only the non-workout reflection day
    }

    // MARK: - Sorted points

    func testSortedPointsPrimaryFirstThenTimeThenCreated() {
        let d = day(2026, 6, 20)
        let entries = [
            entry("p2", d, primary: false, minute: 600, created: 50),
            entry("primary", d, primary: true, minute: nil, created: 100),
            entry("p1", d, primary: false, minute: 540),
            entry("noTimeA", d, primary: false, minute: nil, created: 10),
            entry("noTimeB", d, primary: false, minute: nil, created: 20),
        ]
        let sorted = journalSortedPoints(entries).map { $0.id }
        // primary first, then by minute (540, 600), then unset-minute points by createdAt.
        XCTAssertEqual(sorted, ["primary", "p1", "p2", "noTimeA", "noTimeB"])
    }

    // MARK: - Body serialize / parse

    func testSerializeMainOnly() {
        XCTAssertEqual(journalSerializeBody(mainNote: "A calm day.", subNotes: []), "A calm day.")
    }

    func testSerializeWithSubNotes() {
        let body = journalSerializeBody(mainNote: "Today", subNotes: ["Walked", "Read"])
        XCTAssertEqual(body, "Today\n• Walked\n• Read")
    }

    func testSerializeDropsBlankSubNotes() {
        let body = journalSerializeBody(mainNote: "Main", subNotes: ["  ", "Kept", ""])
        XCTAssertEqual(body, "Main\n• Kept")
    }

    func testSerializeSubNotesOnly() {
        let body = journalSerializeBody(mainNote: "", subNotes: ["Only a bullet"])
        XCTAssertEqual(body, "• Only a bullet")
    }

    func testParsePlainBody() {
        let parts = journalParseBody("Just a normal day.")
        XCTAssertEqual(parts.mainNote, "Just a normal day.")
        XCTAssertTrue(parts.subNotes.isEmpty)
    }

    func testParseSplitsBullets() {
        let parts = journalParseBody("Today\n• Walked\n• Read")
        XCTAssertEqual(parts.mainNote, "Today")
        XCTAssertEqual(parts.subNotes, ["Walked", "Read"])
    }

    func testParseMultiLineMainNote() {
        let parts = journalParseBody("Line one\nLine two\n• A point")
        XCTAssertEqual(parts.mainNote, "Line one\nLine two")
        XCTAssertEqual(parts.subNotes, ["A point"])
    }

    func testParseIgnoresBlankBullets() {
        // A "• " with nothing after it contributes no sub-note.
        let parts = journalParseBody("Main\n• \n• Real")
        XCTAssertEqual(parts.subNotes, ["Real"])
    }

    func testParseSerializeRoundTrip() {
        let serialized = journalSerializeBody(mainNote: "Reflection here", subNotes: ["One", "Two", "Three"])
        let parts = journalParseBody(serialized)
        XCTAssertEqual(parts.mainNote, "Reflection here")
        XCTAssertEqual(parts.subNotes, ["One", "Two", "Three"])
    }

    func testParseHandlesCarriageReturns() {
        let parts = journalParseBody("Today\r\n• Walked\r\n• Read")
        XCTAssertEqual(parts.mainNote, "Today")
        XCTAssertEqual(parts.subNotes, ["Walked", "Read"])
    }

    // MARK: - Epoch day + daily prompt

    func testEpochDay() {
        XCTAssertEqual(journalEpochDay(day(1970, 1, 1), calendar: cal), 0)
        XCTAssertEqual(journalEpochDay(day(1970, 1, 2), calendar: cal), 1)
        XCTAssertEqual(journalEpochDay(day(1969, 12, 31), calendar: cal), -1)
        // 2026-06-21 — sanity: matches LocalDate.of(2026,6,21).toEpochDay().
        XCTAssertEqual(journalEpochDay(day(2026, 6, 21), calendar: cal), 20625)
    }

    func testEpochDayIgnoresTimeOfDay() {
        let morning = day(2026, 6, 21).addingTimeInterval(3600)
        let nearMidnight = day(2026, 6, 21).addingTimeInterval(23 * 3600 + 59 * 60)
        XCTAssertEqual(journalEpochDay(morning, calendar: cal),
                       journalEpochDay(nearMidnight, calendar: cal))
    }

    func testPromptOfTheDayDeterministic() {
        // Same day → same prompt, regardless of time-of-day.
        let d = day(2026, 6, 21)
        let p1 = journalPromptOfTheDay(date: d, calendar: cal)
        let p2 = journalPromptOfTheDay(date: d.addingTimeInterval(12 * 3600), calendar: cal)
        XCTAssertEqual(p1, p2)
        // Index = epochDay % 12. 20625 % 12 == 9 → "How did you take care of yourself today?".
        XCTAssertEqual(p1, journalDailyPrompts[9])
    }

    func testPromptRotatesAcrossConsecutiveDays() {
        let base = day(2026, 6, 21)
        let baseIdx = journalEpochDay(base, calendar: cal) % journalDailyPrompts.count
        for offset in 0..<journalDailyPrompts.count {
            let d = cal.date(byAdding: .day, value: offset, to: base)!
            let expected = journalDailyPrompts[(baseIdx + offset) % journalDailyPrompts.count]
            XCTAssertEqual(journalPromptOfTheDay(date: d, calendar: cal), expected)
        }
    }

    func testPromptOfTheDayHandlesPreEpochDates() {
        // Negative epoch day must still index safely into the list.
        let d = day(1960, 1, 1)
        let prompt = journalPromptOfTheDay(date: d, calendar: cal)
        XCTAssertTrue(journalDailyPrompts.contains(prompt))
    }

    func testPromptOffsetShuffleCycles() {
        let d = day(2026, 6, 21)
        let base = journalPromptOfTheDay(date: d, calendar: cal)
        XCTAssertEqual(journalPrompt(date: d, calendar: cal, offset: 0), base)
        // Offset 1 is the next prompt in the list (wrapping).
        let baseIdx = journalDailyPrompts.firstIndex(of: base)!
        XCTAssertEqual(journalPrompt(date: d, calendar: cal, offset: 1),
                       journalDailyPrompts[(baseIdx + 1) % journalDailyPrompts.count])
        // A full cycle returns to the base prompt.
        XCTAssertEqual(journalPrompt(date: d, calendar: cal, offset: journalDailyPrompts.count), base)
        // Negative offsets wrap too.
        XCTAssertEqual(journalPrompt(date: d, calendar: cal, offset: -1),
                       journalDailyPrompts[(baseIdx - 1 + journalDailyPrompts.count) % journalDailyPrompts.count])
    }

    func testTwelveDailyPrompts() {
        XCTAssertEqual(journalDailyPrompts.count, 12)
        XCTAssertEqual(journalDailyPrompts.first, "What's one small win from today?")
    }

    // MARK: - Flexible minute parsing

    func testParse24Hour() {
        XCTAssertEqual(parseFlexibleMinute("21:00"), 21 * 60)
        XCTAssertEqual(parseFlexibleMinute("08:30"), 8 * 60 + 30)
        XCTAssertEqual(parseFlexibleMinute("0:00"), 0)
        XCTAssertEqual(parseFlexibleMinute("23:59"), 23 * 60 + 59)
    }

    func testParseAmPm() {
        XCTAssertEqual(parseFlexibleMinute("9:00 AM"), 9 * 60)
        XCTAssertEqual(parseFlexibleMinute("12:00 AM"), 0)        // midnight
        XCTAssertEqual(parseFlexibleMinute("12:00 PM"), 12 * 60)  // noon
        XCTAssertEqual(parseFlexibleMinute("1:30 PM"), 13 * 60 + 30)
        XCTAssertEqual(parseFlexibleMinute("11:45pm"), 23 * 60 + 45)  // lowercase, no space
    }

    func testParseBareHour() {
        XCTAssertEqual(parseFlexibleMinute("7"), 7 * 60)
        XCTAssertEqual(parseFlexibleMinute("  18  "), 18 * 60)
        // Bare hour with trailing colon defaults minutes to 0 (matches Kotlin toIntOrNull ?: 0).
        XCTAssertEqual(parseFlexibleMinute("9:"), 9 * 60)
    }

    func testParseInvalid() {
        XCTAssertNil(parseFlexibleMinute(""))
        XCTAssertNil(parseFlexibleMinute("abc"))
        XCTAssertNil(parseFlexibleMinute("25:00"))   // hour out of range
        XCTAssertNil(parseFlexibleMinute("10:60"))   // minute out of range
        XCTAssertNil(parseFlexibleMinute("1:2:3"))   // too many parts
        XCTAssertNil(parseFlexibleMinute("13:00 PM")) // 13 + 12 = 25 → out of range
    }

    // MARK: - Format display minute

    func testFormatDisplayMinute() {
        XCTAssertEqual(formatDisplayMinute(0), "12:00 AM")
        XCTAssertEqual(formatDisplayMinute(8 * 60), "8:00 AM")
        XCTAssertEqual(formatDisplayMinute(12 * 60), "12:00 PM")
        XCTAssertEqual(formatDisplayMinute(13 * 60 + 5), "1:05 PM")
        XCTAssertEqual(formatDisplayMinute(23 * 60 + 59), "11:59 PM")
    }

    func testFormatDisplayMinuteWraps() {
        // Negative and overflow wrap into 0–1439 (floorMod semantics).
        XCTAssertEqual(formatDisplayMinute(-60), "11:00 PM")
        XCTAssertEqual(formatDisplayMinute(24 * 60), "12:00 AM")
    }

    func testParseFormatRoundTrip() {
        for minute in stride(from: 0, to: 24 * 60, by: 17) {
            let display = formatDisplayMinute(minute)
            XCTAssertEqual(parseFlexibleMinute(display), minute, "round-trip failed for \(minute)")
        }
    }

    // MARK: - Insight prompt

    func testInsightPromptStructure() {
        let today = day(2026, 6, 21)
        let entries = [
            entry("a", day(2026, 6, 20), body: "Felt good, shipped a feature.", rating: 4),
            entry("b", day(2026, 6, 19), body: "Tired.", rating: 2),
        ]
        let prompt = buildJournalInsightPrompt(entries: entries, today: today, calendar: cal)
        XCTAssertTrue(prompt.contains("warm, concise journaling companion"))
        XCTAssertTrue(prompt.contains("Today is 2026-06-21."))
        XCTAssertTrue(prompt.contains("Reflections (newest first):"))
        XCTAssertTrue(prompt.contains("- 2026-06-20 (mood 4/5): Felt good, shipped a feature."))
        XCTAssertTrue(prompt.contains("- 2026-06-19 (mood 2/5): Tired."))
    }

    func testInsightPromptMoodDashAndMoodOnly() {
        let today = day(2026, 6, 21)
        let entries = [entry("a", day(2026, 6, 20), body: "   ", rating: nil)]
        let prompt = buildJournalInsightPrompt(entries: entries, today: today, calendar: cal)
        XCTAssertTrue(prompt.contains("- 2026-06-20 (mood —): (mood only)"))
    }

    func testInsightPromptCollapsesNewlinesAndTrims() {
        let today = day(2026, 6, 21)
        let longBody = String(repeating: "x", count: 200)
        let entries = [entry("a", day(2026, 6, 20), body: "first line\nsecond line\n\(longBody)", rating: 3)]
        let prompt = buildJournalInsightPrompt(entries: entries, today: today, calendar: cal)
        // Newlines collapsed to spaces, snippet trimmed to 140 chars.
        XCTAssertTrue(prompt.contains("first line second line"))
        XCTAssertFalse(prompt.contains("\nx"))  // no embedded newline from the body
        let xCount = prompt.filter { $0 == "x" }.count
        XCTAssertLessThanOrEqual(xCount, 140)
    }

    func testInsightPromptCapsAtFourteenEntries() {
        let today = day(2026, 6, 21)
        let entries = (0..<20).map { i in
            entry("e\(i)", cal.date(byAdding: .day, value: -i, to: today)!, body: "note \(i)", rating: 3)
        }
        let prompt = buildJournalInsightPrompt(entries: entries, today: today, calendar: cal)
        let noteLines = prompt.components(separatedBy: "\n").filter { $0.hasPrefix("- ") }
        XCTAssertEqual(noteLines.count, 14)
    }
}
