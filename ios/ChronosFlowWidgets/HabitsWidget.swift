import WidgetKit
import SwiftUI
import SwiftData

// MARK: - Habits widget (today's habits + streaks) — ports the Android HabitsGlanceWidget.

struct HabitsEntry: TimelineEntry {
    let date: Date
    struct Row: Identifiable { let id: String; let title: String; let streak: Int; let done: Bool }
    let rows: [Row]
    let doneCount: Int
    let total: Int
}

struct HabitsProvider: TimelineProvider {
    func placeholder(in context: Context) -> HabitsEntry {
        HabitsEntry(date: .now, rows: [
            .init(id: "1", title: "Read", streak: 12, done: true),
            .init(id: "2", title: "Workout", streak: 4, done: false),
        ], doneCount: 1, total: 2)
    }
    func getSnapshot(in context: Context, completion: @escaping (HabitsEntry) -> Void) { completion(load()) }
    func getTimeline(in context: Context, completion: @escaping (Timeline<HabitsEntry>) -> Void) {
        // Refresh at the next local midnight so the "done today" state resets cleanly.
        let nextMidnight = Calendar.current.nextDate(
            after: .now, matching: DateComponents(hour: 0, minute: 1),
            matchingPolicy: .nextTime) ?? Calendar.current.date(byAdding: .hour, value: 6, to: .now)!
        completion(Timeline(entries: [load()], policy: .after(nextMidnight)))
    }
    private func load() -> HabitsEntry {
        let context = ModelContext(ChronosStore.makeContainer())
        let habits = ((try? context.fetch(FetchDescriptor<Habit>())) ?? [])
            .filter(\.isActive)
            // Unfinished first, then by longest streak — the most useful nudges at the top.
            .sorted { lhs, rhs in
                let l = lhs.isCompleted(on: .now), r = rhs.isCompleted(on: .now)
                if l != r { return !l }
                return lhs.streakCount > rhs.streakCount
            }
        // Carry up to the largest family's limit; the view trims per its actual family.
        let rows = habits.prefix(8).map {
            HabitsEntry.Row(id: $0.id, title: $0.title, streak: $0.streakCount, done: $0.isCompleted(on: .now))
        }
        let done = habits.filter { $0.isCompleted(on: .now) }.count
        return HabitsEntry(date: .now, rows: Array(rows), doneCount: done, total: habits.count)
    }
}

struct HabitsWidgetView: View {
    var entry: HabitsEntry
    @Environment(\.widgetFamily) private var family

    /// Row limit per family — the iOS analogue of Android's COMPACT (4) / TALL (8) responsive limits.
    private var rowLimit: Int {
        switch family {
        case .systemSmall:  return 3
        case .systemMedium: return 4
        default:            return 8   // systemLarge
        }
    }

    var body: some View {
        // Wrap the entire card in a Link so tapping anywhere opens the Habits section in the app.
        Link(destination: URL(string: "chronosflow://habits")!) {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Image(systemName: "heart.fill").foregroundStyle(.pink)
                    Text("\(entry.doneCount)/\(entry.total) today")
                        .font(.caption).foregroundStyle(.secondary)
                }
                if entry.rows.isEmpty {
                    Text("No habits yet").font(.caption).foregroundStyle(.secondary)
                } else {
                    ForEach(entry.rows.prefix(rowLimit)) { row in
                        HStack(spacing: 6) {
                            // Tap to toggle today's completion in place (Android HabitMark).
                            Button(intent: ToggleHabitIntent(habitID: row.id)) {
                                Image(systemName: row.done ? "checkmark.circle.fill" : "circle")
                                    .font(.caption2)
                                    .foregroundStyle(row.done ? .green : .secondary)
                            }
                            .buttonStyle(.plain)
                            Text(row.title).font(.caption).lineLimit(1)
                            Spacer(minLength: 2)
                            if row.streak > 0 {
                                Text("\(row.streak)🔥").font(.caption2).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        }
        .containerBackground(.fill.tertiary, for: .widget)
    }
}

#Preview(as: .systemMedium) {
    HabitsWidget()
} timeline: {
    HabitsEntry(date: .now, rows: [
        .init(id: "1", title: "Read", streak: 12, done: true),
        .init(id: "2", title: "Workout", streak: 4, done: false),
        .init(id: "3", title: "Meditate", streak: 7, done: false),
    ], doneCount: 1, total: 3)
    HabitsEntry(date: .now, rows: [], doneCount: 0, total: 0)
}

struct HabitsWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ChronosHabitsWidget", provider: HabitsProvider()) { entry in
            HabitsWidgetView(entry: entry)
        }
        .configurationDisplayName("Habits")
        .description("Today's habits and streaks at a glance.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}
