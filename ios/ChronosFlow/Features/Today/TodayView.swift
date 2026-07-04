import SwiftUI
import SwiftData

/// The Today tab: the read-only day dial plus a calm "what's now / next" view over the day, quick
/// check-in, and the sleep-readiness banner that adapts the day. Ports the Today shell target.
struct TodayView: View {
    @Environment(\.modelContext) private var context
    /// Shared browsed day (Android `selectDate()` re-derives all tabs). Optional so previews
    /// without a shell fall back to today — same pattern as `DayDialScreen`.
    @Environment(ShellState.self) private var shell: ShellState?
    @Query private var allBlocks: [TimeBlock]
    @Query private var tasks: [TaskItem]
    @Query private var sleepNights: [SleepTrack]
    @Query(sort: \JournalEntry.entryDate, order: .reverse) private var journalEntries: [JournalEntry]
    @Query private var habits: [Habit]
    /// Active medication plans for the quick mark-taken row (Android TodayTab medication items).
    @Query(filter: #Predicate<MedicationPlan> { $0.isActive }, sort: \MedicationPlan.name)
    private var medicationPlans: [MedicationPlan]
    /// Cross-tab surfacing of the running focus session (Android's shell-level focus state).
    @State private var focusTimer = FocusTimerModel.shared
    @State private var activeSheet: TodaySheet?
    /// Increments on a med mark-taken to fire a one-shot success haptic (MedicationView idiom).
    @State private var medTakeTick = 0

    private enum TodaySheet: String, Identifiable {
        case assistant, checkIn, data, newTask, newBlock, logSleep, journal, planDay, fillGaps
        var id: String { rawValue }
    }

    private var nowMinute: Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: .now)
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }

    /// The browsed day, shared with the Plan dial via the shell (today when no shell is injected).
    private var browsedDate: Date { shell?.selectedDate ?? Calendar.current.startOfDay(for: .now) }

    /// Now/next emphasis and the quick action rows only apply when the browsed day IS today
    /// (Android `isViewingToday` in `TodayTab` / `findActiveBlock(forToday:)`).
    private var isViewingToday: Bool { Calendar.current.isDateInToday(browsedDate) }

    private var dayBlocks: [TimeBlock] {
        allBlocks.filter { Calendar.current.isDate($0.date, inSameDayAs: browsedDate) }
            .sorted { $0.startMinuteOfDay < $1.startMinuteOfDay }
    }

    private var current: TimeBlock? {
        guard isViewingToday else { return nil }
        return dayBlocks.first { nowMinute >= $0.startMinuteOfDay && nowMinute < $0.startMinuteOfDay + $0.durationMinutes }
    }

    /// Today: the next block to start. Off-today: the day's first block (Android `findNextBlock`
    /// returns the earliest block when not viewing today).
    private var upNext: TimeBlock? {
        guard isViewingToday else { return dayBlocks.first }
        return dayBlocks.first { $0.startMinuteOfDay > nowMinute }
    }

    private var lastNight: SleepTrack? {
        sleepNights.max { $0.date < $1.date }
    }

    private var readiness: SleepReadiness { deriveSleepReadiness(lastNight: lastNight) }

    private var dayTasks: [TaskItem] {
        tasks.filter { task in
            guard let target = task.targetDate else { return false }
            return Calendar.current.isDate(target, inSameDayAs: browsedDate)
        }
    }

    private var hasDayJournalEntry: Bool {
        journalEntries.contains { Calendar.current.isDate($0.entryDate, inSameDayAs: browsedDate) }
    }

    private var hasDaySleepLog: Bool {
        sleepNights.contains { Calendar.current.isDate($0.date, inSameDayAs: browsedDate) }
    }

    /// Plans due for a quick mark-taken today: active (queried), not paused. Mirrors the
    /// MedicationView "take" affordance surfaced as Today quick items (Android TodayTab meds).
    private var dueMedicationPlans: [MedicationPlan] {
        guard ChronosSettings.shared.medicationEnabled else { return [] }
        return medicationPlans.filter { !$0.isPaused() }
    }

    private var activeHabits: [Habit] { habits.filter(\.isActive) }

    /// Active habits the cadence actually schedules for the browsed day (or already completed then) —
    /// so a "3x/week" or every-N habit only appears on Today when it's due, matching Android's TodayTab.
    private var dueHabits: [Habit] {
        activeHabits.filter { $0.isDue(on: browsedDate) || $0.isCompleted(on: browsedDate) }
    }

    var body: some View {
        NavigationStack {
            chromedSurface
                // Keep the schedule live surface aligned with today's plan.
                .task(id: dayBlocks.map(\.id)) {
                    guard isViewingToday else { return }
                    if ChronosSettings.shared.currentBlockLiveActivityEnabled {
                        ChronosNotifications.shared.cancel(idPrefix: "block-next")
                        BlockLiveActivityCoordinator.refresh(blocks: dayBlocks, nowMinute: nowMinute)
                    } else {
                        await ChronosNotifications.shared.scheduleNextBlockNotification(blocks: dayBlocks)
                    }
                }
                .sheet(item: $activeSheet) { sheet in
                    switch sheet {
                    case .assistant: AssistantSheet()
                    case .checkIn: CheckInSheet()
                    case .data: DataManagementView()
                    case .newTask: TaskEditorSheet(task: nil)
                    case .newBlock: TimeBlockEditorSheet(block: nil)
                    case .logSleep: SleepLogSheet()
                    case .journal: JournalView()
                    case .planDay: AIPlannerSheet(date: browsedDate, existingBlocks: dayBlocks)
                    case .fillGaps: GapFillSheet(date: browsedDate, existingBlocks: dayBlocks)
                    }
                }
        }
    }

    /// The scroll surface plus title/toolbar chrome. Split out like `DayDialScreen.chromedSurface` so
    /// lifecycle modifiers stay in `body` and the layout matches Plan's edge-to-edge dial surface.
    private var chromedSurface: some View {
        todaySurface
            .navigationTitle(browsedDate.formatted(.dateTime.weekday(.wide).month().day()))
            .chronosScrollMinimizedBar()
            .chronosCommandPaletteToolbar()
            .toolbar { todayToolbar }
    }

    @ToolbarContentBuilder
    private var todayToolbar: some ToolbarContent {
        ToolbarItem(placement: .topBarTrailing) {
            Button { activeSheet = .assistant } label: { Image(systemName: "sparkles") }
                .accessibilityLabel("Assistant")
        }
        ToolbarItem(placement: .topBarTrailing) {
            // Quick-create palette — the iOS analogue of ChronosQuickCreateCommandProvider.
            Menu {
                Button { activeSheet = .newTask } label: { Label("New task", systemImage: "checklist") }
                Button { activeSheet = .newBlock } label: { Label("New time block", systemImage: "calendar.badge.plus") }
                Button { activeSheet = .logSleep } label: { Label("Log sleep", systemImage: "moon.zzz.fill") }
                Button { activeSheet = .checkIn } label: { Label("Mood check-in", systemImage: "face.smiling") }
            } label: { Image(systemName: "plus.circle") }
                .accessibilityLabel("Quick create")
        }
        ToolbarItem(placement: .topBarTrailing) {
            Menu {
                Button { activeSheet = .data } label: { Label("Data & backup", systemImage: "externaldrive") }
            } label: { Image(systemName: "ellipsis.circle") }
                .accessibilityLabel("More")
        }
    }

    /// The scrolling Today feed over the living backdrop. Mirrors `DayDialScreen.dialSurface`:
    /// vertical padding on the scroll column, horizontal padding on sections (not the scroll
    /// container), and a hidden scroll background so content runs edge-to-edge under the nav bar.
    private var todaySurface: some View {
        ZStack {
            ChronosBackdrop()
            ScrollView {
                VStack(alignment: .leading, spacing: ChronosSpacing.medium) {
                    if focusTimer.phase != .idle {
                        todaySection {
                            focusSessionCard
                                .transition(.opacity.combined(with: .move(edge: .top)))
                        }
                    }
                    if isViewingToday && readiness != .unknown && readiness != .normal {
                        todaySection {
                            readinessBanner
                                .transition(.opacity.combined(with: .move(edge: .top)))
                        }
                    }
                    todaySection { dialCard }
                    if isViewingToday {
                        todaySection { nowCard }
                    }
                    todaySection { dayActionStrip }
                    if isViewingToday && !hasDayJournalEntry {
                        todaySection {
                            journalActionCard
                                .transition(.opacity.combined(with: .move(edge: .top)))
                        }
                    }
                    if isViewingToday && !hasDaySleepLog {
                        todaySection {
                            sleepActionCard
                                .transition(.opacity.combined(with: .move(edge: .top)))
                        }
                    }
                    if let upNext {
                        todaySection {
                            upNextCard(upNext)
                                .transition(.opacity.combined(with: .move(edge: .top)))
                        }
                    }
                    todaySection { taskSection }
                    if !dueHabits.isEmpty {
                        todaySection {
                            habitSummarySection
                                .transition(.opacity.combined(with: .move(edge: .top)))
                        }
                    }
                    if isViewingToday && !dueMedicationPlans.isEmpty {
                        todaySection {
                            medicationSection
                                .transition(.opacity.combined(with: .move(edge: .top)))
                        }
                    }
                }
                .padding(.vertical, ChronosSpacing.standard)
                .animation(ChronosMotion.smooth, value: hasDayJournalEntry)
                .animation(ChronosMotion.smooth, value: hasDaySleepLog)
                .animation(ChronosMotion.smooth, value: upNext?.id)
                .animation(ChronosMotion.smooth, value: dueHabits.isEmpty)
                .animation(ChronosMotion.smooth, value: readiness)
                .animation(ChronosMotion.smooth, value: focusTimer.phase)
            }
            .scrollContentBackground(.hidden)
        }
    }

    /// Sections inset horizontally — mirrors Plan's per-row `.padding(.horizontal)` on the scroll
    /// column while the scroll view itself stays full-bleed edge-to-edge under the nav bar.
    private func todaySection<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        content().padding(.horizontal, ChronosSpacing.standard)
    }

    /// Compact running-session card — the cross-tab surfacing of the focus timer (Android shows
    /// the running session above the Today content). Tapping lands on the Focus tab.
    private var focusSessionCard: some View {
        Button { shell?.select(.focus) } label: {
            ChronosGlassCard(tint: ChronosColors.brandPrimary) {
                HStack(spacing: ChronosSpacing.compact) {
                    Image(systemName: "timer")
                        .foregroundStyle(ChronosColors.brandPrimary)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(focusTimer.blockTitle).font(.chronosHeadline).lineLimit(1)
                        Text(focusPhaseLabel).font(.chronosCaption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Text(focusRemainingLabel)
                        .font(.chronosLabel).monospacedDigit().foregroundStyle(.secondary)
                    Image(systemName: "chevron.right").font(.caption).foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .buttonStyle(.plain)
        .pressable()
        .accessibilityLabel("Focus session running: \(focusTimer.blockTitle), \(focusPhaseLabel)")
        .accessibilityHint("Opens the Focus tab")
    }

    private var focusPhaseLabel: String {
        if focusTimer.awaitingPhaseAdvance { return "Holding — tap to continue" }
        if focusTimer.isPaused { return "Paused" }
        switch focusTimer.phase {
        case .work: return "Focusing"
        case .shortBreak, .longBreak: return "On a break"
        case .completed: return "Session complete"
        case .idle: return ""
        }
    }

    /// mm:ss remaining in the current phase (same format as the Focus tab's ring).
    private var focusRemainingLabel: String {
        String(format: "%02d:%02d", Int(focusTimer.remaining) / 60, Int(focusTimer.remaining) % 60)
    }

    /// Read-only Chronos Dial over the browsed day — Android's TodayTab centers the shared dial.
    /// The feed mirrors `DayDialScreen` (day blocks + conflict IDs); the calendar overlay and drag
    /// editing stay Plan-only, so hit testing is disabled and any tap lands on the Plan tab.
    private var dialCard: some View {
        Button {
            withAnimation(ChronosMotion.snappy) { shell?.select(.plan) }
        } label: {
            ChronosDialCanvas(
                blocks: dayBlocks,
                nowMinute: nowMinute,
                conflictBlockIDs: Set(PlannerMath.conflicts(in: dayBlocks).flatMap { [$0.firstID, $0.secondID] }),
                showNowHand: isViewingToday
            )
            .allowsHitTesting(false)
        }
        .buttonStyle(.plain)
        .frame(maxWidth: 380)
        .frame(maxWidth: .infinity)
        .accessibilityLabel("Day dial, \(dayBlocks.count) blocks")
        .accessibilityHint("Opens the Plan tab")
    }

    private var readinessBanner: some View {
        ChronosGlassCard(tone: .quiet, tint: readiness == .depleted ? ChronosColors.brandAccent : ChronosColors.brandSecondary) {
            Label {
                Text(readiness == .depleted
                     ? "You slept lightly. The plan favors lighter tasks and earlier breaks today."
                     : "Well rested — a good day for demanding deep work.")
                    .font(.chronosLabel)
            } icon: {
                Image(systemName: readiness == .depleted ? "moon.zzz" : "sparkles")
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var nowCard: some View {
        ChronosGlassPanel(tint: current.map { ChronosColors.category($0.category) }) {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                Text("NOW").font(.chronosCaption).foregroundStyle(.secondary)
                if let current {
                    Text(current.title).font(.chronosTitleLarge)
                    Text("until \(current.plannedEndMinuteOfDay.clockTime)")
                        .font(.chronosBody).foregroundStyle(.secondary)
                    if current.category == "FOCUS" {
                        NavigationLink {
                            FocusView(prefilledBlockID: current.id)
                        } label: {
                            Label("Start focus", systemImage: "timer")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                        .padding(.top, ChronosSpacing.small)
                    }
                    blockDoneButton(current)
                } else {
                    Text("Open time").font(.chronosTitle)
                    Text("Nothing scheduled right now").font(.chronosBody).foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    /// Mark the current block done / not-done. Completion is recorded as actual start/end times
    /// (planned values), which the dial's actual ring and the Insights planned/actual/missed rollups
    /// already read — previously a scheduled block could never be marked complete on iOS.
    @ViewBuilder
    private func blockDoneButton(_ block: TimeBlock) -> some View {
        let done = block.actualEndMinuteOfDay != nil
        Button {
            withAnimation(ChronosMotion.bouncy) {
                if done {
                    block.actualStartMinuteOfDay = nil
                    block.actualEndMinuteOfDay = nil
                } else {
                    block.actualStartMinuteOfDay = block.startMinuteOfDay
                    block.actualEndMinuteOfDay = block.plannedEndMinuteOfDay
                }
                try? context.save()
                BlockLiveActivityCoordinator.refreshToday()
            }
        } label: {
            Label(done ? "Completed" : "Mark done",
                  systemImage: done ? "checkmark.circle.fill" : "circle")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
        .tint(done ? ChronosColors.brandSecondary : ChronosColors.brandPrimary)
        .padding(.top, ChronosSpacing.small)
    }

    /// State-driven quick-action strip (parity with the Android Today "daily action strip"): an empty
    /// day nudges planning; a populated day offers gap-fill. Both always offer "Add block".
    private var dayActionStrip: some View {
        HStack(spacing: ChronosSpacing.small) {
            if dayBlocks.isEmpty {
                actionChip("Plan my day", "sparkles") { activeSheet = .planDay }
            } else {
                actionChip("Fill gaps", "wand.and.stars") { activeSheet = .fillGaps }
            }
            actionChip("Add block", "calendar.badge.plus") { activeSheet = .newBlock }
        }
    }

    private func actionChip(_ title: String, _ icon: String, _ tap: @escaping () -> Void) -> some View {
        Button(action: tap) {
            Label(title, systemImage: icon)
                .font(.chronosLabel)
                .frame(maxWidth: .infinity)
                .padding(.vertical, ChronosSpacing.small)
        }
        .buttonStyle(.bordered)
        .buttonBorderShape(.capsule)
        .tint(ChronosColors.brandPrimary)
    }

    private func upNextCard(_ block: TimeBlock) -> some View {
        ChronosGlassCard {
            HStack {
                VStack(alignment: .leading) {
                    Text("UP NEXT").font(.chronosCaption).foregroundStyle(.secondary)
                    Text(block.title).font(.chronosHeadline)
                }
                Spacer()
                Text(block.startMinuteOfDay.clockTime)
                    .font(.chronosLabel).monospacedDigit().foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
        }
    }

    private var taskSection: some View {
        VStack(alignment: .leading, spacing: ChronosSpacing.small) {
            Text(isViewingToday ? "Today's tasks" : "Tasks").font(.chronosTitle)
            if dayTasks.isEmpty {
                VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                    Text(isViewingToday ? "No tasks scheduled for today" : "No tasks scheduled for this day")
                        .font(.chronosBody).foregroundStyle(.secondary)
                    Button { activeSheet = .newTask } label: {
                        Label("Add task", systemImage: "checklist")
                            .font(.chronosLabel)
                            .padding(.vertical, ChronosSpacing.small)
                    }
                    .buttonStyle(.bordered)
                    .buttonBorderShape(.capsule)
                    .tint(ChronosColors.brandPrimary)
                }
            } else {
                ForEach(dayTasks) { task in
                    Button {
                        task.isCompleted.toggle()
                        try? context.save()
                        BlockLiveActivityCoordinator.refreshToday()
                    } label: {
                        HStack {
                            Image(systemName: task.isCompleted ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(task.isCompleted ? ChronosColors.brandSecondary : .secondary)
                            Text(task.title).strikethrough(task.isCompleted)
                            Spacer()
                        }
                        .font(.chronosBody)
                        .padding(.vertical, ChronosSpacing.micro)
                        .frame(minHeight: 44)
                    }
                    .buttonStyle(.plain)
                    .sensoryFeedback(.success, trigger: task.isCompleted) { old, new in !old && new }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var journalActionCard: some View {
        Button { activeSheet = .journal } label: {
            ChronosGlassCard(tint: ChronosColors.brandSecondary) {
                HStack {
                    VStack(alignment: .leading, spacing: ChronosSpacing.micro) {
                        Label("Reflect on your day", systemImage: "book.closed.fill")
                            .font(.chronosHeadline)
                            .foregroundStyle(ChronosColors.brandSecondary)
                        Text("Write in your journal to build your streak.")
                            .font(.chronosCaption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right").foregroundStyle(.secondary)
                }
            }
        }
        .buttonStyle(.plain)
        .pressable()
        .accessibilityLabel("Reflect on your day")
        .accessibilityHint("Opens your journal")
    }

    private var sleepActionCard: some View {
        Button { activeSheet = .logSleep } label: {
            ChronosGlassCard(tone: .quiet) {
                HStack {
                    VStack(alignment: .leading, spacing: ChronosSpacing.micro) {
                        Label("Log tonight's sleep", systemImage: "moon.zzz.fill")
                            .font(.chronosHeadline)
                        Text("Track your sleep to adapt tomorrow's plan.")
                            .font(.chronosCaption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right").foregroundStyle(.secondary)
                }
            }
        }
        .buttonStyle(.plain)
        .pressable()
        .accessibilityLabel("Log tonight's sleep")
        .accessibilityHint("Opens the sleep log")
    }

    private var habitSummarySection: some View {
        VStack(alignment: .leading, spacing: ChronosSpacing.small) {
            Text(isViewingToday ? "Today's habits" : "Habits").font(.chronosTitle)
            ForEach(dueHabits.prefix(5)) { habit in
                Button {
                    habit.toggleCompletion(on: browsedDate)
                    try? context.save()
                    BlockLiveActivityCoordinator.refreshToday()
                } label: {
                    HStack {
                        Image(systemName: habit.isCompleted(on: browsedDate) ? "checkmark.circle.fill" : "circle")
                            .foregroundStyle(habit.isCompleted(on: browsedDate) ? ChronosColors.brandSecondary : .secondary)
                        Text(habit.title).font(.chronosBody)
                        Spacer()
                        if habit.streakCount > 0 {
                            Text("\(habit.streakCount)d").font(.chronosCaption)
                                .foregroundStyle(ChronosColors.brandPrimary)
                        }
                    }
                    .padding(.vertical, ChronosSpacing.micro)
                    .frame(minHeight: 44)
                }
                .buttonStyle(.plain)
                .sensoryFeedback(.success, trigger: habit.streakCount)
            }
        }
    }

    /// Medication quick items — mark a dose taken without leaving Today (Android TodayTab meds).
    /// The write path mirrors MedicationView's take action: `acknowledgeDose()` appends a taken
    /// `DoseEvent` and decrements supply; already-taken plans show as done and don't re-record.
    private var medicationSection: some View {
        VStack(alignment: .leading, spacing: ChronosSpacing.small) {
            Text("Today's medications").font(.chronosTitle)
            ForEach(dueMedicationPlans) { plan in
                let taken = plan.isTaken(on: .now)
                Button {
                    if !taken {
                        plan.acknowledgeDose()
                        try? context.save()
                        medTakeTick += 1
                        BlockLiveActivityCoordinator.refreshToday()
                    }
                } label: {
                    HStack {
                        Image(systemName: taken ? "checkmark.circle.fill" : "circle")
                            .foregroundStyle(taken ? ChronosColors.brandSecondary : .secondary)
                        Text(plan.name).font(.chronosBody)
                        if !plan.dosage.isEmpty {
                            Text("\(plan.dosage) \(plan.unit)")
                                .font(.chronosCaption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text(plan.reminderMinuteOfDay.clockTime)
                            .font(.chronosCaption).monospacedDigit().foregroundStyle(.secondary)
                    }
                    .padding(.vertical, ChronosSpacing.micro)
                    .frame(minHeight: 44)
                }
                .buttonStyle(.plain)
                .disabled(taken)
                .accessibilityLabel(taken ? "\(plan.name), taken" : "Mark \(plan.name) taken")
            }
        }
        .sensoryFeedback(.success, trigger: medTakeTick)
    }
}

/// Quick mood / energy / stress / focus check-in. Ports the Android mood-energy check-in.
struct CheckInSheet: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @State private var mood = 3.0
    @State private var energy = 3.0
    @State private var stress = 3.0
    @State private var focus = 3.0
    @State private var notes = ""

    var body: some View {
        NavigationStack {
            Form {
                slider("Mood", value: $mood, system: "face.smiling")
                slider("Energy", value: $energy, system: "bolt.fill")
                slider("Stress", value: $stress, system: "wind")
                slider("Focus", value: $focus, system: "scope")
                Section { TextField("Notes (optional)", text: $notes, axis: .vertical) }
            }
            .navigationTitle("Check in")
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        context.insert(MoodEnergyCheckIn(
                            moodScore: Int(mood), stressScore: Int(stress),
                            energyScore: Int(energy), focusScore: Int(focus),
                            notes: notes.isEmpty ? nil : notes))
                        try? context.save()
                        dismiss()
                    }
                }
            }
        }
        .presentationDetents([.medium])
    }

    private func slider(_ label: String, value: Binding<Double>, system: String) -> some View {
        Section {
            VStack {
                HStack {
                    Label(label, systemImage: system)
                    Spacer()
                    Text("\(Int(value.wrappedValue))/5").foregroundStyle(.secondary)
                }
                Slider(value: value, in: 1...5, step: 1)
                    // Speak "Mood, 3 of 5" rather than the default percentage (§7).
                    .accessibilityLabel(label)
                    .accessibilityValue("\(Int(value.wrappedValue)) of 5")
            }
        }
    }
}

#Preview {
    TodayView().modelContainer(ChronosStore.previewContainer())
}
