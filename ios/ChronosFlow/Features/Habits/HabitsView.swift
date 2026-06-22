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
    // Only active (non-archived) habits, matching Android's GetActiveHabitsUseCase.
    @Query(filter: #Predicate<Habit> { $0.isActive }, sort: \Habit.title) private var habits: [Habit]
    @Query private var allBlocks: [TimeBlock]
    @State private var creating = false
    @State private var repair = HabitRepairAssistant()
    @State private var contextHabit: Habit?

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
            ScrollView {
                LazyVStack(spacing: ChronosSpacing.compact) {
                    if !habits.isEmpty {
                        metricTiles
                        if !repair.suggestions.isEmpty || repair.isThinking {
                            HabitRepairPanel(
                                assistant: repair,
                                onComplete: { applyRepair($0) }
                            )
                        }
                        ConsistencyCard(habits: habits)
                        StreakChartCard(habits: habits)
                    }
                    ForEach(habits) { habit in
                        HabitCard(
                            habit: habit,
                            busy: todayBusy,
                            onMore: { contextHabit = habit }
                        )
                    }
                }
                .padding(ChronosSpacing.standard)
            }
            .background { ChronosBackdrop() }
            .navigationTitle("Habits")
            .chronosScrollMinimizedBar()
            .overlay {
                if habits.isEmpty {
                    ContentUnavailableView("No habits", systemImage: "heart",
                                           description: Text("Build a routine that fits your day"))
                }
            }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { creating = true } label: { Image(systemName: "plus") }
                }
            }
            .sheet(isPresented: $creating) { HabitEditorSheet() }
            .sheet(item: $contextHabit) { habit in
                HabitContextActionSheet(
                    habit: habit,
                    onDuplicate: { duplicate(habit); contextHabit = nil },
                    onArchive: { archive(habit); contextHabit = nil }
                )
            }
            .task(id: habits.map(\.id)) { refreshRepair() }
            .onChange(of: todayBusy.count) { refreshRepair() }
        }
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
        ChronosGlassCard {
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
            ChronosGlassCard {
                VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                    Text("Top streaks").font(.chronosHeadline)
                    ForEach(rows, id: \.id) { row in
                        HStack(spacing: ChronosSpacing.small) {
                            Text(row.title)
                                .font(.chronosLabel).lineLimit(1)
                                .frame(width: 96, alignment: .leading)
                            GeometryReader { geo in
                                Capsule()
                                    .fill(ChronosColors.brandAccent)
                                    .frame(width: max(geo.size.width * CGFloat(row.streak) / CGFloat(maxStreak), 6))
                                    .frame(maxHeight: .infinity, alignment: .leading)
                            }
                            .frame(height: 10)
                            Text("\(row.streak)d")
                                .font(.chronosCaption).fontWeight(.semibold)
                                .frame(width: 36, alignment: .trailing)
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

    private var doneToday: Bool { habit.isCompleted(on: .now) }
    private var skippedToday: Bool { habit.isSkipped(on: .now) }
    private var paused: Bool { habit.isPaused() }
    private var analytics: HabitAnalytics { habit.analytics(today: .now) }

    var body: some View {
        ChronosGlassCard(tint: doneToday ? ChronosColors.brandSecondary : nil) {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                HStack(spacing: ChronosSpacing.compact) {
                    VStack(alignment: .leading, spacing: ChronosSpacing.micro) {
                        Text(habit.title).font(.chronosHeadline)
                        HStack(spacing: ChronosSpacing.small) {
                            Label("\(analytics.currentStreak)", systemImage: "flame.fill")
                                .foregroundStyle(ChronosColors.brandAccent)
                            Text(cadenceLabel)
                            Text("·")
                            Text("\(habit.windowStartMinute.clockTime)–\(habit.windowEndMinute.clockTime)")
                        }
                        .font(.chronosCaption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button {
                        withAnimation(ChronosMotion.bouncy) {
                            habit.toggleCompletion(on: .now); try? context.save()
                        }
                    } label: {
                        Image(systemName: doneToday ? "checkmark.circle.fill" : "circle")
                            .font(.largeTitle)
                            .foregroundStyle(doneToday ? ChronosColors.brandSecondary : .secondary)
                    }
                    .buttonStyle(.plain)
                    Button { onMore() } label: {
                        Image(systemName: "ellipsis.circle").font(.title3).foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                }
                statusPills
            }
            .frame(maxWidth: .infinity)
        }
        .pressable()
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

    private var computedPills: [Pill] {
        var pills: [Pill] = []
        let now = nowMinuteOfDay()
        let dueNow = !doneToday && !skippedToday && !paused
            && now >= habit.windowStartMinute && now <= habit.windowEndMinute
        if dueNow { pills.append(Pill(text: "Due now", tint: ChronosColors.brandPrimary, emphasized: true)) }
        if paused { pills.append(Pill(text: "Paused", tint: .secondary, emphasized: false)) }
        if skippedToday { pills.append(Pill(text: "Skipped today", tint: .secondary, emphasized: false)) }
        if habit.isBundled { pills.append(Pill(text: "On day plan", tint: ChronosColors.brandSecondary, emphasized: false)) }
        if let milestone = habitStreakMilestoneLabel(analytics.currentStreak) {
            pills.append(Pill(text: milestone, tint: ChronosColors.brandAccent, emphasized: true))
        }
        // Adherence is over the trailing 14 days; only surface it once there's signal.
        let hasAdherenceSignal = analytics.adherenceRate > 0
            || analytics.skippedCountLast14Days > 0
            || analytics.completedCountLast7Days > 0
        if hasAdherenceSignal {
            let pct = Int((analytics.adherenceRate * 100).rounded())
            let emphasized = analytics.adherenceRate >= 0.8
            pills.append(Pill(text: "\(pct)% adherence",
                              tint: emphasized ? ChronosColors.brandSecondary : .secondary,
                              emphasized: emphasized))
        }
        if let best = analytics.bestCompletionMinuteOfDay {
            pills.append(Pill(text: "Best \(best.clockTime)", tint: .secondary, emphasized: false))
        }
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
        ChronosGlassCard(tint: ChronosColors.brandPrimary) {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                HStack(spacing: ChronosSpacing.small) {
                    Image(systemName: "wand.and.stars").foregroundStyle(ChronosColors.brandPrimary)
                    Text("Missed-habit repair").font(.chronosHeadline)
                    Spacer()
                }
                banner
                ForEach(assistant.suggestions.prefix(3)) { suggestion in
                    VStack(alignment: .leading, spacing: ChronosSpacing.micro) {
                        Text(suggestion.title).font(.chronosLabel)
                        Text(suggestion.reason).font(.chronosCaption).foregroundStyle(.secondary)
                        HStack {
                            Label("Schedule at \(suggestion.startMinute.clockTime)–\(suggestion.endMinute.clockTime)",
                                  systemImage: "calendar.badge.plus")
                                .font(.chronosCaption).foregroundStyle(.secondary)
                            Spacer()
                            Text(suggestion.source.label)
                                .font(.chronosCaption).foregroundStyle(.tertiary)
                        }
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
                    Button { onDuplicate() } label: { Label("Duplicate", systemImage: "plus.square.on.square") }
                    Button(role: .destructive) {
                        confirmingArchive = true
                    } label: { Label("Archive", systemImage: "archivebox") }
                }
            }
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
    @State private var title = ""
    @State private var cadence = "DAILY"
    @State private var difficulty = 2.0
    @State private var windowStart = Date.now
    @State private var windowEnd = Calendar.current.date(byAdding: .hour, value: 16, to: .now) ?? .now

    var body: some View {
        NavigationStack {
            Form {
                TextField("Habit", text: $title)
                Picker("Cadence", selection: $cadence) {
                    Text("Daily").tag("DAILY")
                    Text("Weekly").tag("WEEKLY")
                    Text("3× / week").tag("3x/week")
                    Text("5× / week").tag("5x/week")
                }
                VStack(alignment: .leading) {
                    Text("Difficulty: \(Int(difficulty))")
                    Slider(value: $difficulty, in: 1...5, step: 1)
                }
                DatePicker("Window start", selection: $windowStart, displayedComponents: .hourAndMinute)
                DatePicker("Window end", selection: $windowEnd, displayedComponents: .hourAndMinute)
            }
            .navigationTitle("New habit")
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        context.insert(Habit(
                            title: title, cadence: cadence, windowStartMinute: minute(windowStart),
                            windowEndMinute: minute(windowEnd), difficulty: Int(difficulty)))
                        try? context.save(); dismiss()
                    }.disabled(title.isEmpty)
                }
            }
        }
        .presentationDetents([.medium])
    }

    private func minute(_ date: Date) -> Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: date)
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }
}

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
