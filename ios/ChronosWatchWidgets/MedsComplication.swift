import WidgetKit
import SwiftUI

// MARK: - Medication watch-face complication
//
// The watchOS-native analogue of the Android Wear `ChronosMedsComplicationService`: today's dose
// progress as "N due" / "all taken" / "none" with a progress arc. Reads the cached `WatchSnapshot`
// from the on-watch App Group (`WatchSyncPayload.cacheKey`). When titles were redacted on the phone
// the `medications` array is empty but `medsDueCount` still rides the snapshot, so the complication
// can show urgency ("N due") without leaking any medication name.

struct MedsEntry: TimelineEntry {
    let date: Date
    /// Total doses scheduled today (0 when redacted — fall back to dueCount for the denominator).
    let total: Int
    let due: Int

    /// Doses already taken — only known when the array travels (not redacted).
    var taken: Int { max(0, total - due) }
    /// Fraction taken for the gauge; when redacted (total == 0) reflect remaining-due as empty.
    var fraction: Double {
        let denom = max(total, due)
        return denom > 0 ? Double(denom - due) / Double(denom) : 0
    }
    var allTaken: Bool { due == 0 && (total > 0) }
    var hasAny: Bool { total > 0 || due > 0 }
}

struct MedsProvider: TimelineProvider {
    func placeholder(in context: Context) -> MedsEntry {
        MedsEntry(date: .now, total: 3, due: 1)
    }

    func getSnapshot(in context: Context, completion: @escaping (MedsEntry) -> Void) {
        completion(loadEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<MedsEntry>) -> Void) {
        let refresh = Calendar.current.date(byAdding: .hour, value: 1, to: .now) ?? .now
        completion(Timeline(entries: [loadEntry()], policy: .after(refresh)))
    }

    private func loadEntry() -> MedsEntry {
        let defaults = UserDefaults(suiteName: WatchSyncPayload.watchAppGroup) ?? .standard
        let snapshot: WatchSnapshot? = (defaults.data(forKey: WatchSyncPayload.cacheKey))
            .flatMap { try? JSONDecoder().decode(WatchSnapshot.self, from: $0) }
        let meds = snapshot?.medications ?? []
        return MedsEntry(date: .now, total: meds.count, due: snapshot?.medsDueCount ?? 0)
    }
}

struct MedsComplicationView: View {
    @Environment(\.widgetFamily) private var family
    var entry: MedsEntry

    private var statusText: String {
        if !entry.hasAny { return "None today" }
        if entry.allTaken { return "All taken" }
        return "\(entry.due) due"
    }

    var body: some View {
        switch family {
        case .accessoryInline:
            Label(statusText, systemImage: "pills.fill")
        case .accessoryCorner:
            Image(systemName: "pills.fill")
                .widgetLabel {
                    Gauge(value: entry.fraction) { Text(statusText) }
                }
        case .accessoryRectangular:
            HStack {
                Gauge(value: entry.fraction) {
                    Image(systemName: entry.allTaken ? "checkmark.circle.fill" : "pills.fill")
                }
                .gaugeStyle(.accessoryCircularCapacity)
                .tint(entry.due > 0 ? .red : .green)
                VStack(alignment: .leading, spacing: 1) {
                    Text("Meds").font(.caption2).foregroundStyle(.secondary)
                    Text(statusText).font(.headline)
                }
                Spacer()
            }
            .containerBackground(.fill.tertiary, for: .widget)
        default: // .accessoryCircular
            Gauge(value: entry.fraction) {
                Image(systemName: "pills.fill")
            } currentValueLabel: {
                Text(entry.hasAny ? (entry.allTaken ? "✓" : "\(entry.due)") : "—")
            }
            .gaugeStyle(.accessoryCircular)
            .tint(entry.due > 0 ? .red : .green)
        }
    }
}

struct MedsComplication: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ChronosMedsComplication", provider: MedsProvider()) { entry in
            MedsComplicationView(entry: entry)
        }
        .configurationDisplayName("Medication")
        .description("Doses due today at a glance.")
        .supportedFamilies([.accessoryInline, .accessoryCircular, .accessoryCorner, .accessoryRectangular])
    }
}
