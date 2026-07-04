import SwiftUI
import SwiftData
import Charts
import FoundationModels
import ChronosCore

/// The Habits tab: streaks, consistency, flexible cadence, lifecycle actions, and AI-assisted
/// missed-habit repair. Ports `feature/habits` (HabitScreen.kt + HabitConsistencyCard /
/// HabitStreakChart / HabitRepairPanel / HabitRow / HabitContextActionSheet).
struct HabitsView: View {
    @Environment(\.modelContext) private var context
    @Environment(ShellState.self) private var shell: ShellState?
    // Only active (non-archived) habits, matching Android's GetActiveHabitsUseCase.
    @Query(filter: #Predicate<Habit> { $0.isActive }, sort: \Habit.title) private var habits: [Habit]
    @Query private var allBlocks: [TimeBlock]
    @State private var creating = false
    /// Title to prefill the add sheet with when launched from an empty-state quick-start chip.
    @State private var prefillTitle: String?
    @State private var repair = HabitRepairAssistant()
    @State private var contextHabit: Habit?
    /// Habit opened for inline editing from a row's Edit icon (Android `HabitRow` onEdit).
    @State private var editingHabit: Habit?
    /// Habit pending archive confirmation from a row's Archive icon (Android `HabitRow` onArchive).
    @State private var archivingHabit: Habit?
    /// Scroll target for notification deep links (`chronosflow://habits?id=…`).
    @State private var scrollTarget: String?

    private var todayBusy: [(start: Int, end: Int)] {
        allBlocks
            .filter { Calendar.current.isDateInToday($0.date) }
            .map { (start: $0.startMinuteOfDay, end: $0.startMinuteOfDay + $0.durationMinutes) }
    }

    // MARK: Aggregate metrics (reactive — recomputed as the @Query results change).

    private var activeCount: Int { habits.count }
    private var bestStreak: Int { habits.map { $0.analytics(today: .now).currentStreak }.max() ?? 0 }
    private var doneToday: Int { habits.filter { $0.isCompleted(on: .now) }.count }

    var body: some View {
        NavigationStack {
            chromedSurface
            .overlay {
                if habits.isEmpty {
                    VStack(spacing: ChronosSpacing.standard) {
                        ContentUnavailableView("No habits", systemImage: "heart",
                                               description: Text("Build a routine that fits your day"))
                        // Quick-start chips that prefill the add sheet (Android `ChronosQuickAddChips`).
                        FlowChips(titles: ["Morning walk", "Meditation", "Read 20 min", "Hydrate"]) { title in
                            prefillTitle = title
                            creating = true
                        }
                        .padding(.horizontal, ChronosSpacing.standard)
                    }
                }
            }
            .sheet(isPresented: $creating) { HabitEditorSheet(initialTitle: prefillTitle) }
            .sheet(item: $contextHabit) { habit in
                HabitContextActionSheet(
                    habit: habit,
                    onDuplicate: { duplicate(habit); contextHabit = nil },
                    onArchive: { archive(habit); contextHabit = nil }
                )
            }
            .sheet(item: $editingHabit) { habit in HabitEditorSheet(habit: habit) }
            .confirmationDialog(
                archivingHabit.map { "Archive \"\($0.title)\"?" } ?? "Archive this habit?",
                isPresented: Binding(get: { archivingHabit != nil },
                                     set: { if !$0 { archivingHabit = nil } }),
                titleVisibility: .visible
            ) {
                Button("Archive", role: .destructive) {
                    if let habit = archivingHabit { archive(habit) }
                    archivingHabit = nil
                }
                Button("Cancel", role: .cancel) { archivingHabit = nil }
            } message: {
                Text("Archived habits are hidden and stop reminding you. Streak history stays saved.")
            }
            .task(id: habits.map(\.id)) { refreshRepair() }
            .onChange(of: todayBusy.count) { refreshRepair() }
            .onAppear { drainPendingDeepLinkHabit() }
            .onChange(of: shell?.pendingHabitID) { _, id in
                if id != nil { drainPendingDeepLinkHabit() }
            }
        }
    }

    private var chromedSurface: some View {
        habitsSurface
            .navigationTitle("Habits")
            .chronosScrollMinimizedBar()
            .chronosCommandPaletteToolbar()
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { prefillTitle = nil; creating = true } label: { Image(systemName: "plus") }
                        .accessibilityLabel("Add habit")
                }
            }
    }

    private var habitsSurface: some View {
        ZStack {
            ChronosBackdrop()
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: ChronosSpacing.compact) {
                        if !habits.isEmpty {
                            habitsSection { metricTiles }
                            if !repair.suggestions.isEmpty || repair.isThinking {
                                habitsSection {
                                    HabitRepairPanel(
                                        assistant: repair,
                                        onComplete: { applyRepair($0) }
                                    )
                                }
                            }
                            habitsSection { ConsistencyCard(habits: habits) }
                            habitsSection { StreakChartCard(habits: habits) }
                        }
                        ForEach(habits) { habit in
                            habitsSection {
                                HabitCard(
                                    habit: habit,
                                    busy: todayBusy,
                                    onMore: { contextHabit = habit },
                                    onEdit: { editingHabit = habit },
                                    onArchive: { archivingHabit = habit }
                                )
                            }
                            .id(habit.id)
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

    private func habitsSection<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        content().padding(.horizontal, ChronosSpacing.standard)
    }

    /// Notification deep link (`chronosflow://habits?id=…`) — open the matching habit's context sheet.
    private func drainPendingDeepLinkHabit() {
        guard let id = shell?.pendingHabitID else { return }
        shell?.pendingHabitID = nil
        guard let habit = habits.first(where: { $0.id == id }) else { return }
        scrollTarget = id
        contextHabit = habit
    }

    // MARK: Metric tiles (Active / Best streak / Done today).

    private var metricTiles: some View {
        HStack(spacing: ChronosSpacing.compact) {
            MetricTile(label: "Active", value: "\(activeCount)",
                       systemImage: "heart.fill", tint: ChronosColors.brandPrimary)
            MetricTile(label: "Best streak", value: "\(bestStreak)",
                       systemImage: "flame.fill", tint: ChronosColors.brandAccent)
            MetricTile(label: "Done today", value: "\(doneToday)",
                       systemImage: "checkmark.circle.fill", tint: ChronosColors.brandSecondary)
        }
    }

    // MARK: Repair wiring.

    /// Recompute the deterministic missed-for-repair candidates and (re)run the assistant.
    /// Mirrors Android's HabitViewModel.refreshRepairSuggestions reacting to the active-habit list.
    private func refreshRepair() {
        let nowMin = nowMinuteOfDay()
        let candidates = missedForRepair(
            habits: habits.map(\.repairCandidate), today: .now, currentMinute: nowMin
        )
        let inputs: [HabitRepairAssistant.HabitInput] = candidates.compactMap { candidate in
            guard let habit = habits.first(where: { $0.id == candidate.id }) else { return nil }
            return HabitRepairAssistant.HabitInput(
                id: habit.id, title: habit.title,
                windowStart: habit.windowStartMinute, windowEnd: habit.windowEndMinute,
                durationMinutes: 30
            )
        }
        Task { await repair.refresh(habits: inputs, busy: todayBusy, nowMinute: nowMin) }
    }

    /// Materialize an accepted repair suggestion as a HABIT TimeBlock and drop it from the panel.
    private func applyRepair(_ suggestion: HabitRepairAssistant.Suggestion) {
        guard let habit = habits.first(where: { $0.id == suggestion.habitID }) else { return }
        context.insert(TimeBlock(
            date: .now, title: habit.title, category: "HABIT",
            startMinuteOfDay: suggestion.startMinute,
            durationMinutes: suggestion.endMinute - suggestion.startMinute,
            provenance: .habit, flexibility: .movable, habitID: habit.id))
        try? context.save()
        repair.dismiss(suggestion.id)
    }

    // MARK: Lifecycle.

    /// Duplicate a habit with reset progress and cleared schedule state. Ports Android's duplicate.
    private func duplicate(_ habit: Habit) {
        context.insert(Habit(
            title: habit.title + " (copy)", cadence: habit.cadence,
            windowStartMinute: habit.windowStartMinute, windowEndMinute: habit.windowEndMinute,
            difficulty: habit.difficulty, isBundled: habit.isBundled,
            streakCount: 0, lastCompletedDate: nil, isActive: true, goalID: habit.goalID,
            completionDates: [], skipDates: [], pausedUntil: nil, deferUntilMinuteOfDay: nil))
        try? context.save()
    }

    /// Archive (soft-delete) a habit — hidden from the list via the isActive predicate.
    private func archive(_ habit: Habit) {
        withAnimation(ChronosMotion.smooth) {
            habit.isActive = false
            try? context.save()
        }
    }
}

// MARK: - Opaque card

/// iOS analogue of Android's `ChronosListCard` — a flat, opaque (non-glass) card surface used for
/// the habit rows, consistency / streak cards, and repair panel. Android draws these on an opaque
/// `surfaceContainer` rather than the frosted-glass panels, so this matches that neutral styling
/// (no `.glassEffect`, no tint) while keeping the same corner radius and padding as ChronosGlassCard.
private struct ChronosOpaqueCard<Content: View>: View {
    @ViewBuilder var content: () -> Content

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: ChronosRadius.large, style: .continuous)
        content()
            .padding(ChronosSpacing.standard)
            .background(.regularMaterial, in: shape)
            .overlay(shape.strokeBorder(.separator.opacity(0.5), lineWidth: 1))
    }
}

// MARK: - Metric tile

/// A compact card-like metric, mirroring Android's ChronosMetricTile (label + big value + accent).
private struct MetricTile: View {
    let label: String
    let value: String
    let systemImage: String
    let tint: Color

    var body: some View {
        ChronosGlassCard(tone: .quiet, tint: tint) {
            VStack(alignment: .leading, spacing: ChronosSpacing.micro) {
                Label(label, systemImage: systemImage)
                    .font(.chronosCaption).foregroundStyle(.secondary)
                    .labelStyle(.titleAndIcon)
                Text(value).font(.chronosTitle).foregroundStyle(tint)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label): \(value)")
    }
}

// MARK: - 14-day consistency card

/// 14-day completion histogram + headline ("X/Y days active") and a completed/missed summary.
/// Ports Android's HabitConsistencyCard (ChronosTrendChart BAR mode + habitConsistencyHeadline).
private struct ConsistencyCard: View {
    let habits: [Habit]

    private let windowDays = 14

    /// Per-day completion counts across all active habits over the trailing 14 calendar days.
    private var daily: [(day: Date, count: Int)] {
        let cal = Calendar.current
        let today = cal.startOfDay(for: .now)
        let allDates = habits.flatMap { $0.completionDates }.map { cal.startOfDay(for: $0) }
        var counts: [Date: Int] = [:]
        for d in allDates { counts[d, default: 0] += 1 }
        return (0..<windowDays).reversed().compactMap { offset in
            guard let day = cal.date(byAdding: .day, value: -offset, to: today) else { return nil }
            return (day: day, count: counts[day] ?? 0)
        }
    }

    private var activeDays: Int { daily.filter { $0.count > 0 }.count }
    private var completedTotal: Int { daily.reduce(0) { $0 + $1.count } }
    private var missedDays: Int { windowDays - activeDays }

    var body: some View {
        ChronosOpaqueCard {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                HStack(alignment: .firstTextBaseline) {
                    Text("Consistency").font(.chronosHeadline)
                    Spacer()
                    Text("\(activeDays)/\(windowDays) days active")
                        .font(.chronosCaption).foregroundStyle(.secondary)
                }
                Text(habitConsistencyHeadline(activeDays: activeDays, windowDays: windowDays))
                    .font(.chronosLabel).foregroundStyle(ChronosColors.brandSecondary)
                Chart(daily, id: \.day) { entry in
                    BarMark(
                        x: .value("Day", entry.day, unit: .day),
                        y: .value("Completed", entry.count)
                    )
                    .foregroundStyle(entry.count > 0 ? ChronosColors.brandSecondary : Color.secondary.opacity(0.25))
                    .cornerRadius(3)
                }
                .chartYAxis { AxisMarks(position: .leading, values: .automatic(desiredCount: 3)) }
                .chartXAxis(.hidden)
                .frame(height: 110)
                Text("\(completedTotal) completed · \(missedDays) missed in the last \(windowDays) days")
                    .font(.chronosCaption).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

// MARK: - Top-5 streak chart

/// Top-5 active habits by current streak as proportional horizontal bars. Ports HabitStreakChart.
private struct StreakChartCard: View {
    let habits: [Habit]

    private var rows: [(id: String, title: String, streak: Int)] {
        habits
            .map { (id: $0.id, title: $0.title, streak: $0.analytics(today: .now).currentStreak) }
            .filter { $0.streak > 0 }
            .sorted { $0.streak > $1.streak }
            .prefix(5)
            .map { $0 }
    }

    var body: some View {
        if !rows.isEmpty {
            let maxStreak = max(rows.first?.streak ?? 1, 1)
            ChronosOpaqueCard {
                VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                    Text("Top streaks").font(.chronosHeadline)
                    ForEach(rows, id: \.id) { row in
                        HStack(spacing: ChronosSpacing.small) {
                            Text(row.title)
                                .font(.chronosLabel).lineLimit(1)
                                .minimumScaleFactor(0.7)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            GeometryReader { geo in
                                Capsule()
                                    .fill(ChronosColors.brandAccent)
                                    .frame(width: max(geo.size.width * CGFloat(row.streak) / CGFloat(maxStreak), 6))
                                    .frame(maxHeight: .infinity, alignment: .leading)
                            }
                            .frame(height: 10)
                            Text("\(row.streak)d")
                                .font(.chronosCaption).fontWeight(.semibold)
                                .fixedSize(horizontal: true, vertical: false)
                                .frame(minWidth: 36, alignment: .trailing)
                        }
                        .accessibilityElement(children: .combine)
                        .accessibilityLabel("\(row.title): \(row.streak) day streak")
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
}

// MARK: - Habit card

private struct HabitCard: View {
    @Environment(\.modelContext) private var context
    @Bindable var habit: Habit
    var busy: [(start: Int, end: Int)]
    var onMore: () -> Void
    /// Edit / archive callbacks for the inline header icon buttons (Android `HabitRow` shows Edit +
    /// Archive icons in the title row). Defaulted so existing call sites keep compiling.
    var onEdit: () -> Void = {}
    var onArchive: () -> Void = {}

    private var doneToday: Bool { habit.isCompleted(on: .now) }
    private var skippedToday: Bool { habit.isSkipped(on: .now) }
    private var paused: Bool { habit.isPaused() }
    private var analytics: HabitAnalytics { habit.analytics(today: .now) }

    /// Whether the Complete button should be enabled — disabled once done, paused, or skipped today,
    /// mirroring Android's `enabled = !isDone && !isPaused && !skippedToday`.
    private var canComplete: Bool { !doneToday && !paused && !skippedToday }

    var body: some View {
        // Opaque card to match Android's `ChronosListCard`; the whole card is tappable to open the
        // context action sheet (Android wraps the row in `chronosHapticClick(onClick = onOpenContext)`).
        ChronosOpaqueCard {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                // Title row + inline Edit / Archive icon buttons (Android HabitRow header).
                HStack(alignment: .firstTextBaseline, spacing: ChronosSpacing.compact) {
                    Text(habit.title)
                        .font(.chronosHeadline)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    HStack(spacing: ChronosSpacing.small) {
                        Button(action: onEdit) {
                            Image(systemName: "pencil").foregroundStyle(.secondary)
                        }
                        .frame(minWidth: 44, minHeight: 44)
                        .contentShape(Rectangle())
                        .buttonStyle(.plain)
                        .accessibilityLabel("Edit \(habit.title)")
                        Button(action: onArchive) {
                            Image(systemName: "archivebox").foregroundStyle(.secondary)
                        }
                        .frame(minWidth: 44, minHeight: 44)
                        .contentShape(Rectangle())
                        .buttonStyle(.plain)
                        .accessibilityLabel("Archive \(habit.title)")
                    }
                }
                // Status pills row (Due / On day plan / Cadence / Streak|Milestone / Adherence /
                // Best time / Paused / Skipped) — primary content per Android's FlowRow.
                statusPills
                // Window + difficulty summary line (Android: "HH:MM – HH:MM · difficulty N/5").
                Text("\(habit.windowStartMinute.clockTime) – \(habit.windowEndMinute.clockTime) · difficulty \(habit.difficulty)/5")
                    .font(.chronosCaption).foregroundStyle(.secondary)
                weekStrip
                if let deferUntil = habit.deferUntilMinuteOfDay {
                    Text("Deferred until \(deferUntil.clockTime)")
                        .font(.chronosCaption)
                        .foregroundStyle(.secondary)
                }
                // Action row: Complete + More (Android: Complete + Resume/More).
                HStack(spacing: ChronosSpacing.small) {
                    Button {
                        withAnimation(ChronosMotion.bouncy) {
                            habit.toggleCompletion(on: .now); try? context.save()
                        }
                        BlockLiveActivityCoordinator.refreshToday()
                    } label: {
                        Label(doneToday ? "Completed today" : "Complete", systemImage: "checkmark")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(!canComplete && !doneToday)
                    .accessibilityLabel(doneToday ? "\(habit.title) completed today" : "Complete \(habit.title)")

                    if paused {
                        Button {
                            withAnimation(ChronosMotion.smooth) { habit.resume(); try? context.save() }
                        } label: {
                            Text("Resume").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                        .accessibilityLabel("Resume \(habit.title)")
                    } else {
                        Button(action: onMore) {
                            Text("More").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                        .accessibilityLabel("More actions for \(habit.title)")
                    }
                }
                .buttonBorderShape(.capsule)
            }
            .frame(maxWidth: .infinity)
        }
        .contentShape(Rectangle())
        .onTapGesture { onMore() }
        .pressable()
        .sensoryFeedback(.success, trigger: doneToday) { old, new in new && !old }
    }

    // MARK: 7-day week strip (parity with Android `HabitWeekStrip`).

    /// Trailing 7 days (today rightmost): a filled 16dp rounded square on completed days, weekday
    /// initial below, today highlighted. Matches Android's `HabitWeekStrip` (16dp Box, 6dp corner
    /// radius, filled / today-empty / empty palette). Derived from `habit.completionDates`.
    private var weekStrip: some View {
        let cal = Calendar.current
        let days: [Date] = (0..<7).reversed().compactMap { cal.date(byAdding: .day, value: -$0, to: .now) }
        // Android palette: filled = primary, todayEmpty = primary @28%, empty = outline @22%.
        let filled = ChronosColors.brandPrimary
        let todayEmpty = ChronosColors.brandPrimary.opacity(0.28)
        let empty = Color.secondary.opacity(0.22)
        return HStack(spacing: ChronosSpacing.small) {
            ForEach(days, id: \.timeIntervalSince1970) { day in
                let done = habit.isCompleted(on: day)
                let isToday = cal.isDateInToday(day)
                VStack(spacing: ChronosSpacing.micro) {
                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .fill(done ? filled : (isToday ? todayEmpty : empty))
                        .frame(width: 16, height: 16)
                    Text(cal.veryShortStandaloneWeekdaySymbols[cal.component(.weekday, from: day) - 1])
                        .font(.chronosCaption)
                        .fontWeight(isToday ? .semibold : .regular)
                        .foregroundStyle(isToday ? .primary : .secondary)
                }
                .frame(maxWidth: .infinity)
            }
        }
    }

    /// Friendly cadence display reusing the raw string (DAILY/WEEKLY/3x/week…).
    private var cadenceLabel: String {
        switch habit.cadence.uppercased() {
        case "DAILY": "Daily"
        case "WEEKLY": "Weekly"
        default: habit.cadence
        }
    }

    // MARK: Status pills (due / paused / skipped / milestone / adherence / best time).

    @ViewBuilder private var statusPills: some View {
        let pills = computedPills
        if !pills.isEmpty {
            PillFlowLayout(spacing: ChronosSpacing.small) {
                ForEach(pills, id: \.text) { pill in
                    StatusPill(text: pill.text, tint: pill.tint, emphasized: pill.emphasized)
                }
            }
        }
    }

    private struct Pill { let text: String; let tint: Color; let emphasized: Bool }

    /// Pills, in Android `HabitRow` FlowRow order: status (Due/Done) → On day plan → cadence →
    /// milestone-or-streak → adherence → best time → paused → skipped.
    private var computedPills: [Pill] {
        var pills: [Pill] = []
        let now = nowMinuteOfDay()
        // Status pill: "Done" when completed; "Due now" (emphasized) when the cadence schedules
        // today and we're inside the window; "Not due today" when the cadence skips today (every-N /
        // weekly-interval off-week / quota target already met). Cadence-awareness via `habit.isDue`.
        let dueToday = habit.isDue(on: .now)
        if doneToday {
            pills.append(Pill(text: "Done", tint: ChronosColors.brandSecondary, emphasized: true))
        } else if !skippedToday && !paused && dueToday
                    && now >= habit.windowStartMinute && now <= habit.windowEndMinute {
            pills.append(Pill(text: "Due now", tint: ChronosColors.brandPrimary, emphasized: true))
        } else if !skippedToday && !paused && !dueToday {
            pills.append(Pill(text: "Not due today", tint: .secondary, emphasized: false))
        }
        if habit.isBundled { pills.append(Pill(text: "On day plan", tint: ChronosColors.brandSecondary, emphasized: false)) }
        // Cadence pill (Android always shows the raw cadence label).
        pills.append(Pill(text: cadenceLabel, tint: .secondary, emphasized: false))
        // Milestone celebration pill, else a plain streak pill (Android: milestone OR "Streak N").
        let currentStreak = analytics.currentStreak
        if let milestone = habitStreakMilestoneLabel(currentStreak) {
            pills.append(Pill(text: milestone, tint: ChronosColors.brandAccent, emphasized: true))
        } else {
            pills.append(Pill(text: "Streak \(currentStreak)",
                              tint: currentStreak > 0 ? ChronosColors.brandSecondary : .secondary,
                              emphasized: currentStreak > 0))
        }
        // Adherence pill (Android always shows "Adherence N%").
        let pct = Int((analytics.adherenceRate * 100).rounded())
        let adherenceStrong = pct >= 80
        pills.append(Pill(text: "Adherence \(pct)%",
                          tint: adherenceStrong ? ChronosColors.brandSecondary : .secondary,
                          emphasized: adherenceStrong))
        if let best = analytics.bestCompletionMinuteOfDay {
            pills.append(Pill(text: "Best \(best.clockTime)", tint: .secondary, emphasized: false))
        }
        if paused { pills.append(Pill(text: "Paused", tint: .secondary, emphasized: false)) }
        if skippedToday { pills.append(Pill(text: "Skipped today", tint: .secondary, emphasized: false)) }
        return pills
    }
}

/// A small color-coded status capsule. Mirrors Android's HabitStatusPill (tertiaryContainer for
/// success / primaryContainer for emphasized).
private struct StatusPill: View {
    let text: String
    let tint: Color
    let emphasized: Bool

    var body: some View {
        Text(text)
            .font(.chronosCaption)
            .fontWeight(emphasized ? .semibold : .regular)
            .padding(.horizontal, ChronosSpacing.small)
            .padding(.vertical, ChronosSpacing.micro)
            .background(tint.opacity(emphasized ? 0.20 : 0.12), in: Capsule())
            .foregroundStyle(emphasized ? tint : .secondary)
    }
}

// MARK: - Missed-habit AI repair panel

/// AI-assisted repair panel shown when there are missed habits to recover today. Ports Android's
/// HabitRepairPanel (header + GenAiAssistBanner + up to 3 suggestions with source label & Complete).
private struct HabitRepairPanel: View {
    @Bindable var assistant: HabitRepairAssistant
    var onComplete: (HabitRepairAssistant.Suggestion) -> Void

    var body: some View {
        // Opaque card (Android `ChronosListCard`), neutral styling — no glass tint.
        ChronosOpaqueCard {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                HStack(spacing: ChronosSpacing.small) {
                    // SF Symbol analogue of Material's AutoFixHigh (wand + sparkles).
                    Image(systemName: "wand.and.stars").foregroundStyle(ChronosColors.brandPrimary)
                    Text("Missed-habit repair").font(.chronosHeadline)
                    Spacer()
                }
                banner
                ForEach(assistant.suggestions.prefix(3)) { suggestion in
                    HStack(alignment: .center, spacing: ChronosSpacing.compact) {
                        VStack(alignment: .leading, spacing: ChronosSpacing.micro) {
                            Text(suggestion.title).font(.chronosLabel)
                            // Android: "${reason}. HH:MM-HH:MM" on a single body line.
                            Text("\(suggestion.reason). \(suggestion.startMinute.clockTime)–\(suggestion.endMinute.clockTime)")
                                .font(.chronosCaption).foregroundStyle(.secondary)
                            // Source label on its own line below, primary-tinted (Android labelSmall).
                            Text(suggestion.source.label)
                                .font(.chronosCaption).foregroundStyle(ChronosColors.brandPrimary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        Button { onComplete(suggestion) } label: {
                            Label("Complete", systemImage: "checkmark").font(.chronosCaption)
                        }
                        .buttonStyle(.bordered).buttonBorderShape(.capsule)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    if suggestion.id != assistant.suggestions.prefix(3).last?.id {
                        Divider()
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    @ViewBuilder private var banner: some View {
        if assistant.isThinking {
            HStack(spacing: ChronosSpacing.small) {
                ProgressView().controlSize(.small)
                Text("Finding the best slots on device…")
                    .font(.chronosCaption).foregroundStyle(.secondary)
            }
        } else if assistant.usingOnDeviceAI {
            Label("On-device AI suggestions", systemImage: "sparkles")
                .font(.chronosCaption).foregroundStyle(ChronosColors.brandPrimary)
        } else {
            Label("Smart suggestions", systemImage: "bolt.fill")
                .font(.chronosCaption).foregroundStyle(.secondary)
        }
    }
}

// MARK: - Context action sheet

/// Modal housing the full habit lifecycle: complete, defer, skip, pause/resume, edit, duplicate,
/// archive. Ports Android's HabitContextActionSheet.
private struct HabitContextActionSheet: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @Bindable var habit: Habit
    var onDuplicate: () -> Void
    var onArchive: () -> Void

    @State private var confirmingArchive = false
    @State private var editing = false

    private var doneToday: Bool { habit.isCompleted(on: .now) }
    private var skippedToday: Bool { habit.isSkipped(on: .now) }
    private var paused: Bool { habit.isPaused() }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    VStack(alignment: .leading, spacing: ChronosSpacing.micro) {
                        Text(habit.title).font(.chronosHeadline)
                        Text("\(habit.windowStartMinute.clockTime)–\(habit.windowEndMinute.clockTime)")
                            .font(.chronosCaption).foregroundStyle(.secondary)
                    }
                }
                Section {
                    Button {
                        habit.toggleCompletion(on: .now); save(); dismiss()
                    } label: {
                        Label(doneToday ? "Mark incomplete" : "Complete",
                              systemImage: doneToday ? "arrow.uturn.backward" : "checkmark.circle")
                    }
                    Button {
                        habit.deferReminder(byMinutes: 60, nowMinute: nowMinuteOfDay()); save(); dismiss()
                    } label: {
                        Label("Defer 1 hour", systemImage: "clock.arrow.circlepath")
                    }
                    Button {
                        habit.toggleSkip(on: .now); save(); dismiss()
                    } label: {
                        Label(skippedToday ? "Unskip today" : "Skip today",
                              systemImage: skippedToday ? "arrow.uturn.backward" : "forward.end")
                    }
                    if paused {
                        Button {
                            habit.resume(); save(); dismiss()
                        } label: { Label("Resume", systemImage: "play.circle") }
                    } else {
                        Button {
                            habit.pause(forDays: 1); save(); dismiss()
                        } label: { Label("Pause 1 day", systemImage: "pause.circle") }
                    }
                }
                Section {
                    Button { editing = true } label: { Label("Edit", systemImage: "pencil") }
                    Button { onDuplicate() } label: { Label("Duplicate", systemImage: "plus.square.on.square") }
                    Button(role: .destructive) {
                        confirmingArchive = true
                    } label: { Label("Archive", systemImage: "archivebox") }
                }
            }
            .sheet(isPresented: $editing) { HabitEditorSheet(habit: habit) }
            .navigationTitle("Habit")
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
            .confirmationDialog("Archive this habit?", isPresented: $confirmingArchive, titleVisibility: .visible) {
                Button("Archive", role: .destructive) { onArchive() }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Archived habits are hidden and stop reminding you. You can recreate them later.")
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func save() { try? context.save() }
}

// MARK: - Editor

struct HabitEditorSheet: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    /// Active goals to link a habit to (parity with Android's goal-link picker in the habit form).
    @Query(filter: #Predicate<Goal> { !$0.isCompleted }, sort: \Goal.title) private var goals: [Goal]
    /// Archived habits offered as "From history" prefill chips for new habits — the iOS analogue of
    /// Android's `HabitHistoryTemplate` section (recreate a past habit's saved setup).
    @Query(filter: #Predicate<Habit> { !$0.isActive }, sort: \Habit.title) private var archivedHabits: [Habit]

    /// When non-nil the sheet edits an existing habit; otherwise it creates a new one. Tapping a
    /// habit card / "Edit" in the context sheet now opens this in edit mode (previously the editor
    /// was create-only, so existing habits could never be edited — the Android parity bug).
    private let editing: Habit?

    @State private var title: String
    @State private var cadence: String
    /// Weekdays (Mon-first index 0…6) backing the "Custom days" cadence, serialized as
    /// "Mon/Thu"-style cadence strings — Android's SELECTED_WEEKDAYS custom recurrence.
    @State private var customDays: Set<Int>
    /// Interval backing the "Every N days…" cadence (Android's arbitrary EVERY_N_DAYS; the presets
    /// only expose N=2). Serialized as "Every N days".
    @State private var everyNDays: Int
    @State private var difficulty: Double
    @State private var windowStart: Date
    @State private var windowEnd: Date
    @State private var isBundled: Bool
    @State private var goalID: String?

    /// A stored cadence that matches no preset (e.g. imported data) is kept selectable as-is.
    private let extraCadenceOption: String?

    init(habit: Habit? = nil, initialTitle: String? = nil) {
        editing = habit
        let cal = Calendar.current
        _title = State(initialValue: habit?.title ?? initialTitle ?? "")
        let stored = habit?.cadence ?? "Daily"
        let cadenceState = habitCadenceEditorState(stored)
        // An arbitrary every-N-days cadence (N ≠ 2, which is a named preset) opens the stepper mode
        // instead of showing as a raw extra option.
        if case .everyNDays(let n) = parseHabitCadence(stored), n != 2 {
            _cadence = State(initialValue: Self.everyNDaysOption)
            _everyNDays = State(initialValue: n)
            extraCadenceOption = nil
        } else {
            _cadence = State(initialValue: cadenceState.selection)
            _everyNDays = State(initialValue: 3)
            extraCadenceOption = cadenceState.extraOption
        }
        _customDays = State(initialValue: cadenceState.customDays)
        _difficulty = State(initialValue: Double(habit?.difficulty ?? 2))
        _windowStart = State(initialValue: habit.map { Self.date(fromMinute: $0.windowStartMinute) } ?? .now)
        _windowEnd = State(initialValue: habit.map { Self.date(fromMinute: $0.windowEndMinute) }
            ?? (cal.date(byAdding: .hour, value: 16, to: .now) ?? .now))
        _isBundled = State(initialValue: habit?.isBundled ?? false)
        _goalID = State(initialValue: habit?.goalID)
    }

    private static func date(fromMinute minute: Int) -> Date {
        Calendar.current.date(bySettingHour: minute / 60, minute: minute % 60, second: 0, of: .now) ?? .now
    }

    var body: some View {
        CardEditorScaffold(
            kind: "habit",
            navTitle: editing == nil ? "New habit" : "Edit habit",
            chips: habitChips,
            sections: habitSections,
            saveDisabled: title.isEmpty,
            onCancel: { dismiss() },
            onSave: saveHabit
        ) {
            habitHeader
        }
    }

    // MARK: - Scaffold pieces

    @ViewBuilder private var habitHeader: some View {
        TextField("Habit", text: $title)
        Picker("Cadence", selection: $cadence) {
            ForEach(cadenceOptions, id: \.self) { Text($0).tag($0) }
        }
        if cadence == "Custom days" {
            customDaysRow
        }
        if cadence == Self.everyNDaysOption {
            Stepper("Every \(everyNDays) days", value: $everyNDays, in: 2...30)
                .font(.chronosBody)
        }
        if let hint = historyHint {
            // Android's "Today snapshot" streak/adherence context line in the edit form.
            Label(hint, systemImage: "flame")
                .font(.chronosCaption).foregroundStyle(.secondary)
        }
    }

    private var habitChips: [EditorChip] {
        var chips: [EditorChip] = [
            EditorChip(id: "difficulty", systemImage: "gauge.medium", title: "Difficulty",
                       value: "Level \(Int(difficulty))"),
            EditorChip(id: "window", systemImage: "clock", title: "Window",
                       value: "\(minute(windowStart).clockTime)–\(minute(windowEnd).clockTime)"),
        ]
        let planValue = isBundled ? "On plan" : goals.first { $0.id == goalID }?.title
        chips.append(EditorChip(id: "plan", systemImage: "target", title: "Goal & plan", value: planValue))
        return chips
    }

    private var habitSections: [EditorSection] {
        var list: [EditorSection] = [
            EditorSection(id: "difficulty", title: "Difficulty", systemImage: "gauge.medium") { difficultyRows },
            EditorSection(id: "window", title: "Window", systemImage: "clock") { windowRows },
            EditorSection(id: "plan", title: "Plan & goal", systemImage: "target",
                          hasValue: isBundled || goalID != nil) { planRows },
        ]
        if editing == nil {
            list.append(EditorSection(id: "templates", title: "Templates", systemImage: "square.on.square") { templatesRows })
            if !historyTitles.isEmpty {
                list.append(EditorSection(id: "history", title: "From history",
                                          systemImage: "clock.arrow.circlepath") { historyRows })
            }
        }
        return list
    }

    @ViewBuilder private var difficultyRows: some View {
        VStack(alignment: .leading) {
            Text("Difficulty: \(Int(difficulty))")
            Slider(value: $difficulty, in: 1...5, step: 1)
                .accessibilityLabel("Difficulty")
                .accessibilityValue("\(Int(difficulty)) of 5")
        }
    }

    @ViewBuilder private var windowRows: some View {
        DatePicker("Window start", selection: $windowStart, displayedComponents: .hourAndMinute)
        DatePicker("Window end", selection: $windowEnd, displayedComponents: .hourAndMinute)
    }

    @ViewBuilder private var planRows: some View {
        Toggle("On day plan", isOn: $isBundled)
        if !goals.isEmpty {
            Picker("Goal", selection: $goalID) {
                Text("None").tag(String?.none)
                ForEach(goals) { goal in
                    Text(goal.title).tag(String?.some(goal.id))
                }
            }
        }
        Text("“On day plan” lets this habit be scheduled as a block. Linking a goal counts completions toward it.")
            .font(.chronosCaption).foregroundStyle(.secondary)
    }

    // MARK: Cadence presets + custom weekdays (Android habitRecurrenceQuickPresets / SELECTED_WEEKDAYS)

    /// Sentinel picker entry that reveals the arbitrary "Every N days" stepper. Static so `init`
    /// can reference it without touching `self` before stored properties are initialized.
    private static let everyNDaysOption = "Every N days…"

    private var cadenceOptions: [String] {
        let base = habitCadencePresets + [Self.everyNDaysOption]
        guard let extra = extraCadenceOption, !base.contains(extra) else { return base }
        return base + [extra]
    }

    /// Weekday toggle chips for the "Custom days" cadence (Android's weekday FilterChip row).
    private var customDaysRow: some View {
        HStack(spacing: ChronosSpacing.small) {
            ForEach(0..<7, id: \.self) { day in
                let selected = customDays.contains(day)
                Button {
                    // Discard the Set mutation results — a non-Void withAnimation closure
                    // poisons type inference (see ios-swiftui-typecheck-pitfalls).
                    withAnimation(ChronosMotion.snappy) {
                        if selected { _ = customDays.remove(day) } else { _ = customDays.insert(day) }
                    }
                } label: {
                    Text(habitWeekdayShortNames[day])
                        .font(.chronosCaption)
                        .fontWeight(selected ? .semibold : .regular)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, ChronosSpacing.small)
                        .background(selected ? ChronosColors.brandPrimary.opacity(0.2) : Color(.tertiarySystemFill),
                                    in: Capsule())
                        .foregroundStyle(selected ? ChronosColors.brandPrimary : .secondary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(habitWeekdayShortNames[day])
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
    }

    /// The cadence string persisted on the model: the preset label, or the custom weekday set
    /// serialized "Mon/Thu"-style (empty custom selection falls back to Daily).
    private var cadenceValue: String {
        if cadence == Self.everyNDaysOption { return "Every \(everyNDays) days" }
        guard cadence == "Custom days" else { return cadence }
        let days = customDays.sorted().map { habitWeekdayShortNames[$0] }
        return days.isEmpty ? "Daily" : days.joined(separator: "/")
    }

    // MARK: Templates (Android habitTemplates chips)

    @ViewBuilder private var templatesRows: some View {
        FlowChips(titles: habitTemplates.map(\.label)) { label in
            guard let template = habitTemplates.first(where: { $0.label == label }) else { return }
            withAnimation(ChronosMotion.smooth) { apply(template) }
        }
        Text("Prefills the form — nothing is saved until you tap Save.")
            .font(.chronosCaption).foregroundStyle(.secondary)
    }

    private func apply(_ template: HabitTemplate) {
        title = template.title
        cadence = template.cadence
        customDays = []
        windowStart = Self.date(fromMinute: template.startMinute)
        windowEnd = Self.date(fromMinute: template.endMinute)
        difficulty = Double(template.difficulty)
        isBundled = template.isBundled
    }

    // MARK: From history (Android HabitHistoryTemplate chips — archived setups, new habits only)

    /// Distinct archived-habit titles offered as prefill chips (first occurrence wins).
    private var historyTitles: [String] {
        var seen = Set<String>()
        return archivedHabits.map(\.title).filter { seen.insert($0).inserted }
    }

    @ViewBuilder private var historyRows: some View {
        FlowChips(titles: historyTitles) { picked in
            guard let source = archivedHabits.first(where: { $0.title == picked }) else { return }
            withAnimation(ChronosMotion.smooth) {
                title = source.title
                let state = habitCadenceEditorState(source.cadence)
                cadence = state.selection
                customDays = state.customDays
                windowStart = Self.date(fromMinute: source.windowStartMinute)
                windowEnd = Self.date(fromMinute: source.windowEndMinute)
                difficulty = Double(source.difficulty)
                isBundled = source.isBundled
            }
        }
        Text("Recreate an archived habit's saved setup.")
            .font(.chronosCaption).foregroundStyle(.secondary)
    }

    // MARK: History hint (edit mode)

    /// One-line completion context for the habit being edited, mirroring the streak/adherence line
    /// in Android's "Today snapshot" form section.
    private var historyHint: String? {
        guard let habit = editing else { return nil }
        let analytics = habit.analytics(today: .now)
        var parts = ["Streak \(analytics.currentStreak)",
                     "adherence \(Int((analytics.adherenceRate * 100).rounded()))%"]
        if let best = analytics.bestCompletionMinuteOfDay {
            parts.append("best time \(best.clockTime)")
        }
        return parts.joined(separator: " · ")
    }

    private func saveHabit() {
        let saved: Habit
        if let habit = editing {
            habit.title = title
            habit.cadence = cadenceValue
            habit.difficulty = Int(difficulty)
            habit.windowStartMinute = minute(windowStart)
            habit.windowEndMinute = minute(windowEnd)
            habit.isBundled = isBundled
            habit.goalID = goalID
            saved = habit
        } else {
            let habit = Habit(
                title: title, cadence: cadenceValue, windowStartMinute: minute(windowStart),
                windowEndMinute: minute(windowEnd), difficulty: Int(difficulty),
                isBundled: isBundled, goalID: goalID)
            context.insert(habit)
            saved = habit
        }
        try? context.save()
        Task {
            await ChronosNotifications.shared.scheduleHabit(saved)
            BlockLiveActivityCoordinator.refreshToday()
        }
        dismiss()
    }

    private func minute(_ date: Date) -> Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: date)
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }
}

// MARK: - Habit form presets (Android HabitFormSheet parity data)

/// Quick cadence presets, mirroring Android's `habitRecurrenceQuickPresets` (the Custom entry maps
/// to the weekday-set editor). Stored on the model as the display string, like Android's `cadence`.
private let habitCadencePresets = [
    "Daily", "Weekdays", "Weekends", "Mon/Wed/Fri", "Tue/Thu", "Weekly", "Every 2 days",
    "2x / week", "3x / week", "4x / week", "5x / week", "6x / week", "1x / month", "Custom days"
]

/// Mon-first weekday short names used by the custom-days editor and its cadence serialization.
private let habitWeekdayShortNames = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]

/// Resolve a stored cadence string into editor state: legacy values ("DAILY", "3x/week") normalize
/// onto the preset labels; "Mon/Thu"-style weekday lists open the custom-days editor; anything else
/// is kept as an extra selectable option so existing data never breaks the picker.
private func habitCadenceEditorState(_ stored: String)
    -> (selection: String, customDays: Set<Int>, extraOption: String?) {
    let normalized: String
    switch stored.uppercased() {
    case "DAILY": normalized = "Daily"
    case "WEEKLY": normalized = "Weekly"
    case "3X/WEEK": normalized = "3x / week"
    case "5X/WEEK": normalized = "5x / week"
    default: normalized = stored
    }
    if habitCadencePresets.contains(normalized) { return (normalized, [], nil) }
    // A "/"-joined list of weekday names (that isn't one of the named presets) is a custom-day set.
    let tokens = normalized.split(separator: "/").map { $0.trimmingCharacters(in: .whitespaces) }
    let days = tokens.compactMap { token in
        habitWeekdayShortNames.firstIndex { $0.caseInsensitiveCompare(token) == .orderedSame }
    }
    if !tokens.isEmpty && days.count == tokens.count {
        return ("Custom days", Set(days), nil)
    }
    return (normalized, [], normalized)
}

/// Starter templates that prefill the whole form, mirroring Android's `habitTemplates`.
private struct HabitTemplate {
    let label: String
    let title: String
    let cadence: String
    let startMinute: Int
    let endMinute: Int
    let difficulty: Int
    let isBundled: Bool
}

private let habitTemplates: [HabitTemplate] = [
    HabitTemplate(label: "Morning reset", title: "Morning walk", cadence: "Daily",
                  startMinute: 6 * 60, endMinute: 9 * 60, difficulty: 2, isBundled: true),
    HabitTemplate(label: "Workout", title: "Workout", cadence: "Weekdays",
                  startMinute: 6 * 60, endMinute: 8 * 60, difficulty: 4, isBundled: true),
    HabitTemplate(label: "Reading", title: "Read 20 min", cadence: "Daily",
                  startMinute: 19 * 60, endMinute: 22 * 60, difficulty: 2, isBundled: false),
    HabitTemplate(label: "Hydration", title: "Hydrate", cadence: "Daily",
                  startMinute: 8 * 60, endMinute: 20 * 60, difficulty: 1, isBundled: true),
    HabitTemplate(label: "Dental", title: "Brush and floss", cadence: "Daily",
                  startMinute: 20 * 60, endMinute: 22 * 60, difficulty: 1, isBundled: true),
    HabitTemplate(label: "Study", title: "Study 25 min", cadence: "Weekdays",
                  startMinute: 18 * 60, endMinute: 21 * 60, difficulty: 3, isBundled: true),
    HabitTemplate(label: "Sleep routine", title: "Wind down for sleep", cadence: "Daily",
                  startMinute: 20 * 60, endMinute: 22 * 60, difficulty: 2, isBundled: true),
    HabitTemplate(label: "Nutrition", title: "Prep healthy meal", cadence: "Daily",
                  startMinute: 11 * 60, endMinute: 14 * 60, difficulty: 2, isBundled: false),
    HabitTemplate(label: "Wind down", title: "Journal", cadence: "Daily",
                  startMinute: 20 * 60, endMinute: 22 * 60, difficulty: 2, isBundled: false)
]

// MARK: - Repair assistant

/// On-device AI repair assistant. Ports Android's HabitRepairAssistPlanner: for each missed habit it
/// asks Apple Intelligence (Foundation Models) for a slot and a one-line reason, then validates the
/// slot against the deterministic ChronosCore.suggestHabitRepair. Falls back entirely to the
/// deterministic gap-finder when on-device AI is unavailable or returns nothing usable.
@MainActor
@Observable
final class HabitRepairAssistant {
    struct HabitInput: Sendable {
        let id: String
        let title: String
        let windowStart: Int
        let windowEnd: Int
        let durationMinutes: Int
    }

    /// Where a suggestion came from. `label` mirrors Android's routineAssistSourceLabel copy.
    enum Source {
        case local, onDeviceAI
        var label: String {
            switch self {
            case .local: "Smart"
            case .onDeviceAI: "On-device AI"
            }
        }
    }

    struct Suggestion: Identifiable, Sendable {
        let id: String          // == habitID
        var habitID: String { id }
        let title: String
        let startMinute: Int
        let endMinute: Int
        let reason: String
        let source: Source
    }

    var suggestions: [Suggestion] = []
    var isThinking = false
    var usingOnDeviceAI = false

    private var aiAvailable: Bool {
        if case .available = SystemLanguageModel.default.availability { return true }
        return false
    }

    func dismiss(_ id: String) {
        suggestions.removeAll { $0.id == id }
    }

    /// Recompute suggestions for the supplied missed habits.
    func refresh(habits: [HabitInput], busy: [(start: Int, end: Int)], nowMinute: Int) async {
        guard !habits.isEmpty else {
            suggestions = []; isThinking = false; usingOnDeviceAI = false
            return
        }
        isThinking = true
        defer { isThinking = false }

        var result: [Suggestion] = []
        var anyAI = false
        for input in habits {
            // Deterministic slot is always the source of truth for *where* — AI only adds *why*.
            guard let slot = suggestHabitRepair(
                windowStart: input.windowStart, windowEnd: input.windowEnd,
                durationMinutes: input.durationMinutes, busy: busy, nowMinute: nowMinute
            ) else { continue }

            var reason = "Open slot in your window — keep the streak alive."
            var source: Source = .local
            if aiAvailable, let aiReason = await aiReason(for: input, slot: slot) {
                reason = aiReason
                source = .onDeviceAI
                anyAI = true
            }
            result.append(Suggestion(
                id: input.id, title: input.title,
                startMinute: slot.startMinute, endMinute: slot.startMinute + slot.durationMinutes,
                reason: reason, source: source))
        }
        suggestions = result
        usingOnDeviceAI = anyAI
    }

    /// Ask the on-device model for a single encouraging one-line reason. Returns nil on any failure
    /// so the caller falls back to the deterministic copy. We do not let the model pick the slot.
    private func aiReason(for input: HabitInput, slot: HabitRepairSuggestion) async -> String? {
        let instructions = Instructions {
            "You write one short, warm, encouraging sentence (max 16 words) telling someone they can still fit a missed habit into a free slot today. No emoji."
        }
        let prompt = """
        Habit: \(input.title). Suggested slot: \(slot.startMinute.clockTime). \
        Write one encouraging sentence to nudge them to do it now.
        """
        do {
            let session = LanguageModelSession(instructions: instructions)
            let response = try await session.respond(to: prompt)
            let text = response.content.trimmingCharacters(in: .whitespacesAndNewlines)
            return text.isEmpty ? nil : text
        } catch {
            return nil
        }
    }
}

// MARK: - Shared helpers

/// Current minute-of-day (0…1439) for the wall clock.
private func nowMinuteOfDay() -> Int {
    let c = Calendar.current.dateComponents([.hour, .minute], from: .now)
    return (c.hour ?? 0) * 60 + (c.minute ?? 0)
}

// MARK: - Flow layout

/// Minimal flowing wrap layout for status pills (iOS 16+ `Layout`). Lays children left-to-right,
/// wrapping when the proposed width is exceeded.
/// A wrapping row of tappable suggestion chips, reused for the empty-state quick-start prompts.
private struct FlowChips: View {
    let titles: [String]
    let onTap: (String) -> Void

    var body: some View {
        PillFlowLayout(spacing: ChronosSpacing.small) {
            ForEach(titles, id: \.self) { title in
                Button { onTap(title) } label: {
                    Text(title)
                        .font(.chronosCaption)
                        .padding(.horizontal, ChronosSpacing.compact)
                        .padding(.vertical, ChronosSpacing.small)
                        .background(ChronosColors.brandPrimary.opacity(0.14), in: Capsule())
                }
                .buttonStyle(.plain)
            }
        }
    }
}

private struct PillFlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var rowWidth: CGFloat = 0
        var rowHeight: CGFloat = 0
        var totalHeight: CGFloat = 0
        var totalWidth: CGFloat = 0

        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if rowWidth + size.width > maxWidth, rowWidth > 0 {
                totalHeight += rowHeight + spacing
                totalWidth = max(totalWidth, rowWidth - spacing)
                rowWidth = 0
                rowHeight = 0
            }
            rowWidth += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        totalHeight += rowHeight
        totalWidth = max(totalWidth, rowWidth - spacing)
        return CGSize(width: min(totalWidth, maxWidth), height: totalHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) {
        let maxWidth = bounds.width
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0

        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x + size.width > bounds.minX + maxWidth, x > bounds.minX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}

#Preview { HabitsView().modelContainer(ChronosStore.previewContainer()) }
