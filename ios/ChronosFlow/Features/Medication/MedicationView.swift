import SwiftUI
import SwiftData
import Charts
import ChronosCore

/// The Meds tab: medication schedules, dose acknowledgement, refill and missed-dose tracking.
/// Ports `feature/medication`. ChronosFlow helps track and remind — it does not give medical advice.
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

    /// Plans whose remaining supply is at/under the refill threshold — surfaced in a single banner
    /// at the top, mirroring Android's `MedicationRefillCard`.
    private var refillPlans: [MedicationPlan] { plans.filter(refillSoon) }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: ChronosSpacing.compact) {
                    if !refillPlans.isEmpty {
                        RefillSummaryCard(plans: refillPlans)
                    }
                    ForEach(plans) { plan in
                        MedicationCard(plan: plan)
                        MedicationAdherenceCard(plan: plan)
                    }
                    Text("ChronosFlow helps you track and remember medication times. It does not provide medical advice.")
                        .font(.chronosCaption).foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.top, ChronosSpacing.medium)
                }
                .padding(ChronosSpacing.standard)
            }
            .background { ChronosBackdrop() }
            .navigationTitle("Medication")
            .chronosScrollMinimizedBar()
            .overlay { if plans.isEmpty { ContentUnavailableView("No medication", systemImage: "pills", description: Text("Add a schedule to get reminders")) } }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { creating = true } label: { Image(systemName: "plus") }
                }
            }
            .sheet(isPresented: $creating) { MedicationEditorSheet() }
        }
        .medicationLock()
    }
}

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

// MARK: - Refill summary banner (all plans low on supply)

/// One banner listing every medication running low, mirroring Android's `MedicationRefillCard`.
private struct RefillSummaryCard: View {
    let plans: [MedicationPlan]

    private var message: String {
        let names = plans.map(\.name)
        let list: String
        switch names.count {
        case 1: list = names[0]
        case 2: list = "\(names[0]) and \(names[1])"
        default: list = names.dropLast().joined(separator: ", ") + ", and \(names.last ?? "")"
        }
        return "Refill soon: \(list) running low."
    }

    var body: some View {
        ChronosGlassCard(tint: ChronosColors.brandAccent) {
            Label {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Refill needed").font(.chronosHeadline)
                    Text(message).font(.chronosCaption).foregroundStyle(.secondary)
                }
            } icon: {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(ChronosColors.brandAccent)
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

    private var takenToday: Bool { plan.isTaken(on: .now) }
    private var paused: Bool { plan.isPaused() }
    private var medColor: Color { ChronosColors.category("MEDICATION") }

    /// "Take with food" / "Before bed" etc., derived from the boolean (the model carries the
    /// simplified meal-timing as `takeWithFood`).
    private var mealTimingLabel: (text: String, icon: String)? {
        plan.takeWithFood ? ("With food", "fork.knife") : nil
    }

    var body: some View {
        ChronosGlassCard(tint: paused ? .secondary : medColor) {
            HStack(spacing: ChronosSpacing.compact) {
                Image(systemName: "pills.fill")
                    .font(.title2)
                    .foregroundStyle(paused ? Color.secondary : medColor)
                VStack(alignment: .leading, spacing: 2) {
                    Text(plan.name).font(.chronosHeadline)
                    Text(scheduleSummary)
                        .font(.chronosCaption).foregroundStyle(.secondary)
                    if let meal = mealTimingLabel {
                        Label(meal.text, systemImage: meal.icon)
                            .font(.chronosCaption).foregroundStyle(.secondary)
                    }
                    if let cap = plan.maxDosesPerDay {
                        Label("Max \(cap)/day", systemImage: "gauge.with.dots.needle.bottom.50percent")
                            .font(.chronosCaption).foregroundStyle(.secondary)
                    }
                    if paused, let until = plan.pausedUntil {
                        Label("Paused until \(until.formatted(.relative(presentation: .named)))",
                              systemImage: "pause.circle.fill")
                            .font(.chronosCaption)
                            .foregroundStyle(ChronosColors.brandAccent)
                    } else if let days = refillDays(for: plan) {
                        Label(refillSoon(plan) ? "Refill soon · \(max(days, 0))d left" : "Refill in \(days)d",
                              systemImage: "arrow.triangle.2.circlepath")
                            .font(.chronosCaption)
                            .foregroundStyle(refillSoon(plan) ? ChronosColors.brandAccent : .secondary)
                    }
                }
                Spacer()
                takeButton
            }
            .frame(maxWidth: .infinity)
        }
        .contextMenu { contextMenu }
        .confirmationDialog("Archive \"\(plan.name)\"?", isPresented: $confirmingArchive, titleVisibility: .visible) {
            Button("Archive", role: .destructive) { archive() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("It will be hidden and its reminders cancelled. You can restore it from your data later.")
        }
        .sheet(isPresented: $editing) { MedicationEditorSheet(plan: plan) }
    }

    /// e.g. "200 mg · 8:00 AM, 8:00 PM" — lists every reminder time, not just the primary.
    private var scheduleSummary: String {
        let dose = "\(plan.dosage) \(plan.unit)".trimmingCharacters(in: .whitespaces)
        let times = plan.reminderMinutes.sorted().map(\.clockTime).joined(separator: ", ")
        return dose.isEmpty ? times : "\(dose) · \(times)"
    }

    @ViewBuilder private var takeButton: some View {
        Button {
            withAnimation(ChronosMotion.bouncy) {
                if !takenToday { plan.acknowledgeDose(); try? context.save() }
            }
        } label: {
            Text(takenToday ? "Taken" : "Take")
                .font(.chronosLabel)
                .padding(.horizontal, ChronosSpacing.compact)
                .padding(.vertical, ChronosSpacing.small)
        }
        .buttonStyle(.borderedProminent)
        .tint(takenToday ? ChronosColors.brandSecondary : ChronosColors.brandPrimary)
        .disabled(takenToday || paused)
    }

    // MARK: Context menu (per-dose skip/snooze + pause/resume + duplicate/archive)

    @ViewBuilder private var contextMenu: some View {
        Button { editing = true } label: { Label("Edit", systemImage: "pencil") }

        if !takenToday && !paused {
            Button { skip() } label: { Label("Skip today's dose", systemImage: "forward.end.fill") }
            Button { markMissed() } label: { Label("Mark missed", systemImage: "xmark.circle") }
            Button { snooze() } label: { Label("Snooze 15 min", systemImage: "zzz") }
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
        plan.recordDose(.missed, reason: "Marked missed")
        plan.missedCount += 1
        try? context.save()
    }

    /// Records a snoozed dose event for the adherence history (mirrors Android `snoozeReminder`).
    /// The actual +15-minute one-shot reschedule is handled by the notification delegate; here we
    /// only log the outcome, since dose-reminder scheduling lives outside this view.
    private func snooze() {
        plan.recordDose(.snoozed, reason: "Snoozed 15 minutes")
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

// MARK: - Adherence + refill projection
//
// Surfaces the adherence/refill analytics that already live in ChronosCore but weren't shown on
// the tab. All math goes through `ChronosCore.adherenceStats` / `ChronosCore.daysUntilRefill` —
// we do NOT recompute rates/refill here.

private struct MedicationAdherenceCard: View {
    let plan: MedicationPlan

    private var perDay: Int { dosesPerDay(for: plan) }

    private var sevenDay: AdherenceStats {
        adherenceStats(takenDates: plan.takenAt, dosesPerDay: perDay, overDays: 7)
    }
    private var fourteenDay: AdherenceStats {
        adherenceStats(takenDates: plan.takenAt, dosesPerDay: perDay, overDays: 14)
    }

    /// Days (taken in full, none missed) over the trailing 14 — Android's "on-track days" metric.
    private var onTrackDays: Int { trendBuckets.filter { $0.taken >= $0.expected && $0.expected > 0 }.count }

    private var medColor: Color { ChronosColors.category("MEDICATION") }
    private var soon: Bool { refillSoon(plan) }

    /// Most recent non-taken outcomes for the inline history list.
    private var recentEvents: [DoseEvent] {
        (plan.doseEvents ?? []).sorted { $0.date > $1.date }.prefix(6).map { $0 }
    }

    var body: some View {
        ChronosGlassCard(tint: soon ? ChronosColors.brandAccent : medColor) {
            VStack(alignment: .leading, spacing: ChronosSpacing.compact) {
                HStack {
                    Label("Adherence", systemImage: "chart.bar.fill").font(.chronosHeadline)
                    Spacer()
                    Text("\(plan.name)").font(.chronosCaption).foregroundStyle(.secondary)
                }

                HStack(spacing: ChronosSpacing.standard) {
                    rateColumn(title: "7-day", stats: sevenDay)
                    Divider().frame(height: 36)
                    rateColumn(title: "14-day", stats: fourteenDay)
                    Divider().frame(height: 36)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("On-track").font(.chronosCaption).foregroundStyle(.secondary)
                        Text("\(onTrackDays)")
                            .font(.chronosTitle)
                            .foregroundStyle(onTrackDays > 0 ? ChronosColors.brandSecondary : .secondary)
                        Text("of 14d").font(.chronosCaption).foregroundStyle(.secondary)
                    }
                }

                if hasDoseHistory {
                    AdherenceTrendChart(buckets: trendBuckets, tint: medColor)
                        .frame(height: 64)
                        .padding(.top, ChronosSpacing.micro)
                }

                if soon, let days = refillDays(for: plan) {
                    Label(days <= 0 ? "Refill now — supply is out" : "Refill soon — \(days)d of supply left",
                          systemImage: "exclamationmark.triangle.fill")
                        .font(.chronosLabel)
                        .foregroundStyle(ChronosColors.brandAccent)
                        .padding(.vertical, ChronosSpacing.small)
                        .padding(.horizontal, ChronosSpacing.compact)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(ChronosColors.brandAccent.opacity(0.12),
                                    in: RoundedRectangle(cornerRadius: ChronosRadius.small, style: .continuous))
                }

                if !recentEvents.isEmpty {
                    Divider().padding(.vertical, ChronosSpacing.micro)
                    DoseHistoryList(events: recentEvents)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func rateColumn(title: String, stats: AdherenceStats) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.chronosCaption).foregroundStyle(.secondary)
            Text("\(Int((stats.rate * 100).rounded()))%")
                .font(.chronosTitle)
                .foregroundStyle(medColor)
            Text("\(stats.takenCount)/\(stats.expectedCount)").font(.chronosCaption).foregroundStyle(.secondary)
        }
    }

    // MARK: 14-day trend buckets (per-day taken/expected, capped at the cadence)

    private var hasDoseHistory: Bool { !plan.takenAt.isEmpty }

    /// One bucket per day for the last 14 days (oldest → newest), each carrying the day's taken
    /// count (capped at the cadence) and the expected cadence — driving the small bar chart.
    private var trendBuckets: [AdherenceDay] {
        let cal = Calendar.current
        let today = cal.startOfDay(for: .now)
        let perDayCount = perDay
        return (0..<14).reversed().compactMap { offset in
            guard let day = cal.date(byAdding: .day, value: -offset, to: today) else { return nil }
            let count = plan.takenAt.filter { cal.isDate($0, inSameDayAs: day) }.count
            return AdherenceDay(date: day, taken: min(count, perDayCount), expected: perDayCount)
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
                        Text(label(for: event.status)).font(.chronosCaption)
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

    private func label(for status: DoseStatus) -> String {
        switch status {
        case .taken: return "Taken"
        case .missed: return "Missed"
        case .skipped: return "Skipped"
        case .snoozed: return "Snoozed"
        }
    }
}

/// One day in the adherence trend.
private struct AdherenceDay: Identifiable {
    let date: Date
    let taken: Int
    let expected: Int
    var id: Date { date }
    var rate: Double { expected > 0 ? Double(taken) / Double(expected) : 0 }
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

    init(plan: MedicationPlan? = nil) {
        self.editing = plan
        _name = State(initialValue: plan?.name ?? "")
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
                TextField("Name", text: $name)
                HStack {
                    TextField("Dosage", text: $dosage).keyboardType(.decimalPad)
                    Picker("Unit", selection: $unit) {
                        ForEach(["mg", "mcg", "mL", "IU", "tablet"], id: \.self) { Text($0).tag($0) }
                    }.labelsHidden()
                }
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
                Toggle("Take with food", isOn: $withFood)
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
                Section { TextField("Notes", text: $notes, axis: .vertical) }
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
