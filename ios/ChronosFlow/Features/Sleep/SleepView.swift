import SwiftUI
import SwiftData
import ChronosCore

/// The Sleep tab: log nights, see readiness, and (on device) import from HealthKit — the iOS
/// analogue of the Android Health Connect read-only sleep import. Ports the sleep tracking layer.
struct SleepView: View {
    @Environment(\.modelContext) private var context
    @Query(sort: \SleepTrack.date, order: .reverse) private var nights: [SleepTrack]
    @State private var logging = false
    @State private var importer = HealthKitSleepImporter()
    private var settings: ChronosSettings { .shared }

    private var lastNight: SleepTrack? { nights.first }
    /// Readiness banner routed through the single ChronosCore facade (`SleepReadinessCore`) so the
    /// rule set lives in exactly one portable, unit-tested place — the raw-field overload lets us pass
    /// a SwiftData row without the app-side bridge.
    private var readiness: SleepReadiness {
        guard let lastNight else { return .unknown }
        // `SleepReadinessCore.deriveReadiness` returns the ChronosCore enum; bridge to the app enum by
        // raw value (both are identical `String`-backed cases — same convention as GapFillSheet).
        let core = SleepReadinessCore.deriveReadiness(
            sleepQuality: lastNight.sleepQuality,
            interruptedCount: lastNight.interruptedCount,
            actualStartMinute: lastNight.actualStartMinute,
            actualEndMinute: lastNight.actualEndMinute)
        return SleepReadiness(rawValue: core.rawValue) ?? .unknown
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: ChronosSpacing.medium) {
                    readinessCard
                    appleHealthCard
                    if let trends = sleepTrends { trendsCard(trends) }
                    if !nights.isEmpty {
                        Text("Recent nights").font(.chronosTitle)
                        ForEach(nights) { night in
                            ChronosGlassCard {
                                HStack {
                                    VStack(alignment: .leading) {
                                        Text(night.date.formatted(.dateTime.weekday().month().day()))
                                            .font(.chronosHeadline)
                                        if let d = night.durationMinutes {
                                            Text("\(d / 60)h \(d % 60)m · quality \(night.sleepQuality)/5")
                                                .font(.chronosCaption).foregroundStyle(.secondary)
                                        }
                                    }
                                    Spacer()
                                    if night.source == .healthKit {
                                        Label("Apple Health", systemImage: "heart.fill")
                                            .labelStyle(.titleAndIcon)
                                            .font(.chronosCaption)
                                            .foregroundStyle(.pink)
                                    }
                                }
                                .frame(maxWidth: .infinity)
                            }
                        }
                    }
                }
                .padding(ChronosSpacing.standard)
            }
            .background { ChronosBackdrop() }
            .navigationTitle("Sleep")
            .chronosScrollMinimizedBar()
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { logging = true } label: { Image(systemName: "plus") }
                }
            }
            .sheet(isPresented: $logging) { SleepLogSheet() }
            .task { await scheduleLogReminder() }
        }
    }

    // MARK: Sleep trends (mirrors ChronosCore.sleepTrends, unit-tested)

    private struct Trends { let avg: Int; let consistency: Int; let debt: Int; let direction: String; let nights: Int }

    private var sleepTrends: Trends? {
        let recent = nights.sorted { $0.date < $1.date }.suffix(14)
        let durations = recent.compactMap(\.durationMinutes)
        guard durations.count >= 2 else { return nil }
        let bedtimes = recent.compactMap(\.actualStartMinute)
        let avg = durations.reduce(0, +) / durations.count
        let debt = max(0, 8 * 60 * durations.count - durations.reduce(0, +))
        // stddev of bedtime
        let mean = Double(bedtimes.reduce(0, +)) / Double(max(bedtimes.count, 1))
        let variance = bedtimes.reduce(0.0) { $0 + pow(Double($1) - mean, 2) } / Double(max(bedtimes.count, 1))
        let consistency = Int(variance.squareRoot().rounded())
        // quality trend direction
        let q = recent.map(\.sleepQuality).filter { $0 > 0 }
        let direction: String
        if q.count >= 2 {
            let half = q.count / 2
            let em = Double(q.prefix(half).reduce(0, +)) / Double(max(half, 1))
            let lm = Double(q.suffix(q.count - half).reduce(0, +)) / Double(max(q.count - half, 1))
            direction = lm - em > 0.4 ? "Improving" : (em - lm > 0.4 ? "Declining" : "Steady")
        } else { direction = "—" }
        return Trends(avg: avg, consistency: consistency, debt: debt, direction: direction, nights: durations.count)
    }

    private func trendsCard(_ t: Trends) -> some View {
        ChronosGlassCard {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                Text("Trends · last \(t.nights) nights").font(.chronosHeadline)
                HStack {
                    trendStat("Avg", "\(t.avg / 60)h \(t.avg % 60)m")
                    Spacer()
                    trendStat("Consistency", t.consistency <= 30 ? "High" : (t.consistency <= 60 ? "Medium" : "Low"))
                    Spacer()
                    trendStat("Debt", t.debt == 0 ? "None" : "\(t.debt / 60)h")
                    Spacer()
                    trendStat("Quality", t.direction)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func trendStat(_ label: String, _ value: String) -> some View {
        VStack(spacing: 2) {
            Text(value).font(.chronosLabel)
            Text(label).font(.chronosCaption).foregroundStyle(.secondary)
        }
    }

    private var readinessCard: some View {
        ChronosGlassPanel(tint: tint) {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                Text("READINESS").font(.chronosCaption).foregroundStyle(.secondary)
                Text(readinessLabel).font(.chronosTitleLarge)
                Text(readinessDetail).font(.chronosBody).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: Apple Health import (iOS analogue of Android's read-only Health Connect sleep card)

    @ViewBuilder
    private var appleHealthCard: some View {
        switch importer.availability {
        case .unavailable:
            ChronosGlassCard(tone: .quiet) {
                ContentUnavailableView(
                    "Apple Health unavailable",
                    systemImage: "heart.slash",
                    description: Text("This device can't read sleep from Apple Health."))
            }
        case .available, .needsAuthorization:
            ChronosGlassCard {
                VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                    Label("Apple Health", systemImage: "heart.fill")
                        .font(.chronosHeadline).foregroundStyle(.pink)
                    Text("Import your nights read-only from Apple Health. Manually logged nights are never overwritten.")
                        .font(.chronosCaption).foregroundStyle(.secondary)

                    if !settings.healthKitSleepEnabled {
                        Toggle("Enable Health import", isOn: healthKitToggle)
                            .font(.chronosBody)
                    }

                    if let date = importer.lastImportDate {
                        Text("Last imported \(date.formatted(.relative(presentation: .named)))")
                            .font(.chronosCaption).foregroundStyle(.secondary)
                    }
                    if let r = importer.lastResult {
                        Text(importSummary(r)).font(.chronosCaption).foregroundStyle(.secondary)
                    }

                    Button {
                        Task { await runImport() }
                    } label: {
                        HStack(spacing: ChronosSpacing.small) {
                            if importer.isImporting { ProgressView() }
                            Text(importer.availability == .needsAuthorization
                                 ? "Connect & import" : "Import from Health")
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(importer.isImporting || !settings.healthKitSleepEnabled)

                    Toggle("Evening reminder (20:00)", isOn: logReminderBinding)
                        .font(.chronosBody)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    /// Two-way binding into the global settings flag (no `@Bindable` since it's the shared singleton).
    private var healthKitToggle: Binding<Bool> {
        Binding(get: { settings.healthKitSleepEnabled },
                set: { settings.healthKitSleepEnabled = $0 })
    }

    /// Two-way binding for the 20:00 log-sleep-and-journal reminder toggle.
    /// Writing the flag also re-schedules (or cancels) the notification immediately.
    private var logReminderBinding: Binding<Bool> {
        Binding(get: { ChronosSettings.shared.logReminderEnabled },
                set: { ChronosSettings.shared.logReminderEnabled = $0
                       Task { await scheduleLogReminder() }
                })
    }

    /// Schedule (or cancel) the 20:00 daily log reminder based on the current settings value.
    private func scheduleLogReminder() async {
        if ChronosSettings.shared.logReminderEnabled {
            await ChronosNotifications.shared.scheduleLogReminder()
        } else {
            ChronosNotifications.shared.cancel(idPrefix: "log-reminder")
        }
    }

    private func runImport() async {
        if importer.availability == .needsAuthorization {
            await importer.requestAuthorization()
        }
        await importer.importRecent(into: context)
    }

    private func importSummary(_ r: HealthKitSleepImporter.ImportResult) -> String {
        var parts: [String] = []
        if r.inserted > 0 { parts.append("\(r.inserted) new") }
        if r.updated > 0 { parts.append("\(r.updated) updated") }
        if r.skippedManual > 0 { parts.append("\(r.skippedManual) manual kept") }
        if parts.isEmpty { return r.nights == 0 ? "No nights found" : "Up to date" }
        return parts.joined(separator: " · ")
    }

    private var tint: Color {
        switch readiness {
        case .rested: ChronosColors.brandSecondary
        case .depleted: ChronosColors.brandAccent
        default: ChronosColors.brandPrimary
        }
    }
    private var readinessLabel: String {
        switch readiness {
        case .rested: "Rested"
        case .normal: "Normal"
        case .depleted: "Depleted"
        case .unknown: "Log a night"
        }
    }
    private var readinessDetail: String {
        switch readiness {
        case .rested: "A good day for demanding deep work."
        case .normal: "A balanced day ahead."
        case .depleted: "The planner will favor lighter tasks and earlier breaks."
        case .unknown: "Track your sleep to adapt tomorrow's plan."
        }
    }
}

/// Manually log a night, with live validation hints (short/long-night, vs-8h-target, overnight tag,
/// restfulness) as the user edits bed/wake/quality. The pure hint logic lives in
/// `ChronosCore.SleepLogHints`. This is the manual path — the HealthKit import card on the main
/// SleepView is a separate, additive flow.
struct SleepLogSheet: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    /// Newest night, reverse-sorted — the import upserts here, so the sheet reacts and auto-populates.
    @Query(sort: \SleepTrack.date, order: .reverse) private var nights: [SleepTrack]
    @State private var bedtime = Calendar.current.date(bySettingHour: 23, minute: 0, second: 0, of: .now) ?? .now
    @State private var wake = Calendar.current.date(bySettingHour: 7, minute: 0, second: 0, of: .now) ?? .now
    @State private var quality = 3.0
    @State private var interruptions = 0.0

    // In-sheet one-time HealthKit pull (iOS analogue of Android's HealthConnectSleepSyncButton). This
    // runs a single import on demand and NEVER flips `healthKitSleepEnabled` (the tab-level toggle owns
    // the recurring import — matching Android's `onPermissionResultRunOnce`).
    @State private var importer = HealthKitSleepImporter()
    /// Becomes true after a one-time pull so completion feedback (and field auto-fill) only react to a
    /// fetch the user triggered from this sheet, not a pre-existing newest night.
    @State private var didSync = false
    @State private var syncError: String?
    /// Drives success/failure haptics off the `lastResult` change after a sync.
    @State private var syncFeedback: SyncFeedback?

    private enum SyncFeedback: Equatable { case success, failure }

    private var bedMinute: Int { minute(bedtime) }
    private var wakeMinute: Int { minute(wake) }

    var body: some View {
        NavigationStack {
            Form {
                Section("Times") {
                    DatePicker("Bedtime", selection: $bedtime, displayedComponents: .hourAndMinute)
                    DatePicker("Wake", selection: $wake, displayedComponents: .hourAndMinute)
                    healthSyncButton
                    liveHints
                }
                Section("Quality") {
                    VStack(alignment: .leading) {
                        HStack {
                            Text("Quality: \(Int(quality))/5")
                            Spacer()
                            Text(SleepLogHints.qualityLabel(Int(quality)))
                                .font(.chronosCaption).foregroundStyle(ChronosColors.brandPrimary)
                        }
                        Slider(value: $quality, in: 1...5, step: 1)
                    }
                    Stepper("Interruptions: \(Int(interruptions))", value: $interruptions, in: 0...10)
                    Text("Overall · \(SleepLogHints.restfulnessLabel(quality: Int(quality), interruptions: Int(interruptions)))")
                        .font(.chronosCaption).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Log night")
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        context.insert(SleepTrack(
                            actualStartMinute: bedMinute, actualEndMinute: wakeMinute,
                            sleepQuality: Int(quality), interruptedCount: Int(interruptions)))
                        try? context.save(); dismiss()
                    }
                }
            }
            // When a sheet-triggered import lands a newer night, mirror its stage-derived values into
            // the editable fields so the user reviews/saves rather than re-entering. We only react
            // after `didSync` so a pre-existing night never clobbers fresh manual edits.
            .onChange(of: nights.first?.id) { _, _ in if didSync { autoPopulateFromLatestImport() } }
            // Success/failure haptics, parity with the Android sync button's Confirm/Reject feedback.
            .sensoryFeedback(.success, trigger: syncFeedback) { _, new in new == .success }
            .sensoryFeedback(.error, trigger: syncFeedback) { _, new in new == .failure }
        }
        .presentationDetents([.medium, .large])
    }

    // MARK: In-sheet HealthKit sync (one-time pull; does NOT flip the persistent auto-sync setting)

    /// The on-demand "pull last night from Apple Health" control. Hidden entirely when HealthKit is
    /// unavailable on the device. Tapping requests read access if needed, then runs a single import.
    @ViewBuilder
    private var healthSyncButton: some View {
        if importer.availability != .unavailable {
            VStack(alignment: .leading, spacing: 4) {
                Button {
                    Task { await runOneTimeSync() }
                } label: {
                    HStack(spacing: ChronosSpacing.small) {
                        if importer.isImporting {
                            ProgressView()
                            Text("Syncing…")
                        } else {
                            Image(systemName: "heart.fill").foregroundStyle(.pink)
                            Text(importer.availability == .needsAuthorization
                                 ? "Connect & sync from Health" : "Sync from Apple Health")
                        }
                    }
                }
                .disabled(importer.isImporting)
                .font(.chronosBody)

                if let syncError {
                    Text(syncError)
                        .font(.chronosCaption)
                        .foregroundStyle(ChronosColors.brandAccent)
                } else if didSync, let r = importer.lastResult {
                    Text(syncSummary(r))
                        .font(.chronosCaption)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    /// One-time pull: grant read access first if needed, run the importer once, then surface feedback.
    /// Deliberately does NOT touch `ChronosSettings.shared.healthKitSleepEnabled` — the recurring
    /// background import stays under the user's control via the tab-level toggle.
    private func runOneTimeSync() async {
        syncError = nil
        syncFeedback = nil   // reset so a repeat outcome still changes the trigger and re-fires haptics
        if importer.availability == .needsAuthorization {
            await importer.requestAuthorization()
        }
        // A denied/unavailable read yields zero nights; treat the no-data case as a soft failure hint.
        let result = await importer.importRecent(into: context)
        didSync = true
        if result.nights == 0 {
            syncError = "No recent nights found in Apple Health."
            syncFeedback = .failure
        } else {
            syncFeedback = .success
            autoPopulateFromLatestImport()
        }
    }

    /// Mirror the newest imported night's stage-derived window/quality into the editable fields. Only
    /// pulls from a `.healthKit` row (never a manual one — manual entries are never overwritten here,
    /// matching the importer's provenance guard).
    private func autoPopulateFromLatestImport() {
        guard let latest = nights.first, latest.source == .healthKit else { return }
        if let start = latest.actualStartMinute { bedtime = date(fromMinute: start) }
        if let end = latest.actualEndMinute { wake = date(fromMinute: end) }
        if latest.sleepQuality > 0 { quality = Double(latest.sleepQuality) }
        interruptions = Double(latest.interruptedCount)
    }

    private func syncSummary(_ r: HealthKitSleepImporter.ImportResult) -> String {
        if r.skippedManual > 0 && r.inserted == 0 && r.updated == 0 {
            return "Kept your manual entry — Health night not imported."
        }
        if r.inserted > 0 { return "Imported last night from Apple Health." }
        if r.updated > 0 { return "Updated last night from Apple Health." }
        return "Already up to date."
    }

    /// Build today's `Date` at a given minute-of-day, for seeding the time pickers.
    private func date(fromMinute m: Int) -> Date {
        Calendar.current.date(
            bySettingHour: m / 60, minute: m % 60, second: 0, of: .now) ?? .now
    }

    /// Live, derived feedback on the entered window — window label (+ overnight tag), time-in-bed,
    /// short/long-night warning or vs-target delta. Recomputes as bed/wake change.
    @ViewBuilder
    private var liveHints: some View {
        if let window = SleepLogHints.windowLabel(bedMinute: bedMinute, wakeMinute: wakeMinute) {
            HStack {
                Text(window).font(.chronosCaption).foregroundStyle(.secondary)
                if SleepLogHints.isOvernight(bedMinute: bedMinute, wakeMinute: wakeMinute) {
                    Text("Overnight")
                        .font(.chronosCaption)
                        .padding(.horizontal, 8).padding(.vertical, 2)
                        .background(ChronosColors.brandSecondary.opacity(0.18), in: Capsule())
                }
            }
        }
        if let summary = SleepLogHints.durationSummary(bedMinute: bedMinute, wakeMinute: wakeMinute) {
            Text(summary).font(.chronosHeadline).foregroundStyle(ChronosColors.brandPrimary)
        }
        if let warning = SleepLogHints.warningLabel(bedMinute: bedMinute, wakeMinute: wakeMinute) {
            Label(warning, systemImage: "exclamationmark.triangle.fill")
                .font(.chronosCaption).foregroundStyle(ChronosColors.brandAccent)
        } else if let vs = SleepLogHints.vsTargetLabel(bedMinute: bedMinute, wakeMinute: wakeMinute) {
            Text(vs).font(.chronosCaption).foregroundStyle(.secondary)
        }
    }

    private func minute(_ d: Date) -> Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: d)
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }
}

#Preview { SleepView().modelContainer(ChronosStore.previewContainer()) }
