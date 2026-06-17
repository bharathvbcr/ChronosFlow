import XCTest
@testable import ChronosCore

final class DialRingsTests: XCTestCase {

    // MARK: ringForBlock routing (mirrors Android ringForBlock)

    func testCalendarLinkRoutesToOuter() {
        let block = RingBlockInput(provenance: "manual", category: "WORK", hasCalendarLink: true)
        XCTAssertEqual(ringForBlock(block), .outer)
    }

    func testCalendarProvenanceRoutesToOuter() {
        let block = RingBlockInput(provenance: "calendar", category: "FOCUS")
        XCTAssertEqual(ringForBlock(block), .outer)
    }

    func testCalendarCategoryRoutesToOuterCaseInsensitive() {
        let block = RingBlockInput(provenance: "manual", category: "Calendar")
        XCTAssertEqual(ringForBlock(block), .outer)
    }

    func testTaskLinkRoutesToInner() {
        let block = RingBlockInput(provenance: "manual", category: "FOCUS", hasTaskLink: true)
        XCTAssertEqual(ringForBlock(block), .inner)
    }

    func testHabitLinkRoutesToInner() {
        let block = RingBlockInput(provenance: "habit", category: "HABIT", hasHabitLink: true)
        XCTAssertEqual(ringForBlock(block), .inner)
    }

    func testMedicationCategoryRoutesToInner() {
        let block = RingBlockInput(provenance: "medication", category: "MEDICATION")
        XCTAssertEqual(ringForBlock(block), .inner)
    }

    func testRoutineCategoryRoutesToInner() {
        let block = RingBlockInput(provenance: "routine", category: "ROUTINE")
        XCTAssertEqual(ringForBlock(block), .inner)
    }

    func testManualPlanBlockRoutesToMiddle() {
        let block = RingBlockInput(provenance: "manual", category: "FOCUS")
        XCTAssertEqual(ringForBlock(block), .middle)
    }

    func testAiPlanBlockRoutesToMiddle() {
        let block = RingBlockInput(provenance: "ai", category: "STUDY")
        XCTAssertEqual(ringForBlock(block), .middle)
    }

    func testCalendarWinsOverActionLinks() {
        // A block linked to both a calendar event and a task should still ride the outer ring.
        let block = RingBlockInput(provenance: "calendar", category: "FOCUS",
                                   hasTaskLink: true, hasCalendarLink: true)
        XCTAssertEqual(ringForBlock(block), .outer)
    }

    // MARK: Ring radii

    func testRingRadiiOrderingAndValues() {
        XCTAssertEqual(DialRingRadius.fraction(for: .outer), 0.98, accuracy: 1e-9)
        XCTAssertEqual(DialRingRadius.fraction(for: .middle), 0.72, accuracy: 1e-9)
        XCTAssertEqual(DialRingRadius.fraction(for: .inner), 0.40, accuracy: 1e-9)
        XCTAssertGreaterThan(DialRingRadius.outer, DialRingRadius.middle)
        XCTAssertGreaterThan(DialRingRadius.middle, DialRingRadius.inner)
    }

    // MARK: Night band

    func testNightBandWrapsMidnight() {
        // 21:00 → 07:00 is a 10h (600 min) overnight band.
        let band = nightBandSegment(startMinute: 21 * 60, endMinute: 7 * 60)
        XCTAssertEqual(band, DialArcSegment(startMinute: 21 * 60, durationMinutes: 600))
        XCTAssertEqual(band?.endMinute, 7 * 60)
    }

    func testNightBandSameDayWindow() {
        // A daytime nap 13:00 → 14:00.
        let band = nightBandSegment(startMinute: 13 * 60, endMinute: 14 * 60)
        XCTAssertEqual(band, DialArcSegment(startMinute: 13 * 60, durationMinutes: 60))
    }

    func testNightBandEmptyWhenStartEqualsEnd() {
        XCTAssertNil(nightBandSegment(startMinute: 8 * 60, endMinute: 8 * 60))
    }

    func testNightBandNormalizesOutOfRangeInput() {
        // 1440 normalizes to 0, so 0 → 7:00 is a 7h band starting at midnight.
        let band = nightBandSegment(startMinute: 1440, endMinute: 7 * 60)
        XCTAssertEqual(band, DialArcSegment(startMinute: 0, durationMinutes: 7 * 60))
    }

    // MARK: Free-time segments

    func testFreeSegmentsEmptyDayIsOneDaytimeWindow() {
        // No blocks, no night → the whole [6:00, 23:00) window is free.
        let segs = freeTimeSegments(busy: [], night: nil)
        XCTAssertEqual(segs, [DialArcSegment(startMinute: 6 * 60, durationMinutes: 17 * 60)])
    }

    func testFreeSegmentsSplitAroundOneBlock() {
        // A 10:00–11:00 block splits the day into 6:00–10:00 and 11:00–23:00.
        let busy = [DialArcSegment(startMinute: 10 * 60, durationMinutes: 60)]
        let segs = freeTimeSegments(busy: busy, night: nil)
        XCTAssertEqual(segs, [
            DialArcSegment(startMinute: 6 * 60, durationMinutes: 4 * 60),
            DialArcSegment(startMinute: 11 * 60, durationMinutes: 12 * 60),
        ])
    }

    func testFreeSegmentsDropSliversBelowMinDuration() {
        // Two blocks leaving only a 20-minute gap (< default 30) — the sliver is dropped.
        let busy = [
            DialArcSegment(startMinute: 9 * 60, durationMinutes: 60),       // 9:00–10:00
            DialArcSegment(startMinute: 10 * 60 + 20, durationMinutes: 60), // 10:20–11:20
        ]
        let segs = freeTimeSegments(busy: busy, night: nil)
        // 10:00–10:20 (20 min) gone; remaining gaps survive.
        XCTAssertFalse(segs.contains { $0.startMinute == 10 * 60 })
        XCTAssertTrue(segs.contains { $0.startMinute == 6 * 60 })          // 6:00–9:00
        XCTAssertTrue(segs.contains { $0.startMinute == 11 * 60 + 20 })   // 11:20–23:00
    }

    func testNightWindowCarvedOutOfFreeTime() {
        // Night 21:00→07:00 should clip the trailing free run at 21:00 (within the day window).
        let segs = freeTimeSegments(busy: [], night: nightBandSegment(startMinute: 21 * 60, endMinute: 7 * 60))
        // Free time runs 7:00 (night end) … 21:00 (night start); but day window starts at 6:00 and
        // the night covers 6:00–7:00 too, so the free run is 7:00–21:00.
        XCTAssertEqual(segs, [DialArcSegment(startMinute: 7 * 60, durationMinutes: 14 * 60)])
    }

    func testFreeSegmentsHandleBlockSpanningDayStart() {
        // A block 5:00–7:00 overlaps the 6:00 day start; free time should resume at 7:00.
        let busy = [DialArcSegment(startMinute: 5 * 60, durationMinutes: 120)]
        let segs = freeTimeSegments(busy: busy, night: nil)
        XCTAssertEqual(segs.first?.startMinute, 7 * 60)
    }
}
