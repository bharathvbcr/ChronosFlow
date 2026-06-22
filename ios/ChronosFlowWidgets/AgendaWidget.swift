import WidgetKit
import SwiftUI
import SwiftData

// MARK: - Agenda / Schedule widget — ports the Android AgendaGlanceWidget ("Today's Schedule").
//
// Shows the current block (with its end time) followed by the next few upcoming blocks in a
// time-window format. Read-only by design: tapping anywhere opens the app's day timeline. Responsive
// to the widget family the way Android's SizeMode.Responsive(COMPACT/TALL) adapts the upcoming-limit
// to the available height — fewer rows when small, more when large.

struct AgendaEntry: TimelineEntry {
    let date: Date
    struct Item: Identifiable {
        let id: String
        let title: String
        let startMinute: Int
        let endMinute: Int
        let category: String
    }
    /// The block underway right now, if any.
    let current: Item?
    /// Upcoming blocks (excluding `current`), earliest first.
    let upcoming: [Item]
    /// Total number of blocks scheduled today (for the header count).
    let blockCount: Int
}

struct AgendaProvider: TimelineProvider {
    func placeholder(in context: Context) -> AgendaEntry {
        AgendaEntry(
            date: .now,
            current: .init(id: "1", title: "Deep work", startMinute: 540, endMinute: 660, category: "FOCUS"),
            upcoming: [
                .init(id: "2", title: "Standup", startMinute: 660, endMinute: 675, category: "WORK"),
                .init(id: "3", title: "Lunch", startMinute: 750, endMinute: 795, category: "MEAL"),
                .init(id: "4", title: "Workout", startMinute: 1080, endMinute: 1140, category: "EXERCISE"),
            ],
            blockCount: 7)
    }

    func getSnapshot(in context: Context, completion: @escaping (AgendaEntry) -> Void) { completion(load()) }

    func getTimeline(in context: Context, completion: @escaping (Timeline<AgendaEntry>) -> Void) {
        // Refresh at the next block boundary so the "Now" row rolls forward, else in 15 minutes.
        let refresh = Calendar.current.date(byAdding: .minute, value: 15, to: .now) ?? .now
        completion(Timeline(entries: [load()], policy: .after(refresh)))
    }

    private func nowMinute() -> Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: .now)
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }

    private func load() -> AgendaEntry {
        let context = ModelContext(ChronosStore.makeContainer())
        let now = nowMinute()
        let blocks = (try? context.fetch(FetchDescriptor<TimeBlock>()))?
            .filter { Calendar.current.isDateInToday($0.date) }
            .sorted { $0.startMinuteOfDay < $1.startMinuteOfDay } ?? []
        func item(_ b: TimeBlock) -> AgendaEntry.Item {
            .init(id: b.id, title: b.title,
                  startMinute: b.startMinuteOfDay, endMinute: b.plannedEndMinuteOfDay, category: b.category)
        }
        let current = blocks.first { now >= $0.startMinuteOfDay && now < $0.startMinuteOfDay + $0.durationMinutes }
        // Upcoming = blocks starting after now (excludes the current one), in time order.
        let upcoming = blocks.filter { $0.startMinuteOfDay > now }.map(item)
        return AgendaEntry(
            date: .now,
            current: current.map(item),
            upcoming: upcoming,
            blockCount: blocks.count)
    }
}

struct AgendaWidgetView: View {
    var entry: AgendaEntry
    @Environment(\.widgetFamily) private var family

    /// How many upcoming rows fit per family — the iOS analogue of Android's COMPACT/TALL limits.
    private var upcomingLimit: Int {
        switch family {
        case .systemSmall:  return 2
        case .systemMedium: return 4
        default:            return 7   // systemLarge / extra-large
        }
    }

    var body: some View {
        // Whole-widget tap opens the day timeline (Android SECTION_DAY → iOS Plan section).
        Link(destination: URL(string: "chronosflow://plan")!) {
            VStack(alignment: .leading, spacing: 6) {
                header
                if entry.current == nil && entry.upcoming.isEmpty {
                    Text("Nothing left on today's schedule")
                        .font(.caption).foregroundStyle(.secondary)
                } else {
                    if let current = entry.current {
                        currentRow(current)
                    }
                    ForEach(entry.upcoming.prefix(upcomingLimit)) { item in
                        upcomingRow(item)
                    }
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        }
        .containerBackground(.fill.tertiary, for: .widget)
    }

    private var header: some View {
        HStack {
            Text("Today").font(.headline)
            Spacer()
            Text(entry.date, format: .dateTime.weekday(.abbreviated).month(.abbreviated).day())
                .font(.caption2).foregroundStyle(.secondary)
        }
    }

    private func currentRow(_ item: AgendaEntry.Item) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            HStack(spacing: 5) {
                Circle().fill(ChronosColors.category(item.category)).frame(width: 7, height: 7)
                Text("Now · \(item.title)")
                    .font(.subheadline.weight(.semibold)).lineLimit(1)
            }
            Text("until \(item.endMinute.clockTime)")
                .font(.caption2).foregroundStyle(.secondary)
        }
    }

    private func upcomingRow(_ item: AgendaEntry.Item) -> some View {
        HStack(spacing: 6) {
            Text(item.startMinute.clockTime)
                .font(.caption.weight(.medium).monospacedDigit())
                .foregroundStyle(ChronosColors.category(item.category))
                .frame(width: 58, alignment: .leading)
            Text(item.title).font(.caption).lineLimit(1)
            Spacer(minLength: 0)
        }
    }
}

struct AgendaWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ChronosAgendaWidget", provider: AgendaProvider()) { entry in
            AgendaWidgetView(entry: entry)
        }
        .configurationDisplayName("Schedule")
        .description("Today's schedule at a glance — current and upcoming blocks.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

#Preview(as: .systemLarge) {
    AgendaWidget()
} timeline: {
    AgendaEntry(
        date: .now,
        current: .init(id: "1", title: "Deep work", startMinute: 540, endMinute: 660, category: "FOCUS"),
        upcoming: [
            .init(id: "2", title: "Standup", startMinute: 660, endMinute: 675, category: "WORK"),
            .init(id: "3", title: "Lunch", startMinute: 750, endMinute: 795, category: "MEAL"),
            .init(id: "4", title: "Code review", startMinute: 840, endMinute: 900, category: "WORK"),
            .init(id: "5", title: "Workout", startMinute: 1080, endMinute: 1140, category: "EXERCISE"),
        ],
        blockCount: 7)
    AgendaEntry(date: .now, current: nil, upcoming: [], blockCount: 0)
}
