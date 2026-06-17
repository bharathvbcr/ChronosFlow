import XCTest
@testable import ChronosCore

/// Cross-module *invariant* (property-style) tests. The iOS/watchOS app target can't be compiled
/// off a Mac, so these contracts are the safety net for the logic the SwiftUI layer depends on:
/// they assert relationships that must hold for ALL inputs, not just hand-picked examples.
final class ContractInvariantsTests: XCTestCase {

    // MARK: planFocusPhases — phases always tile the block exactly

    func testFocusPhasesAlwaysSumToBlockDuration() {
        for minutes in 1...300 {
            for preset in FocusSplitPreset.allCases {
                let phases = planFocusPhases(blockMinutes: minutes, preset: preset)
                XCTAssertFalse(phases.isEmpty, "\(minutes)m/\(preset) produced no phases")
                let sum = phases.reduce(0) { $0 + $1.durationMinutes }
                XCTAssertEqual(sum, minutes, "\(minutes)m/\(preset): phases sum \(sum) != block \(minutes)")
                XCTAssertTrue(phases.allSatisfy { $0.durationMinutes >= 1 }, "\(minutes)m/\(preset): non-positive phase")
                // Focus minutes are a subset of the block.
                XCTAssertLessThanOrEqual(focusMinutes(in: phases), minutes)
                // noBreaks is always a single work phase covering the whole block.
                if preset == .noBreaks {
                    XCTAssertEqual(phases.count, 1)
                    XCTAssertEqual(phases.first?.kind, .work)
                }
            }
        }
    }

    // MARK: ringForBlock — total and matches the routing contract

    func testRingRoutingIsTotalAndCorrect() {
        // Calendar always → outer (link or provenance or category).
        XCTAssertEqual(ringForBlock(RingBlockInput(provenance: "calendar", category: "WORK")), .outer)
        XCTAssertEqual(ringForBlock(RingBlockInput(provenance: "manual", category: "MEETING", hasCalendarLink: true)), .outer)
        // Action links / ROUTINE / MEDICATION → inner.
        for input in [
            RingBlockInput(provenance: "task", category: "WORK"),
            RingBlockInput(provenance: "habit", category: "PERSONAL"),
            RingBlockInput(provenance: "manual", category: "WORK", hasMedicationLink: true),
            RingBlockInput(provenance: "manual", category: "ROUTINE"),
            RingBlockInput(provenance: "manual", category: "MEDICATION"),
        ] {
            XCTAssertEqual(ringForBlock(input), .inner, "\(input) should route to inner")
        }
        // Everything else (the plan) → middle.
        for input in [
            RingBlockInput(provenance: "manual", category: "WORK"),
            RingBlockInput(provenance: "ai", category: "FOCUS"),
        ] {
            XCTAssertEqual(ringForBlock(input), .middle, "\(input) should route to middle")
        }
        // Case-insensitive provenance/category.
        XCTAssertEqual(ringForBlock(RingBlockInput(provenance: "CALENDAR", category: "x")), .outer)
        XCTAssertEqual(ringForBlock(RingBlockInput(provenance: "manual", category: "routine")), .inner)
        // Radius fractions are ordered outer > middle > inner and within (0, 1].
        let r = (DialRingRadius.fraction(for: .outer), DialRingRadius.fraction(for: .middle), DialRingRadius.fraction(for: .inner))
        XCTAssertGreaterThan(r.0, r.1); XCTAssertGreaterThan(r.1, r.2)
        XCTAssertTrue(r.2 > 0 && r.0 <= 1)
    }

    // MARK: QuietHours — the next allowed minute is never inside the quiet window

    func testNextAllowedMinuteIsNeverQuiet() {
        let windows = [(22 * 60, 7 * 60), (60, 120), (0, 0), (23 * 60, 23 * 60 + 30)]
        for (start, end) in windows {
            for minute in stride(from: 0, to: 1440, by: 7) {
                let allowed = QuietHours.nextAllowedMinute(minute: minute, startMinute: start, endMinute: end)
                XCTAssertFalse(
                    QuietHours.isQuiet(minute: allowed, startMinute: start, endMinute: end),
                    "next allowed \(allowed) is quiet for window \(start)-\(end) from \(minute)")
                // An already-allowed minute is returned unchanged.
                if !QuietHours.isQuiet(minute: minute, startMinute: start, endMinute: end) {
                    XCTAssertEqual(allowed, minute)
                }
            }
        }
    }

    func testNormalizeMinuteAlwaysInDay() {
        for m in [-2000, -1, 0, 5, 1439, 1440, 1441, 3000] {
            let n = QuietHours.normalizeMinute(m)
            XCTAssertTrue((0...1439).contains(n), "normalizeMinute(\(m)) = \(n) out of range")
        }
    }

    // MARK: applyReadiness — depleted shifts demanding-early blocks; others are identity

    private func demanding(_ id: String, start: Int, dur: Int = 60, fixed: Bool = false) -> ReadinessPlanBlock {
        ReadinessPlanBlock(id: id, startMinute: start, durationMinutes: dur,
                           energyLevel: ReadinessSchedule.demandingEnergyLevel, isFixed: fixed)
    }

    func testReadinessIdentityWhenNotDepleted() {
        let blocks = [demanding("a", start: 8 * 60), demanding("b", start: 9 * 60)]
        for readiness: SleepReadiness in [.normal, .rested, .unknown] {
            let out = applyReadiness(to: blocks, readiness: readiness)
            XCTAssertEqual(out.map(\.startMinute), blocks.map(\.startMinute),
                           "readiness \(readiness) must not move blocks")
        }
    }

    func testDepletedShiftsDemandingBlocksPastFloorButNotFixedOrLight() {
        let floor = ReadinessSchedule.demandingTaskEarliestStartMinute(.depleted) ?? 660
        let early = demanding("early", start: 8 * 60)                 // demanding, before floor → shifts
        let fixedEarly = demanding("fixed", start: 8 * 60, fixed: true) // fixed → never moves
        let light = ReadinessPlanBlock(id: "light", startMinute: 8 * 60, durationMinutes: 30,
                                       energyLevel: 1, isFixed: false)  // not demanding → stays
        let out = applyReadiness(to: [early, fixedEarly, light], readiness: .depleted)
        let byID = Dictionary(uniqueKeysWithValues: out.map { ($0.id, $0.startMinute) })
        XCTAssertGreaterThanOrEqual(byID["early"] ?? -1, floor, "demanding block not floored")
        XCTAssertEqual(byID["fixed"], 8 * 60, "fixed block was moved")
        XCTAssertEqual(byID["light"], 8 * 60, "non-demanding block was moved")
        // Shifted blocks must not overlap the fixed obstacle.
        XCTAssertFalse(spansOverlap(out, idA: "early", idB: "fixed"))
    }

    private func spansOverlap(_ blocks: [ReadinessPlanBlock], idA: String, idB: String) -> Bool {
        guard let a = blocks.first(where: { $0.id == idA }), let b = blocks.first(where: { $0.id == idB }) else { return false }
        return a.startMinute < b.endMinute && b.startMinute < a.endMinute
    }

    // MARK: parseSmartFill — deterministic, strips recognized tokens from the title

    func testSmartFillIsDeterministicAndCleansTitle() {
        // Fixed reference instant so the parser is reproducible (it must never read the clock).
        let now = Date(timeIntervalSinceReferenceDate: 800_000_000)
        let a = parseSmartFill("Email the team urgent", now: now)
        let b = parseSmartFill("Email the team urgent", now: now)
        XCTAssertEqual(a, b, "parseSmartFill must be a pure function of (text, now)")
        XCTAssertEqual(a.priority, .high, "‘urgent’ should map to high priority")
        XCTAssertFalse(a.cleanedTitle.lowercased().contains("urgent"),
                       "recognized priority token should be stripped from the title")
        XCTAssertFalse(a.cleanedTitle.isEmpty)
        XCTAssertTrue(a.hasDetection)
        // No recognized tokens → title is preserved, nothing detected.
        let plain = parseSmartFill("Buy milk", now: now)
        XCTAssertEqual(plain.cleanedTitle, "Buy milk")
        XCTAssertFalse(plain.hasDetection)
    }

    // MARK: freeTimeSegments — never overlaps busy spans, respects minDuration

    func testFreeSegmentsNeverOverlapBusyAndRespectMinDuration() {
        let busy = [
            DialArcSegment(startMinute: 9 * 60, durationMinutes: 60),   // 09:00–10:00
            DialArcSegment(startMinute: 13 * 60, durationMinutes: 90),  // 13:00–14:30
        ]
        let free = freeTimeSegments(busy: busy, minDuration: 30)
        for f in free {
            let fStart = f.startMinute, fEnd = f.startMinute + f.durationMinutes
            XCTAssertGreaterThanOrEqual(f.durationMinutes, 30, "free sliver below minDuration")
            for b in busy {
                let bStart = b.startMinute, bEnd = b.startMinute + b.durationMinutes
                XCTAssertFalse(fStart < bEnd && bStart < fEnd,
                               "free \(fStart)-\(fEnd) overlaps busy \(bStart)-\(bEnd)")
            }
        }
    }
}
