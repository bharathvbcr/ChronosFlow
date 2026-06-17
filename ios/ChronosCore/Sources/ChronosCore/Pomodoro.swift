import Foundation

// Pomodoro phase sequencing — the legacy INFINITE-LOOP cycle logic (work/break splits; a long break
// every N work sessions). The live FocusTimerModel no longer drives from this: it now uses the
// BLOCK-BOUNDED `planFocusPhases` in FocusPhasePlan.swift, which fills the selected block's finite
// duration and holds at each boundary instead of looping forever. These helpers are retained for the
// existing cycle-preview tests; prefer `planFocusPhases` for new focus work.

public enum PomodoroPhase: String, Sendable, Equatable {
    case work, shortBreak, longBreak
}

public struct PomodoroConfig: Sendable, Equatable {
    public var workMinutes: Int
    public var shortBreakMinutes: Int
    public var longBreakMinutes: Int
    public var sessionsBeforeLongBreak: Int

    public init(workMinutes: Int = 25, shortBreakMinutes: Int = 5,
                longBreakMinutes: Int = 15, sessionsBeforeLongBreak: Int = 4) {
        self.workMinutes = workMinutes
        self.shortBreakMinutes = shortBreakMinutes
        self.longBreakMinutes = longBreakMinutes
        self.sessionsBeforeLongBreak = sessionsBeforeLongBreak
    }

    public func durationMinutes(for phase: PomodoroPhase) -> Int {
        switch phase {
        case .work: workMinutes
        case .shortBreak: shortBreakMinutes
        case .longBreak: longBreakMinutes
        }
    }
}

/// Given the current phase and how many work sessions have completed, return the next phase.
/// After a work phase: a long break when the just-completed session lands on the long-break
/// boundary, otherwise a short break. After any break: back to work.
public func nextPomodoroPhase(
    current: PomodoroPhase, completedWorkSessions: Int, config: PomodoroConfig = .init()
) -> PomodoroPhase {
    switch current {
    case .work:
        let n = completedWorkSessions + 1
        return n % max(config.sessionsBeforeLongBreak, 1) == 0 ? .longBreak : .shortBreak
    case .shortBreak, .longBreak:
        return .work
    }
}

/// The phase order for a full cycle (work … long break), useful for previews and validation.
public func pomodoroCycle(config: PomodoroConfig = .init()) -> [PomodoroPhase] {
    var phases: [PomodoroPhase] = []
    var completed = 0
    var current = PomodoroPhase.work
    for _ in 0..<(config.sessionsBeforeLongBreak * 2) {
        phases.append(current)
        if current == .work { completed += 1 }
        let next = nextPomodoroPhase(current: current, completedWorkSessions: current == .work ? completed - 1 : completed, config: config)
        if next == .longBreak { phases.append(.longBreak); break }
        current = next
    }
    return phases
}
