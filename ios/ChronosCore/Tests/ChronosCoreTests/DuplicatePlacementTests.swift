import XCTest
@testable import ChronosCore

private func pblock(
    _ id: String,
    start: Int,
    duration: Int,
    flexibility: BlockFlexibility = .movable,
    provenance: BlockProvenance = .user,
    category: String = "Work",
    calendarEventId: Int64? = nil,
    isLocked: Bool = false,
    taskId: String? = nil,
    habitId: String? = nil,
    medicationPlanId: String? = nil,
    routineId: String? = nil,
    recurrenceRuleId: String? = nil,
    actualStart: Int? = nil,
    actualEnd: Int? = nil
) -> PlannerBlock {
    PlannerBlock(
        id: id,
        title: "Block \(id)",
        category: category,
        startMinuteOfDay: start,
        durationMinutes: duration,
        provenance: provenance,
        flexibility: flexibility,
        source: "USER",
        taskId: taskId,
        calendarEventId: calendarEventId,
        medicationPlanId: medicationPlanId,
        habitId: habitId,
        routineId: routineId,
        recurrenceRuleId: recurrenceRuleId,
        isLocked: isLocked,
        actualStartMinuteOfDay: actualStart,
        actualEndMinuteOfDay: actualEnd
    )
}

// MARK: - FreeTimeCalculator

final class FreeTimeCalculatorTests: XCTestCase {

    func testEmptyDayIsOneFullFreeSegment() {
        let free = FreeTimeCalculator.calculate([])
        XCTAssertEqual(free, [FreeTimeSegment(startMinute: 0, endMinute: 1440)])
    }

    func testSingleBlockSplitsDay() {
        let free = FreeTimeCalculator.calculate([pblock("a", start: 540, duration: 60)]) // 9:00–10:00
        XCTAssertEqual(free, [
            FreeTimeSegment(startMinute: 0, endMinute: 540),
            FreeTimeSegment(startMinute: 600, endMinute: 1440)
        ])
    }

    func testAdjacentBlocksLeaveNoSliverBetween() {
        let free = FreeTimeCalculator.calculate([
            pblock("a", start: 540, duration: 60),  // 9–10
            pblock("b", start: 600, duration: 60)   // 10–11
        ])
        XCTAssertEqual(free, [
            FreeTimeSegment(startMinute: 0, endMinute: 540),
            FreeTimeSegment(startMinute: 660, endMinute: 1440)
        ])
    }

    func testAllDayCalendarImportDoesNotOccupyTime() {
        // A 24h calendar import is excluded -> the whole day stays free.
        let allDay = pblock(
            "cal", start: 0, duration: 1440,
            provenance: .calendar, category: "CALENDAR_ALL_DAY", calendarEventId: 7
        )
        let free = FreeTimeCalculator.calculate([allDay])
        XCTAssertEqual(free, [FreeTimeSegment(startMinute: 0, endMinute: 1440)])
    }

    func testCrossMidnightBlockWraps() {
        // 23:30 + 60min -> occupies 1410–1440 and 0–30.
        let free = FreeTimeCalculator.calculate([pblock("a", start: 1410, duration: 60)])
        XCTAssertEqual(free, [FreeTimeSegment(startMinute: 30, endMinute: 1410)])
    }
}

// MARK: - nextDuplicatePlacement (port of DayDialBlockDelegate.nextDuplicatePlacement)

final class DuplicatePlacementTests: XCTestCase {

    func testPrefersFirstGapAtOrAfterSourceEndFittingFullDuration() {
        // source 9:00–10:00; another block 14:00–15:00. Preferred start = 600 (10:00).
        let source = pblock("s", start: 540, duration: 60)
        let day = [source, pblock("b", start: 840, duration: 60)]
        let placement = nextDuplicatePlacement(dayBlocks: day, source: source)
        XCTAssertEqual(placement, DuplicatePlacement(startMinute: 600, durationMinutes: 60))
    }

    func testClampsStartToGapStartWhenPreferredFallsInsideABlock() {
        // source 9:00–10:00 (60m). Busy 10:00–12:00 so preferred 600 is occupied.
        // First gap at/after end fitting full = the 12:00 (720) gap.
        let source = pblock("s", start: 540, duration: 60)
        let day = [source, pblock("b", start: 600, duration: 120)] // 10–12
        let placement = nextDuplicatePlacement(dayBlocks: day, source: source)
        XCTAssertEqual(placement, DuplicatePlacement(startMinute: 720, durationMinutes: 60))
    }

    func testFallsBackToEarliestGapAnywhereWhenNothingFitsAfterEnd() {
        // source sits at the very end of the day; the only full-duration room is BEFORE it.
        // source 23:00 (1380) +60 -> ends 1440. Nothing fits at/after end. Earliest gap = 0.
        let source = pblock("s", start: 1380, duration: 60)
        let day = [source]
        let placement = nextDuplicatePlacement(dayBlocks: day, source: source)
        XCTAssertEqual(placement, DuplicatePlacement(startMinute: 0, durationMinutes: 60))
    }

    func testShrinksToLargestGapWhenNoGapFitsFull() {
        // source is 600 minutes long. Day is packed except a single 120-min hole.
        // No gap fits full -> shrink to the largest gap (120, >= 15).
        let source = pblock("s", start: 0, duration: 600)   // 0–600
        let filler = pblock("b", start: 720, duration: 720) // 720–1440
        // free segment: 600–720 (120 min).
        let placement = nextDuplicatePlacement(dayBlocks: [source, filler], source: source)
        XCTAssertEqual(placement, DuplicatePlacement(startMinute: 600, durationMinutes: 120))
    }

    func testReturnsNilWhenNoFreeTime() {
        // Whole day occupied -> no segments at all.
        let source = pblock("s", start: 0, duration: 1440)
        let placement = nextDuplicatePlacement(dayBlocks: [source], source: source)
        XCTAssertNil(placement)
    }

    func testReturnsNilWhenLargestGapBelowMinimum() {
        // Only a 10-minute hole remains (< minDuplicateGapMinutes = 15) -> nil.
        let source = pblock("s", start: 0, duration: 700)    // 0–700
        let filler = pblock("b", start: 710, duration: 730)  // 710–1440; gap 700–710 = 10 min
        let placement = nextDuplicatePlacement(dayBlocks: [source, filler], source: source)
        XCTAssertNil(placement)
    }

    func testGapExactlyAtMinimumIsAccepted() {
        // A 15-minute hole is the largest and equals the minimum -> shrink to 15.
        let source = pblock("s", start: 0, duration: 700)    // 0–700
        let filler = pblock("b", start: 715, duration: 725)  // 715–1440; gap 700–715 = 15 min
        let placement = nextDuplicatePlacement(dayBlocks: [source, filler], source: source)
        XCTAssertEqual(placement, DuplicatePlacement(startMinute: 700, durationMinutes: 15))
    }
}

// MARK: - makeDuplicate (instance-link clearing + FIXED downgrade)

final class MakeDuplicateTests: XCTestCase {

    func testClearsAllInstanceIdentityLinksAndActuals() {
        let source = pblock(
            "s", start: 540, duration: 60,
            taskId: "t1", habitId: "h1", medicationPlanId: "m1",
            routineId: "r1", recurrenceRuleId: "rec1",
            actualStart: 545, actualEnd: 600
        )
        var withGoalAndOccurrence = source
        withGoalAndOccurrence.goalId = "g1"
        withGoalAndOccurrence.calendarEventId = 99
        withGoalAndOccurrence.taskOccurrenceDate = Date(timeIntervalSince1970: 1_000_000)

        let copy = makeDuplicate(
            of: withGoalAndOccurrence,
            placement: DuplicatePlacement(startMinute: 600, durationMinutes: 60),
            newId: "new-id"
        )

        XCTAssertEqual(copy.id, "new-id")
        XCTAssertNil(copy.taskId)
        XCTAssertNil(copy.taskOccurrenceDate)
        XCTAssertNil(copy.calendarEventId)
        XCTAssertNil(copy.medicationPlanId)
        XCTAssertNil(copy.habitId)
        XCTAssertNil(copy.goalId)
        XCTAssertNil(copy.routineId)
        XCTAssertNil(copy.recurrenceRuleId)
        XCTAssertNil(copy.actualStartMinuteOfDay)
        XCTAssertNil(copy.actualEndMinuteOfDay)
    }

    func testNormalizesProvenanceSourceAndAppliesPlacement() {
        let source = pblock("s", start: 540, duration: 60, provenance: .habit)
        let copy = makeDuplicate(
            of: source,
            placement: DuplicatePlacement(startMinute: 720, durationMinutes: 45),
            newId: "n"
        )
        XCTAssertEqual(copy.provenance, .user)
        XCTAssertEqual(copy.source, "USER")
        XCTAssertEqual(copy.startMinuteOfDay, 720)
        XCTAssertEqual(copy.durationMinutes, 45)
        XCTAssertEqual(copy.title, "Block s (Copy)")
    }

    func testDowngradesFixedToMovableAndUnlocks() {
        let source = pblock("s", start: 540, duration: 60, flexibility: .fixed, isLocked: true)
        let copy = makeDuplicate(
            of: source,
            placement: DuplicatePlacement(startMinute: 600, durationMinutes: 60),
            newId: "n"
        )
        XCTAssertEqual(copy.flexibility, .movable)
        XCTAssertFalse(copy.isLocked)
    }

    func testPreservesNonFixedFlexibility() {
        let source = pblock("s", start: 540, duration: 60, flexibility: .resizable)
        let copy = makeDuplicate(
            of: source,
            placement: DuplicatePlacement(startMinute: 600, durationMinutes: 60),
            newId: "n"
        )
        XCTAssertEqual(copy.flexibility, .resizable)
    }
}
