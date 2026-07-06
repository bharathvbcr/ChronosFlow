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
    @Environment(ShellState.self) private var shell: ShellState?
    @Query(sort: \Goal.startDate, order: .reverse) private var goals: [Goal]
    @Query private var tasks: [TaskItem]
    @Query private var habits: [Habit]

    @State private var creating = false
    @State private var detail: Goal?
    @State private var selectedCategory: String?
    // Completed goals stay tucked behind a toggle so active work leads the page (mirrors
    // Android's `rememberSaveable { mutableStateOf(false) }` default in GoalScreen.kt).
    @State private var showCompleted = false
    /// Scroll target for deep links (`chronosflow://goals?id=…`).
    @State private var scrollTarget: String?

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
            chromedSurface
            .overlay {
                if goals.isEmpty {
                    ContentUnavailableView {
                        Label("No goals yet", systemImage: "flag")
                    } description: {
                        Text("Add a goal, then link tasks and habits so completing them moves you forward.")
                    } actions: {
                        Button("Add goal") { creating = true }
                            .buttonStyle(.borderedProminent)
                            .tint(ChronosColors.brandPrimary)
                    }
                }
            }
            .sheet(isPresented: $creating) { GoalEditorSheet(goal: nil) }
            .sheet(item: $detail) { GoalDetailView(goal: $0) }
            .onAppear { drainPendingDeepLinkGoal() }
            .onChange(of: shell?.pendingGoalID) { _, id in
                if id != nil { drainPendingDeepLinkGoal() }
            }
        }
    }

    private var chromedSurface: some View {
        goalsSurface
            .navigationTitle("Goals")
            .chronosScrollMinimizedBar()
            .chronosCommandPaletteToolbar()
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { creating = true } label: { Image(systemName: "plus") }
                        .accessibilityLabel("Add goal")
                }
            }
    }

    private var goalsSurface: some View {
        ZStack {
            ChronosBackdrop()
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: ChronosSpacing.compact) {
                        goalsSection {
                            sectionHeader("Goals",
                                          subtitle: "Long-term objectives that your daily tasks and habits roll up into.")
                        }

                        if !goals.isEmpty { goalsSection { metricTiles } }
                        if overdueCount > 0 { goalsSection { overdueBanner } }
                        if !goals.isEmpty { goalsSection { addGoalButton } }
                        if categoriesInUse.count >= 2 { goalsSection { categoryFilter } }

                        let active = filtered(activeGoals)
                        let completed = filtered(completedGoals)

                        if !goals.isEmpty && active.isEmpty && completed.isEmpty {
                            goalsSection { filterEmptyState }
                        }

                        if !active.isEmpty {
                            goalsSection {
                                sectionHeader("Active", subtitle: "\(active.count) in progress")
                            }
                            ForEach(active) { goal in goalsSection { card(for: goal) } }
                        }

                        if !completed.isEmpty {
                            goalsSection {
                                HStack(alignment: .firstTextBaseline) {
                                    sectionHeader("Completed", subtitle: "\(completed.count) achieved")
                                    Spacer()
                                    Button(showCompleted ? "Hide" : "Show") {
                                        withAnimation(ChronosMotion.snappy) { showCompleted.toggle() }
                                    }
                                    .font(.chronosCaption)
                                    .foregroundStyle(ChronosColors.brandPrimary)
                                    .buttonStyle(.plain)
                                }
                            }
                            if showCompleted {
                                ForEach(completed) { goal in goalsSection { card(for: goal) } }
                            }
                        }
                    }
                    .padding(.vertical, ChronosSpacing.standard)
                }
                .scrollContentBackground(.hidden)
                .onChange(of: scrollTarget) { _, target in
                    guard let target else { return }
                    withAnimation(ChronosMotion.snappy) { proxy.scrollTo(target, anchor: .center) }
                    scrollTarget = nil
                }
            }
        }
    }

    private func goalsSection<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        content().padding(.horizontal, ChronosSpacing.standard)
    }

    /// Deep link (`chronosflow://goals?id=…`) — scroll to the goal card and open its detail sheet.
    private func drainPendingDeepLinkGoal() {
        guard let id = shell?.pendingGoalID else { return }
        shell?.pendingGoalID = nil
        guard let goal = goals.first(where: { $0.id == id }) else { return }
        if goal.isCompleted { showCompleted = true }
        scrollTarget = id
        detail = goal
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
        .id(goal.id)
    }

    // MARK: Banner / tiles / filter

    private var overdueBanner: some View {
        ChronosGlassCard(tone: .standard, tint: ChronosColors.brandAccent) {
            HStack(alignment: .top, spacing: ChronosSpacing.small) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(ChronosColors.brandAccent)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Overdue").font(.chronosLabel)
                    Text(GoalLabels.goalsOverdueMessage(overdueCount))
                        .font(.chronosCaption).foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    /// Inline "Add goal" action, mirroring Android's filled-tonal button in the list (the toolbar
    /// "+" remains for quick access). Both open the same `GoalEditorSheet`.
    private var addGoalButton: some View {
        Button {
            creating = true
        } label: {
            Label("Add goal", systemImage: "plus")
                .font(.chronosLabel.weight(.semibold))
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
    }

    /// Shown when a category filter excludes every goal — mirrors Android's "Nothing in {category}".
    private var filterEmptyState: some View {
        ContentUnavailableView(
            "Nothing in \(selectedCategory?.capitalized ?? "this category")",
            systemImage: "line.3.horizontal.decrease.circle",
            description: Text("No goals match this category yet. Switch the filter or add a new goal.")
        )
        .frame(maxWidth: .infinity)
        .padding(.vertical, ChronosSpacing.large)
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
                        // Exclusive selection (mirrors Android's ChronosOptionChips): tapping a
                        // category always selects it; re-tapping does NOT toggle it off. Use the
                        // "All" chip to clear the filter.
                        withAnimation(ChronosMotion.snappy) { selectedCategory = cat }
                    }
                }
            }
            .padding(.vertical, ChronosSpacing.micro)
        }
    }

    /// Section header with an optional supporting subtitle, mirroring Android's
    /// `ChronosSectionHeader(title, subtitle)`.
    private func sectionHeader(_ title: String, subtitle: String? = nil) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.chronosTitle)
            if let subtitle {
                Text(subtitle).font(.chronosCaption).foregroundStyle(.secondary)
            }
        }
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

    @State private var editing = false

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
                    .accessibilityLabel(goal.isCompleted ? "Mark \(goal.title) incomplete" : "Mark \(goal.title) complete")

                    Text(goal.title).font(.chronosHeadline).strikethrough(goal.isCompleted)
                    Spacer()
                }

                // Progress bar with the human-readable summary beside it ("X of Y · Z%"),
                // mirroring Android's GoalCard progress row.
                HStack(spacing: ChronosSpacing.small) {
                    ProgressView(value: fraction).tint(tint)
                    Text(goalProgressSummary(progress: total, target: goal.targetValue))
                        .font(.chronosLabel)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

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
                    Button { editing = true } label: { Image(systemName: "pencil") }
                        .buttonStyle(.bordered).buttonBorderShape(.capsule)
                        .frame(minWidth: 44, minHeight: 44)
                        .accessibilityLabel("Edit goal")
                    Button { adjust(-1) } label: { Image(systemName: "minus") }
                        .buttonStyle(.bordered).buttonBorderShape(.capsule)
                        .frame(minWidth: 44, minHeight: 44)
                        .disabled(goal.progressValue <= 0)
                        .accessibilityLabel("Decrease progress")
                    // Raw manual progress value (mirrors Android's "Manual progress" row).
                    Text("\(goal.progressValue)")
                        .font(.chronosLabel.weight(.semibold))
                        .monospacedDigit()
                        .accessibilityLabel("Manual progress \(goal.progressValue)")
                    Button("+1") { adjust(1) }
                        .buttonStyle(.bordered).buttonBorderShape(.capsule)
                        .frame(minWidth: 44, minHeight: 44)
                        .accessibilityLabel("Increase progress")
                }
                .font(.chronosCaption)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onOpen)
        // The whole-card tap opens the goal detail; expose it to VoiceOver too (the inline
        // edit/±progress buttons stay individually navigable via `.contain`). Additive — no
        // existing child semantics change.
        .accessibilityElement(children: .contain)
        .accessibilityAction(named: Text("Open goal")) { onOpen() }
        .pressable()
        .sheet(isPresented: $editing) { GoalEditorSheet(goal: goal) }
        .sensoryFeedback(.success, trigger: goal.isCompleted) { _, done in done }
        .sensoryFeedback(.selection, trigger: goal.progressValue)
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
                .foregroundStyle(selected ? ChronosColors.onBrand : .primary)
        }
        .buttonStyle(.plain)
        // Selection is shown by fill color; announce it to VoiceOver too (§7), matching the
        // canonical `SelectChip`.
        .accessibilityAddTraits(selected ? .isSelected : [])
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

    /// `initialTitle` seeds the title field for a new goal (palette / quick-capture prefill,
    /// the Android `prefillName` pattern); ignored when editing an existing goal.
    init(goal: Goal?, initialTitle: String? = nil) {
        self.goal = goal
        _title = State(initialValue: goal?.title ?? initialTitle ?? "")
        _detail = State(initialValue: goal?.detail ?? "")
        _category = State(initialValue: goal?.category ?? GoalLabels.defaultCategory)
        _target = State(initialValue: goal?.targetValue ?? 10)
        _hasTargetDate = State(initialValue: goal?.targetDate != nil)
        _targetDate = State(initialValue: goal?.targetDate ?? Calendar.current.date(byAdding: .month, value: 1, to: .now) ?? .now)
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
        CardEditorScaffold(
            kind: "goal",
            navTitle: goal == nil ? "New goal" : "Edit goal",
            chips: goalChips,
            sections: goalSections,
            saveDisabled: trimmedTitle.isEmpty,
            onCancel: { dismiss() },
            onSave: save
        ) {
            goalHeader
        }
    }

    // MARK: - Scaffold pieces

    @ViewBuilder private var goalHeader: some View {
        TextField("Goal", text: $title)
        TextField("Description (optional)", text: $detail, axis: .vertical)
            .lineLimit(1...4)
    }

    private var goalChips: [EditorChip] {
        [EditorChip(id: "details", systemImage: "calendar", title: "Deadline",
                    value: hasTargetDate ? targetDate.formatted(date: .abbreviated, time: .omitted) : nil,
                    onClear: { withAnimation(ChronosMotion.snappy) { hasTargetDate = false } })]
    }

    private var goalSections: [EditorSection] {
        var list: [EditorSection] = [
            EditorSection(id: "category", title: "Category", systemImage: "tag", hasValue: true) { categoryRows },
            EditorSection(id: "target", title: "Target", systemImage: "target", hasValue: true) { targetRows },
            EditorSection(id: "details", title: "Details", systemImage: "calendar",
                          hasValue: hasTargetDate) { detailsRows },
        ]
        if goal != nil {
            list.append(EditorSection(id: "progress", title: "Progress", systemImage: "chart.bar",
                                      hasValue: true) { progressRows })
        }
        return list
    }

    @ViewBuilder private var categoryRows: some View {
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

    @ViewBuilder private var targetRows: some View {
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

    @ViewBuilder private var detailsRows: some View {
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
    }

    @ViewBuilder private var progressRows: some View {
        if let goal {
            ProgressView(value: goalProgressFraction(progressValue: goal.progressValue,
                                                     targetValue: target,
                                                     isCompleted: goal.isCompleted))
                .tint(ChronosColors.category(category))
            Text(goalProgressSummary(progress: goal.progressValue, target: target))
                .font(.chronosCaption).foregroundStyle(.secondary)
        }
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
