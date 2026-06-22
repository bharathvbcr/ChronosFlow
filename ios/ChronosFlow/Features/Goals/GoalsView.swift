import SwiftUI
import SwiftData
import ChronosCore

/// The Goals tab: measurable objectives with progress rolled up from manual entry + linked
/// task/habit completions. Ports `feature/goals` (GoalScreen.kt).
///
/// Goals split into Active / Completed sections; an overdue banner and metric tiles sit above
/// the list, and a category filter row appears once two or more categories are in use. Each card
/// taps through to `GoalDetailView` for the linked-work breakdown. The list math (overdue counts,
/// due-date chips, linked-work hints) is all delegated to the shared `GoalLabels` helpers.
struct GoalsView: View {
    @Environment(\.modelContext) private var context
    @Query(sort: \Goal.startDate, order: .reverse) private var goals: [Goal]
    @Query private var tasks: [TaskItem]
    @Query private var habits: [Habit]

    @State private var creating = false
    @State private var detail: Goal?
    @State private var selectedCategory: String?
    @State private var showCompleted = true

    // MARK: Derived collections

    private var activeGoals: [Goal] { goals.filter { !$0.isCompleted } }
    private var completedGoals: [Goal] { goals.filter(\.isCompleted) }

    private var categoriesInUse: [String] {
        GoalLabels.categoriesInUse(goals.map(\.category))
    }

    private func filtered(_ list: [Goal]) -> [Goal] {
        guard let selectedCategory else { return list }
        return list.filter { $0.category == selectedCategory }
    }

    private var overdueCount: Int {
        GoalLabels.goalsOverdueCount(
            targetDates: activeGoals.map { ($0.targetDate, $0.isCompleted) },
            today: .now
        )
    }

    // MARK: Body

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: ChronosSpacing.compact) {
                    if overdueCount > 0 { overdueBanner }
                    if !goals.isEmpty { metricTiles }
                    if categoriesInUse.count >= 2 { categoryFilter }

                    let active = filtered(activeGoals)
                    if !active.isEmpty {
                        sectionHeader("Active")
                        ForEach(active) { card(for: $0) }
                    }

                    let completed = filtered(completedGoals)
                    if !completed.isEmpty {
                        HStack {
                            sectionHeader("Completed")
                            Spacer()
                            Button(showCompleted ? "Hide" : "Show") {
                                withAnimation(ChronosMotion.snappy) { showCompleted.toggle() }
                            }
                            .font(.chronosCaption)
                        }
                        if showCompleted {
                            ForEach(completed) { card(for: $0) }
                        }
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
            .sheet(isPresented: $creating) { GoalEditorSheet(goal: nil) }
            .sheet(item: $detail) { GoalDetailView(goal: $0) }
        }
    }

    @ViewBuilder
    private func card(for goal: Goal) -> some View {
        GoalCard(
            goal: goal,
            completedTasks: tasks.filter { $0.goalID == goal.id && $0.isCompleted }.count,
            habitCompletions: habits.filter { $0.goalID == goal.id }
                .reduce(0) { $0 + $1.completionDates.count },
            onOpen: { detail = goal }
        )
    }

    // MARK: Banner / tiles / filter

    private var overdueBanner: some View {
        ChronosGlassCard(tone: .standard, tint: ChronosColors.brandAccent) {
            Label(GoalLabels.goalsOverdueMessage(overdueCount), systemImage: "exclamationmark.triangle.fill")
                .font(.chronosLabel)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var metricTiles: some View {
        HStack(spacing: ChronosSpacing.compact) {
            MetricTile(value: activeGoals.count, label: "Active", tint: ChronosColors.brandPrimary)
            MetricTile(value: completedGoals.count, label: "Completed", tint: ChronosColors.brandSecondary)
        }
    }

    private var categoryFilter: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: ChronosSpacing.small) {
                FilterChip(label: "All", selected: selectedCategory == nil) {
                    withAnimation(ChronosMotion.snappy) { selectedCategory = nil }
                }
                ForEach(categoriesInUse, id: \.self) { cat in
                    FilterChip(label: cat.capitalized, selected: selectedCategory == cat,
                               tint: ChronosColors.category(cat)) {
                        withAnimation(ChronosMotion.snappy) {
                            selectedCategory = (selectedCategory == cat) ? nil : cat
                        }
                    }
                }
            }
            .padding(.vertical, ChronosSpacing.micro)
        }
    }

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(.chronosTitle)
            .padding(.top, ChronosSpacing.small)
    }
}

// MARK: - Goal card

private struct GoalCard: View {
    @Environment(\.modelContext) private var context
    @Bindable var goal: Goal
    let completedTasks: Int
    let habitCompletions: Int
    let onOpen: () -> Void

    private var derived: GoalDerivedProgress {
        GoalDerivedProgress(completedTaskCount: completedTasks, habitCompletionCount: habitCompletions)
    }
    private var fraction: Double {
        goal.progressFraction(completedTasks: completedTasks, habitCompletions: habitCompletions)
    }
    private var total: Int {
        goal.totalProgress(completedTasks: completedTasks, habitCompletions: habitCompletions)
    }
    private var tint: Color { ChronosColors.category(goal.category) }
    private var dueLabel: GoalDueLabel? {
        GoalLabels.goalDueLabel(targetDate: goal.targetDate, isCompleted: goal.isCompleted, today: .now)
    }

    var body: some View {
        ChronosGlassCard(tint: tint) {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                HStack(spacing: ChronosSpacing.small) {
                    Button {
                        withAnimation(ChronosMotion.bouncy) {
                            goal.isCompleted.toggle(); try? context.save()
                        }
                    } label: {
                        Image(systemName: goal.isCompleted ? "checkmark.circle.fill" : "circle")
                            .font(.title3)
                            .foregroundStyle(goal.isCompleted ? tint : .secondary)
                    }
                    .buttonStyle(.plain)

                    Text(goal.title).font(.chronosHeadline).strikethrough(goal.isCompleted)
                    Spacer()
                    Text("\(total)/\(goal.targetValue)").font(.chronosLabel).foregroundStyle(.secondary)
                }

                ProgressView(value: fraction).tint(tint)

                HStack(spacing: ChronosSpacing.small) {
                    Text(goal.category.capitalized).font(.chronosCaption).foregroundStyle(.secondary)
                    if let due = dueLabel {
                        Text(due.text)
                            .font(.chronosCaption)
                            .foregroundStyle(due.emphasized ? ChronosColors.brandAccent : .secondary)
                    }
                    if let hint = GoalLabels.goalLinkedWorkLabel(derived) {
                        Text(hint).font(.chronosCaption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button { adjust(-1) } label: { Image(systemName: "minus") }
                        .buttonStyle(.bordered).buttonBorderShape(.capsule)
                        .disabled(goal.progressValue <= 0)
                    Button("+1") { adjust(1) }
                        .buttonStyle(.bordered).buttonBorderShape(.capsule)
                }
                .font(.chronosCaption)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onOpen)
    }

    /// Manual progress adjustment, clamped to 0...targetValue (mirrors Android `adjustProgress`).
    private func adjust(_ delta: Int) {
        withAnimation(ChronosMotion.bouncy) {
            let next = goal.progressValue + delta
            goal.progressValue = max(0, min(next, max(goal.targetValue, 0)))
            try? context.save()
        }
    }
}

// MARK: - Small reusable bits

private struct MetricTile: View {
    let value: Int
    let label: String
    let tint: Color
    var body: some View {
        ChronosGlassCard(tone: .quiet, tint: tint) {
            VStack(spacing: 2) {
                Text("\(value)").font(.chronosTitleLarge).foregroundStyle(tint)
                Text(label).font(.chronosCaption).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
        }
    }
}

private struct FilterChip: View {
    let label: String
    let selected: Bool
    var tint: Color = ChronosColors.brandPrimary
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.chronosCaption)
                .padding(.horizontal, ChronosSpacing.compact)
                .padding(.vertical, ChronosSpacing.small)
                .background(selected ? tint : Color(.tertiarySystemFill), in: Capsule())
                .foregroundStyle(selected ? .white : .primary)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Editor

/// Create / edit form for a goal. Ports `GoalFormSheet.kt`: title + optional description, a
/// category chip row with custom entry and a keyword-based suggestion, a target count with
/// quick-pick chips and a title-extraction shortcut, and a collapsible "Details" section for the
/// target date (with deadline presets + past-date warning). In edit mode it also shows the live
/// progress bar and a target-below-progress warning.
struct GoalEditorSheet: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    let goal: Goal?

    @State private var title: String
    @State private var detail: String
    @State private var category: String
    @State private var customCategory: String = ""
    @State private var target: Int
    @State private var hasTargetDate: Bool
    @State private var targetDate: Date
    @State private var detailsExpanded: Bool

    init(goal: Goal?) {
        self.goal = goal
        _title = State(initialValue: goal?.title ?? "")
        _detail = State(initialValue: goal?.detail ?? "")
        _category = State(initialValue: goal?.category ?? GoalLabels.defaultCategory)
        _target = State(initialValue: goal?.targetValue ?? 10)
        _hasTargetDate = State(initialValue: goal?.targetDate != nil)
        _targetDate = State(initialValue: goal?.targetDate ?? Calendar.current.date(byAdding: .month, value: 1, to: .now) ?? .now)
        // Auto-expand details when editing a goal that already has a target date.
        _detailsExpanded = State(initialValue: goal?.targetDate != nil)
    }

    private var trimmedTitle: String { title.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var suggestion: String? {
        guard let s = GoalLabels.suggestGoalCategory(trimmedTitle), s != category else { return nil }
        return s
    }
    private var titleTarget: Int? {
        guard let t = GoalLabels.goalTargetFromTitle(trimmedTitle), t != target else { return nil }
        return t
    }
    private var categoryChoices: [String] {
        GoalLabels.categoryOptions(presets: GoalLabels.categoryOptions,
                                   existingCategory: goal?.category, customCategory: customCategory)
    }
    private var belowProgressWarning: String? {
        guard let goal else { return nil }
        return GoalLabels.goalTargetBelowProgressWarning(target: target, progress: goal.progressValue)
    }
    private var dateWarning: String? {
        guard hasTargetDate else { return nil }
        return GoalLabels.goalTargetDateWarning(targetDate: targetDate, today: .now)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Goal", text: $title)
                    TextField("Description (optional)", text: $detail, axis: .vertical)
                        .lineLimit(1...4)
                }

                Section("Category") {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: ChronosSpacing.small) {
                            ForEach(categoryChoices, id: \.self) { choice in
                                FilterChip(label: choice.capitalized, selected: category == choice,
                                           tint: ChronosColors.category(choice)) {
                                    withAnimation(ChronosMotion.snappy) { category = choice }
                                }
                            }
                        }
                    }
                    if let suggestion {
                        Button {
                            withAnimation(ChronosMotion.snappy) { category = suggestion }
                        } label: {
                            Label("Suggested: \(suggestion.capitalized)", systemImage: "wand.and.stars")
                                .font(.chronosCaption)
                        }
                    }
                    TextField("Custom category", text: $customCategory)
                        .onSubmit {
                            let c = customCategory.trimmingCharacters(in: .whitespacesAndNewlines)
                            if !c.isEmpty { category = c }
                        }
                }

                Section("Target") {
                    Stepper("Target: \(target)", value: $target, in: 1...99999)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: ChronosSpacing.small) {
                            ForEach(GoalLabels.targetQuickPicks, id: \.self) { pick in
                                FilterChip(label: "\(pick)", selected: target == pick) {
                                    withAnimation(ChronosMotion.snappy) { target = pick }
                                }
                            }
                        }
                    }
                    if let titleTarget {
                        Button {
                            withAnimation(ChronosMotion.snappy) { target = titleTarget }
                        } label: {
                            Label("Use \(titleTarget) from title", systemImage: "text.magnifyingglass")
                                .font(.chronosCaption)
                        }
                    }
                    if let belowProgressWarning {
                        Text(belowProgressWarning).font(.chronosCaption).foregroundStyle(.red)
                    }
                }

                Section(isExpanded: $detailsExpanded) {
                    Toggle("Set a target date", isOn: $hasTargetDate.animation(ChronosMotion.snappy))
                    if hasTargetDate {
                        DatePicker("Target date", selection: $targetDate, displayedComponents: .date)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: ChronosSpacing.small) {
                                ForEach(GoalLabels.goalDeadlinePresets(today: .now), id: \.label) { preset in
                                    FilterChip(label: preset.label,
                                               selected: Calendar.current.isDate(targetDate, inSameDayAs: preset.date)) {
                                        withAnimation(ChronosMotion.snappy) { targetDate = preset.date }
                                    }
                                }
                            }
                        }
                        if let dateWarning {
                            Text(dateWarning).font(.chronosCaption).foregroundStyle(.red)
                        }
                    }
                } header: {
                    Text("Details")
                }

                if let goal {
                    Section("Progress") {
                        ProgressView(value: goalProgressFraction(progressValue: goal.progressValue,
                                                                 targetValue: target,
                                                                 isCompleted: goal.isCompleted))
                            .tint(ChronosColors.category(category))
                        Text(goalProgressSummary(progress: goal.progressValue, target: target))
                            .font(.chronosCaption).foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle(goal == nil ? "New goal" : "Edit goal")
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }.disabled(trimmedTitle.isEmpty)
                }
            }
        }
        .presentationDetents([.large])
    }

    private func save() {
        let resolvedCategory: String = {
            let c = customCategory.trimmingCharacters(in: .whitespacesAndNewlines)
            return c.isEmpty ? category : c
        }()
        let resolvedDate = hasTargetDate ? Calendar.current.startOfDay(for: targetDate) : nil
        let trimmedDetail = detail.trimmingCharacters(in: .whitespacesAndNewlines)

        if let goal {
            goal.title = trimmedTitle
            goal.detail = trimmedDetail.isEmpty ? nil : trimmedDetail
            goal.category = resolvedCategory
            goal.targetValue = target
            goal.targetDate = resolvedDate
        } else {
            context.insert(Goal(
                title: trimmedTitle,
                detail: trimmedDetail.isEmpty ? nil : trimmedDetail,
                category: resolvedCategory,
                targetValue: target,
                targetDate: resolvedDate
            ))
        }
        try? context.save()
        dismiss()
    }
}

// MARK: - Local label helper

/// Human-readable progress, e.g. "3 of 10 · 30%". Mirrors Android's `goalProgressSummary`
/// (an over-achieved count is capped to the target; a target ≤ 0 falls back to a raw count).
/// Kept local to the Goals feature since it is presentation-only string formatting.
func goalProgressSummary(progress: Int, target: Int) -> String {
    guard target > 0 else { return "\(progress) logged" }
    let capped = max(0, min(progress, target))
    let percent = Int((goalProgressFraction(progressValue: progress, targetValue: target, isCompleted: false) * 100).rounded())
    return "\(capped) of \(target) · \(percent)%"
}

#Preview { GoalsView().modelContainer(ChronosStore.previewContainer()) }
