import Foundation

// Daily review — port of DailyReviewCalculator: execution score and planned-vs-actual variance for
// a day's blocks.

public struct ReviewBlock: Sendable {
    public let plannedStart: Int
    public let plannedDuration: Int
    public let actualStart: Int?
    public let actualEnd: Int?
    public init(plannedStart: Int, plannedDuration: Int, actualStart: Int? = nil, actualEnd: Int? = nil) {
        self.plannedStart = plannedStart
        self.plannedDuration = plannedDuration
        self.actualStart = actualStart
        self.actualEnd = actualEnd
    }
    var actualDuration: Int? {
        guard let s = actualStart, let e = actualEnd else { return nil }
        let span = e - s
        return span >= 0 ? span : span + 1440
    }
}

public struct DailyReview: Sendable, Equatable {
    public let executionScore: Double      // fraction of blocks with a logged actual, 0...1
    public let plannedMinutes: Int
    public let actualMinutes: Int
    public let startDriftMinutes: Int       // mean |actualStart - plannedStart| over logged blocks
    public let loggedCount: Int
    public let totalCount: Int
}

/// Compute the day's review. Execution score = logged / total blocks; drift = average absolute
/// start difference over logged blocks.
public func computeDailyReview(_ blocks: [ReviewBlock]) -> DailyReview {
    let total = blocks.count
    guard total > 0 else {
        return DailyReview(executionScore: 0, plannedMinutes: 0, actualMinutes: 0,
                           startDriftMinutes: 0, loggedCount: 0, totalCount: 0)
    }
    let logged = blocks.filter { $0.actualStart != nil }
    let plannedMinutes = blocks.reduce(0) { $0 + $1.plannedDuration }
    let actualMinutes = logged.reduce(0) { $0 + ($1.actualDuration ?? 0) }
    let drift = logged.isEmpty ? 0 :
        logged.reduce(0) { $0 + abs(($1.actualStart ?? 0) - $1.plannedStart) } / logged.count
    return DailyReview(
        executionScore: Double(logged.count) / Double(total),
        plannedMinutes: plannedMinutes,
        actualMinutes: actualMinutes,
        startDriftMinutes: drift,
        loggedCount: logged.count,
        totalCount: total
    )
}
