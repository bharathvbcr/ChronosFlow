import WidgetKit
import SwiftUI
import SwiftData

// MARK: - Tasks widget (open tasks for today) — ports TasksGlanceWidget.

struct TasksEntry: TimelineEntry {
    let date: Date
    struct Row: Identifiable { let id: String; let title: String }
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
        let rows = open.prefix(4).map { TasksEntry.Row(id: $0.id, title: $0.title) }
        return TasksEntry(date: .now, rows: Array(rows), openCount: open.count)
    }
}

struct TasksWidgetView: View {
    var entry: TasksEntry
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Image(systemName: "checklist")
                Text("\(entry.openCount) open").font(.caption).foregroundStyle(.secondary)
            }
            ForEach(entry.rows) { row in
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
        .containerBackground(.fill.tertiary, for: .widget)
    }
}

struct TasksWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ChronosTasksWidget", provider: TasksProvider()) { entry in
            TasksWidgetView(entry: entry)
        }
        .configurationDisplayName("Tasks")
        .description("Your open tasks at a glance.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
