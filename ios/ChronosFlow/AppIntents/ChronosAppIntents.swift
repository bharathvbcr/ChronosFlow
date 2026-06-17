import AppIntents
import SwiftData
import Foundation

// App Intents + Siri Shortcuts — the iOS-native equivalent of the Android AppFunctions surface
// (ChronosAppFunctions.kt, ~20 functions): expose the core workflows so they run from Siri,
// Spotlight, the Shortcuts app, and the Action Button without opening the UI.
//
// Each intent opens the shared App-Group ModelContainer via `ChronosStore.shared`, performs its
// mutation on the main context, and returns a concise spoken dialog. Reads return a snapshot string.

// MARK: - Shared helpers

@MainActor
private func chronosContext() -> ModelContext { ChronosStore.shared.mainContext }

private func clampScore(_ v: Int, _ range: ClosedRange<Int> = 1...5) -> Int {
    min(max(v, range.lowerBound), range.upperBound)
}

private func nowMinuteOfDay() -> Int {
    let c = Calendar.current.dateComponents([.hour, .minute], from: .now)
    return (c.hour ?? 0) * 60 + (c.minute ?? 0)
}

// MARK: - Create / log (existing)

struct AddTaskIntent: AppIntent {
    static let title: LocalizedStringResource = "Add Task"
    static let description = IntentDescription("Create a new task in ChronosFlow.")

    @Parameter(title: "Title") var taskTitle: String
    @Parameter(title: "Priority", default: .none) var priority: TaskPriorityAppEnum

    static var parameterSummary: some ParameterSummary {
        Summary("Add task \(\.$taskTitle) with priority \(\.$priority)")
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let context = chronosContext()
        context.insert(TaskItem(title: taskTitle, priority: priority.rawValue))
        try? context.save()
        return .result(dialog: "Added “\(taskTitle)” to your tasks.")
    }
}

enum TaskPriorityAppEnum: Int, AppEnum {
    case none = 0, low = 1, medium = 2, high = 3
    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "Priority")
    static let caseDisplayRepresentations: [TaskPriorityAppEnum: DisplayRepresentation] = [
        .none: "None", .low: "Low", .medium: "Medium", .high: "High",
    ]
}

struct LogMoodIntent: AppIntent {
    static let title: LocalizedStringResource = "Log Mood Check-in"
    static let description = IntentDescription("Record a quick mood and energy check-in.")

    @Parameter(title: "Mood (1–5)", default: 3) var mood: Int
    @Parameter(title: "Energy (1–5)", default: 3) var energy: Int

    static var parameterSummary: some ParameterSummary {
        Summary("Log mood \(\.$mood) and energy \(\.$energy)")
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let context = chronosContext()
        context.insert(MoodEnergyCheckIn(
            moodScore: clampScore(mood), energyScore: clampScore(energy)))
        try? context.save()
        return .result(dialog: "Logged your check-in. Mood \(clampScore(mood))/5, energy \(clampScore(energy))/5.")
    }
}

// MARK: - Habits (ports logHabitCompleted)

struct LogHabitIntent: AppIntent {
    static let title: LocalizedStringResource = "Log Habit"
    static let description = IntentDescription("Mark a habit as done (or undo it) for today.")

    @Parameter(title: "Habit") var habit: HabitEntity

    static var parameterSummary: some ParameterSummary {
        Summary("Log habit \(\.$habit)")
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let context = chronosContext()
        let id = habit.id
        guard let model = try? context.fetch(
            FetchDescriptor<Habit>(predicate: #Predicate { $0.id == id })).first else {
            return .result(dialog: "I couldn't find that habit.")
        }
        let wasDone = model.isCompleted(on: .now)
        model.toggleCompletion(on: .now)
        try? context.save()
        return .result(dialog: wasDone
            ? "Unmarked “\(model.title)” for today."
            : "Marked “\(model.title)” done. \(model.streakCount)-day streak.")
    }
}

// MARK: - Medication (ports logMedicationTaken)

struct LogMedicationDoseIntent: AppIntent {
    static let title: LocalizedStringResource = "Log Medication Dose"
    static let description = IntentDescription("Record that you took a medication dose.")

    @Parameter(title: "Medication") var plan: MedicationEntity

    static var parameterSummary: some ParameterSummary {
        Summary("Log dose for \(\.$plan)")
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let context = chronosContext()
        let id = plan.id
        guard let model = try? context.fetch(
            FetchDescriptor<MedicationPlan>(predicate: #Predicate { $0.id == id })).first else {
            return .result(dialog: "I couldn't find that medication.")
        }
        model.acknowledgeDose()
        try? context.save()
        if let days = model.daysUntilRefill {
            return .result(dialog: "Logged your \(model.name) dose. About \(days) days of supply left.")
        }
        return .result(dialog: "Logged your \(model.name) dose.")
    }
}

// MARK: - Time blocks (ports addTimeBlock / completeTimeBlock / markTimeBlockMissed)

struct AddTimeBlockIntent: AppIntent {
    static let title: LocalizedStringResource = "Schedule Time Block"
    static let description = IntentDescription("Schedule a block of time on today's plan.")

    @Parameter(title: "Title") var blockTitle: String
    @Parameter(title: "Category", default: "FOCUS") var category: String
    @Parameter(title: "Start (minute of day, 0–1439)", default: 540) var startMinuteOfDay: Int
    @Parameter(title: "Duration (minutes)", default: 60) var durationMinutes: Int

    static var parameterSummary: some ParameterSummary {
        Summary("Schedule \(\.$blockTitle) for \(\.$durationMinutes) minutes at \(\.$startMinuteOfDay)")
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let start = min(max(startMinuteOfDay, 0), 1439)
        let duration = min(max(durationMinutes, 1), 1440)
        let context = chronosContext()
        context.insert(TimeBlock(
            date: .now,
            title: blockTitle,
            category: category.isEmpty ? "FOCUS" : category,
            startMinuteOfDay: start,
            durationMinutes: duration,
            provenance: .manual,
            source: "appintents"))
        try? context.save()
        return .result(dialog: "Scheduled “\(blockTitle)” at \(start.clockTime).")
    }
}

struct CompleteTimeBlockIntent: AppIntent {
    static let title: LocalizedStringResource = "Complete Time Block"
    static let description = IntentDescription("Mark a planned block as done, filling its window as actual time.")

    @Parameter(title: "Block") var block: TimeBlockEntity

    static var parameterSummary: some ParameterSummary { Summary("Complete \(\.$block)") }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let context = chronosContext()
        let id = block.id
        guard let model = try? context.fetch(
            FetchDescriptor<TimeBlock>(predicate: #Predicate { $0.id == id })).first else {
            return .result(dialog: "I couldn't find that block.")
        }
        // Idempotent: fill the planned window as actual time so the day review counts it done.
        if model.actualStartMinuteOfDay == nil {
            model.actualStartMinuteOfDay = model.startMinuteOfDay
            model.actualEndMinuteOfDay = model.plannedEndMinuteOfDay
            model.updatedAt = .now
            try? context.save()
        }
        return .result(dialog: "Marked “\(model.title)” complete.")
    }
}

struct MarkTimeBlockMissedIntent: AppIntent {
    static let title: LocalizedStringResource = "Mark Block Missed"
    static let description = IntentDescription("Mark a planned block as missed or skipped.")

    @Parameter(title: "Block") var block: TimeBlockEntity

    static var parameterSummary: some ParameterSummary { Summary("Mark \(\.$block) missed") }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let context = chronosContext()
        let id = block.id
        guard let model = try? context.fetch(
            FetchDescriptor<TimeBlock>(predicate: #Predicate { $0.id == id })).first else {
            return .result(dialog: "I couldn't find that block.")
        }
        // Clear any logged actual window so the day review surfaces it as missed.
        model.actualStartMinuteOfDay = nil
        model.actualEndMinuteOfDay = nil
        model.updatedAt = .now
        try? context.save()
        return .result(dialog: "Marked “\(model.title)” as missed.")
    }
}

// MARK: - Sleep (ports logSleep)

struct LogSleepIntent: AppIntent {
    static let title: LocalizedStringResource = "Log Sleep"
    static let description = IntentDescription("Record last night's sleep quality and times.")

    @Parameter(title: "Quality (1–5)", default: 3) var quality: Int
    @Parameter(title: "Bedtime (minute of day)") var bedMinute: Int?
    @Parameter(title: "Wake time (minute of day)") var wakeMinute: Int?

    static var parameterSummary: some ParameterSummary {
        Summary("Log sleep quality \(\.$quality)") {
            \.$bedMinute
            \.$wakeMinute
        }
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let context = chronosContext()
        let today = Calendar.current.startOfDay(for: .now)
        // Merge into any existing track for the day (date is logically unique).
        let existing = (try? context.fetch(FetchDescriptor<SleepTrack>()))?
            .first { Calendar.current.isDate($0.date, inSameDayAs: today) }
        let q = clampScore(quality)
        if let existing {
            existing.sleepQuality = q
            if let bedMinute { existing.actualStartMinute = min(max(bedMinute, 0), 1439) }
            if let wakeMinute { existing.actualEndMinute = min(max(wakeMinute, 0), 1439) }
            existing.source = .manual
        } else {
            context.insert(SleepTrack(
                date: today,
                actualStartMinute: bedMinute.map { min(max($0, 0), 1439) },
                actualEndMinute: wakeMinute.map { min(max($0, 0), 1439) },
                sleepQuality: q,
                source: .manual))
        }
        try? context.save()
        return .result(dialog: "Logged your sleep. Quality \(q)/5.")
    }
}

// MARK: - Journal (ports addJournalEntry)

struct AddJournalEntryIntent: AppIntent {
    static let title: LocalizedStringResource = "Add Journal Entry"
    static let description = IntentDescription("Write a journal entry for today.")

    @Parameter(title: "Entry", inputOptions: String.IntentInputOptions(multiline: true))
    var text: String

    static var parameterSummary: some ParameterSummary { Summary("Journal \(\.$text)") }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return .result(dialog: "Nothing to journal.") }
        let context = chronosContext()
        context.insert(JournalEntry(body: trimmed))
        try? context.save()
        return .result(dialog: "Saved your journal entry.")
    }
}

// MARK: - Goals (ports createGoal)

struct CreateGoalIntent: AppIntent {
    static let title: LocalizedStringResource = "Create Goal"
    static let description = IntentDescription("Set a long-term goal to work towards.")

    @Parameter(title: "Title") var goalTitle: String
    @Parameter(title: "Target value", default: 1) var targetValue: Int

    static var parameterSummary: some ParameterSummary {
        Summary("Create goal \(\.$goalTitle) with target \(\.$targetValue)")
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let context = chronosContext()
        context.insert(Goal(title: goalTitle, targetValue: max(targetValue, 1)))
        try? context.save()
        return .result(dialog: "Created the goal “\(goalTitle)”.")
    }
}

// MARK: - Routines (ports applyRoutine)

struct ApplyRoutineIntent: AppIntent {
    static let title: LocalizedStringResource = "Apply Routine"
    static let description = IntentDescription("Schedule a saved routine's steps onto today's plan.")

    @Parameter(title: "Routine") var routine: RoutineEntity

    static var parameterSummary: some ParameterSummary { Summary("Apply routine \(\.$routine)") }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let context = chronosContext()
        let id = routine.id
        guard let model = try? context.fetch(
            FetchDescriptor<Routine>(predicate: #Predicate { $0.id == id })).first else {
            return .result(dialog: "I couldn't find that routine.")
        }
        let today = Calendar.current.startOfDay(for: .now)
        // Routine step offsets are absolute minute-of-day; instantiate each as a TimeBlock (anchor 0).
        var created = 0
        for step in model.steps {
            let start = min(max(step.offsetMinute, 0), 1439)
            context.insert(TimeBlock(
                date: today,
                title: step.title,
                category: step.category,
                startMinuteOfDay: start,
                durationMinutes: min(max(step.durationMinutes, 1), 1440),
                provenance: .routine,
                energyLevel: EnergyIntensity.fromLevel(step.energyLevel),
                source: "routine",
                routineID: model.id))
            created += 1
        }
        model.lastCompletedDate = today
        try? context.save()
        return created >= 1
            ? .result(dialog: "Applied “\(model.title)” — scheduled \(created) blocks.")
            : .result(dialog: "“\(model.title)” has no steps to schedule.")
    }
}

// MARK: - Reflow (ports reflowRemainingDay)

struct ReflowRemainingDayIntent: AppIntent {
    static let title: LocalizedStringResource = "Reflow Remaining Day"
    static let description = IntentDescription("Pull flexible blocks forward to close gaps from now on.")

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let context = chronosContext()
        let now = nowMinuteOfDay()
        let blocks = (try? context.fetch(FetchDescriptor<TimeBlock>()))?
            .filter { Calendar.current.isDateInToday($0.date) }
            .sorted { $0.startMinuteOfDay < $1.startMinuteOfDay } ?? []

        // Repack movable/resizable/optional blocks that start at or after `now`, back-to-back from
        // the earliest such start, leaving fixed/locked/in-progress blocks where they are. This is a
        // lightweight, on-device analogue of PlannerService.rebalanceDay (gap-closing only).
        let flexible = blocks.filter {
            $0.startMinuteOfDay >= now && !$0.isLocked
                && ($0.flexibility == .movable || $0.flexibility == .resizable || $0.flexibility == .optional)
        }
        guard let firstStart = flexible.map(\.startMinuteOfDay).min() else {
            return .result(dialog: "No flexible blocks left to reorganize.")
        }
        var cursor = firstStart
        for block in flexible.sorted(by: { $0.startMinuteOfDay < $1.startMinuteOfDay }) {
            if block.startMinuteOfDay != cursor { block.updateStart(cursor) }
            cursor = min(cursor + block.durationMinutes, 1439)
        }
        try? context.save()
        return .result(dialog: "Reflowed the rest of your day, closing \(flexible.count == 1 ? "1 gap" : "\(flexible.count) blocks").")
    }
}

// MARK: - Reads (port getTodaySchedule / listOpenTasks / listHabits)

struct GetTodayScheduleIntent: AppIntent {
    static let title: LocalizedStringResource = "Today's Schedule"
    static let description = IntentDescription("Read back today's planned time blocks.")

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog & ReturnsValue<String> {
        let context = chronosContext()
        let blocks = (try? context.fetch(FetchDescriptor<TimeBlock>()))?
            .filter { Calendar.current.isDateInToday($0.date) }
            .sorted { $0.startMinuteOfDay < $1.startMinuteOfDay } ?? []
        guard !blocks.isEmpty else {
            return .result(value: "Nothing planned today.", dialog: "Nothing planned today.")
        }
        let lines = blocks.map { "\($0.startMinuteOfDay.clockTime) \($0.title)" }
        let summary = lines.joined(separator: ", ")
        return .result(value: summary, dialog: "You have \(blocks.count) blocks: \(summary).")
    }
}

struct ListOpenTasksIntent: AppIntent {
    static let title: LocalizedStringResource = "List Open Tasks"
    static let description = IntentDescription("Read back your open tasks, highest priority first.")

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog & ReturnsValue<String> {
        let context = chronosContext()
        let open = (try? context.fetch(FetchDescriptor<TaskItem>()))?
            .filter { !$0.isCompleted }
            .sorted { $0.priority > $1.priority } ?? []
        guard !open.isEmpty else {
            return .result(value: "No open tasks.", dialog: "You're all caught up — no open tasks.")
        }
        let summary = open.prefix(10).map(\.title).joined(separator: ", ")
        return .result(value: summary, dialog: "You have \(open.count) open tasks: \(summary).")
    }
}

struct ListHabitsIntent: AppIntent {
    static let title: LocalizedStringResource = "List Habits"
    static let description = IntentDescription("Read back your active habits and streaks.")

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog & ReturnsValue<String> {
        let context = chronosContext()
        let habits = (try? context.fetch(FetchDescriptor<Habit>()))?
            .filter(\.isActive)
            .sorted { $0.streakCount > $1.streakCount } ?? []
        guard !habits.isEmpty else {
            return .result(value: "No habits yet.", dialog: "You have no habits set up yet.")
        }
        let summary = habits.map { h in
            let mark = h.isCompleted(on: .now) ? "done" : "pending"
            return "\(h.title) (\(mark), \(h.streakCount)-day)"
        }.joined(separator: ", ")
        return .result(value: summary, dialog: "Your habits: \(summary).")
    }
}

// MARK: - Focus (opens app)

struct StartFocusSessionIntent: AppIntent {
    static let title: LocalizedStringResource = "Start Focus Session"
    static let description = IntentDescription("Open ChronosFlow to start a focus session.")
    static let openAppWhenRun = true

    @MainActor
    func perform() async throws -> some IntentResult {
        // Opening the app lands on the Focus tab via the default selection flow.
        .result()
    }
}

struct NextBlockIntent: AppIntent {
    static let title: LocalizedStringResource = "What's Next"
    static let description = IntentDescription("Ask ChronosFlow what's on your plan next.")

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let now = nowMinuteOfDay()
        let context = chronosContext()
        let blocks = (try? context.fetch(FetchDescriptor<TimeBlock>()))?
            .filter { Calendar.current.isDateInToday($0.date) } ?? []
        let current = blocks.first { now >= $0.startMinuteOfDay && now < $0.startMinuteOfDay + $0.durationMinutes }
        let next = blocks.filter { $0.startMinuteOfDay > now }.min { $0.startMinuteOfDay < $1.startMinuteOfDay }

        if let current {
            return .result(dialog: "Right now: \(current.title) until \(current.plannedEndMinuteOfDay.clockTime).")
        } else if let next {
            return .result(dialog: "Up next: \(next.title) at \(next.startMinuteOfDay.clockTime).")
        }
        return .result(dialog: "Nothing scheduled for the rest of today.")
    }
}

// MARK: - AppEntity types for picker parameters
//
// These let Siri / Shortcuts present a list to choose the habit, medication, block, or routine.
// Each is backed by its model's stable id and resolved against the shared App-Group store.

struct HabitEntity: AppEntity {
    let id: String
    let title: String

    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "Habit")
    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(title)") }
    static let defaultQuery = HabitEntityQuery()
}

struct HabitEntityQuery: EntityQuery {
    @MainActor
    func entities(for identifiers: [String]) async throws -> [HabitEntity] {
        let context = chronosContext()
        let all = (try? context.fetch(FetchDescriptor<Habit>())) ?? []
        return all.filter { identifiers.contains($0.id) }.map { HabitEntity(id: $0.id, title: $0.title) }
    }
    @MainActor
    func suggestedEntities() async throws -> [HabitEntity] {
        let context = chronosContext()
        return ((try? context.fetch(FetchDescriptor<Habit>())) ?? [])
            .filter(\.isActive)
            .map { HabitEntity(id: $0.id, title: $0.title) }
    }
}

struct MedicationEntity: AppEntity {
    let id: String
    let name: String

    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "Medication")
    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)") }
    static let defaultQuery = MedicationEntityQuery()
}

struct MedicationEntityQuery: EntityQuery {
    @MainActor
    func entities(for identifiers: [String]) async throws -> [MedicationEntity] {
        let context = chronosContext()
        let all = (try? context.fetch(FetchDescriptor<MedicationPlan>())) ?? []
        return all.filter { identifiers.contains($0.id) }.map { MedicationEntity(id: $0.id, name: $0.name) }
    }
    @MainActor
    func suggestedEntities() async throws -> [MedicationEntity] {
        let context = chronosContext()
        return ((try? context.fetch(FetchDescriptor<MedicationPlan>())) ?? [])
            .filter(\.isActive)
            .map { MedicationEntity(id: $0.id, name: $0.name) }
    }
}

struct TimeBlockEntity: AppEntity {
    let id: String
    let title: String

    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "Time Block")
    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(title)") }
    static let defaultQuery = TimeBlockEntityQuery()
}

struct TimeBlockEntityQuery: EntityQuery {
    @MainActor
    func entities(for identifiers: [String]) async throws -> [TimeBlockEntity] {
        let context = chronosContext()
        let all = (try? context.fetch(FetchDescriptor<TimeBlock>())) ?? []
        return all.filter { identifiers.contains($0.id) }.map { TimeBlockEntity(id: $0.id, title: $0.title) }
    }
    @MainActor
    func suggestedEntities() async throws -> [TimeBlockEntity] {
        let context = chronosContext()
        return ((try? context.fetch(FetchDescriptor<TimeBlock>())) ?? [])
            .filter { Calendar.current.isDateInToday($0.date) }
            .sorted { $0.startMinuteOfDay < $1.startMinuteOfDay }
            .map { TimeBlockEntity(id: $0.id, title: $0.title) }
    }
}

struct RoutineEntity: AppEntity {
    let id: String
    let title: String

    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "Routine")
    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(title)") }
    static let defaultQuery = RoutineEntityQuery()
}

struct RoutineEntityQuery: EntityQuery {
    @MainActor
    func entities(for identifiers: [String]) async throws -> [RoutineEntity] {
        let context = chronosContext()
        let all = (try? context.fetch(FetchDescriptor<Routine>())) ?? []
        return all.filter { identifiers.contains($0.id) }.map { RoutineEntity(id: $0.id, title: $0.title) }
    }
    @MainActor
    func suggestedEntities() async throws -> [RoutineEntity] {
        let context = chronosContext()
        return ((try? context.fetch(FetchDescriptor<Routine>())) ?? [])
            .filter(\.isActive)
            .map { RoutineEntity(id: $0.id, title: $0.title) }
    }
}

// MARK: - Shortcut phrases
//
// Registers spoken phrases for the highest-value intents (the system caps the visible set, so we
// surface the most natural voice actions; the rest remain available in the Shortcuts app).
// `AppShortcutsProvider` is auto-discovered — no manual registration in the app entry point.

struct ChronosShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(intent: NextBlockIntent(),
                    phrases: ["What's next in \(.applicationName)", "\(.applicationName) what's next"],
                    shortTitle: "What's Next", systemImageName: "clock")
        AppShortcut(intent: AddTaskIntent(),
                    phrases: ["Add a task in \(.applicationName)", "New \(.applicationName) task"],
                    shortTitle: "Add Task", systemImageName: "checklist")
        AppShortcut(intent: LogMoodIntent(),
                    phrases: ["Log my mood in \(.applicationName)", "\(.applicationName) check in"],
                    shortTitle: "Log Mood", systemImageName: "face.smiling")
        AppShortcut(intent: StartFocusSessionIntent(),
                    phrases: ["Start a focus session in \(.applicationName)", "Focus with \(.applicationName)"],
                    shortTitle: "Start Focus", systemImageName: "timer")
        AppShortcut(intent: LogHabitIntent(),
                    phrases: ["Log a habit in \(.applicationName)", "\(.applicationName) habit done"],
                    shortTitle: "Log Habit", systemImageName: "heart.fill")
        AppShortcut(intent: LogMedicationDoseIntent(),
                    phrases: ["Log a dose in \(.applicationName)", "I took my medication in \(.applicationName)"],
                    shortTitle: "Log Dose", systemImageName: "pills.fill")
        AppShortcut(intent: LogSleepIntent(),
                    phrases: ["Log my sleep in \(.applicationName)", "\(.applicationName) sleep log"],
                    shortTitle: "Log Sleep", systemImageName: "bed.double.fill")
        AppShortcut(intent: AddJournalEntryIntent(),
                    phrases: ["Add a journal entry in \(.applicationName)", "Journal in \(.applicationName)"],
                    shortTitle: "Journal", systemImageName: "book.closed")
        AppShortcut(intent: GetTodayScheduleIntent(),
                    phrases: ["What's my schedule in \(.applicationName)", "\(.applicationName) today's schedule"],
                    shortTitle: "Today's Schedule", systemImageName: "calendar")
        AppShortcut(intent: ReflowRemainingDayIntent(),
                    phrases: ["Reflow my day in \(.applicationName)", "Fix my day in \(.applicationName)"],
                    shortTitle: "Reflow Day", systemImageName: "arrow.triangle.2.circlepath")
    }
}
