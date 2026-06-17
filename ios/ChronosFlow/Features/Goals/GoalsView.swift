import SwiftUI
import SwiftData

/// The Goals tab: measurable objectives with progress rolled up from manual entry + linked
/// task/habit completions. Ports `feature/goals`.
struct GoalsView: View {
    @Environment(\.modelContext) private var context
    @Query(sort: \Goal.startDate, order: .reverse) private var goals: [Goal]
    @Query private var tasks: [TaskItem]
    @Query private var habits: [Habit]
    @State private var creating = false

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: ChronosSpacing.compact) {
                    ForEach(goals) { goal in
                        GoalCard(
                            goal: goal,
                            completedTasks: tasks.filter { $0.goalID == goal.id && $0.isCompleted }.count,
                            habitCompletions: habits.filter { $0.goalID == goal.id }
                                .reduce(0) { $0 + $1.completionDates.count }
                        )
                    }
                }
                .padding(ChronosSpacing.standard)
            }
            .background { ChronosBackdrop() }
            .navigationTitle("Goals")
            .chronosScrollMinimizedBar()
            .overlay { if goals.isEmpty { ContentUnavailableView("No goals", systemImage: "flag", description: Text("Set a measurable objective")) } }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { creating = true } label: { Image(systemName: "plus") }
                }
            }
            .sheet(isPresented: $creating) { GoalEditorSheet() }
        }
    }
}

private struct GoalCard: View {
    @Environment(\.modelContext) private var context
    @Bindable var goal: Goal
    let completedTasks: Int
    let habitCompletions: Int

    private var fraction: Double {
        goal.progressFraction(completedTasks: completedTasks, habitCompletions: habitCompletions)
    }
    private var total: Int {
        goal.totalProgress(completedTasks: completedTasks, habitCompletions: habitCompletions)
    }

    var body: some View {
        ChronosGlassCard(tint: ChronosColors.category(goal.category)) {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                HStack {
                    Text(goal.title).font(.chronosHeadline)
                    Spacer()
                    Text("\(total)/\(goal.targetValue)").font(.chronosLabel).foregroundStyle(.secondary)
                }
                ProgressView(value: fraction)
                    .tint(ChronosColors.category(goal.category))
                HStack {
                    Text(goal.category.capitalized).font(.chronosCaption).foregroundStyle(.secondary)
                    Spacer()
                    Button("+1") {
                        withAnimation(ChronosMotion.bouncy) {
                            goal.progressValue += 1; try? context.save()
                        }
                    }
                    .font(.chronosCaption)
                    .buttonStyle(.bordered)
                    .buttonBorderShape(.capsule)
                }
            }
            .frame(maxWidth: .infinity)
        }
    }
}

struct GoalEditorSheet: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var category = "GENERAL"
    @State private var target = 10.0

    var body: some View {
        NavigationStack {
            Form {
                TextField("Goal", text: $title)
                Picker("Category", selection: $category) {
                    ForEach(["GENERAL", "FITNESS", "LEARNING", "WORK", "HEALTH"], id: \.self) {
                        Text($0.capitalized).tag($0)
                    }
                }
                VStack(alignment: .leading) {
                    Text("Target: \(Int(target))")
                    Slider(value: $target, in: 1...100, step: 1)
                }
            }
            .navigationTitle("New goal")
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        context.insert(Goal(title: title, category: category, targetValue: Int(target)))
                        try? context.save(); dismiss()
                    }.disabled(title.isEmpty)
                }
            }
        }
        .presentationDetents([.medium])
    }
}

#Preview { GoalsView().modelContainer(ChronosStore.previewContainer()) }
