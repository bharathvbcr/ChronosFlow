import WidgetKit
import SwiftUI

// MARK: - Habits watch-face complication
//
// The watchOS-native analogue of the Android Wear `ChronosHabitsComplicationService`: today's habit
// completion as "N/M done" with a progress arc. Reads the same cached `WatchSnapshot` the watch app
// writes into the on-watch App Group (`WatchSyncPayload.cacheKey`) — never opens a store or talks to
// the phone. Supports the gauge-style accessory families (circular / corner / inline / rectangular).

struct HabitsEntry: TimelineEntry {
    let date: Date
    let done: Int
    let total: Int

    var fraction: Double { total > 0 ? Double(done) / Double(total) : 0 }
    var allDone: Bool { total > 0 && done >= total }
}

struct HabitsProvider: TimelineProvider {
    func placeholder(in context: Context) -> HabitsEntry {
        HabitsEntry(date: .now, done: 2, total: 3)
    }

    func getSnapshot(in context: Context, completion: @escaping (HabitsEntry) -> Void) {
        completion(loadEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<HabitsEntry>) -> Void) {
        // Habit completion changes on the phone, which reloads timelines; otherwise refresh hourly.
        let refresh = Calendar.current.date(byAdding: .hour, value: 1, to: .now) ?? .now
        completion(Timeline(entries: [loadEntry()], policy: .after(refresh)))
    }

    private func loadEntry() -> HabitsEntry {
        let defaults = UserDefaults(suiteName: WatchSyncPayload.watchAppGroup) ?? .standard
        let snapshot: WatchSnapshot? = (defaults.data(forKey: WatchSyncPayload.cacheKey))
            .flatMap { try? JSONDecoder().decode(WatchSnapshot.self, from: $0) }
        let habits = snapshot?.habits ?? []
        return HabitsEntry(date: .now, done: habits.filter(\.doneToday).count, total: habits.count)
    }
}

struct HabitsComplicationView: View {
    @Environment(\.widgetFamily) private var family
    var entry: HabitsEntry

    var body: some View {
        switch family {
        case .accessoryInline:
            Label("\(entry.done)/\(entry.total) habits", systemImage: "checklist")
        case .accessoryCorner:
            Image(systemName: "checklist")
                .widgetLabel {
                    Gauge(value: entry.fraction) { Text("\(entry.done)/\(entry.total)") }
                }
        case .accessoryRectangular:
            HStack {
                Gauge(value: entry.fraction) {
                    Image(systemName: entry.allDone ? "checkmark.circle.fill" : "checklist")
                }
                .gaugeStyle(.accessoryCircularCapacity)
                VStack(alignment: .leading, spacing: 1) {
                    Text("Habits").font(.caption2).foregroundStyle(.secondary)
                    Text(entry.total == 0 ? "None today"
                                          : (entry.allDone ? "All done" : "\(entry.done)/\(entry.total) done"))
                        .font(.headline)
                }
                Spacer()
            }
            .containerBackground(.fill.tertiary, for: .widget)
        default: // .accessoryCircular
            Gauge(value: entry.fraction) {
                Image(systemName: "checklist")
            } currentValueLabel: {
                Text(entry.total == 0 ? "—" : "\(entry.done)")
            }
            .gaugeStyle(.accessoryCircular)
        }
    }
}

struct HabitsComplication: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ChronosHabitsComplication", provider: HabitsProvider()) { entry in
            HabitsComplicationView(entry: entry)
        }
        .configurationDisplayName("Habits")
        .description("Today's habit completion at a glance.")
        .supportedFamilies([.accessoryInline, .accessoryCircular, .accessoryCorner, .accessoryRectangular])
    }
}
