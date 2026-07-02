import SwiftUI
import SwiftData
import Charts
import ChronosCore

/// The Meds tab: medication schedules, dose acknowledgement, refill and missed-dose tracking.
/// Ports `feature/medication` (`MedicationScreen.kt`). ChronosFlow helps track and remind — it does
/// not give medical advice.
///
/// Layout mirrors Android's `MedicationScreen` single-column page (a `LazyColumn`): page header →
/// metric tiles (Active / Taken 7d / Missed) → "Add medication" button → next-dose card → refill
/// card → 14-day adherence trend → adherence-suggestion panel → per-plan rows. Each plan renders as
/// one `MedicationCard` carrying inline info pills, an expand/collapse details section, and a context
/// action sheet with discrete snooze options — replacing the previous 2N (card + adherence-card)
/// stacking so the page reads at Android's density.
///
/// When `ChronosSettings.shared.medicationLockEnabled` is on, the whole tab is gated behind
/// Face ID / Touch ID / passcode (`.medicationLock()`), mirroring Android's SensitiveArea.MEDICATION.
/// The gate fails open when the device has no credential (handled in `AppLock`), so this view never
/// becomes permanently inaccessible — matching Android's `SensitiveRouteGate` canAuthenticate fallback.
struct MedicationView: View {
    @Environment(\.modelContext) private var context
    /// Only active (non-archived) plans appear; archiving flips `isActive` off (Android `archive(plan)`).
    @Query(filter: #Predicate<MedicationPlan> { $0.isActive },
           sort: \MedicationPlan.reminderMinuteOfDay) private var plans: [MedicationPlan]
    @State private var creating = false
    /// Pre-fill name for the editor when launched from a quick-add chip (Android `prefillName`).
    @State private var prefillName: String?
    /// Suggestions surfaced in the adherence panel, plus per-plan dismissals (Android
    /// `adherenceSuggestions` flow). Recomputed locally; mirrors `MedicationAdherenceAssistPlanner`.
    @State private var dismissedSuggestionPlanIDs: Set<String> = []

    /// Plans whose remaining supply is at/under the refill threshold — surfaced in a single banner,
    /// mirroring Android's `MedicationRefillCard`.
    private var refillPlans: [MedicationPlan] { plans.filter(refillSoon) }

    /// "Taken 7d" metric: every taken dose logged across all plans in the trailing 7 days.
    private var takenLast7: Int {
        let cutoff = Calendar.current.date(byAdding: .day, value: -7, to: .now) ?? .now
        return plans.reduce(0) { sum, plan in
            sum + plan.takenAt.filter { $0 >= cutoff }.count
        }
    }

    /// "Missed" metric: aggregate missed-dose counter across all plans (Android `sumOf { missedCount }`).
    private var totalMissed: Int { plans.reduce(0) { $0 + $1.missedCount } }

    /// Local adherence suggestions (Android `MedicationAdherenceAssistPlanner.suggestAdjustments`),
    /// minus any the user dismissed this session. Capped at 3, matching Android's panel.
    private var suggestions: [MedicationAdherenceSuggestion] {
        MedicationAdherencePlanner.suggestAdjustments(plans: plans)
            .filter { !dismissedSuggestionPlanIDs.contains($0.plan.id) }
            .prefix(3)
            .map { $0 }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: ChronosSpacing.compact) {
                    pageHeader

                    metricTiles

                    Button { startCreate() } label: {
                        Label("Add medication", systemImage: "plus")
                            .font(.chronosLabel)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, ChronosSpacing.small)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(ChronosColors.brandPrimary)
                    .controlSize(.large)

                    if let next = nextMedicationDose(plans: plans) {
                        NextDoseCard(next: next)
                    }

                    if !refillPlans.isEmpty {
                        RefillSummaryCard(plans: refillPlans)
                            .transition(.opacity.combined(with: .move(edge: .top)))
                    }

                    AdherenceTrendCard(plans: plans)

                    if !suggestions.isEmpty {
                        AdherenceSuggestionPanel(
                            suggestions: suggestions,
                            onApply: applySuggestion,
                            onDismiss: { suggestion in
                                withAnimation(ChronosMotion.smooth) {
                                    _ = dismissedSuggestionPlanIDs.insert(suggestion.plan.id)
                                }
                            })
                        .transition(.opacity.combined(with: .move(edge: .top)))
                    }

                    if plans.isEmpty {
                        emptyState
                    } else {
                        ForEach(plans) { plan in
                            MedicationCard(plan: plan)
                        }
                    }

                    Text("ChronosFlow helps you track and remember medication times. It does not provide medical advice.")
                        .font(.chronosCaption).foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .padding(.top, ChronosSpacing.medium)
                }
                .padding(ChronosSpacing.standard)
            }
            .background { ChronosBackdrop() }
            .navigationTitle("Medication")
            .chronosScrollMinimizedBar()
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { startCreate() } label: { Image(systemName: "plus") }
                        .accessibilityLabel("Add medication")
                }
            }
            .sheet(isPresented: $creating, onDismiss: { prefillName = nil }) {
                MedicationEditorSheet(prefillName: prefillName)
            }
        }
        .medicationLock()
    }

    // MARK: Header + metrics (Android ChronosPageHeader + ChronosMetricTile row)

    private var pageHeader: some View {
        HStack(spacing: ChronosSpacing.compact) {
            Image(systemName: "pills.fill")
                .font(.title2)
                .foregroundStyle(ChronosColors.category("MEDICATION"))
            VStack(alignment: .leading, spacing: 2) {
                Text("Medication tracking").font(.chronosHeadline)
                    .accessibilityAddTraits(.isHeader)
                Text("Track active plans and get exact reminders for critical doses.")
                    .font(.chronosCaption).foregroundStyle(.secondary)
            }
            Spacer()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var metricTiles: some View {
        HStack(spacing: ChronosSpacing.compact) {
            MedicationMetricTile(value: plans.count, label: "Active", tint: ChronosColors.brandPrimary)
            MedicationMetricTile(value: takenLast7, label: "Taken 7d", tint: ChronosColors.brandSecondary)
            MedicationMetricTile(value: totalMissed, label: "Missed", tint: ChronosColors.brandAccent)
        }
    }

    // MARK: Empty state + quick-add chips (Android ChronosEmptyState + ChronosQuickAddChips)

    private var emptyState: some View {
        VStack(spacing: ChronosSpacing.compact) {
            ContentUnavailableView("No medication plans", systemImage: "pills",
                                   description: Text("Add a plan to schedule a reminder and track adherence."))
            QuickAddChips(label: "Start quickly",
                          options: ["Vitamin D", "Blood pressure", "Evening dose", "Inhaler"],
                          onSelect: { startCreate(prefill: $0) })
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: Actions

    private func startCreate(prefill: String? = nil) {
        prefillName = prefill
        creating = true
    }

    /// Apply a suggested reminder time through the same edit path Android uses
    /// (`applyAdherenceSuggestion` → `updateMedication`): updates the primary reminder minute, keeps
    /// it as the sole reminder for the day, and reschedules notifications.
    private func applySuggestion(_ suggestion: MedicationAdherenceSuggestion) {
        let plan = suggestion.plan
        plan.reminderMinuteOfDay = suggestion.suggestedReminderMinute
        plan.reminderMinutes = [suggestion.suggestedReminderMinute]
        try? context.save()
        Task { await ChronosNotifications.shared.scheduleMedication(plan) }
        withAnimation(ChronosMotion.smooth) { _ = dismissedSuggestionPlanIDs.insert(plan.id) }
    }
}

// MARK: - Doses-per-day / refill helpers

/// Doses expected per day, from the configured reminder times. Shared by adherence + refill math.
private func dosesPerDay(for plan: MedicationPlan) -> Int { max(plan.reminderMinutes.count, 1) }

/// Refill ETA in days via ChronosCore (nil when supply isn't tracked or cadence is 0).
private func refillDays(for plan: MedicationPlan) -> Int? {
    guard let remaining = plan.remainingDoses else { return nil }
    return daysUntilRefill(remainingDoses: remaining, dosesPerDay: dosesPerDay(for: plan))
}

/// Whether a plan needs refilling: either it's down to ≤3 days of supply, or remaining doses have
/// fallen to/under the configured threshold (`refillNeededAfterDoses`, mirrors Android refillThreshold).
private func refillSoon(_ plan: MedicationPlan) -> Bool {
    if let remaining = plan.remainingDoses, let threshold = plan.refillNeededAfterDoses,
       remaining <= threshold { return true }
    return (refillDays(for: plan) ?? .max) <= 3
}

// MARK: - Next dose (Android nextMedicationDose / MedicationNextDoseCard)

/// The soonest upcoming dose across active, non-paused plans: the earliest reminder still ahead
/// today, else the earliest reminder overall (rolling to tomorrow). Pure, mirrors Android's
/// `nextMedicationDose`.
private func nextMedicationDose(plans: [MedicationPlan]) -> NextMedicationDose? {
    let now = Calendar.current.dateComponents([.hour, .minute], from: .now)
    let nowMinute = (now.hour ?? 0) * 60 + (now.minute ?? 0)
    let eligible = plans.filter { !$0.isPaused() }
    guard !eligible.isEmpty else { return nil }

    if let upcoming = eligible
        .filter({ $0.reminderMinuteOfDay >= nowMinute })
        .min(by: { $0.reminderMinuteOfDay < $1.reminderMinuteOfDay }) {
        return NextMedicationDose(planName: upcoming.name, minuteOfDay: upcoming.reminderMinuteOfDay, isToday: true)
    }
    guard let earliest = eligible.min(by: { $0.reminderMinuteOfDay < $1.reminderMinuteOfDay }) else { return nil }
    return NextMedicationDose(planName: earliest.name, minuteOfDay: earliest.reminderMinuteOfDay, isToday: false)
}

/// The next scheduled dose across eligible plans.
private struct NextMedicationDose {
    let planName: String
    let minuteOfDay: Int
    let isToday: Bool
    /// Android `medicationNextDoseLabel`: "<plan> at <time>" / "<plan> tomorrow at <time>".
    var label: String {
        isToday ? "\(planName) at \(minuteOfDay.clockTime)"
                : "\(planName) tomorrow at \(minuteOfDay.clockTime)"
    }
}

/// "What's next" prompt at the top of the page (Android `MedicationNextDoseCard`).
private struct NextDoseCard: View {
    let next: NextMedicationDose
    var body: some View {
        ChronosGlassCard(tint: ChronosColors.brandPrimary) {
            Label {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Next dose").font(.chronosCaption).foregroundStyle(.secondary)
                    Text(next.label).font(.chronosHeadline)
                }
            } icon: {
                Image(systemName: "clock.fill").foregroundStyle(ChronosColors.brandPrimary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

// MARK: - Metric tile

/// One metric tile in the Active / Taken 7d / Missed row (Android `ChronosMetricTile`).
private struct MedicationMetricTile: View {
    let value: Int
    let label: String
    let tint: Color
    var body: some View {
        ChronosGlassCard(tone: .quiet, tint: tint) {
            VStack(spacing: 2) {
                Text("\(value)").font(.chronosTitleLarge).foregroundStyle(tint)
                Text(label).font(.chronosCaption).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
        }
    }
}

// MARK: - Quick-add chips (Android ChronosQuickAddChips)

/// Tappable preset chips shown in the empty state, pre-filling the editor with a medication name.
private struct QuickAddChips: View {
    let label: String
    let options: [String]
    let onSelect: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: ChronosSpacing.small) {
            Text(label).font(.chronosCaption).foregroundStyle(.secondary)
            MedicationPillFlow(spacing: ChronosSpacing.small) {
                ForEach(options, id: \.self) { option in
                    Button { onSelect(option) } label: {
                        Text(option)
                            .font(.chronosCaption)
                            .padding(.horizontal, ChronosSpacing.compact)
                            .padding(.vertical, ChronosSpacing.small)
                            .background(ChronosColors.brandPrimary.opacity(0.14), in: Capsule())
                            .foregroundStyle(ChronosColors.brandPrimary)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Refill summary banner (all plans low on supply)

/// One banner listing every medication running low, mirroring Android's `MedicationRefillCard`.
private struct RefillSummaryCard: View {
    let plans: [MedicationPlan]

    /// Android `medicationRefillMessage`: lists up to two names, then "N more".
    private var message: String {
        let names = plans.map { plan -> String in
            if let remaining = plan.remainingDoses { return "\(plan.name) (\(remaining) left)" }
            return plan.name
        }
        switch names.count {
        case 1: return "\(names[0]) is running low."
        case 2: return "\(names[0]) and \(names[1]) are running low."
        default: return "\(names[0]), \(names[1]) and \(names.count - 2) more are running low."
        }
    }

    var body: some View {
        ChronosGlassCard(tint: ChronosColors.brandAccent) {
            Label {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Refill soon").font(.chronosHeadline)
                        .accessibilityAddTraits(.isHeader)
                    Text(message).font(.chronosCaption).foregroundStyle(.secondary)
                }
            } icon: {
                Image(systemName: "shippingbox.fill")
                    .foregroundStyle(ChronosColors.brandAccent)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

// MARK: - Aggregated 14-day adherence trend (Android MedicationAdherenceTrendCard)

/// A single page-level 14-day taken/missed trend across all plans, replacing the per-plan adherence
/// cards. Renders nothing when nothing has been logged.
private struct AdherenceTrendCard: View {
    let plans: [MedicationPlan]

    private var buckets: [AdherenceDay] {
        let cal = Calendar.current
        let today = cal.startOfDay(for: .now)
        return (0..<14).reversed().compactMap { offset in
            guard let day = cal.date(byAdding: .day, value: -offset, to: today) else { return nil }
            var taken = 0
            var missed = 0
            for plan in plans {
                for event in plan.doseEvents ?? [] where cal.isDate(event.date, inSameDayAs: day) {
                    if event.status == .taken { taken += 1 }
                    else if event.status == .missed { missed += 1 }
                }
            }
            // Expected = total per-day cadence across plans, so a full day reads at 1.0.
            let expected = max(plans.reduce(0) { $0 + dosesPerDay(for: $1) }, 1)
            return AdherenceDay(date: day, taken: taken, missed: missed, expected: expected)
        }
    }

    private var totalTaken: Int { buckets.reduce(0) { $0 + $1.taken } }
    private var totalMissed: Int { buckets.reduce(0) { $0 + $1.missed } }
    /// Days with at least one taken dose and no misses (Android "on-track days").
    private var onTrackDays: Int { buckets.filter { $0.taken > 0 && $0.missed == 0 }.count }

    /// Android `medicationTrendHeadline`.
    private var headline: String {
        let ratio = buckets.isEmpty ? 0 : Double(onTrackDays) / Double(buckets.count)
        switch ratio {
        case 0.85...: return "Excellent adherence — keep it up."
        case 0.5...: return "Solid routine. Stay consistent."
        case 0.25...: return "Getting on track, dose by dose."
        default: return "Every logged dose builds the habit."
        }
    }

    var body: some View {
        if totalTaken == 0 {
            EmptyView()
        } else {
            ChronosGlassCard(tint: ChronosColors.category("MEDICATION")) {
                VStack(alignment: .leading, spacing: ChronosSpacing.compact) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Dose history").font(.chronosHeadline)
                                .accessibilityAddTraits(.isHeader)
                            Text(headline).font(.chronosCaption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text("\(onTrackDays)/\(buckets.count) days")
                            .font(.chronosLabel)
                            .foregroundStyle(ChronosColors.brandPrimary)
                    }
                    AdherenceTrendChart(buckets: buckets, tint: ChronosColors.category("MEDICATION"))
                        .frame(height: 72)
                    Text("\(totalTaken) taken · \(totalMissed) missed in the last \(buckets.count) days")
                        .font(.chronosCaption).foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
}

// MARK: - Adherence suggestion panel (Android MedicationAdherencePanel)

/// A passive timing-only suggestion for a plan whose dose slipped today or is routinely logged late.
/// Mirrors Android's `MedicationAdherenceSuggestion`. Timing only — never dosing/medical advice.
struct MedicationAdherenceSuggestion: Identifiable {
    let plan: MedicationPlan
    let suggestedReminderMinute: Int
    let reason: String
    var id: String { plan.id }
}

/// Local, on-device port of Android's `MedicationAdherenceAssistPlanner` (the LOCAL fallback path).
/// iOS lacks the GenAI coordinator, so only the deterministic heuristic is ported: flag plans whose
/// reminder passed today without a logged dose, or that are routinely taken well after the reminder,
/// and propose a better minute-of-day. No network, no dosing advice.
private enum MedicationAdherencePlanner {
    private static let missedGraceMinutes = 30
    private static let lateThresholdMinutes = 45
    private static let lateMinOccurrences = 3

    static func suggestAdjustments(plans: [MedicationPlan]) -> [MedicationAdherenceSuggestion] {
        let cal = Calendar.current
        let today = cal.startOfDay(for: .now)
        let comps = cal.dateComponents([.hour, .minute], from: .now)
        let currentMinute = (comps.hour ?? 0) * 60 + (comps.minute ?? 0)

        return plans.compactMap { plan -> MedicationAdherenceSuggestion? in
            guard !plan.isPaused() else { return nil }
            let missedToday = !acknowledgedToday(plan, today: today, cal: cal)
                && currentMinute > plan.reminderMinuteOfDay + missedGraceMinutes
            let lateMinutes = lateTakenMinutes(plan, today: today, cal: cal)
            let latePattern = lateMinutes.count >= lateMinOccurrences
            guard missedToday || latePattern else { return nil }

            if latePattern, !missedToday {
                let sorted = lateMinutes.sorted()
                let typical = sorted[sorted.count / 2]
                return MedicationAdherenceSuggestion(
                    plan: plan,
                    suggestedReminderMinute: min(max(typical, 0), 1439),
                    reason: "Usually taken around \(typical.clockTime) — consider moving the reminder")
            }
            return MedicationAdherenceSuggestion(
                plan: plan,
                suggestedReminderMinute: min(currentMinute + 15, 1439),
                reason: "Reminder at \(plan.reminderMinuteOfDay.clockTime) passed without a logged dose")
        }
        .sorted { $0.plan.reminderMinuteOfDay < $1.plan.reminderMinuteOfDay }
    }

    private static func acknowledgedToday(_ plan: MedicationPlan, today: Date, cal: Calendar) -> Bool {
        (plan.doseEvents ?? []).contains { event in
            cal.isDate(event.date, inSameDayAs: today)
                && (event.status == .taken || event.status == .skipped)
        }
    }

    /// Minute-of-day for taken events (on earlier days) logged ≥45 min after the scheduled reminder.
    private static func lateTakenMinutes(_ plan: MedicationPlan, today: Date, cal: Calendar) -> [Int] {
        (plan.doseEvents ?? []).compactMap { event -> Int? in
            guard event.status == .taken, !cal.isDate(event.date, inSameDayAs: today) else { return nil }
            let scheduled = event.scheduledMinuteOfDay ?? plan.reminderMinuteOfDay
            let c = cal.dateComponents([.hour, .minute], from: event.date)
            let recorded = (c.hour ?? 0) * 60 + (c.minute ?? 0)
            return recorded - scheduled >= lateThresholdMinutes ? recorded : nil
        }
    }
}

/// The suggestion card with Apply / Dismiss per suggestion (Android `MedicationAdherencePanel`).
private struct AdherenceSuggestionPanel: View {
    let suggestions: [MedicationAdherenceSuggestion]
    let onApply: (MedicationAdherenceSuggestion) -> Void
    let onDismiss: (MedicationAdherenceSuggestion) -> Void

    var body: some View {
        ChronosGlassCard(tint: ChronosColors.brandSecondary) {
            VStack(alignment: .leading, spacing: ChronosSpacing.compact) {
                Label("Adherence helper", systemImage: "lightbulb.fill")
                    .font(.chronosHeadline)
                    .foregroundStyle(ChronosColors.brandSecondary)
                ForEach(suggestions) { suggestion in
                    HStack(alignment: .top, spacing: ChronosSpacing.compact) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(suggestion.plan.name).font(.chronosLabel)
                            Text("\(suggestion.reason). New reminder: \(suggestion.suggestedReminderMinute.clockTime)")
                                .font(.chronosCaption).foregroundStyle(.secondary)
                        }
                        Spacer(minLength: 0)
                        VStack(spacing: ChronosSpacing.micro) {
                            Button("Apply") { onApply(suggestion) }
                                .buttonStyle(.borderedProminent)
                                .tint(ChronosColors.brandSecondary)
                                .controlSize(.small)
                            Button("Dismiss") { onDismiss(suggestion) }
                                .buttonStyle(.plain)
                                .font(.chronosCaption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    if suggestion.id != suggestions.last?.id {
                        Divider()
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

// MARK: - Per-medication card

private struct MedicationCard: View {
    @Environment(\.modelContext) private var context
    @Bindable var plan: MedicationPlan

    @State private var editing = false
    @State private var confirmingArchive = false
    @State private var expanded = false
    /// Drives the Android-style context action sheet (taken/missed, discrete snooze, skip, pause).
    @State private var showingActions = false
    /// Haptic triggers tied to the dose mutations (so card, action sheet, and context menu all tick).
    @State private var takeTick = 0
    @State private var missTick = 0

    private var takenToday: Bool { plan.isTaken(on: .now) }
    private var paused: Bool { plan.isPaused() }
    private var medColor: Color { ChronosColors.category("MEDICATION") }
    private var soon: Bool { refillSoon(plan) }

    /// 7-day adherence rate, surfaced inline as a pill (Android `MedicationInfoPill` "Adherence").
    private var adherenceRate: Double {
        adherenceStats(takenDates: plan.takenAt, dosesPerDay: dosesPerDay(for: plan), overDays: 7).rate
    }

    /// Form label for the "Form" pill / icon — derived from the unit (Android `safetyProfile.form`).
    private var form: String { plan.unit.isEmpty ? "tablet" : plan.unit }

    /// SF Symbol mirroring Android's `dosageFormIcon`.
    private var formIcon: String {
        switch form.lowercased() {
        case "inhaler": return "wind"
        case "liquid", "drop", "ml": return "drop.fill"
        case "injection", "iu": return "syringe.fill"
        default: return "pills.fill"
        }
    }

    /// Most recent non-taken outcome for the expanded section (Android "Recent dose").
    private var recentEvent: DoseEvent? {
        (plan.doseEvents ?? []).max(by: { $0.date < $1.date })
    }

    var body: some View {
        ChronosGlassCard(tint: paused ? .secondary : (soon ? ChronosColors.brandAccent : medColor)) {
            VStack(alignment: .leading, spacing: ChronosSpacing.compact) {
                header
                infoPills

                // Inline low-supply warning banner (Android renders this within the card, not at top).
                if soon, let days = refillDays(for: plan) {
                    Label(days <= 0
                          ? "Remaining supply is out. Please refill."
                          : "Remaining supply is low (\(days)d of doses left). Please refill soon.",
                          systemImage: "exclamationmark.triangle.fill")
                        .font(.chronosCaption)
                        .foregroundStyle(ChronosColors.brandAccent)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.vertical, ChronosSpacing.small)
                        .padding(.horizontal, ChronosSpacing.compact)
                        .background(ChronosColors.brandAccent.opacity(0.12),
                                    in: RoundedRectangle(cornerRadius: ChronosRadius.small, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: ChronosRadius.small, style: .continuous)
                            .strokeBorder(ChronosColors.brandAccent.opacity(0.5), lineWidth: 1))
                        .transition(.opacity.combined(with: .move(edge: .top)))
                }

                actionRow

                if expanded { expandedSection }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .sensoryFeedback(.success, trigger: takeTick)
        .sensoryFeedback(.warning, trigger: missTick)
        .contextMenu { contextMenu }
        .confirmationDialog("Archive \"\(plan.name)\"?", isPresented: $confirmingArchive, titleVisibility: .visible) {
            Button("Archive", role: .destructive) { archive() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("It will be hidden and its reminders cancelled. You can restore it from your data later.")
        }
        .sheet(isPresented: $editing) { MedicationEditorSheet(plan: plan) }
        .sheet(isPresented: $showingActions) { contextActionSheet }
    }

    // MARK: Header (icon + name + low-supply badge + edit/archive/expand)

    private var header: some View {
        HStack(alignment: .top, spacing: ChronosSpacing.compact) {
            Image(systemName: formIcon)
                .font(.title3)
                .foregroundStyle(paused ? Color.secondary : medColor)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: ChronosSpacing.small) {
                    Text(plan.name).font(.chronosHeadline)
                    if soon {
                        Label("Low Supply", systemImage: "exclamationmark.circle.fill")
                            .font(.chronosCaption.weight(.bold))
                            .padding(.horizontal, ChronosSpacing.small).padding(.vertical, 2)
                            .background(ChronosColors.brandAccent.opacity(0.18), in: Capsule())
                            .foregroundStyle(ChronosColors.brandAccent)
                    }
                }
                Text(doseSummary).font(.chronosCaption).foregroundStyle(.secondary)
            }
            Spacer()
            HStack(spacing: ChronosSpacing.micro) {
                Button { editing = true } label: { Image(systemName: "pencil") }
                    .frame(minWidth: 44, minHeight: 44)
                    .contentShape(Rectangle())
                    .accessibilityLabel("Edit \(plan.name)")
                Button { confirmingArchive = true } label: { Image(systemName: "archivebox") }
                    .frame(minWidth: 44, minHeight: 44)
                    .contentShape(Rectangle())
                    .accessibilityLabel("Archive \(plan.name)")
                Button {
                    withAnimation(ChronosMotion.snappy) { expanded.toggle() }
                } label: {
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                }
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(Rectangle())
                .accessibilityLabel("\(expanded ? "Hide" : "Show") details for \(plan.name)")
            }
            .buttonStyle(.plain)
            .foregroundStyle(.secondary)
        }
    }

    /// "200 mg" dosage line (Android renders dosage separately above the pills).
    private var doseSummary: String {
        "\(plan.dosage) \(plan.unit)".trimmingCharacters(in: .whitespaces)
    }

    // MARK: Info pills (Android MedicationInfoPill FlowRow)

    private var infoPills: some View {
        MedicationPillFlow(spacing: ChronosSpacing.small) {
            MedicationInfoPill(label: "Reminder", value: plan.reminderMinuteOfDay.clockTime, emphasized: true)
            MedicationInfoPill(label: "Adherence", value: "\(Int((adherenceRate * 100).rounded()))%")
            MedicationInfoPill(label: "Form", value: form.capitalized)
            if paused {
                MedicationInfoPill(label: "Status", value: "Paused", alert: true)
            }
            if let remaining = plan.remainingDoses {
                MedicationInfoPill(label: "Refill", value: "\(remaining) doses", alert: soon)
            }
        }
    }

    // MARK: Taken / Missed action row (Android prominent in-card buttons)

    private var actionRow: some View {
        HStack(spacing: ChronosSpacing.small) {
            Button {
                withAnimation(ChronosMotion.bouncy) {
                    if !takenToday { plan.acknowledgeDose(); try? context.save(); takeTick += 1 }
                }
            } label: {
                Label(takenToday ? "Taken" : "Take", systemImage: "checkmark.circle.fill")
                    .font(.chronosLabel).frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(takenToday ? ChronosColors.brandSecondary : ChronosColors.brandPrimary)
            .disabled(takenToday || paused)
            .accessibilityLabel("Mark \(plan.name) taken")

            Button { markMissed() } label: {
                Label("Missed", systemImage: "exclamationmark.circle")
                    .font(.chronosLabel).frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .tint(ChronosColors.brandAccent)
            .disabled(takenToday || paused)
            .accessibilityLabel("Mark \(plan.name) missed")
        }
    }

    // MARK: Expanded details (Android AnimatedVisibility section)

    private var expandedSection: some View {
        VStack(alignment: .leading, spacing: ChronosSpacing.compact) {
            Divider()
            MedicationPillFlow(spacing: ChronosSpacing.small) {
                MedicationInfoPill(label: "Misses", value: "\(plan.missedCount)", alert: plan.missedCount > 0)
                MedicationInfoPill(label: "Meal", value: plan.takeWithFood ? "With food" : "Anytime")
                if let cap = plan.maxDosesPerDay {
                    MedicationInfoPill(label: "Daily cap", value: "\(cap)/day")
                }
            }

            if let notes = plan.notes, !notes.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Notes").font(.chronosCaption).foregroundStyle(ChronosColors.brandPrimary)
                    Text(notes).font(.chronosCaption).foregroundStyle(.secondary)
                }
            }
            if let safety = plan.safetyNotes, !safety.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Cautions").font(.chronosCaption).foregroundStyle(ChronosColors.brandAccent)
                    Text(safety).font(.chronosCaption).foregroundStyle(.secondary)
                }
            }
            if let event = recentEvent {
                Text("Recent dose: \(doseStatusLabel(event.status)) · \(event.date.formatted(.dateTime.month().day().hour().minute()))")
                    .font(.chronosCaption).foregroundStyle(.secondary)
            }

            HStack(spacing: ChronosSpacing.small) {
                Button { skip() } label: {
                    Text("Skip today").font(.chronosLabel).frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(paused)

                if paused {
                    Button { resume() } label: {
                        Text("Resume").font(.chronosLabel).frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent).tint(ChronosColors.brandSecondary)
                } else {
                    Button { pause(days: 1) } label: {
                        Text("Pause").font(.chronosLabel).frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }
            }

            if !recentHistory.isEmpty {
                Divider()
                DoseHistoryList(events: recentHistory)
            }
        }
    }

    private var recentHistory: [DoseEvent] {
        (plan.doseEvents ?? []).sorted { $0.date > $1.date }.prefix(6).map { $0 }
    }

    // MARK: Context action sheet (Android MedicationContextActionSheet w/ discrete snooze)

    private var contextActionSheet: some View {
        NavigationStack {
            VStack(spacing: ChronosSpacing.compact) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(plan.name).font(.chronosTitle)
                    Text("\(doseSummary) · \(plan.reminderMinuteOfDay.clockTime)")
                        .font(.chronosCaption).foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                HStack(spacing: ChronosSpacing.small) {
                    Button { act { plan.acknowledgeDose(); takeTick += 1 } } label: {
                        Label("Taken", systemImage: "checkmark.circle.fill").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent).tint(ChronosColors.brandPrimary)
                    Button { act { markMissedInline() } } label: {
                        Label("Missed", systemImage: "exclamationmark.circle").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered).tint(ChronosColors.brandAccent)
                }
                .disabled(paused)

                // Discrete snooze options — Android offers 15 / 30 / 60 minutes.
                HStack(spacing: ChronosSpacing.small) {
                    ForEach([15, 30, 60], id: \.self) { minutes in
                        Button("\(minutes)m") { act { snooze(minutes: minutes) } }
                            .buttonStyle(.bordered)
                            .frame(maxWidth: .infinity)
                            .accessibilityLabel("Snooze \(plan.name) for \(minutes) minutes")
                    }
                }
                .disabled(paused)

                HStack(spacing: ChronosSpacing.small) {
                    Button("Skip today") { act { skip() } }
                        .buttonStyle(.bordered).frame(maxWidth: .infinity).disabled(paused)
                    if paused {
                        Button("Resume") { act { resume() } }
                            .buttonStyle(.borderedProminent).tint(ChronosColors.brandSecondary)
                            .frame(maxWidth: .infinity)
                    } else {
                        Button("Pause") { act { pause(days: 1) } }
                            .buttonStyle(.bordered).frame(maxWidth: .infinity)
                    }
                }

                Button { showingActions = false; editing = true } label: {
                    Label("Edit", systemImage: "pencil").frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)

                Button(role: .destructive) { showingActions = false; confirmingArchive = true } label: {
                    Label("Archive", systemImage: "archivebox").frame(maxWidth: .infinity)
                }
                .buttonStyle(.plain).foregroundStyle(ChronosColors.brandAccent)

                Spacer(minLength: 0)
            }
            .padding(ChronosSpacing.standard)
            .navigationTitle("Actions")
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { showingActions = false }
                }
            }
        }
        .presentationDetents([.medium])
    }

    /// Run a mutation, persist, and dismiss the action sheet.
    private func act(_ mutate: () -> Void) {
        mutate()
        try? context.save()
        showingActions = false
    }

    // MARK: Native context menu (long-press) — mirrors the action sheet's options

    @ViewBuilder private var contextMenu: some View {
        Button { showingActions = true } label: { Label("Actions…", systemImage: "ellipsis.circle") }
        Button { editing = true } label: { Label("Edit", systemImage: "pencil") }

        if !takenToday && !paused {
            Button { skip() } label: { Label("Skip today's dose", systemImage: "forward.end.fill") }
            Button { markMissed() } label: { Label("Mark missed", systemImage: "xmark.circle") }
            Menu {
                ForEach([15, 30, 60], id: \.self) { minutes in
                    Button("\(minutes) minutes") { snooze(minutes: minutes) }
                }
            } label: { Label("Snooze", systemImage: "zzz") }
        }

        Divider()

        if paused {
            Button { resume() } label: { Label("Resume", systemImage: "play.circle.fill") }
        } else {
            Button { pause(days: 1) } label: { Label("Pause 1 day", systemImage: "pause.circle") }
            Button { pause(days: 7) } label: { Label("Pause 1 week", systemImage: "pause.circle") }
        }

        Button { duplicate() } label: { Label("Duplicate", systemImage: "plus.square.on.square") }

        Divider()

        Button(role: .destructive) { confirmingArchive = true } label: {
            Label("Archive", systemImage: "archivebox")
        }
    }

    // MARK: Actions

    private func skip() {
        plan.recordDose(.skipped, reason: "Skipped today")
        try? context.save()
    }

    private func markMissed() {
        markMissedInline()
        try? context.save()
    }

    /// Mutation-only missed record (used inside `act` which handles the save).
    private func markMissedInline() {
        plan.recordDose(.missed, reason: "Marked missed")
        plan.missedCount += 1
        missTick += 1
    }

    /// Records a snoozed dose event for the adherence history (mirrors Android `snoozeReminder`).
    /// The actual reschedule is handled by the notification delegate; here we only log the outcome.
    private func snooze(minutes: Int) {
        plan.recordDose(.snoozed, reason: "Snoozed \(minutes) minutes")
        try? context.save()
    }

    /// Pause reminders for N days. Records a `.snoozed` history marker (the iOS DoseStatus has no
    /// PAUSED case) and cancels the scheduled reminders so nothing fires while paused.
    private func pause(days: Int) {
        let until = Calendar.current.date(byAdding: .day, value: days, to: .now) ?? .now
        plan.pausedUntil = until
        plan.recordDose(.snoozed, reason: "Paused for \(days) day\(days == 1 ? "" : "s")")
        try? context.save()
        ChronosNotifications.shared.cancel(idPrefix: "med-\(plan.id)-")
    }

    private func resume() {
        plan.pausedUntil = nil
        try? context.save()
        Task { await ChronosNotifications.shared.scheduleMedication(plan) }
    }

    /// Create an active copy with a fresh id and cleared history, then schedule its reminders
    /// (mirrors Android `duplicateMedication`).
    private func duplicate() {
        let copy = MedicationPlan(
            name: "\(plan.name) (copy)",
            dosage: plan.dosage,
            unit: plan.unit,
            notes: plan.notes,
            startAt: plan.startAt,
            endAt: plan.endAt,
            reminderMinuteOfDay: plan.reminderMinuteOfDay,
            takeWithFood: plan.takeWithFood,
            missedCount: 0,
            refillNeededAfterDoses: plan.refillNeededAfterDoses,
            remainingDoses: plan.remainingDoses,
            isActive: true,
            reminderMinutes: plan.reminderMinutes,
            pausedUntil: nil,
            maxDosesPerDay: plan.maxDosesPerDay,
            safetyNotes: plan.safetyNotes,
            doseEvents: nil)
        context.insert(copy)
        try? context.save()
        Task { await ChronosNotifications.shared.scheduleMedication(copy) }
    }

    /// Archive: flip off `isActive` (so it drops out of the `@Query`) and cancel its reminders.
    private func archive() {
        plan.isActive = false
        try? context.save()
        ChronosNotifications.shared.cancel(idPrefix: "med-\(plan.id)-")
    }
}

// MARK: - Info pill (Android MedicationInfoPill)

/// A small label/value pill with optional emphasis (primary) or alert (accent) tinting.
private struct MedicationInfoPill: View {
    let label: String
    let value: String
    var emphasized: Bool = false
    var alert: Bool = false

    private var container: Color {
        if alert { return ChronosColors.brandAccent.opacity(0.15) }
        if emphasized { return ChronosColors.brandPrimary.opacity(0.15) }
        return Color(.tertiarySystemFill)
    }
    private var content: Color {
        if alert { return ChronosColors.brandAccent }
        if emphasized { return ChronosColors.brandPrimary }
        return .secondary
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption2).foregroundStyle(content.opacity(0.85))
            Text(value).font(.chronosCaption.weight(.semibold)).foregroundStyle(content)
        }
        .padding(.horizontal, ChronosSpacing.small).padding(.vertical, ChronosSpacing.micro)
        .background(container, in: RoundedRectangle(cornerRadius: ChronosRadius.small, style: .continuous))
    }
}

// MARK: - Pill flow layout (Android FlowRow)

/// Wrapping horizontal flow layout for pills and chips, mirroring Compose's `FlowRow`.
private struct MedicationPillFlow: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var rowWidth: CGFloat = 0
        var rowHeight: CGFloat = 0
        var totalHeight: CGFloat = 0
        var totalWidth: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if rowWidth + size.width > maxWidth, rowWidth > 0 {
                totalHeight += rowHeight + spacing
                totalWidth = max(totalWidth, rowWidth - spacing)
                rowWidth = 0
                rowHeight = 0
            }
            rowWidth += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        totalHeight += rowHeight
        totalWidth = max(totalWidth, rowWidth - spacing)
        return CGSize(width: min(totalWidth, maxWidth), height: totalHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) {
        let maxWidth = bounds.width
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x + size.width > bounds.minX + maxWidth, x > bounds.minX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}

// MARK: - Dose history list

/// A compact recent-history list of dose outcomes (taken/missed/skipped/snoozed) with the reason
/// and time. Mirrors Android's dose-event history surfaced under the adherence analytics.
private struct DoseHistoryList: View {
    let events: [DoseEvent]

    var body: some View {
        VStack(alignment: .leading, spacing: ChronosSpacing.small) {
            Text("Recent history").font(.chronosCaption).foregroundStyle(.secondary)
            ForEach(events) { event in
                HStack(spacing: ChronosSpacing.small) {
                    Image(systemName: icon(for: event.status))
                        .font(.chronosCaption)
                        .foregroundStyle(color(for: event.status))
                        .frame(width: 18)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(doseStatusLabel(event.status)).font(.chronosCaption)
                        if let reason = event.reason, !reason.isEmpty {
                            Text(reason).font(.chronosCaption).foregroundStyle(.secondary)
                        }
                    }
                    Spacer()
                    Text(event.date.formatted(.dateTime.month().day().hour().minute()))
                        .font(.chronosCaption).foregroundStyle(.secondary)
                }
            }
        }
    }

    private func icon(for status: DoseStatus) -> String {
        switch status {
        case .taken: return "checkmark.circle.fill"
        case .missed: return "xmark.circle.fill"
        case .skipped: return "forward.end.fill"
        case .snoozed: return "zzz"
        }
    }

    private func color(for status: DoseStatus) -> Color {
        switch status {
        case .taken: return ChronosColors.brandSecondary
        case .missed: return ChronosColors.brandAccent
        case .skipped, .snoozed: return .secondary
        }
    }
}

private func doseStatusLabel(_ status: DoseStatus) -> String {
    switch status {
    case .taken: return "Taken"
    case .missed: return "Missed"
    case .skipped: return "Skipped"
    case .snoozed: return "Snoozed"
    }
}

/// One day in the adherence trend (per-day taken/missed against the day's expected cadence).
private struct AdherenceDay: Identifiable {
    let date: Date
    let taken: Int
    let missed: Int
    let expected: Int
    var id: Date { date }
    var rate: Double { expected > 0 ? Double(min(taken, expected)) / Double(expected) : 0 }
}

/// A compact 14-day adherence bar chart. Each bar's height encodes that day's adherence; fully
/// adhered days read in the medication accent, partial/missed days fade toward muted.
private struct AdherenceTrendChart: View {
    let buckets: [AdherenceDay]
    let tint: Color

    var body: some View {
        Chart(buckets) { day in
            BarMark(
                x: .value("Day", day.date, unit: .day),
                y: .value("Adherence", max(day.rate, day.taken > 0 ? 0.15 : 0.04))
            )
            .foregroundStyle(day.rate >= 1 ? tint : tint.opacity(day.taken > 0 ? 0.55 : 0.22))
            .cornerRadius(ChronosRadius.extraSmall / 2)
        }
        .chartYScale(domain: 0...1)
        .chartXAxis(.hidden)
        .chartYAxis(.hidden)
        .accessibilityLabel("14-day adherence trend")
    }
}

// MARK: - Editor (create + edit)

struct MedicationEditorSheet: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    /// When non-nil the sheet edits an existing plan; otherwise it creates a new one.
    private let editing: MedicationPlan?

    @State private var name: String
    @State private var dosage: String
    @State private var unit: String
    /// One or more reminder times. The model stores `reminderMinutes: [Int]`; the cadence
    /// (count of times/day) also drives the adherence "doses per day" expectation.
    @State private var reminderTimes: [Date]
    @State private var withFood: Bool
    @State private var notes: String
    @State private var trackSupply: Bool
    @State private var supply: Double
    @State private var trackRefillThreshold: Bool
    @State private var refillThreshold: Double
    @State private var capDosesPerDay: Bool
    @State private var maxDosesPerDay: Double
    @State private var safetyNotes: String
    /// Collapsed by default to keep the create flow short (Android's "Safety & Details" section).
    @State private var showSafety: Bool

    /// Form-assist suggestions from the deterministic ChronosCore planner (Android
    /// `MedicationAssistPlanner`), each applied into the fields via an Apply button.
    @State private var assistSuggestions: [MedicationAssistSuggestion] = []
    @State private var assistLoading = false
    /// On-device text tools: medication-name proofread refinement + the notes rewrite menu
    /// (Android `refineName` / `rewriteNotes`). Falls back to planner-only when AI is unavailable.
    @State private var textTools = ChronosTextTools()

    /// New plans launched from a quick-add chip / command-palette capture (Android `prefillName`).
    /// Additive parameter with a default so existing `MedicationEditorSheet(plan:)` callers compile.
    init(plan: MedicationPlan? = nil, prefillName: String? = nil) {
        self.editing = plan
        _name = State(initialValue: plan?.name ?? (prefillName ?? ""))
        _dosage = State(initialValue: plan?.dosage ?? "")
        _unit = State(initialValue: plan?.unit ?? "mg")
        let cal = Calendar.current
        let times = (plan?.reminderMinutes ?? []).sorted().compactMap { minute -> Date? in
            cal.date(bySettingHour: minute / 60, minute: minute % 60, second: 0, of: .now)
        }
        _reminderTimes = State(initialValue: times.isEmpty ? [Date.now] : times)
        _withFood = State(initialValue: plan?.takeWithFood ?? false)
        _notes = State(initialValue: plan?.notes ?? "")
        _trackSupply = State(initialValue: plan?.remainingDoses != nil)
        _supply = State(initialValue: Double(plan?.remainingDoses ?? 30))
        _trackRefillThreshold = State(initialValue: plan?.refillNeededAfterDoses != nil)
        _refillThreshold = State(initialValue: Double(plan?.refillNeededAfterDoses ?? 7))
        _capDosesPerDay = State(initialValue: plan?.maxDosesPerDay != nil)
        _maxDosesPerDay = State(initialValue: Double(plan?.maxDosesPerDay ?? 1))
        _safetyNotes = State(initialValue: plan?.safetyNotes ?? "")
        _showSafety = State(initialValue: (plan?.safetyNotes?.isEmpty == false)
                            || plan?.maxDosesPerDay != nil)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Basics") {
                    TextField("Name", text: $name)
                    HStack {
                        TextField("Dosage", text: $dosage).keyboardType(.decimalPad)
                        Picker("Unit", selection: $unit) {
                            ForEach(unitOptions, id: \.self) { Text($0).tag($0) }
                        }.labelsHidden()
                    }
                }

                assistSection
                Section("Reminders") {
                    ForEach(reminderTimes.indices, id: \.self) { index in
                        DatePicker("Time \(index + 1)", selection: $reminderTimes[index],
                                   displayedComponents: .hourAndMinute)
                    }
                    .onDelete { offsets in
                        // Keep at least one reminder time.
                        guard reminderTimes.count > 1 else { return }
                        reminderTimes.remove(atOffsets: offsets)
                    }
                    Button {
                        withAnimation(ChronosMotion.snappy) {
                            reminderTimes.append(reminderTimes.last ?? .now)
                        }
                    } label: {
                        Label("Add another time", systemImage: "plus.circle.fill")
                    }
                }
                Section("Meal timing") {
                    Toggle("Take with food", isOn: $withFood)
                }
                Section("Refill") {
                    Toggle("Track supply", isOn: $trackSupply.animation(ChronosMotion.snappy))
                    if trackSupply {
                        Stepper("Doses remaining: \(Int(supply))", value: $supply, in: 1...365, step: 1)
                        Toggle("Warn at low supply", isOn: $trackRefillThreshold.animation(ChronosMotion.snappy))
                        if trackRefillThreshold {
                            Stepper("Warn at \(Int(refillThreshold)) doses left",
                                    value: $refillThreshold, in: 1...Swift.max(supply, 1), step: 1)
                        }
                    }
                }
                Section("Safety & details") {
                    DisclosureGroup(isExpanded: $showSafety.animation(ChronosMotion.snappy)) {
                        Toggle("Daily dose cap", isOn: $capDosesPerDay.animation(ChronosMotion.snappy))
                        if capDosesPerDay {
                            Stepper("Max \(Int(maxDosesPerDay)) doses/day",
                                    value: $maxDosesPerDay, in: 1...24, step: 1)
                        }
                        TextField("Cautions / instructions", text: $safetyNotes, axis: .vertical)
                            .lineLimit(1...4)
                    } label: {
                        Label("Safety & details", systemImage: "cross.case")
                    }
                }
                Section("Notes") {
                    TextField("Notes", text: $notes, axis: .vertical)
                    // Tone/length rewrite for the free-text notes only (Android `rewriteNotes`) —
                    // the name, dose, and frequency fields are never rewritten so real drug names
                    // and amounts stay exactly as typed. Same menu pattern as the task editor.
                    if textTools.isAvailable && !notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        Menu {
                            ForEach(ChronosTextOp.allCases) { op in
                                Button { rewriteNotes(op) } label: { Label(op.label, systemImage: op.systemImage) }
                            }
                        } label: {
                            Label(textTools.isWorking ? "Rewriting…" : "AI rewrite", systemImage: "wand.and.sparkles")
                                .font(.chronosCaption)
                        }
                        .disabled(textTools.isWorking)
                    }
                }
            }
            .navigationTitle(editing == nil ? "New medication" : "Edit medication")
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }.disabled(name.isEmpty)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    /// Base units plus whatever the plan already stores / a suggestion applied, so the picker
    /// selection is always representable.
    private var unitOptions: [String] {
        let base = ["mg", "mcg", "mL", "IU", "tablet", "capsule", "drop", "dose"]
        return base.contains(unit) ? base : base + [unit]
    }

    // MARK: Form assist (Android MedicationFormSheet assist chips → MedicationAssistPlanner)

    private var assistSection: some View {
        Section {
            Button { requestAssist() } label: {
                Label(assistLoading ? "Drafting suggestions…"
                        : (assistSuggestions.isEmpty ? "Suggest details with AI" : "Refresh suggestions"),
                      systemImage: "wand.and.sparkles")
                    .font(.chronosLabel)
            }
            .disabled(assistLoading || name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            ForEach(assistSuggestions) { suggestion in
                HStack(alignment: .center, spacing: ChronosSpacing.compact) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(suggestion.label).font(.chronosLabel)
                        Text(suggestion.reason).font(.chronosCaption).foregroundStyle(.secondary)
                    }
                    Spacer(minLength: 0)
                    Button("Apply") { apply(suggestion) }
                        .buttonStyle(.borderedProminent)
                        .tint(ChronosColors.brandSecondary)
                        .controlSize(.small)
                }
            }
        } footer: {
            if !assistSuggestions.isEmpty {
                Text("Organizes what you typed into tracking fields — never medical advice. Nothing changes until you tap Apply.")
            }
        }
    }

    /// Deterministic ChronosCore suggestions from the current fields, plus an optional on-device
    /// name refinement (Android `refineName`: proofread-only, so a real drug or brand name is
    /// tidied but never rewritten). Planner-only when Apple Intelligence is unavailable.
    private func requestAssist() {
        assistLoading = true
        Task { @MainActor in
            defer { assistLoading = false }
            var result = medicationAssistSuggestions(MedicationAssistInput(
                name: name, dosage: dosage, unit: unit, notes: notes,
                primaryReminderMinute: reminderTimes.first.map(minuteOfDay) ?? 8 * 60,
                reminderCount: reminderTimes.count,
                takeWithFood: withFood, tracksSupply: trackSupply))
            let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
            let plannerHasName = result.contains {
                if case .details(.some, _, _) = $0.change { return true }
                return false
            }
            if textTools.isAvailable, trimmedName.count >= 3, !plannerHasName,
               let cleaned = await textTools.run(.proofread, on: trimmedName),
               cleaned.caseInsensitiveCompare(trimmedName) != .orderedSame {
                result.insert(MedicationAssistSuggestion(
                    id: "ai:medication:proofread", label: cleaned,
                    reason: "Tidied the medication name spelling on-device.",
                    change: .details(name: cleaned, dosage: nil, unit: nil)), at: 0)
            }
            withAnimation(ChronosMotion.smooth) { assistSuggestions = result }
        }
    }

    /// Fill the form fields from an accepted suggestion, then drop it from the panel
    /// (Android `applyMedicationAssistSuggestion`).
    private func apply(_ suggestion: MedicationAssistSuggestion) {
        switch suggestion.change {
        case let .details(newName, newDosage, newUnit):
            if let newName, !newName.isEmpty { name = newName }
            if let newDosage, !newDosage.isEmpty { dosage = newDosage }
            if let newUnit, !newUnit.isEmpty { unit = newUnit }
        case let .reminders(minutes):
            let cal = Calendar.current
            reminderTimes = minutes.compactMap { minute in
                cal.date(bySettingHour: minute / 60, minute: minute % 60, second: 0, of: .now)
            }
        case .takeWithFood:
            withFood = true
        case let .unit(newUnit):
            unit = newUnit
        case let .trackSupply(dosesLeft):
            trackSupply = true
            supply = Double(dosesLeft)
        case let .note(text):
            notes = text
        }
        withAnimation(ChronosMotion.smooth) {
            assistSuggestions.removeAll { $0.id == suggestion.id }
        }
    }

    /// Run an on-device text tool over the notes and replace them with the result.
    private func rewriteNotes(_ op: ChronosTextOp) {
        let input = notes
        Task {
            if let result = await textTools.run(op, on: input) {
                withAnimation(ChronosMotion.snappy) { notes = result }
            }
        }
    }

    private func save() {
        let minutes = reminderTimes.map(minuteOfDay).sorted()
        let deduped = Array(Set(minutes)).sorted()
        let primary = deduped.first ?? 8 * 60
        let finalMinutes = deduped.isEmpty ? [primary] : deduped

        let plan = editing ?? MedicationPlan(name: name)
        plan.name = name
        plan.dosage = dosage
        plan.unit = unit
        plan.notes = notes.isEmpty ? nil : notes
        plan.reminderMinuteOfDay = primary
        plan.reminderMinutes = finalMinutes
        plan.takeWithFood = withFood
        plan.remainingDoses = trackSupply ? Int(supply) : nil
        plan.refillNeededAfterDoses = (trackSupply && trackRefillThreshold) ? Int(refillThreshold) : nil
        plan.maxDosesPerDay = capDosesPerDay ? Int(maxDosesPerDay) : nil
        plan.safetyNotes = safetyNotes.isEmpty ? nil : safetyNotes

        if editing == nil { context.insert(plan) }
        try? context.save()
        Task { await ChronosNotifications.shared.scheduleMedication(plan) }
        dismiss()
    }

    private func minuteOfDay(_ date: Date) -> Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: date)
        return (c.hour ?? 8) * 60 + (c.minute ?? 0)
    }
}

#Preview { MedicationView().modelContainer(ChronosStore.previewContainer()) }
