import WidgetKit
import SwiftUI
import SwiftData

// MARK: - Medication widget (today's doses + times) — ports the Android MedicationGlanceWidget,
// including its privacy redaction: when `privacy.medicationLock` is on, names are hidden on the
// (lock-screen-visible) widget, matching Android's widget medication redaction.

struct MedicationEntry: TimelineEntry {
    let date: Date
    struct Dose: Identifiable { let id: String; let planID: String; let name: String; let time: String; let taken: Bool }
    let doses: [Dose]
    let pendingCount: Int
    let redacted: Bool
}

struct MedicationProvider: TimelineProvider {
    func placeholder(in context: Context) -> MedicationEntry {
        MedicationEntry(date: .now, doses: [
            .init(id: "1", planID: "1", name: "Vitamin D", time: "8:00 AM", taken: true),
            .init(id: "2", planID: "2", name: "Omega-3", time: "8:00 PM", taken: false),
        ], pendingCount: 1, redacted: false)
    }
    func getSnapshot(in context: Context, completion: @escaping (MedicationEntry) -> Void) { completion(load()) }
    func getTimeline(in context: Context, completion: @escaping (Timeline<MedicationEntry>) -> Void) {
        let refresh = Calendar.current.date(byAdding: .minute, value: 30, to: .now) ?? .now
        completion(Timeline(entries: [load()], policy: .after(refresh)))
    }

    private var redactNames: Bool {
        // Read the same App-Group flag ChronosSettings persists (default on, like the app).
        (UserDefaults(suiteName: ChronosStore.appGroup) ?? .standard)
            .object(forKey: "privacy.medicationLock") as? Bool ?? true
    }

    private func load() -> MedicationEntry {
        let context = ModelContext(ChronosStore.makeContainer())
        let redact = redactNames
        let plans = ((try? context.fetch(FetchDescriptor<MedicationPlan>())) ?? []).filter(\.isActive)
        // Flatten each plan's per-day reminder times into individual dose rows, pending-first.
        var doses: [MedicationEntry.Dose] = []
        for plan in plans {
            let taken = plan.isTaken(on: .now)
            for minute in plan.reminderMinutes.sorted() {
                doses.append(MedicationEntry.Dose(
                    id: "\(plan.id)-\(minute)",
                    planID: plan.id,
                    name: redact ? "Medication" : plan.name,
                    time: minute.clockTime,
                    taken: taken))
            }
        }
        doses.sort { lhs, rhs in
            if lhs.taken != rhs.taken { return !lhs.taken }   // pending first
            return lhs.time < rhs.time
        }
        let pending = doses.filter { !$0.taken }.count
        return MedicationEntry(date: .now, doses: Array(doses.prefix(4)), pendingCount: pending, redacted: redact)
    }
}

struct MedicationWidgetView: View {
    var entry: MedicationEntry
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Image(systemName: "pills.fill").foregroundStyle(.teal)
                Text(entry.pendingCount == 0 ? "All taken" : "\(entry.pendingCount) due")
                    .font(.caption).foregroundStyle(.secondary)
            }
            if entry.doses.isEmpty {
                Text("No medications").font(.caption).foregroundStyle(.secondary)
            } else {
                ForEach(entry.doses) { dose in
                    HStack(spacing: 6) {
                        // Tap to acknowledge the dose in place (Android DoseAck). Disabled once taken.
                        Button(intent: MarkDoseTakenIntent(planID: dose.planID)) {
                            Image(systemName: dose.taken ? "checkmark.circle.fill" : "circle")
                                .font(.caption2)
                                .foregroundStyle(dose.taken ? .green : .secondary)
                        }
                        .buttonStyle(.plain)
                        .disabled(dose.taken)
                        Text(dose.name).font(.caption).lineLimit(1)
                        Spacer(minLength: 2)
                        Text(dose.time).font(.caption2).foregroundStyle(.secondary)
                    }
                }
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .containerBackground(.fill.tertiary, for: .widget)
    }
}

struct MedicationWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ChronosMedicationWidget", provider: MedicationProvider()) { entry in
            MedicationWidgetView(entry: entry)
        }
        .configurationDisplayName("Medication")
        .description("Today's doses and reminder times.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
