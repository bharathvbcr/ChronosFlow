import SwiftUI
import SwiftData
import ChronosCore

/// The Habits tab: streaks, completion windows, flexible cadence, and one-tap completion.
/// Ports `feature/habits`.
struct HabitsView: View {
    @Environment(\.modelContext) private var context
    @Query(sort: \Habit.title) private var habits: [Habit]
    @Query private var allBlocks: [TimeBlock]
    @State private var creating = false

    private var todayBusy: [(start: Int, end: Int)] {
        allBlocks
            .filter { Calendar.current.isDateInToday($0.date) }
            .map { (start: $0.startMinuteOfDay, end: $0.startMinuteOfDay + $0.durationMinutes) }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: ChronosSpacing.compact) {
                    ForEach(habits) { habit in HabitCard(habit: habit, busy: todayBusy) }
                }
                .padding(ChronosSpacing.standard)
            }
            .background { ChronosBackdrop() }
            .navigationTitle("Habits")
            .chronosScrollMinimizedBar()
            .overlay { if habits.isEmpty { ContentUnavailableView("No habits", systemImage: "heart", description: Text("Build a routine that fits your day")) } }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { creating = true } label: { Image(systemName: "plus") }
                }
            }
            .sheet(isPresented: $creating) { HabitEditorSheet() }
        }
    }
}

private struct HabitCard: View {
    @Environment(\.modelContext) private var context
    @Bindable var habit: Habit
    var busy: [(start: Int, end: Int)]
    @State private var repairSuggestion: Int?

    private var doneToday: Bool { habit.isCompleted(on: .now) }

    /// Trailing-window completion rates surfaced from ChronosCore (0…1).
    private var rate7: Double { completionRate(completionDates: habit.completionDates, window: 7) }
    private var rate30: Double { completionRate(completionDates: habit.completionDates, window: 30) }

    var body: some View {
        ChronosGlassCard(tint: doneToday ? ChronosColors.brandSecondary : nil) {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                HStack(spacing: ChronosSpacing.compact) {
                    VStack(alignment: .leading, spacing: ChronosSpacing.micro) {
                        Text(habit.title).font(.chronosHeadline)
                        HStack(spacing: ChronosSpacing.small) {
                            Label("\(habit.streakCount)", systemImage: "flame.fill")
                                .foregroundStyle(ChronosColors.brandAccent)
                            Text(habit.cadence)
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
                }
                if !habit.completionDates.isEmpty {
                    ConsistencyRow(rate7: rate7, rate30: rate30)
                }
                if !doneToday, let suggestion = repairSuggestion {
                    Button {
                        scheduleRepair(at: suggestion)
                    } label: {
                        Label("Schedule at \(suggestion.clockTime)", systemImage: "calendar.badge.plus")
                            .font(.chronosCaption)
                    }
                    .buttonStyle(.bordered).buttonBorderShape(.capsule)
                }
            }
            .frame(maxWidth: .infinity)
        }
        .pressable()
        .onAppear { computeRepair() }
    }

    /// Mirror of ChronosCore.suggestHabitRepair: earliest free slot in the window from now.
    private func computeRepair() {
        guard !doneToday else { repairSuggestion = nil; return }
        let now = nowMinute()
        let dur = 30
        let lower = max(habit.windowStartMinute, now)
        guard lower + dur <= habit.windowEndMinute else { repairSuggestion = nil; return }
        let spans = busy
            .map { (start: max($0.start, habit.windowStartMinute), end: min($0.end, habit.windowEndMinute)) }
            .filter { $0.start < $0.end }
            .sorted { $0.start < $1.start }
        var cursor = lower
        for span in spans {
            if span.start - cursor >= dur { repairSuggestion = cursor; return }
            cursor = max(cursor, span.end)
        }
        repairSuggestion = cursor + dur <= habit.windowEndMinute ? cursor : nil
    }

    private func scheduleRepair(at minute: Int) {
        context.insert(TimeBlock(
            date: .now, title: habit.title, category: "HABIT",
            startMinuteOfDay: minute, durationMinutes: 30,
            provenance: .habit, flexibility: .movable, habitID: habit.id))
        try? context.save()
        repairSuggestion = nil
    }

    private func nowMinute() -> Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: .now)
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }
}

/// A small consistency / completion-rate strip surfacing `ChronosCore.completionRate` over the
/// trailing 7 and 30 days. Mirrors the Android habit completion-rate card.
private struct ConsistencyRow: View {
    let rate7: Double
    let rate30: Double

    var body: some View {
        HStack(spacing: ChronosSpacing.standard) {
            stat("7-day", rate7)
            stat("30-day", rate30)
            Spacer()
        }
        .padding(.top, ChronosSpacing.micro)
    }

    @ViewBuilder private func stat(_ label: String, _ rate: Double) -> some View {
        VStack(alignment: .leading, spacing: ChronosSpacing.micro) {
            HStack(spacing: ChronosSpacing.micro) {
                Text(label).font(.chronosCaption).foregroundStyle(.secondary)
                Text("\(Int((rate * 100).rounded()))%")
                    .font(.chronosCaption).fontWeight(.semibold)
            }
            ProgressView(value: rate)
                .progressViewStyle(.linear)
                .tint(ChronosColors.brandSecondary)
                .frame(width: 84)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label) completion \(Int((rate * 100).rounded())) percent")
    }
}

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

#Preview { HabitsView().modelContainer(ChronosStore.previewContainer()) }
