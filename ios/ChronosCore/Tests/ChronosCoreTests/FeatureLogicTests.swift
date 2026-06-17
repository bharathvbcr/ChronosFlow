import XCTest
@testable import ChronosCore

final class GoalProgressTests: XCTestCase {
    func testDerivedCountsAddAndCap() {
        let derived = GoalDerivedProgress(completedTaskCount: 3, habitCompletionCount: 2)
        XCTAssertEqual(deriveGoalProgress(progressValue: 4, targetValue: 12, derived: derived), 9)
        // caps at target
        XCTAssertEqual(deriveGoalProgress(progressValue: 10, targetValue: 12, derived: derived), 12)
    }

    func testFraction() {
        XCTAssertEqual(goalProgressFraction(progressValue: 6, targetValue: 12, isCompleted: false), 0.5, accuracy: 0.0001)
        XCTAssertEqual(goalProgressFraction(progressValue: 0, targetValue: 0, isCompleted: true), 1.0, accuracy: 0.0001)
        XCTAssertEqual(goalProgressFraction(progressValue: 99, targetValue: 10, isCompleted: false), 1.0, accuracy: 0.0001)
    }
}

final class PomodoroTests: XCTestCase {
    func testShortBreakThenLongBreak() {
        let cfg = PomodoroConfig(sessionsBeforeLongBreak: 4)
        XCTAssertEqual(nextPomodoroPhase(current: .work, completedWorkSessions: 0, config: cfg), .shortBreak)
        XCTAssertEqual(nextPomodoroPhase(current: .work, completedWorkSessions: 1, config: cfg), .shortBreak)
        XCTAssertEqual(nextPomodoroPhase(current: .work, completedWorkSessions: 2, config: cfg), .shortBreak)
        // 4th completed work session → long break
        XCTAssertEqual(nextPomodoroPhase(current: .work, completedWorkSessions: 3, config: cfg), .longBreak)
        XCTAssertEqual(nextPomodoroPhase(current: .shortBreak, completedWorkSessions: 1, config: cfg), .work)
        XCTAssertEqual(nextPomodoroPhase(current: .longBreak, completedWorkSessions: 4, config: cfg), .work)
    }

    func testCycleEndsWithLongBreak() {
        let cycle = pomodoroCycle(config: PomodoroConfig(sessionsBeforeLongBreak: 4))
        XCTAssertEqual(cycle.last, .longBreak)
        XCTAssertEqual(cycle.filter { $0 == .work }.count, 4)
    }

    func testDurations() {
        let cfg = PomodoroConfig(workMinutes: 50, shortBreakMinutes: 10, longBreakMinutes: 30)
        XCTAssertEqual(cfg.durationMinutes(for: .work), 50)
        XCTAssertEqual(cfg.durationMinutes(for: .shortBreak), 10)
        XCTAssertEqual(cfg.durationMinutes(for: .longBreak), 30)
    }
}

final class RoutineTests: XCTestCase {
    func testInstantiateAtStart() {
        let steps = [
            RoutineStepSpec(title: "Wake", offsetMinute: 0, durationMinutes: 10),
            RoutineStepSpec(title: "Stretch", offsetMinute: 10, durationMinutes: 15),
            RoutineStepSpec(title: "Shower", offsetMinute: 25, durationMinutes: 20),
        ]
        let blocks = instantiateRoutine(steps: steps, startMinute: 6 * 60) // 06:00
        XCTAssertEqual(blocks.map(\.startMinuteOfDay), [360, 370, 385])
        XCTAssertEqual(blocks[1].title, "Stretch")
    }

    func testWrapsPastMidnight() {
        let steps = [RoutineStepSpec(title: "Late", offsetMinute: 120, durationMinutes: 30)]
        let blocks = instantiateRoutine(steps: steps, startMinute: 23 * 60) // 23:00 + 120 = 25:00 -> 01:00
        XCTAssertEqual(blocks[0].startMinuteOfDay, 60)
    }
}
