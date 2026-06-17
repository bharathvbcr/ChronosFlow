import SwiftUI
import SwiftData
import Charts
import ChronosCore

/// The Review tab (== Insights page): period-switched execution rollups, per-category breakdown,
/// a severity-ranked findings list, plus habit / sleep / mood trends. Ports the unified
/// Review/Insights surface (the Insights tab IS the Review page; the detailed planned/actual sheet
/// is reachable from here). Period rollups + findings are computed by ChronosCore (unit-tested).
struct InsightsView: View {
    @Query private var blocks: [TimeBlock]
    @Query private var habits: [Habit]
    @Query private var nights: [SleepTrack]
    @Query private var checkIns: [MoodEnergyCheckIn]
    @Query private var medications: [MedicationPlan]
    @State private var showingDayReview = false
    @State private var period: InsightsPeriod = .day

    private var todayBlocks: [TimeBlock] {
        blocks.filter { Calendar.current.isDateInToday($0.date) }
    }

    // MARK: Period rollup (ChronosCore.summarize)

    /// Aggregate the selected period off the portable analytics core.
    private var periodSummary: PeriodSummary {
        let inputs = blocks.map {
            InsightsBlock(
                date: $0.date,
                category: $0.category.uppercased(),
                plannedDuration: $0.durationMinutes,
                actualStart: $0.actualStartMinuteOfDay,
                actualEnd: $0.actualEndMinuteOfDay)
        }
        return summarize(blocks: inputs, period: period, anchorDate: .now, calendar: .current)
    }

    /// Severity-ranked findings (ChronosCore.deriveFindings) over the period + day signals.
    private var findings: [ReviewFinding] {
        let summary = periodSummary
        let windowBlocks = periodBlocks
        let demanding = windowBlocks.filter { $0.energyLevel.rawValue >= 4 }
            .reduce(0) { $0 + $1.durationMinutes }
        let total = windowBlocks.reduce(0) { $0 + $1.durationMinutes }
        let breakMin = windowBlocks.filter { ["BREAK", "MEAL", "RECOVERY"].contains($0.category.uppercased()) }
            .reduce(0) { $0 + $1.durationMinutes }
        let variety = Set(windowBlocks.map { $0.category.uppercased() }).count
        let context = FindingsContext(
            demandingMinutes: demanding,
            totalScheduledMinutes: total,
            categoryVariety: variety,
            breakRatio: total > 0 ? Double(breakMin) / Double(total) : 0,
            sleepReadiness: coreReadiness,
            medicationAdherence: medications.isEmpty ? nil : adherenceRate,
            bestHabitStreak: habits.map(\.streakCount).max() ?? 0)
        return deriveFindings(summary: summary, context: context)
    }

    /// Blocks within the selected period window (for context signals the summary doesn't expose).
    private var periodBlocks: [TimeBlock] {
        let cal = Calendar.current
        let endDay = cal.startOfDay(for: .now)
        let startDay = cal.date(byAdding: .day, value: -(period.days - 1), to: endDay) ?? endDay
        let endBound = cal.date(byAdding: .day, value: 1, to: endDay) ?? endDay
        return blocks.filter { $0.date >= startDay && $0.date < endBound }
    }

    /// ChronosCore readiness from the most recently logged night.
    private var coreReadiness: ChronosCore.SleepReadiness {
        let recent = nights.max { $0.date < $1.date }
        let app = deriveSleepReadiness(lastNight: recent)
        return ChronosCore.SleepReadiness(rawValue: app.rawValue) ?? .unknown
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: ChronosSpacing.medium) {
                    periodPicker
                    periodSummaryCard
                    categoryBreakdownCard
                    findingsCard
                    balanceCard
                    if let best = bestWindow { bestWindowCard(best) }
                    if !medications.isEmpty { adherenceCard }
                    if let corr = sleepMoodCorrelation { correlationCard(corr) }
                    habitChart
                    sleepChart
                    moodChart
                }
                .padding(ChronosSpacing.standard)
            }
            .background { ChronosBackdrop() }
            .navigationTitle("Review")
            .chronosScrollMinimizedBar()
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Day review") { showingDayReview = true }
                }
            }
            .sheet(isPresented: $showingDayReview) {
                DayReviewSheet(blocks: todayBlocks)
            }
        }
    }

    // MARK: Period switcher

    private var periodPicker: some View {
        Picker("Period", selection: $period) {
            ForEach(InsightsPeriod.allCases, id: \.self) { p in
                Text(p.label).tag(p)
            }
        }
        .pickerStyle(.segmented)
    }

    // MARK: Period summary (rollup execution metrics)

    private var periodSummaryCard: some View {
        let s = periodSummary
        return ChronosGlassPanel(tint: ChronosColors.brandPrimary) {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                HStack(alignment: .firstTextBaseline) {
                    Text(period == .day ? "EXECUTION" : "EXECUTION · \(period.label.uppercased())")
                        .font(.chronosCaption).foregroundStyle(.secondary)
                    Spacer()
                    Text(s.hasPlan ? "\(s.completionPercent)%" : "N/A")
                        .font(.chronosHeadline)
                        .foregroundStyle(ChronosColors.brandPrimary)
                }
                ProgressView(value: Double(s.completionPercent) / 100).tint(ChronosColors.brandPrimary)
                HStack(spacing: ChronosSpacing.compact) {
                    metricStat("Planned", s.hasPlan ? formatMins(s.plannedMinutes) : "—")
                    metricStat("Actual", s.hasPlan ? formatMins(s.actualMinutes) : "—",
                               tint: ChronosColors.brandSecondary)
                    metricStat("Missed", s.hasPlan ? formatMins(s.missedMinutes) : "—",
                               tint: ChronosColors.brandAccent)
                    metricStat("Drift", s.hasPlan ? formatDrift(s.driftMinutes) : "—",
                               tint: s.driftMinutes < 0 ? ChronosColors.brandAccent : ChronosColors.brandSecondary)
                }
                if period != .day {
                    Text(period == .week
                         ? "Execution metrics cover the last 7 days."
                         : "Execution metrics cover the last 30 days.")
                        .font(.chronosCaption).foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func metricStat(_ label: String, _ value: String, tint: Color = .primary) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.chronosCaption).foregroundStyle(.secondary)
            Text(value).font(.chronosLabel).foregroundStyle(tint)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: Category breakdown

    private var categoryBreakdownCard: some View {
        let s = periodSummary
        return ChronosGlassCard {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                Text("Category breakdown").font(.chronosHeadline)
                if s.categoryRows.isEmpty {
                    Text("No blocks tracked yet").font(.chronosBody).foregroundStyle(.secondary)
                } else {
                    if let top = s.topCategory {
                        Text("★ \(top.category) leads at \(percent(top.share)) · \(s.concentration.rawValue) concentration")
                            .font(.chronosCaption)
                            .foregroundStyle(concentrationColor(s.concentration))
                    }
                    ForEach(Array(s.categoryRows.enumerated()), id: \.element.category) { index, row in
                        let rowTint = index == 0 ? ChronosColors.brandSecondary : ChronosColors.brandPrimary
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(index == 0 ? "★ \(row.category)" : row.category)
                                    .font(.chronosLabel)
                                    .foregroundStyle(index == 0 ? rowTint : .primary)
                                Spacer()
                                Text("\(formatMins(row.minutes)) · \(percent(row.share))")
                                    .font(.chronosCaption).foregroundStyle(.secondary)
                            }
                            ProgressView(value: min(max(row.progress, 0), 1)).tint(rowTint)
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func concentrationColor(_ c: ConcentrationLabel) -> Color {
        switch c {
        case .high: return ChronosColors.brandAccent
        case .moderate: return ChronosColors.brandSecondary
        case .even, .balanced: return .secondary
        }
    }

    // MARK: Findings list

    private var findingsCard: some View {
        let items = findings
        return ChronosGlassCard {
            VStack(alignment: .leading, spacing: ChronosSpacing.compact) {
                Text("Review findings").font(.chronosHeadline)
                if items.isEmpty {
                    Text("No findings yet — log your day to surface insights.")
                        .font(.chronosBody).foregroundStyle(.secondary)
                } else {
                    ForEach(items) { finding in
                        findingRow(finding)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func findingRow(_ f: ReviewFinding) -> some View {
        let tint = severityColor(f.severity)
        return HStack(alignment: .top, spacing: ChronosSpacing.compact) {
            Image(systemName: severityIcon(f.severity)).foregroundStyle(tint)
            VStack(alignment: .leading, spacing: 2) {
                Text(f.title).font(.chronosLabel)
                Text(f.detail).font(.chronosCaption).foregroundStyle(.secondary)
                Text(f.source.uppercased()).font(.chronosCaption).foregroundStyle(tint)
            }
            Spacer()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func severityColor(_ s: FindingSeverity) -> Color {
        switch s {
        case .critical: return ChronosColors.brandAccent
        case .warning: return ChronosColors.brandSecondary
        case .info: return ChronosColors.brandPrimary
        }
    }

    private func severityIcon(_ s: FindingSeverity) -> String {
        switch s {
        case .critical: return "exclamationmark.triangle.fill"
        case .warning: return "exclamationmark.circle.fill"
        case .info: return "info.circle.fill"
        }
    }

    // MARK: Adherence rate (shared with findings)

    private var adherenceRate: Double {
        let window = 7
        let today = Calendar.current.startOfDay(for: .now)
        var taken = 0, expected = 0
        for plan in medications {
            let perDay = max(plan.reminderMinutes.count, 1)
            expected += perDay * window
            for offset in 0..<window {
                guard let day = Calendar.current.date(byAdding: .day, value: -offset, to: today) else { continue }
                let count = plan.takenAt.filter { Calendar.current.isDate($0, inSameDayAs: day) }.count
                taken += min(count, perDay)
            }
        }
        return expected > 0 ? Double(taken) / Double(expected) : 0
    }

    private func formatMins(_ m: Int) -> String {
        let v = max(m, 0)
        if v < 60 { return "\(v)m" }
        let h = v / 60, rem = v % 60
        return rem == 0 ? "\(h)h" : "\(h)h \(rem)m"
    }

    private func formatDrift(_ m: Int) -> String {
        let sign = m > 0 ? "+" : (m < 0 ? "−" : "")
        return "\(sign)\(formatMins(abs(m)))"
    }

    private func percent(_ v: Double) -> String { "\(Int((v * 100).rounded()))%" }

    // MARK: Day balance (mirrors ChronosCore.evaluateDayBalance)

    private var balance: (score: Int, overloaded: Bool, breakRatio: Double, demanding: Int, variety: Int) {
        let day = todayBlocks
        guard !day.isEmpty else { return (0, false, 0, 0, 0) }
        let total = day.reduce(0) { $0 + $1.durationMinutes }
        let breakMin = day.filter { ["BREAK", "MEAL"].contains($0.category.uppercased()) }.reduce(0) { $0 + $1.durationMinutes }
        let demanding = day.filter { $0.energyLevel.rawValue >= 4 }.reduce(0) { $0 + $1.durationMinutes }
        let variety = Set(day.map { $0.category.uppercased() }).count
        let breakRatio = total > 0 ? Double(breakMin) / Double(total) : 0
        let maxDemanding = 5 * 60, maxScheduled = 14 * 60
        let overloaded = demanding > maxDemanding || total > maxScheduled
        var score = 100.0
        if demanding > maxDemanding { score -= Double(demanding - maxDemanding) / 60.0 * 12.0 }
        if breakRatio < 0.10 { score -= (0.10 - breakRatio) * 200.0 }
        if variety < 3 { score -= Double(3 - variety) * 8.0 }
        if total > maxScheduled { score -= Double(total - maxScheduled) / 60.0 * 10.0 }
        return (Int(min(max(score, 0), 100).rounded()), overloaded, breakRatio, demanding, variety)
    }

    private var balanceCard: some View {
        let b = balance
        return ChronosGlassCard(tint: b.overloaded ? ChronosColors.brandAccent : ChronosColors.brandSecondary) {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                HStack {
                    Label("Day balance", systemImage: b.overloaded ? "exclamationmark.triangle.fill" : "scalemass")
                        .font(.chronosHeadline)
                    Spacer()
                    Text("\(b.score)").font(.chronosTitle)
                        .foregroundStyle(b.overloaded ? ChronosColors.brandAccent : ChronosColors.brandSecondary)
                }
                ProgressView(value: Double(b.score) / 100)
                    .tint(b.overloaded ? ChronosColors.brandAccent : ChronosColors.brandSecondary)
                Text(b.overloaded
                     ? "Overloaded — \(b.demanding / 60)h of demanding work. Consider moving some to tomorrow."
                     : "Healthy mix · \(Int(b.breakRatio * 100))% breaks · \(b.variety) categories")
                    .font(.chronosCaption).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: Best deep-work window (mirrors ChronosCore.bestDeepWorkWindow)

    private struct DayBand { let label: String; let range: ClosedRange<Int> }
    private static let bands: [DayBand] = [
        .init(label: "Early morning", range: 300...479),
        .init(label: "Morning", range: 480...659),
        .init(label: "Midday", range: 660...839),
        .init(label: "Afternoon", range: 840...1079),
        .init(label: "Evening", range: 1080...1319),
        .init(label: "Night", range: 1320...1439),
    ]

    private var bestWindow: (label: String, score: Double)? {
        var totals: [String: (sum: Double, count: Int)] = [:]
        for c in checkIns {
            let minute = minuteOfDay(c.recordedAt)
            guard let band = Self.bands.first(where: { $0.range.contains(minute) }) else { continue }
            let score = Double(c.energyScore + c.focusScore) / 2.0
            let e = totals[band.label] ?? (0, 0)
            totals[band.label] = (e.sum + score, e.count + 1)
        }
        return totals.filter { $0.value.count >= 2 }
            .map { (label: $0.key, score: $0.value.sum / Double($0.value.count)) }
            .max { $0.score < $1.score }
    }

    private func minuteOfDay(_ date: Date) -> Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: date)
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }

    private func bestWindowCard(_ best: (label: String, score: Double)) -> some View {
        ChronosGlassCard(tint: ChronosColors.brandSecondary) {
            HStack(spacing: ChronosSpacing.compact) {
                Image(systemName: "scope").font(.title2).foregroundStyle(ChronosColors.brandSecondary)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Best deep-work window").font(.chronosCaption).foregroundStyle(.secondary)
                    Text(best.label).font(.chronosHeadline)
                    Text("Highest energy + focus from your check-ins").font(.chronosCaption).foregroundStyle(.secondary)
                }
                Spacer()
            }
            .frame(maxWidth: .infinity)
        }
    }

    // MARK: Medication adherence (mirrors ChronosCore.adherenceStats)

    private var adherenceCard: some View {
        let window = 7
        let today = Calendar.current.startOfDay(for: .now)
        var taken = 0, expected = 0
        for plan in medications {
            let perDay = max(plan.reminderMinutes.count, 1)
            expected += perDay * window
            for offset in 0..<window {
                guard let day = Calendar.current.date(byAdding: .day, value: -offset, to: today) else { continue }
                let count = plan.takenAt.filter { Calendar.current.isDate($0, inSameDayAs: day) }.count
                taken += min(count, perDay)
            }
        }
        let rate = expected > 0 ? Double(taken) / Double(expected) : 0
        return ChronosGlassCard(tint: ChronosColors.category("MEDICATION")) {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                HStack {
                    Label("Medication adherence", systemImage: "pills.fill").font(.chronosHeadline)
                    Spacer()
                    Text("\(Int(rate * 100))%").font(.chronosTitle).foregroundStyle(ChronosColors.category("MEDICATION"))
                }
                ProgressView(value: rate).tint(ChronosColors.category("MEDICATION"))
                Text("Last \(window) days · \(taken)/\(expected) doses").font(.chronosCaption).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: Sleep → next-day mood correlation

    /// Average mood on days after a good night (quality >= 4) vs a poor night (quality <= 2).
    private var sleepMoodCorrelation: (good: Double, poor: Double)? {
        let nightByDay = Dictionary(nights.map { (Calendar.current.startOfDay(for: $0.date), $0) },
                                    uniquingKeysWith: { a, _ in a })
        var good: [Int] = [], poor: [Int] = []
        for c in checkIns {
            let day = Calendar.current.startOfDay(for: c.checkInDate)
            guard let night = nightByDay[day], night.sleepQuality > 0 else { continue }
            if night.sleepQuality >= 4 { good.append(c.moodScore) }
            else if night.sleepQuality <= 2 { poor.append(c.moodScore) }
        }
        guard !good.isEmpty, !poor.isEmpty else { return nil }
        return (Double(good.reduce(0, +)) / Double(good.count),
                Double(poor.reduce(0, +)) / Double(poor.count))
    }

    private func correlationCard(_ c: (good: Double, poor: Double)) -> some View {
        let delta = c.good - c.poor
        return ChronosGlassCard(tint: ChronosColors.brandPrimary) {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                Label("Sleep shapes your mood", systemImage: "moon.stars.fill").font(.chronosHeadline)
                Text(delta > 0.3
                     ? "After a good night your mood averages \(String(format: "%.1f", c.good))/5 — \(String(format: "%.1f", delta)) higher than after a poor night (\(String(format: "%.1f", c.poor))/5)."
                     : "Your mood is similar after good and poor nights so far.")
                    .font(.chronosCaption).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var habitChart: some View {
        ChronosGlassCard {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                Text("Habit streaks").font(.chronosHeadline)
                Chart(habits) { habit in
                    BarMark(x: .value("Habit", habit.title), y: .value("Streak", habit.streakCount))
                        .foregroundStyle(ChronosColors.brandSecondary.gradient)
                        .cornerRadius(6)
                }
                .frame(height: 160)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var sleepChart: some View {
        let recent = nights.sorted { $0.date < $1.date }.suffix(7)
        return ChronosGlassCard {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                Text("Sleep (last 7 nights)").font(.chronosHeadline)
                Chart(Array(recent)) { night in
                    LineMark(x: .value("Day", night.date, unit: .day),
                             y: .value("Hours", Double(night.durationMinutes ?? 0) / 60))
                        .foregroundStyle(ChronosColors.brandPrimary)
                        .interpolationMethod(.catmullRom)
                    PointMark(x: .value("Day", night.date, unit: .day),
                              y: .value("Hours", Double(night.durationMinutes ?? 0) / 60))
                        .foregroundStyle(ChronosColors.brandPrimary)
                }
                .frame(height: 160)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var moodChart: some View {
        let recent = checkIns.sorted { $0.recordedAt < $1.recordedAt }.suffix(10)
        return ChronosGlassCard {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                Text("Mood & energy").font(.chronosHeadline)
                if recent.isEmpty {
                    Text("No check-ins yet").font(.chronosBody).foregroundStyle(.secondary)
                } else {
                    Chart(Array(recent)) { c in
                        LineMark(x: .value("Time", c.recordedAt), y: .value("Mood", c.moodScore),
                                 series: .value("Series", "Mood"))
                            .foregroundStyle(ChronosColors.brandAccent)
                        LineMark(x: .value("Time", c.recordedAt), y: .value("Energy", c.energyScore),
                                 series: .value("Series", "Energy"))
                            .foregroundStyle(ChronosColors.brandSecondary)
                    }
                    .chartForegroundStyleScale(["Mood": ChronosColors.brandAccent,
                                                "Energy": ChronosColors.brandSecondary])
                    .frame(height: 160)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// The detailed planned-vs-actual day-review drilldown sheet.
struct DayReviewSheet: View {
    @Environment(\.dismiss) private var dismiss
    let blocks: [TimeBlock]

    var body: some View {
        NavigationStack {
            List(blocks.sorted { $0.startMinuteOfDay < $1.startMinuteOfDay }) { block in
                HStack {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(ChronosColors.category(block.category)).frame(width: 4, height: 36)
                    VStack(alignment: .leading) {
                        Text(block.title).font(.chronosLabel)
                        Text("Planned \(block.startMinuteOfDay.clockTime)–\(block.plannedEndMinuteOfDay.clockTime)")
                            .font(.chronosCaption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    if let s = block.actualStartMinuteOfDay, let e = block.actualEndMinuteOfDay {
                        Text("Actual \(s.clockTime)–\(e.clockTime)").font(.chronosCaption)
                            .foregroundStyle(ChronosColors.brandSecondary)
                    } else {
                        Text("Not logged").font(.chronosCaption).foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("Day review")
            .toolbarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
    }
}

#Preview { InsightsView().modelContainer(ChronosStore.previewContainer()) }
