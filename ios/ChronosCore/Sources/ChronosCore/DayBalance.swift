import Foundation

// Day balance — a coarse "is this a humane day?" score, supporting the "repair an overloaded
// schedule" intent. Rewards category variety and a healthy break ratio; penalizes back-to-back
// high-energy load and over-scheduling.

public struct BalanceBlock: Sendable {
    public let category: String
    public let durationMinutes: Int
    public let energyLevel: Int   // 1..5
    public init(category: String, durationMinutes: Int, energyLevel: Int) {
        self.category = category
        self.durationMinutes = durationMinutes
        self.energyLevel = energyLevel
    }
}

public struct DayBalance: Sendable, Equatable {
    public let score: Int             // 0...100
    public let isOverloaded: Bool
    public let breakRatio: Double     // break minutes / scheduled minutes
    public let demandingMinutes: Int  // minutes at energy >= 4
    public let categoryVariety: Int
}

/// Score a day. Overloaded when demanding (energy>=4) minutes exceed `maxDemandingMinutes` or total
/// scheduled time exceeds `maxScheduledMinutes`.
public func evaluateDayBalance(
    _ blocks: [BalanceBlock],
    maxDemandingMinutes: Int = 5 * 60,
    maxScheduledMinutes: Int = 14 * 60
) -> DayBalance {
    guard !blocks.isEmpty else {
        return DayBalance(score: 0, isOverloaded: false, breakRatio: 0, demandingMinutes: 0, categoryVariety: 0)
    }
    let total = blocks.reduce(0) { $0 + $1.durationMinutes }
    let breakMin = blocks.filter { $0.category.uppercased() == "BREAK" || $0.category.uppercased() == "MEAL" }
        .reduce(0) { $0 + $1.durationMinutes }
    let demanding = blocks.filter { $0.energyLevel >= 4 }.reduce(0) { $0 + $1.durationMinutes }
    let variety = Set(blocks.map { $0.category.uppercased() }).count
    let breakRatio = total > 0 ? Double(breakMin) / Double(total) : 0
    let overloaded = demanding > maxDemandingMinutes || total > maxScheduledMinutes

    // Build a 0..100 score from three humane signals.
    var score = 100.0
    if demanding > maxDemandingMinutes {
        score -= Double(demanding - maxDemandingMinutes) / 60.0 * 12.0   // -12 per extra demanding hour
    }
    if breakRatio < 0.10 { score -= (0.10 - breakRatio) * 200.0 }        // penalize too few breaks
    if variety < 3 { score -= Double(3 - variety) * 8.0 }                // reward variety
    if total > maxScheduledMinutes { score -= Double(total - maxScheduledMinutes) / 60.0 * 10.0 }

    return DayBalance(
        score: Int(min(max(score, 0), 100).rounded()),
        isOverloaded: overloaded,
        breakRatio: breakRatio,
        demandingMinutes: demanding,
        categoryVariety: variety
    )
}
