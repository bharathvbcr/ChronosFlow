import Foundation
import HealthKit
import SwiftData
import ChronosCore

// MARK: - HealthKitSleepImporter
//
// The iOS-native, read-only sleep importer — the analogue of Android's HealthConnectSleepDataSource +
// HealthConnectSleepSyncManager + SleepSessionMapper. It reads `.sleepAnalysis` category samples,
// folds them into nights (one SleepTrack per wake-day), derives a 1..5 quality from the deep+REM
// share via ChronosCore's `deriveSleepQuality`, and upserts SleepTrack rows stamped `.healthKit`.
//
// Provenance guard (parity with Android's mergeImported rule): a night the user logged by hand
// (`source == .manual`) is NEVER overwritten. Existing `.healthKit`/`.derived` rows are updated only
// when a value actually changed, so re-running the importer doesn't churn the store.
//
// There is no Changes-API equivalent wired here (Android uses one to page deltas); a full re-query of
// the trailing window each run is cheap for sleep data, and skip-unchanged keeps writes minimal. We
// still persist a last-import timestamp for the UI and to size an incremental window.

@Observable
@MainActor
final class HealthKitSleepImporter {

    /// Whether HealthKit can be used on this device, and whether we still need the user to authorize.
    enum Availability: Equatable {
        case available          // data available; read access granted (or not yet determined — request will prompt)
        case needsAuthorization // data available but read access not yet granted
        case unavailable        // no HealthKit on this device (e.g. iPad without Health)
    }

    /// Outcome of an import run, surfaced in the Sleep screen.
    struct ImportResult: Equatable {
        var inserted = 0
        var updated = 0
        var skippedManual = 0
        var unchanged = 0
        var nights: Int { inserted + updated + unchanged }
    }

    private let store = HKHealthStore()
    private let defaults = UserDefaults(suiteName: ChronosStore.appGroup) ?? .standard
    private let lastImportKey = "health.sleep.lastImport"

    /// Current availability/authorization state, recomputed by `refreshAvailability()`.
    private(set) var availability: Availability = .unavailable
    /// Set true for the duration of an import so the UI can show progress and disable the button.
    private(set) var isImporting = false
    /// The most recent run's result, for the status line.
    private(set) var lastResult: ImportResult?

    private var sleepType: HKCategoryType? {
        HKObjectType.categoryType(forIdentifier: .sleepAnalysis)
    }

    /// Timestamp of the last successful import (App-Group UserDefaults), nil if never run.
    var lastImportDate: Date? {
        let t = defaults.double(forKey: lastImportKey)
        return t > 0 ? Date(timeIntervalSince1970: t) : nil
    }

    init() {
        refreshAvailability()
    }

    // MARK: Availability

    /// Recompute `availability` from device capability + current authorization status. Note HealthKit
    /// only reports `.sharingDenied`/`.sharingAuthorized` for *write* types; for read-only access it
    /// returns `.notDetermined` until the user has been prompted, so we treat `.notDetermined` as
    /// "needs authorization" to surface the request button.
    func refreshAvailability() {
        guard HKHealthStore.isHealthDataAvailable(), let sleepType else {
            availability = .unavailable
            return
        }
        switch store.authorizationStatus(for: sleepType) {
        case .sharingAuthorized: availability = .available
        case .notDetermined, .sharingDenied: availability = .needsAuthorization
        @unknown default: availability = .needsAuthorization
        }
    }

    /// Request read access to sleep-analysis samples, then refresh availability. (HealthKit privacy
    /// keeps the true grant opaque for reads; after the prompt we re-check and proceed to import.)
    func requestAuthorization() async {
        guard HKHealthStore.isHealthDataAvailable(), let sleepType else {
            availability = .unavailable
            return
        }
        do {
            try await store.requestAuthorization(toShare: [], read: [sleepType])
        } catch {
            // Leave availability as-is; importRecent will simply read nothing if access was refused.
        }
        refreshAvailability()
        // For reads, status often stays `.notDetermined` even after a grant — treat a completed
        // prompt as "available" so the import can run; a denied read just yields zero samples.
        if availability == .needsAuthorization { availability = .available }
    }

    // MARK: Import

    /// Query the trailing `days` window of sleep-analysis samples, fold them into nights, and upsert
    /// SleepTrack rows. Safe to call repeatedly — unchanged nights are skipped and manual nights are
    /// never touched.
    @discardableResult
    func importRecent(into context: ModelContext, days: Int = 14) async -> ImportResult {
        guard HKHealthStore.isHealthDataAvailable(), let sleepType else { return ImportResult() }
        isImporting = true
        defer { isImporting = false }

        // Window from `days` ago (or just before the last import, whichever is wider) to now.
        let now = Date()
        let windowStart = Calendar.current.date(byAdding: .day, value: -max(days, 1), to: now) ?? now
        let start = min(windowStart, lastImportDate ?? windowStart)
        let predicate = HKQuery.predicateForSamples(withStart: start, end: now, options: [])

        let samples = await querySleepSamples(type: sleepType, predicate: predicate)
        let nights = SleepNightBuilder.build(from: samples)

        var result = ImportResult()
        for night in nights {
            apply(night, into: context, result: &result)
        }
        try? context.save()

        defaults.set(now.timeIntervalSince1970, forKey: lastImportKey)
        lastResult = result
        return result
    }

    /// Bridges the callback-based `HKSampleQuery` into async/await. (iOS 27's
    /// `HealthKitQueryDescriptor.results(for:)` would also work; the one-shot sample query is the
    /// clearest fit for a pull-to-import that doesn't need a long-lived observer.)
    private func querySleepSamples(
        type: HKCategoryType, predicate: NSPredicate
    ) async -> [HKCategorySample] {
        await withCheckedContinuation { continuation in
            let sort = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: true)
            let query = HKSampleQuery(
                sampleType: type, predicate: predicate,
                limit: HKObjectQueryNoLimit, sortDescriptors: [sort]
            ) { _, samples, _ in
                continuation.resume(returning: (samples as? [HKCategorySample]) ?? [])
            }
            store.execute(query)
        }
    }

    /// Upsert a single derived night, honouring the provenance guard and skip-unchanged rule.
    private func apply(_ night: SleepNight, into context: ModelContext, result: inout ImportResult) {
        let day = night.date
        // Find an existing row for that wake-day.
        let descriptor = FetchDescriptor<SleepTrack>(predicate: #Predicate { $0.date == day })
        let existing = (try? context.fetch(descriptor))?.first

        if let existing {
            if existing.source == .manual {
                result.skippedManual += 1      // never overwrite a hand-logged night
                return
            }
            if existing.actualStartMinute == night.startMinute,
               existing.actualEndMinute == night.endMinute,
               existing.sleepQuality == night.quality,
               existing.interruptedCount == night.interruptions,
               existing.source == .healthKit {
                result.unchanged += 1
                return
            }
            existing.actualStartMinute = night.startMinute
            existing.actualEndMinute = night.endMinute
            existing.sleepQuality = night.quality
            existing.interruptedCount = night.interruptions
            existing.source = .healthKit
            result.updated += 1
        } else {
            context.insert(SleepTrack(
                id: "hk-\(SleepNightBuilder.dayKey(day))",
                date: day,
                actualStartMinute: night.startMinute,
                actualEndMinute: night.endMinute,
                sleepQuality: night.quality,
                interruptedCount: night.interruptions,
                source: .healthKit))
            result.inserted += 1
        }
    }
}

// MARK: - Night building (HealthKit-specific; the pure quality math lives in ChronosCore)

/// One folded night ready to upsert. Minute-of-day values match the manual logging path.
private struct SleepNight {
    let date: Date          // start-of-day of the wake-day (the morning the night ends)
    let startMinute: Int
    let endMinute: Int
    let quality: Int
    let interruptions: Int
}

/// Folds raw `.sleepAnalysis` samples into nights — the HealthKit analogue of Android's
/// `SleepSessionMapper`. Samples are grouped by wake-day, clustered by adjacency (a gap under
/// `mergeGap` joins them so a tracker splitting the night reads as one), and the widest-spanning
/// cluster wins so a daytime nap can't inflate the overnight figure.
private enum SleepNightBuilder {

    /// Gap below which adjacent asleep samples count as one night. Mirrors Android's `MERGE_GAP`.
    static let mergeGap: TimeInterval = 60 * 60

    static func build(from samples: [HKCategorySample]) -> [SleepNight] {
        // Keep only the sleep-related values (ignore `.inBed`-only awake-before-sleep noise is handled
        // by clustering on asleep spans; `.awake` segments feed the interruption count).
        let relevant = samples.filter { classify($0) != nil }
        guard !relevant.isEmpty else { return [] }

        let cal = Calendar.current
        let byWakeDay = Dictionary(grouping: relevant) { cal.startOfDay(for: $0.endDate) }

        return byWakeDay.compactMap { (day, daySamples) in
            night(forWakeDay: day, samples: daySamples)
        }.sorted { $0.date < $1.date }
    }

    private static func night(forWakeDay day: Date, samples: [HKCategorySample]) -> SleepNight? {
        let asleep = samples.filter { isAsleep(classify($0)) }
        guard let cluster = widestAsleepCluster(asleep), let first = cluster.first else { return nil }

        let clusterStart = cluster.map(\.startDate).min() ?? first.startDate
        let clusterEnd = cluster.map(\.endDate).max() ?? first.endDate

        // Interruptions: `.awake` segments overlapping the chosen sleep span.
        let interruptions = samples.filter {
            classify($0) == .awake && $0.endDate > clusterStart && $0.startDate < clusterEnd
        }.count

        // Aggregate stage minutes across the winning cluster for the quality score.
        var minutes = StageAccumulator()
        for sample in cluster { minutes.add(classify(sample), durationOf: sample) }

        let quality = deriveSleepQuality(minutes.toCore(), interruptions: interruptions)

        return SleepNight(
            date: day,
            startMinute: minuteOfDay(clusterStart),
            endMinute: minuteOfDay(clusterEnd),
            quality: quality,
            interruptions: interruptions)
    }

    /// Split asleep samples into adjacency clusters (gap > `mergeGap` starts a new one) and return the
    /// widest-spanning cluster. Mirrors `SleepSessionMapper.primaryNightCluster`.
    private static func widestAsleepCluster(_ asleep: [HKCategorySample]) -> [HKCategorySample]? {
        guard !asleep.isEmpty else { return nil }
        let sorted = asleep.sorted { $0.startDate < $1.startDate }
        var clusters: [[HKCategorySample]] = []
        for sample in sorted {
            if let lastEnd = clusters.last?.map(\.endDate).max(),
               sample.startDate.timeIntervalSince(lastEnd) <= mergeGap {
                clusters[clusters.count - 1].append(sample)
            } else {
                clusters.append([sample])
            }
        }
        return clusters.max { lhs, rhs in span(lhs) < span(rhs) }
    }

    private static func span(_ cluster: [HKCategorySample]) -> TimeInterval {
        let start = cluster.map(\.startDate).min() ?? .distantPast
        let end = cluster.map(\.endDate).max() ?? .distantFuture
        return end.timeIntervalSince(start)
    }

    // MARK: Stage classification (iOS 27 sleep-analysis values)

    private enum Stage { case core, deep, rem, unspecifiedAsleep, awake, inBed }

    /// Map an `.sleepAnalysis` sample's value to our coarse stage, or nil if it isn't a sleep value.
    private static func classify(_ sample: HKCategorySample) -> Stage? {
        switch HKCategoryValueSleepAnalysis(rawValue: sample.value) {
        case .asleepCore: return .core
        case .asleepDeep: return .deep
        case .asleepREM: return .rem
        case .asleepUnspecified: return .unspecifiedAsleep
        case .awake: return .awake
        case .inBed: return .inBed
        case .none: return nil
        @unknown default: return nil
        }
    }

    private static func isAsleep(_ stage: Stage?) -> Bool {
        switch stage {
        case .core, .deep, .rem, .unspecifiedAsleep: return true
        default: return false
        }
    }

    private static func minuteOfDay(_ date: Date) -> Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: date)
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }

    /// Stable per-day id suffix (mirrors Android's `hc-<localDate>`).
    static func dayKey(_ day: Date) -> String {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: day)
    }

    /// Accumulates per-stage minutes from HealthKit samples to feed the ChronosCore tiering. `.core`
    /// counts as light sleep (the iOS "core" stage is light/intermediate sleep).
    private struct StageAccumulator {
        var light = 0, deep = 0, rem = 0, unspecified = 0
        mutating func add(_ stage: Stage?, durationOf sample: HKCategorySample) {
            let mins = Int(sample.endDate.timeIntervalSince(sample.startDate) / 60)
            guard mins > 0 else { return }
            switch stage {
            case .core: light += mins
            case .deep: deep += mins
            case .rem: rem += mins
            case .unspecifiedAsleep: unspecified += mins
            default: break
            }
        }
        func toCore() -> SleepStageMinutes {
            SleepStageMinutes(
                lightMinutes: light, deepMinutes: deep, remMinutes: rem,
                unspecifiedAsleepMinutes: unspecified)
        }
    }
}
