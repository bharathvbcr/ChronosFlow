import SwiftUI
import SwiftData

/// The Tasks tab: prioritized to-dos with checklists, scheduling hints, and goal links.
/// Ports `feature/tasks`.
struct TasksView: View {
    @Environment(\.modelContext) private var context
    @Query(sort: [SortDescriptor(\TaskItem.priority, order: .reverse),
                  SortDescriptor(\TaskItem.createdAt)]) private var tasks: [TaskItem]
    @State private var activeSheet: TaskSheet?

    private var open: [TaskItem] { tasks.filter { !$0.isCompleted } }
    private var done: [TaskItem] { tasks.filter(\.isCompleted) }

    private enum TaskSheet: Identifiable {
        case edit(TaskItem)
        case new
        var id: String { if case .edit(let t) = self { "edit-\(t.id)" } else { "new" } }
    }

    var body: some View {
        NavigationStack {
            List {
                Section("Open") {
                    ForEach(open) { task in TaskRow(task: task) { activeSheet = .edit(task) } }
                        .onDelete { delete($0, from: open) }
                }
                if !done.isEmpty {
                    Section("Completed") {
                        ForEach(done) { task in TaskRow(task: task) { activeSheet = .edit(task) } }
                            .onDelete { delete($0, from: done) }
                    }
                }
            }
            .navigationTitle("Tasks")
            .chronosScrollMinimizedBar()
            .overlay { if tasks.isEmpty { ContentUnavailableView("No tasks", systemImage: "checklist", description: Text("Tap + to add your first task")) } }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { activeSheet = .new } label: { Image(systemName: "plus") }
                }
            }
            .sheet(item: $activeSheet) { sheet in
                switch sheet {
                case .edit(let task): TaskEditorSheet(task: task)
                case .new: TaskEditorSheet(task: nil)
                }
            }
        }
    }

    private func delete(_ offsets: IndexSet, from list: [TaskItem]) {
        offsets.map { list[$0] }.forEach(context.delete)
        try? context.save()
    }
}

private struct TaskRow: View {
    @Environment(\.modelContext) private var context
    let task: TaskItem
    let onEdit: () -> Void

    var body: some View {
        HStack(spacing: ChronosSpacing.compact) {
            Button {
                withAnimation(ChronosMotion.snappy) { complete() }
            } label: {
                Image(systemName: task.isCompleted ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(task.isCompleted ? ChronosColors.brandSecondary : .secondary)
            }
            .buttonStyle(.plain)

            VStack(alignment: .leading, spacing: 2) {
                Text(task.title).strikethrough(task.isCompleted)
                HStack(spacing: ChronosSpacing.small) {
                    if task.priority > 0 {
                        Text(task.priorityLabel).font(.chronosCaption)
                            .foregroundStyle(priorityColor)
                    }
                    if let due = task.dueDate {
                        Label(due.formatted(.dateTime.month().day()), systemImage: "calendar")
                            .font(.chronosCaption).foregroundStyle(.secondary)
                    }
                    if let rec = task.recurrence {
                        Label(rec.label, systemImage: "repeat")
                            .font(.chronosCaption).foregroundStyle(.secondary)
                    }
                    if !task.checklist.isEmpty {
                        Text("\(task.checklist.filter(\.isDone).count)/\(task.checklist.count)")
                            .font(.chronosCaption).foregroundStyle(.secondary)
                    }
                }
            }
            Spacer()
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onEdit)
    }

    private var priorityColor: Color {
        switch task.priority {
        case 3: ChronosColors.brandAccent
        case 2: ChronosColors.brandPrimary
        default: .secondary
        }
    }

    /// Toggle completion; when completing a recurring task, spawn its next occurrence.
    private func complete() {
        let wasCompleted = task.isCompleted
        task.isCompleted.toggle()
        if !wasCompleted, let rec = task.recurrence {
            let anchor = task.dueDate ?? task.targetDate ?? .now
            if let next = rec.nextDate(after: anchor) {
                context.insert(TaskItem(
                    title: task.title, detail: task.detail, priority: task.priority,
                    dueDate: task.dueDate != nil ? next : nil,
                    targetDate: task.targetDate != nil ? next : nil,
                    goalID: task.goalID, recurrence: rec,
                    checklist: task.checklist.map { ChecklistItem(text: $0.text, isDone: false, order: $0.order) }))
            }
        }
        try? context.save()
    }
}

#Preview {
    TasksView().modelContainer(ChronosStore.previewContainer())
}
