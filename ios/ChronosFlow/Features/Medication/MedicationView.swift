import SwiftUI
import SwiftData
import Charts
import ChronosCore

/// The Meds tab: medication schedules, dose acknowledgement, refill and missed-dose tracking.
/// Ports `feature/medication`. ChronosFlow helps track and remind — it does not give medical advice.
///
/// When `ChronosSettings.shared.medicationLockEnabled` is on, the whole tab is gated behind
/// Face ID / Touch ID / passcode (`.medicationLock()`), mirroring Android's SensitiveArea.MEDICATION.
struct MedicationView: View {
    @Environment(\.modelContext) private var context
    @Query(sort: \MedicationPlan.reminderMinuteOfDay) private var plans: [MedicationPlan]
    @State private var creating = false

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: ChronosSpacing.compact) {
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

private struct MedicationCard: View {
    @Environment(\.modelContext) private var context
    @Bindable var plan: MedicationPlan

    private var takenToday: Bool { plan.isTaken(on: .now) }

    var body: some View {
        ChronosGlassCard(tint: ChronosColors.category("MEDICATION")) {
            HStack(spacing: ChronosSpacing.compact) {
                Image(systemName: "pills.fill")
                    .font(.title2).foregroundStyle(ChronosColors.category("MEDICATION"))
                VStack(alignment: .leading, spacing: 2) {
                    Text(plan.name).font(.chronosHeadline)
                    Text("\(plan.dosage) \(plan.unit) · \(plan.reminderMinuteOfDay.clockTime)")
                        .font(.chronosCaption).foregroundStyle(.secondary)
                    if plan.takeWithFood {
                        Label("With food", systemImage: "fork.knife").font(.chronosCaption).foregroundStyle(.secondary)
                    }
                    if let days = plan.daysUntilRefill {
                        Label(days <= 3 ? "Refill soon · \(days)d left" : "Refill in \(days)d",
                              systemImage: "arrow.triangle.2.circlepath")
                            .font(.chronosCaption)
                            .foregroundStyle(days <= 3 ? ChronosColors.brandAccent : .secondary)
                    }
                }
                Spacer()
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
                .disabled(takenToday)
            }
            .frame(maxWidth: .infinity)
        }
    }
}

// MARK: - Adherence + refill projection
//
// Surfaces the adherence/refill analytics that already live in ChronosCore but weren't shown on
// the tab. All math goes through `ChronosCore.adherenceStats` / `ChronosCore.daysUntilRefill` —
// we do NOT recompute rates/refill here.

private struct MedicationAdherenceCard: View {
    let plan: MedicationPlan

    /// Doses expected per day, from the configured reminder times.
    private var dosesPerDay: Int { max(plan.reminderMinutes.count, 1) }

    private var sevenDay: AdherenceStats {
        adherenceStats(takenDates: plan.takenAt, dosesPerDay: dosesPerDay, overDays: 7)
    }
    private var fourteenDay: AdherenceStats {
        adherenceStats(takenDates: plan.takenAt, dosesPerDay: dosesPerDay, overDays: 14)
    }

    /// Refill ETA via ChronosCore (nil when supply isn't tracked or cadence is 0).
    private var refillDays: Int? {
        guard let remaining = plan.remainingDoses else { return nil }
        return daysUntilRefill(remainingDoses: remaining, dosesPerDay: dosesPerDay)
    }

    /// Show a refill-soon warning when 3 or fewer days of supply remain.
    private var refillSoon: Bool { (refillDays ?? .max) <= 3 }

    private var medColor: Color { ChronosColors.category("MEDICATION") }

    var body: some View {
        ChronosGlassCard(tint: refillSoon ? ChronosColors.brandAccent : medColor) {
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
                        Text("Missed").font(.chronosCaption).foregroundStyle(.secondary)
                        Text("\(fourteenDay.missedCount)")
                            .font(.chronosTitle)
                            .foregroundStyle(fourteenDay.missedCount == 0 ? ChronosColors.brandSecondary : ChronosColors.brandAccent)
                        Text("in 14d").font(.chronosCaption).foregroundStyle(.secondary)
                    }
                }

                if hasDoseHistory {
                    AdherenceTrendChart(buckets: trendBuckets, tint: medColor)
                        .frame(height: 64)
                        .padding(.top, ChronosSpacing.micro)
                }

                if refillSoon, let days = refillDays {
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
        let perDay = dosesPerDay
        return (0..<14).reversed().compactMap { offset in
            guard let day = cal.date(byAdding: .day, value: -offset, to: today) else { return nil }
            let count = plan.takenAt.filter { cal.isDate($0, inSameDayAs: day) }.count
            return AdherenceDay(date: day, taken: min(count, perDay), expected: perDay)
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

struct MedicationEditorSheet: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var dosage = ""
    @State private var unit = "mg"
    /// One or more reminder times. The model stores `reminderMinutes: [Int]`; the cadence
    /// (count of times/day) also drives the adherence "doses per day" expectation.
    @State private var reminderTimes: [Date] = [Date.now]
    @State private var withFood = false
    @State private var notes = ""
    @State private var trackSupply = false
    @State private var supply = 30.0

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
                    }
                }
                TextField("Notes", text: $notes, axis: .vertical)
            }
            .navigationTitle("New medication")
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        let minutes = reminderTimes.map(minuteOfDay).sorted()
                        let deduped = Array(Set(minutes)).sorted()
                        let primary = deduped.first ?? 8 * 60
                        let plan = MedicationPlan(
                            name: name, dosage: dosage, unit: unit,
                            notes: notes.isEmpty ? nil : notes,
                            reminderMinuteOfDay: primary, takeWithFood: withFood,
                            remainingDoses: trackSupply ? Int(supply) : nil,
                            reminderMinutes: deduped.isEmpty ? [primary] : deduped)
                        context.insert(plan)
                        try? context.save()
                        Task { await ChronosNotifications.shared.scheduleMedication(plan) }
                        dismiss()
                    }.disabled(name.isEmpty)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func minuteOfDay(_ date: Date) -> Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: date)
        return (c.hour ?? 8) * 60 + (c.minute ?? 0)
    }
}

#Preview { MedicationView().modelContainer(ChronosStore.previewContainer()) }
