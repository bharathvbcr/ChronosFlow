import XCTest
@testable import ChronosCore

private let utcCal: Calendar = {
    var c = Calendar(identifier: .gregorian)
    c.timeZone = TimeZone(identifier: "UTC")!
    return c
}()

private func day(_ y: Int, _ m: Int, _ d: Int) -> Date {
    utcCal.date(from: DateComponents(year: y, month: m, day: d))!
}

/// A day with a non-midnight time component, to prove the helpers ignore time-of-day.
private func dayAt(_ y: Int, _ m: Int, _ d: Int, hour: Int, minute: Int) -> Date {
    utcCal.date(from: DateComponents(year: y, month: m, day: d, hour: hour, minute: minute))!
}

final class GoalLabelsCategoryTests: XCTestCase {

    func testSuggestsCategoryFromTitleKeywords() {
        XCTAssertEqual(GoalLabels.suggestGoalCategory("Run a 10k"), "Health")
        XCTAssertEqual(GoalLabels.suggestGoalCategory("Read 12 books"), "Learning")
        XCTAssertEqual(GoalLabels.suggestGoalCategory("Save 5000 for a trip"), "Finance")
        XCTAssertEqual(GoalLabels.suggestGoalCategory("Launch the new project"), "Work")
        XCTAssertNil(GoalLabels.suggestGoalCategory("Plan the team offsite"))
    }

    func testSuggestionIsCaseInsensitive() {
        XCTAssertEqual(GoalLabels.suggestGoalCategory("GO TO THE GYM"), "Health")
    }

    func testSuggestionPrefersMostSpecificEarlierRule() {
        // "study" (Learning) appears before any later rule; "daily" (Habits) is later.
        XCTAssertEqual(GoalLabels.suggestGoalCategory("study daily"), "Learning")
    }

    func testCategoriesInUseAreDistinctAndSorted() {
        XCTAssertEqual(
            GoalLabels.categoriesInUse(["Work", "Health", "Work"]),
            ["Health", "Work"]
        )
    }

    func testCategoriesInUseEmpty() {
        XCTAssertEqual(GoalLabels.categoriesInUse([]), [])
    }

    func testCategoryOptionsMergePresetsExistingAndCustom() {
        let presets = ["Personal", "Health"]

        // Nothing custom, no existing -> just the presets.
        XCTAssertEqual(
            GoalLabels.categoryOptions(presets: presets, existingCategory: nil, customCategory: ""),
            presets
        )

        // A typed custom value is trimmed and appended once.
        XCTAssertEqual(
            GoalLabels.categoryOptions(presets: presets, existingCategory: nil, customCategory: "  Travel  "),
            ["Personal", "Health", "Travel"]
        )

        // A custom value that duplicates a preset does not double up.
        XCTAssertEqual(
            GoalLabels.categoryOptions(presets: presets, existingCategory: nil, customCategory: "Health"),
            presets
        )

        // The edited goal's own off-list category is preserved.
        XCTAssertEqual(
            GoalLabels.categoryOptions(presets: presets, existingCategory: "Finance", customCategory: ""),
            ["Personal", "Health", "Finance"]
        )
    }

    func testCategoryOptionsBlankCustomIsIgnored() {
        XCTAssertEqual(
            GoalLabels.categoryOptions(presets: ["Personal"], existingCategory: nil, customCategory: "   "),
            ["Personal"]
        )
    }

    func testCategoryOptionDefaultsMatchAndroidOrdering() {
        XCTAssertEqual(GoalLabels.categoryOptions.first, "Personal")
        XCTAssertEqual(GoalLabels.defaultCategory, "Personal")
        XCTAssertEqual(
            GoalLabels.categoryOptions,
            ["Personal", "Health", "Work", "Learning", "Finance", "Habits"]
        )
    }
}

final class GoalLabelsTargetTests: XCTestCase {

    func testTargetExtractedFromFirstUsableNumber() {
        XCTAssertEqual(GoalLabels.goalTargetFromTitle("Read 12 books this year"), 12)
        XCTAssertEqual(GoalLabels.goalTargetFromTitle("Run 100 miles"), 100)
    }

    func testTargetNilWhenNoUsableNumber() {
        XCTAssertNil(GoalLabels.goalTargetFromTitle("Meditate daily"))
        // "0" is below the 1...99999 range, so it yields nothing.
        XCTAssertNil(GoalLabels.goalTargetFromTitle("Goal 0"))
    }

    func testTargetSkipsOutOfRangeThenTakesFirstUsable() {
        // First token "0" is out of range; second token "5" is usable.
        XCTAssertEqual(GoalLabels.goalTargetFromTitle("0 then 5 books"), 5)
        // 100000 is out of range and there is nothing else usable.
        XCTAssertNil(GoalLabels.goalTargetFromTitle("Do 100000 reps"))
    }

    func testTargetHandlesTrailingNumber() {
        XCTAssertEqual(GoalLabels.goalTargetFromTitle("books 7"), 7)
    }

    func testTargetQuickPicksAreAscendingAndUnique() {
        XCTAssertEqual(GoalLabels.targetQuickPicks, GoalLabels.targetQuickPicks.sorted())
        XCTAssertEqual(Set(GoalLabels.targetQuickPicks).count, GoalLabels.targetQuickPicks.count)
        XCTAssertEqual(GoalLabels.targetQuickPicks, [5, 10, 12, 30, 50, 100])
    }
}

final class GoalLabelsCadenceTests: XCTestCase {

    func testCadenceLabelTitleCasesToken() {
        XCTAssertEqual(GoalLabels.cadenceLabel("DAILY"), "Daily")
        XCTAssertEqual(GoalLabels.cadenceLabel("  weekly  "), "Weekly")
        XCTAssertEqual(GoalLabels.cadenceLabel("Monthly"), "Monthly")
    }

    func testCadenceLabelBlankFallsBackToHabit() {
        XCTAssertEqual(GoalLabels.cadenceLabel(""), "Habit")
        XCTAssertEqual(GoalLabels.cadenceLabel("   "), "Habit")
    }
}

final class GoalLabelsLinkedWorkTests: XCTestCase {

    func testLinkedWorkLabelSumsOrNil() {
        XCTAssertNil(GoalLabels.goalLinkedWorkLabel(GoalDerivedProgress()))
        XCTAssertEqual(
            GoalLabels.goalLinkedWorkLabel(GoalDerivedProgress(completedTaskCount: 2, habitCompletionCount: 3)),
            "+5 from linked work"
        )
    }

    func testLinkedWorkLabelNilForZeroTotal() {
        XCTAssertNil(GoalLabels.goalLinkedWorkLabel(GoalDerivedProgress(completedTaskCount: 0, habitCompletionCount: 0)))
    }
}

final class GoalLabelsDueLabelTests: XCTestCase {

    private let today = day(2026, 6, 12)

    func testDueLabelNilWithoutTargetOrWhenCompleted() {
        XCTAssertNil(GoalLabels.goalDueLabel(targetDate: nil, isCompleted: false, today: today, calendar: utcCal))
        XCTAssertNil(GoalLabels.goalDueLabel(targetDate: today, isCompleted: true, today: today, calendar: utcCal))
    }

    func testDueLabelEmphasizesOverdueTodayAndNearDeadlines() {
        XCTAssertEqual(
            GoalLabels.goalDueLabel(targetDate: day(2026, 6, 11), isCompleted: false, today: today, calendar: utcCal),
            GoalDueLabel(text: "Overdue", emphasized: true)
        )
        XCTAssertEqual(
            GoalLabels.goalDueLabel(targetDate: today, isCompleted: false, today: today, calendar: utcCal),
            GoalDueLabel(text: "Due today", emphasized: true)
        )
        XCTAssertEqual(
            GoalLabels.goalDueLabel(targetDate: day(2026, 6, 15), isCompleted: false, today: today, calendar: utcCal),
            GoalDueLabel(text: "Due in 3d", emphasized: true)
        )
    }

    func testDueLabelSevenDaysIsStillEmphasized() {
        XCTAssertEqual(
            GoalLabels.goalDueLabel(targetDate: day(2026, 6, 19), isCompleted: false, today: today, calendar: utcCal),
            GoalDueLabel(text: "Due in 7d", emphasized: true)
        )
    }

    func testFarOffDeadlineShownWithoutEmphasisAsIsoDate() {
        XCTAssertEqual(
            GoalLabels.goalDueLabel(targetDate: day(2026, 7, 12), isCompleted: false, today: today, calendar: utcCal),
            GoalDueLabel(text: "Due 2026-07-12", emphasized: false)
        )
    }

    func testDueLabelIgnoresTimeOfDay() {
        // A target later the same calendar day still reads "Due today".
        XCTAssertEqual(
            GoalLabels.goalDueLabel(
                targetDate: dayAt(2026, 6, 12, hour: 23, minute: 59),
                isCompleted: false, today: dayAt(2026, 6, 12, hour: 0, minute: 1), calendar: utcCal
            ),
            GoalDueLabel(text: "Due today", emphasized: true)
        )
    }
}

final class GoalLabelsOverdueTests: XCTestCase {

    private let today = day(2026, 6, 13)

    func testOverdueCountIncludesOnlyActiveGoalsPastTargetDate() {
        let entries: [(targetDate: Date?, isCompleted: Bool)] = [
            (day(2026, 6, 12), false),                 // past + active -> counts
            (day(2026, 6, 13), false),                 // due today, not before today
            (day(2026, 6, 18), false),                 // future
            (nil, false),                              // no target date
            (day(2026, 6, 10), true),                  // completed, excluded
        ]
        XCTAssertEqual(GoalLabels.goalsOverdueCount(targetDates: entries, today: today, calendar: utcCal), 1)
    }

    func testOverdueCountZeroForEmpty() {
        XCTAssertEqual(GoalLabels.goalsOverdueCount(targetDates: [], today: today, calendar: utcCal), 0)
    }

    func testOverdueMessageSingularAndPlural() {
        XCTAssertEqual(GoalLabels.goalsOverdueMessage(1), "1 active goal is past its target date.")
        XCTAssertEqual(GoalLabels.goalsOverdueMessage(3), "3 active goals are past their target date.")
    }
}

final class GoalLabelsDeadlinePresetTests: XCTestCase {

    func testDeadlinePresetsRelativeToTodayAndEndOfYearIsDec31() {
        let today = day(2026, 6, 12)
        let presets = GoalLabels.goalDeadlinePresets(today: today, calendar: utcCal)

        XCTAssertEqual(presets.map { $0.label }, ["1 month", "3 months", "6 months", "End of year"])
        XCTAssertEqual(presets[0].date, day(2026, 7, 12))
        XCTAssertEqual(presets[1].date, day(2026, 9, 12))
        XCTAssertEqual(presets[2].date, day(2026, 12, 12))
        XCTAssertEqual(presets[3].date, day(2026, 12, 31))
    }

    func testDeadlinePresetsNormalizeToStartOfDay() {
        let presets = GoalLabels.goalDeadlinePresets(
            today: dayAt(2026, 3, 5, hour: 14, minute: 30), calendar: utcCal
        )
        XCTAssertEqual(presets[0].date, day(2026, 4, 5))
        XCTAssertEqual(presets[3].date, day(2026, 12, 31))
    }
}

final class GoalLabelsWarningTests: XCTestCase {

    private let today = day(2026, 6, 12)

    func testTargetBelowProgressWarningFiresOnlyWhenTargetUnderProgress() {
        XCTAssertNil(GoalLabels.goalTargetBelowProgressWarning(target: 10, progress: 3))
        XCTAssertNil(GoalLabels.goalTargetBelowProgressWarning(target: 10, progress: 10))
        XCTAssertNil(GoalLabels.goalTargetBelowProgressWarning(target: 10, progress: 0))
        XCTAssertEqual(
            GoalLabels.goalTargetBelowProgressWarning(target: 5, progress: 7),
            "Target is below your logged progress (7)."
        )
    }

    func testTargetBelowProgressWarningNilForZeroOrNegativeTarget() {
        // A non-positive target (1..<progress excludes 0) never warns, matching Android.
        XCTAssertNil(GoalLabels.goalTargetBelowProgressWarning(target: 0, progress: 7))
    }

    func testTargetDateWarningFiresOnlyForPastDates() {
        XCTAssertNil(GoalLabels.goalTargetDateWarning(targetDate: nil, today: today, calendar: utcCal))
        XCTAssertNil(GoalLabels.goalTargetDateWarning(targetDate: today, today: today, calendar: utcCal))
        XCTAssertNil(GoalLabels.goalTargetDateWarning(targetDate: day(2026, 6, 13), today: today, calendar: utcCal))
        XCTAssertEqual(
            GoalLabels.goalTargetDateWarning(targetDate: day(2026, 6, 11), today: today, calendar: utcCal),
            "This date is in the past — the goal will show as overdue."
        )
    }

    func testTargetDateWarningIgnoresTimeOfDay() {
        // Earlier time on the same calendar day is not "past".
        XCTAssertNil(
            GoalLabels.goalTargetDateWarning(
                targetDate: dayAt(2026, 6, 12, hour: 0, minute: 1),
                today: dayAt(2026, 6, 12, hour: 23, minute: 59), calendar: utcCal
            )
        )
    }
}
