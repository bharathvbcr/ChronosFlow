import Foundation

// Best deep-work window — port of the "discover better deep-work windows" insight. Buckets mood/
// energy check-ins by part of day and finds the band where focus+energy run highest, so the planner
// (and the user) can place demanding work there.

public struct CheckInSample: Sendable {
    public let minuteOfDay: Int
    public let energyScore: Int   // 1..5
    public let focusScore: Int    // 1..5
    public init(minuteOfDay: Int, energyScore: Int, focusScore: Int) {
        self.minuteOfDay = minuteOfDay
        self.energyScore = energyScore
        self.focusScore = focusScore
    }
}

public enum DayPart: String, Sendable, CaseIterable, Equatable {
    case earlyMorning, morning, midday, afternoon, evening, night

    public var range: ClosedRange<Int> {
        switch self {
        case .earlyMorning: 300...479    // 05:00–08:00
        case .morning: 480...659         // 08:00–11:00
        case .midday: 660...839          // 11:00–14:00
        case .afternoon: 840...1079      // 14:00–18:00
        case .evening: 1080...1319       // 18:00–22:00
        case .night: 1320...1439         // 22:00–24:00
        }
    }
    public var label: String {
        switch self {
        case .earlyMorning: "Early morning"
        case .morning: "Morning"
        case .midday: "Midday"
        case .afternoon: "Afternoon"
        case .evening: "Evening"
        case .night: "Night"
        }
    }

    static func of(minute: Int) -> DayPart? {
        allCases.first { $0.range.contains(minute) }
    }
}

public struct BestWindowResult: Sendable, Equatable {
    public let part: DayPart
    public let averageScore: Double   // mean of (energy+focus)/2 over samples in the band
    public let sampleCount: Int
}

/// Returns the day part with the highest average (energy+focus)/2, requiring at least `minSamples`
/// in that band. Returns nil when no band clears the threshold.
public func bestDeepWorkWindow(_ samples: [CheckInSample], minSamples: Int = 2) -> BestWindowResult? {
    var totals: [DayPart: (sum: Double, count: Int)] = [:]
    for s in samples {
        guard let part = DayPart.of(minute: s.minuteOfDay) else { continue }
        let score = Double(s.energyScore + s.focusScore) / 2.0
        let entry = totals[part] ?? (0, 0)
        totals[part] = (entry.sum + score, entry.count + 1)
    }
    let eligible = totals
        .filter { $0.value.count >= minSamples }
        .map { BestWindowResult(part: $0.key, averageScore: $0.value.sum / Double($0.value.count), sampleCount: $0.value.count) }
    return eligible.max {
        $0.averageScore != $1.averageScore ? $0.averageScore < $1.averageScore : $0.sampleCount < $1.sampleCount
    }
}
