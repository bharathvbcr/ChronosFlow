import XCTest
@testable import ChronosCore

// Covers the instance-ID / midnight-split / recurrence / all-day / sync-window / last-synced-label
// semantics ported from CalendarEventRepositoryImpl.kt, CalendarBackgroundSync.kt,
// CalendarImportSemantics.kt and RelativeTimeFormat.kt. Mirrors the Android tests
// CalendarImportSemanticsPlannerTest, CalendarBackgroundSyncWorkerTest.

private let utcCal: Calendar = {
    var c = Calendar(identifier: .gregorian)
    c.timeZone = TimeZone(identifier: "UTC")!
    return c
}()

private func at(_ y: Int, _ mo: Int, _ d: Int, _ h: Int = 0, _ mi: Int = 0) -> Date {
    utcCal.date(from: DateComponents(year: y, month: mo, day: d, hour: h, minute: mi))!
}
private func day(_ y: Int, _ mo: Int, _ d: Int) -> Date {
    utcCal.date(from: DateComponents(year: y, month: mo, day: d))!
}

// MARK: - Instance IDs

final class CalendarInstanceIDTests: XCTestCase {
    func testTimedInstanceIdFormat() {
        let id = instanceUniqueId(eventId: "42", date: day(2025, 1, 5), startMinute: 540, calendar: utcCal)
        XCTAssertEqual(id, "calendar-import-42-2025-01-05-540")
    }

    func testAllDayInstanceIdHasNoStartMinute() {
        let id = allDayInstanceUniqueId(eventId: "42", date: day(2025, 1, 5), calendar: utcCal)
        XCTAssertEqual(id, "calendar-import-42-2025-01-05")
    }

    // Same-day recurring instances share the device EVENT_ID but differ by start minute.
    func testSameDayRecurringInstancesGetDistinctIds() {
        let a = instanceUniqueId(eventId: "7", date: day(2025, 3, 1), startMinute: 540, calendar: utcCal)
        let b = instanceUniqueId(eventId: "7", date: day(2025, 3, 1), startMinute: 600, calendar: utcCal)
        XCTAssertNotEqual(a, b)
    }

    // Recurring instances on different days share the EVENT_ID but differ by date.
    func testRecurringInstancesAcrossDaysGetDistinctIds() {
        let a = instanceUniqueId(eventId: "7", date: day(2025, 3, 1), startMinute: 540, calendar: utcCal)
        let b = instanceUniqueId(eventId: "7", date: day(2025, 3, 2), startMinute: 540, calendar: utcCal)
        XCTAssertNotEqual(a, b)
    }
}

// MARK: - Provenance guards

final class CalendarProvenanceGuardTests: XCTestCase {
    func testIsImportedCalendarBlockCaseInsensitive() {
        XCTAssertTrue(isImportedCalendarBlock(provenance: "calendar"))
        XCTAssertTrue(isImportedCalendarBlock(provenance: "CALENDAR"))
        XCTAssertTrue(isImportedCalendarBlock(provenance: "Calendar"))
        XCTAssertFalse(isImportedCalendarBlock(provenance: "manual"))
        XCTAssertFalse(isImportedCalendarBlock(provenance: "task"))
    }
}

// MARK: - Midnight split

final class CalendarMidnightSplitTests: XCTestCase {
    func testSameDayEventStaysOneBlock() {
        let occ = CalendarOccurrence(eventID: "1", title: "Standup", start: at(2025, 1, 6, 9, 0),
                                     end: at(2025, 1, 6, 9, 30), timezone: "UTC")
        let blocks = splitTimedOccurrence(occ, windowStart: day(2025, 1, 6),
                                          windowEnd: day(2025, 1, 13), calendar: utcCal)
        XCTAssertEqual(blocks.count, 1)
        XCTAssertEqual(blocks[0].startMinute, 540)
        XCTAssertEqual(blocks[0].durationMinutes, 30)
        XCTAssertEqual(blocks[0].id, "calendar-import-1-2025-01-06-540")
        XCTAssertEqual(blocks[0].category, calendarEventCategory)
        XCTAssertEqual(blocks[0].flexibility, .fixed)
        XCTAssertTrue(blocks[0].isLocked)
        XCTAssertTrue(blocks[0].isProtected)
    }

    func testCrossMidnightEventSplitsPerDay() {
        // 23:00 -> next day 02:00 spans two local days.
        let occ = CalendarOccurrence(eventID: "9", title: "Red-eye", start: at(2025, 1, 6, 23, 0),
                                     end: at(2025, 1, 7, 2, 0), timezone: "UTC")
        let blocks = splitTimedOccurrence(occ, windowStart: day(2025, 1, 6),
                                          windowEnd: day(2025, 1, 13), calendar: utcCal)
        XCTAssertEqual(blocks.count, 2)
        // Day 1 segment: 23:00 -> midnight = 60 min at minute 1380.
        XCTAssertEqual(blocks[0].date, day(2025, 1, 6))
        XCTAssertEqual(blocks[0].startMinute, 1380)
        XCTAssertEqual(blocks[0].durationMinutes, 60)
        // Day 2 segment: midnight -> 02:00 = 120 min at minute 0.
        XCTAssertEqual(blocks[1].date, day(2025, 1, 7))
        XCTAssertEqual(blocks[1].startMinute, 0)
        XCTAssertEqual(blocks[1].durationMinutes, 120)
        // Both share the device EVENT_ID but have distinct instance ids.
        XCTAssertEqual(blocks[0].eventID, "9")
        XCTAssertEqual(blocks[1].eventID, "9")
        XCTAssertNotEqual(blocks[0].id, blocks[1].id)
    }

    func testEventClippedToWindow() {
        // Event starts before window; only the in-window segment survives.
        let occ = CalendarOccurrence(eventID: "3", title: "Before", start: at(2025, 1, 5, 22, 0),
                                     end: at(2025, 1, 6, 1, 0), timezone: "UTC")
        let blocks = splitTimedOccurrence(occ, windowStart: day(2025, 1, 6),
                                          windowEnd: day(2025, 1, 13), calendar: utcCal)
        XCTAssertEqual(blocks.count, 1)
        XCTAssertEqual(blocks[0].date, day(2025, 1, 6))
        XCTAssertEqual(blocks[0].startMinute, 0)
        XCTAssertEqual(blocks[0].durationMinutes, 60)
    }

    func testDurationCoercedToAtLeastOne() {
        let occ = CalendarOccurrence(eventID: "z", title: "Zero", start: at(2025, 1, 6, 9, 0),
                                     end: at(2025, 1, 6, 9, 0), timezone: "UTC")
        // Zero-length event -> clippedStart == clippedEnd -> nothing produced.
        let blocks = splitTimedOccurrence(occ, windowStart: day(2025, 1, 6),
                                          windowEnd: day(2025, 1, 13), calendar: utcCal)
        XCTAssertTrue(blocks.isEmpty)
    }
}

// MARK: - Recurrence expansion into occurrences

final class CalendarRecurrenceExpansionTests: XCTestCase {
    func testDailyRecurrenceExpandsAcrossWindow() {
        let rule = RecurrenceRule(frequency: .daily, interval: 1)
        let occ = expandOccurrences(eventID: "5", title: "Daily standup",
                                    firstStart: at(2025, 6, 9, 9, 0), durationMinutes: 30,
                                    timezone: "UTC", rule: rule,
                                    windowStart: day(2025, 6, 9), windowEnd: day(2025, 6, 12),
                                    calendar: utcCal)
        XCTAssertEqual(occ.count, 3) // 9th, 10th, 11th (12th is exclusive window end)
        XCTAssertEqual(occ[0].start, at(2025, 6, 9, 9, 0))
        XCTAssertEqual(occ[2].start, at(2025, 6, 11, 9, 0))
        // Each preserves the original duration.
        for o in occ { XCTAssertEqual(o.end.timeIntervalSince(o.start), 30 * 60) }
    }

    func testRecurrenceTimeOfDayPreserved() {
        let rule = RecurrenceRule(frequency: .daily, interval: 1)
        let occ = expandOccurrences(eventID: "6", title: "Lunch",
                                    firstStart: at(2025, 6, 9, 12, 30), durationMinutes: 60,
                                    timezone: "UTC", rule: rule,
                                    windowStart: day(2025, 6, 9), windowEnd: day(2025, 6, 11),
                                    calendar: utcCal)
        for o in occ {
            let c = utcCal.dateComponents([.hour, .minute], from: o.start)
            XCTAssertEqual(c.hour, 12)
            XCTAssertEqual(c.minute, 30)
        }
    }

    func testNilRuleYieldsSingleOccurrenceInWindow() {
        let occ = expandOccurrences(eventID: "8", title: "One-off",
                                    firstStart: at(2025, 6, 9, 14, 0), durationMinutes: 45,
                                    timezone: "UTC", rule: nil,
                                    windowStart: day(2025, 6, 9), windowEnd: day(2025, 6, 16),
                                    calendar: utcCal)
        XCTAssertEqual(occ.count, 1)
        XCTAssertEqual(occ[0].start, at(2025, 6, 9, 14, 0))
    }

    func testNilRuleOutsideWindowYieldsNothing() {
        let occ = expandOccurrences(eventID: "8", title: "Past",
                                    firstStart: at(2025, 5, 1, 14, 0), durationMinutes: 45,
                                    timezone: "UTC", rule: nil,
                                    windowStart: day(2025, 6, 9), windowEnd: day(2025, 6, 16),
                                    calendar: utcCal)
        XCTAssertTrue(occ.isEmpty)
    }

    func testWeeklyRecurrenceExpandsAndSplitsToBlocks() {
        // Jun 9 2025 is a Monday (weekday 2). Weekly Mon/Wed for two weeks.
        let rule = RecurrenceRule(frequency: .weekly, interval: 1, weekdays: [2, 4])
        let occ = expandOccurrences(eventID: "w", title: "Sync",
                                    firstStart: at(2025, 6, 9, 10, 0), durationMinutes: 30,
                                    timezone: "UTC", rule: rule,
                                    windowStart: day(2025, 6, 9), windowEnd: day(2025, 6, 23),
                                    calendar: utcCal)
        // Mon 9, Wed 11, Mon 16, Wed 18 (and Mon 23 is the exclusive end -> excluded? 23 < end? end=23 exclusive)
        let blocks = occ.flatMap { splitTimedOccurrence($0, windowStart: day(2025, 6, 9),
                                                         windowEnd: day(2025, 6, 23), calendar: utcCal) }
        // All instance ids must be unique (composite-key uniqueness invariant).
        XCTAssertEqual(Set(blocks.map(\.id)).count, blocks.count)
        XCTAssertTrue(blocks.count >= 4)
    }
}

// MARK: - All-day markers

final class CalendarAllDayMarkerTests: XCTestCase {
    func testAllDayLastDateAccountsForExclusiveEnd() {
        // All-day Jun 10 -> Jun 11(exclusive end) is a single day: Jun 10.
        let last = allDayEventLastDate(start: day(2025, 6, 10), exclusiveEnd: day(2025, 6, 11), calendar: utcCal)
        XCTAssertEqual(last, day(2025, 6, 10))
    }

    func testAllDayLastDateNeverBeforeStart() {
        let last = allDayEventLastDate(start: day(2025, 6, 10), exclusiveEnd: day(2025, 6, 10), calendar: utcCal)
        XCTAssertEqual(last, day(2025, 6, 10))
    }

    func testSingleDayAllDayMakesOneMarker() {
        let occ = CalendarOccurrence(eventID: "h", title: "Holiday", start: day(2025, 6, 10),
                                     end: day(2025, 6, 11), timezone: "UTC", isAllDay: true)
        let markers = allDayContextMarkers(occ, windowStart: day(2025, 6, 9),
                                           windowEnd: day(2025, 6, 16), calendar: utcCal)
        XCTAssertEqual(markers.count, 1)
        let m = markers[0]
        XCTAssertEqual(m.id, "calendar-import-h-2025-06-10")
        XCTAssertEqual(m.startMinute, 0)
        XCTAssertEqual(m.durationMinutes, allDayCalendarMarkerMinutes)
        XCTAssertEqual(m.category, allDayCalendarEventCategory)
        XCTAssertEqual(m.flexibility, .optional)
        XCTAssertEqual(m.energy, .low)
        XCTAssertFalse(m.isLocked)
        XCTAssertFalse(m.isProtected)
        XCTAssertTrue(m.isAllDay)
        XCTAssertTrue(isAllDayCalendarImport(m))
        XCTAssertFalse(occupiesScheduleTime(m))
    }

    func testMultiDayAllDayMakesOneMarkerPerDay() {
        // Vacation Jun 10 -> Jun 13(exclusive) covers Jun 10, 11, 12.
        let occ = CalendarOccurrence(eventID: "v", title: "Vacation", start: day(2025, 6, 10),
                                     end: day(2025, 6, 13), timezone: "UTC", isAllDay: true)
        let markers = allDayContextMarkers(occ, windowStart: day(2025, 6, 9),
                                           windowEnd: day(2025, 6, 16), calendar: utcCal)
        XCTAssertEqual(markers.count, 3)
        XCTAssertEqual(markers.map(\.date), [day(2025, 6, 10), day(2025, 6, 11), day(2025, 6, 12)])
        XCTAssertEqual(Set(markers.map(\.id)).count, 3)
    }

    func testAllDayClippedToSyncWindow() {
        // Vacation spans before+through the window; only in-window days produce markers.
        let occ = CalendarOccurrence(eventID: "v", title: "Vacation", start: day(2025, 6, 5),
                                     end: day(2025, 6, 20), timezone: "UTC", isAllDay: true)
        let markers = allDayContextMarkers(occ, windowStart: day(2025, 6, 9),
                                           windowEnd: day(2025, 6, 12), calendar: utcCal)
        // window covers Jun 9, 10, 11 (12 exclusive).
        XCTAssertEqual(markers.map(\.date), [day(2025, 6, 9), day(2025, 6, 10), day(2025, 6, 11)])
    }

    func testImportedBlocksRoutesAllDayVsTimed() {
        let allDay = CalendarOccurrence(eventID: "h", title: "Holiday", start: day(2025, 6, 10),
                                        end: day(2025, 6, 11), timezone: "UTC", isAllDay: true)
        let timed = CalendarOccurrence(eventID: "m", title: "Meeting", start: at(2025, 6, 10, 9, 0),
                                       end: at(2025, 6, 10, 10, 0), timezone: "UTC")
        let a = importedBlocks(for: allDay, windowStart: day(2025, 6, 9),
                               windowEnd: day(2025, 6, 16), calendar: utcCal)
        let t = importedBlocks(for: timed, windowStart: day(2025, 6, 9),
                               windowEnd: day(2025, 6, 16), calendar: utcCal)
        XCTAssertTrue(a.allSatisfy { $0.isAllDay })
        XCTAssertTrue(t.allSatisfy { !$0.isAllDay })
    }
}

// MARK: - isAllDayCalendarImport heuristic

final class CalendarAllDayHeuristicTests: XCTestCase {
    func testFullDayDurationCountsAsAllDayMarker() {
        // Even a CALENDAR-category block counts as all-day if >= 1440 min.
        let block = ImportedCalendarBlock(
            id: "x", eventID: "x", date: day(2025, 6, 10), title: "Long",
            category: calendarEventCategory, startMinute: 0, durationMinutes: 1440,
            timezone: "UTC", provenance: calendarImportedProvenance,
            flexibility: .fixed, energy: .moderate, isAllDay: false,
            isLocked: true, isProtected: true
        )
        XCTAssertTrue(isAllDayCalendarImport(block))
    }

    func testTimedCommitmentIsNotAllDay() {
        let block = ImportedCalendarBlock(
            id: "x", eventID: "x", date: day(2025, 6, 10), title: "Mtg",
            category: calendarEventCategory, startMinute: 540, durationMinutes: 60,
            timezone: "UTC", provenance: calendarImportedProvenance,
            flexibility: .fixed, energy: .moderate, isAllDay: false,
            isLocked: true, isProtected: true
        )
        XCTAssertFalse(isAllDayCalendarImport(block))
        XCTAssertTrue(occupiesScheduleTime(block))
    }
}

// MARK: - Energy classification

final class CalendarEnergyClassifierTests: XCTestCase {
    func testLowEnergyKeywordsWin() {
        XCTAssertEqual(classifyImportedEventEnergy(title: "Team Lunch"), .low)
        XCTAssertEqual(classifyImportedEventEnergy(title: "Vacation in Bali"), .low)
    }

    func testHighEnergyKeywords() {
        XCTAssertEqual(classifyImportedEventEnergy(title: "Job Interview"), .high)
        XCTAssertEqual(classifyImportedEventEnergy(title: "Quarterly Review"), .high)
    }

    func testLowBeatsHighWhenBothPresent() {
        // "lunch meeting" contains both; LOW is checked first (matches Android).
        XCTAssertEqual(classifyImportedEventEnergy(title: "Lunch meeting"), .low)
    }

    func testDefaultsToModerate() {
        XCTAssertEqual(classifyImportedEventEnergy(title: "Dentist"), .moderate)
    }

    func testDescriptionContributes() {
        XCTAssertEqual(classifyImportedEventEnergy(title: "Block", description: "standup with team"), .high)
    }
}

// MARK: - Sync window

final class CalendarSyncWindowTests: XCTestCase {
    func testSyncWindowIsSevenLocalDays() {
        let (start, end) = syncWindow(now: at(2025, 6, 9, 14, 30), calendar: utcCal)
        XCTAssertEqual(start, day(2025, 6, 9))         // floored to start of day
        XCTAssertEqual(end, day(2025, 6, 16))          // +7 days
        XCTAssertEqual(end.timeIntervalSince(start), 7 * 86400)
    }

    func testSyncWindowConstants() {
        XCTAssertEqual(calendarSyncWindowDays, 7)
        XCTAssertEqual(calendarSyncIntervalHours, 6)
    }
}

// MARK: - Last-synced label

final class CalendarLastSyncedLabelTests: XCTestCase {
    private let now = at(2025, 6, 9, 12, 0)

    func testNeverSynced() {
        XCTAssertEqual(formatLastSyncedLabel(lastSyncAt: nil, now: now), "Not synced yet")
    }

    func testJustNow() {
        XCTAssertEqual(formatLastSyncedLabel(lastSyncAt: now.addingTimeInterval(-30), now: now), "Synced just now")
    }

    func testClockSkewIsJustNow() {
        XCTAssertEqual(formatLastSyncedLabel(lastSyncAt: now.addingTimeInterval(60), now: now), "Synced just now")
    }

    func testMinutesAgo() {
        XCTAssertEqual(formatLastSyncedLabel(lastSyncAt: now.addingTimeInterval(-5 * 60), now: now), "Synced 5 min ago")
        XCTAssertEqual(formatLastSyncedLabel(lastSyncAt: now.addingTimeInterval(-59 * 60), now: now), "Synced 59 min ago")
    }

    func testHoursAgo() {
        XCTAssertEqual(formatLastSyncedLabel(lastSyncAt: now.addingTimeInterval(-2 * 3600), now: now), "Synced 2 hr ago")
        XCTAssertEqual(formatLastSyncedLabel(lastSyncAt: now.addingTimeInterval(-23 * 3600), now: now), "Synced 23 hr ago")
    }

    func testYesterday() {
        XCTAssertEqual(formatLastSyncedLabel(lastSyncAt: now.addingTimeInterval(-25 * 3600), now: now), "Synced yesterday")
    }

    func testDaysAgo() {
        XCTAssertEqual(formatLastSyncedLabel(lastSyncAt: now.addingTimeInterval(-3 * 86400), now: now), "Synced 3 days ago")
    }

    func testBoundaryExactlyOneHour() {
        XCTAssertEqual(formatLastSyncedLabel(lastSyncAt: now.addingTimeInterval(-60 * 60), now: now), "Synced 1 hr ago")
    }
}
