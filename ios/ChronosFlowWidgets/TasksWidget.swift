import WidgetKit
import SwiftUI
import SwiftData

// MARK: - Tasks widget (open tasks for today) — ports TasksGlanceWidget.

struct TasksEntry: TimelineEntry {
    let date: Date
    struct Row: Identifiable { let id: String; let title: String }
    /// Up to 8 open tasks; the view trims to the limit its family allows (Android's COMPACT/TALL).
    let rows: [Row]
    let openCount: Int
}

struct TasksProvider: TimelineProvider {
    func placeholder(in context: Context) -> TasksEntry {
        TasksEntry(date: .now, rows: [
            .init(id: "1", title: "Finish report"), .init(id: "2", title: "Reply to email"),
        ], openCount: 3)
    }
    func getSnapshot(in context: Context, completion: @escaping (TasksEntry) -> Void) { completion(load()) }
    func getTimeline(in context: Context, completion: @escaping (Timeline<TasksEntry>) -> Void) {
        let refresh = Calendar.current.date(byAdding: .minute, value: 30, to: .now) ?? .now
        completion(Timeline(entries: [load()], policy: .after(refresh)))
    }
    private func load() -> TasksEntry {
        let context = ModelContext(ChronosStore.makeContainer())
        let open = (try? context.fetch(FetchDescriptor<TaskItem>()))?
            .filter { !$0.isCompleted }
            .sorted { $0.priority > $1.priority } ?? []
        // Carry up to the largest family's limit; the view trims per its actual family.
        let rows = open.prefix(8).map { TasksEntry.Row(id: $0.id, title: $0.title) }
        return TasksEntry(date: .now, rows: Array(rows), openCount: open.count)
    }
}

struct TasksWidgetView: View {
    var entry: TasksEntry
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
        // Wrap the entire card in a Link so tapping anywhere opens the Tasks section in the app.
        Link(destination: URL(string: "chronosflow://tasks")!) {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Image(systemName: "checklist")
                    Text("\(entry.openCount) open").font(.caption).foregroundStyle(.secondary)
                }
                ForEach(entry.rows.prefix(rowLimit)) { row in
                    HStack(spacing: 6) {
                        // Tap the circle to complete the task in place (Android TaskComplete).
                        Button(intent: CompleteTaskIntent(taskID: row.id)) {
                            Image(systemName: "circle").font(.caption2).foregroundStyle(.secondary)
                        }
                        .buttonStyle(.plain)
                        Text(row.title).font(.caption).lineLimit(1)
                    }
                }
                Spacer()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        }
        .containerBackground(.fill.tertiary, for: .widget)
    }
}

#Preview(as: .systemMedium) {
    TasksWidget()
} timeline: {
    TasksEntry(date: .now, rows: [
        .init(id: "1", title: "Finish report"),
        .init(id: "2", title: "Reply to email"),
        .init(id: "3", title: "Buy groceries"),
    ], openCount: 5)
    TasksEntry(date: .now, rows: [], openCount: 0)
}

struct TasksWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ChronosTasksWidget", provider: TasksProvider()) { entry in
            TasksWidgetView(entry: entry)
        }
        .configurationDisplayName("Tasks")
        .description("Your open tasks at a glance.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}
