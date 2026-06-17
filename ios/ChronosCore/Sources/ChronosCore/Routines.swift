import Foundation

// Routine instantiation — port of applying a Routine's steps onto a chosen start time, producing
// concrete block specs (ApplyRoutineToDateUseCase / RoutineStep in Routine.kt).

public struct RoutineStepSpec: Sendable, Equatable {
    public var title: String
    public var category: String
    public var offsetMinute: Int
    public var durationMinutes: Int
    public var energyLevel: Int
    public init(title: String, category: String = "ROUTINE", offsetMinute: Int,
                durationMinutes: Int, energyLevel: Int = 2) {
        self.title = title
        self.category = category
        self.offsetMinute = offsetMinute
        self.durationMinutes = durationMinutes
        self.energyLevel = energyLevel
    }
}

/// A concrete block produced by instantiating a routine step at an absolute start minute.
public struct InstantiatedBlock: Sendable, Equatable {
    public let title: String
    public let category: String
    public let startMinuteOfDay: Int
    public let durationMinutes: Int
    public let energyLevel: Int
}

/// Instantiate routine steps starting at `startMinute`. Each step lands at `startMinute + offset`,
/// wrapping within the 0..1439 day and clamping duration to 1..1440.
public func instantiateRoutine(steps: [RoutineStepSpec], startMinute: Int) -> [InstantiatedBlock] {
    steps.map { step in
        let start = ((startMinute + step.offsetMinute) % 1440 + 1440) % 1440
        return InstantiatedBlock(
            title: step.title,
            category: step.category,
            startMinuteOfDay: start,
            durationMinutes: min(max(step.durationMinutes, 1), 1440),
            energyLevel: step.energyLevel
        )
    }
}
