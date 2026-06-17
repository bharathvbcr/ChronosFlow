import Foundation

// Sleep trend analytics — port of SleepTrends: average duration, bedtime consistency, sleep debt vs
// a target, and a coarse quality trend direction.

public struct SleepTrendSummary: Sendable, Equatable {
    public let averageDurationMinutes: Int
    public let consistencyMinutes: Int     // stddev of bedtime; lower = more consistent
    public let debtMinutes: Int            // max(0, target*nights - total slept)
    public let trend: TrendDirection
    public let nights: Int
}

public enum TrendDirection: String, Sendable, Equatable { case improving, steady, declining, unknown }

/// Compute trends over `nights` (each: bedtime minute-of-day + measured duration + optional quality).
public func sleepTrends(
    durations: [Int], bedtimes: [Int], qualities: [Int?], targetMinutes: Int = 8 * 60
) -> SleepTrendSummary {
    let n = durations.count
    guard n > 0 else {
        return SleepTrendSummary(averageDurationMinutes: 0, consistencyMinutes: 0,
                                 debtMinutes: 0, trend: .unknown, nights: 0)
    }
    let avg = durations.reduce(0, +) / n
    let consistency = standardDeviation(bedtimes)
    let debt = max(0, targetMinutes * n - durations.reduce(0, +))
    return SleepTrendSummary(
        averageDurationMinutes: avg,
        consistencyMinutes: Int(consistency.rounded()),
        debtMinutes: debt,
        trend: qualityTrend(qualities.compactMap { $0 }),
        nights: n
    )
}

private func standardDeviation(_ values: [Int]) -> Double {
    guard values.count > 1 else { return 0 }
    let mean = Double(values.reduce(0, +)) / Double(values.count)
    let variance = values.reduce(0.0) { $0 + pow(Double($1) - mean, 2) } / Double(values.count)
    return variance.squareRoot()
}

/// Compare the mean of the earlier half vs the later half (chronological order assumed).
private func qualityTrend(_ q: [Int]) -> TrendDirection {
    guard q.count >= 2 else { return .unknown }
    let half = q.count / 2
    let earlier = Array(q.prefix(half))
    let later = Array(q.suffix(q.count - half))
    let em = Double(earlier.reduce(0, +)) / Double(max(earlier.count, 1))
    let lm = Double(later.reduce(0, +)) / Double(max(later.count, 1))
    if lm - em > 0.4 { return .improving }
    if em - lm > 0.4 { return .declining }
    return .steady
}
