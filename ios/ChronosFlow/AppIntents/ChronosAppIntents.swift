import AppIntents
import SwiftData
import Foundation
import ActivityKit
import ChronosCore

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

/// Feature-flag gate for AppIntents. Mirrors Android AppFunctions' `featureEnabled { it.XXXEnabled }`
/// guards: when the user has the feature turned off, the intent must not act — it returns a friendly
/// dialog instead of mutating data so a Siri/Shortcuts user can't operate a hidden surface.
@MainActor
private func featureDialog(_ enabled: Bool, _ disabledMessage: String) -> IntentDialog? {
    enabled ? nil : IntentDialog(stringLiteral: disabledMessage)
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
    static let description = IntentDescription("Record a quick mood, energy, stress, and focus check-in.")

    @Parameter(title: "Mood (1–5)", default: 3) var mood: Int
    @Parameter(title: "Energy (1–5)", default: 3) var energy: Int
    // Android logMoodEnergyCheckIn takes all four scores; iOS exposes them with defaults so a voice
    // flow can populate the full MoodEnergyCheckIn while still working from a one-word "log my mood".
    @Parameter(title: "Stress (1–5)", default: 3) var stress: Int
    @Parameter(title: "Focus (1–5)", default: 3) var focus: Int

    static var parameterSummary: some ParameterSummary {
        Summary("Log mood \(\.$mood), energy \(\.$energy), stress \(\.$stress), focus \(\.$focus)")
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let context = chronosContext()
        let m = clampScore(mood), e = clampScore(energy), s = clampScore(stress), f = clampScore(focus)
        context.insert(MoodEnergyCheckIn(
            moodScore: m, stressScore: s, energyScore: e, focusScore: f))
        try? context.save()
        return .result(dialog: "Logged your check-in. Mood \(m)/5, energy \(e)/5, stress \(s)/5, focus \(f)/5.")
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
        if let off = featureDialog(ChronosSettings.shared.habitsEnabled, "Habits are turned off in ChronosFlow.") {
            return .result(dialog: off)
        }
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

/// AI03: port of Android AppFunctions.logHabitSkipped — defer/skip a habit for today without breaking
/// the streak. Uses the Habit `skipDates`/`toggleSkip` fields added in Phase 1A (mirrors the SKIPPED
/// HabitEvent). Toggling: skipping a day clears any logged completion; running it again un-skips.
struct SkipHabitIntent: AppIntent {
    static let title: LocalizedStringResource = "Skip Habit"
    static let description = IntentDescription("Skip (defer) a habit for today without breaking its streak.")

    @Parameter(title: "Habit") var habit: HabitEntity

    static var parameterSummary: some ParameterSummary {
        Summary("Skip habit \(\.$habit)")
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        if let off = featureDialog(ChronosSettings.shared.habitsEnabled, "Habits are turned off in ChronosFlow.") {
            return .result(dialog: off)
        }
        let context = chronosContext()
        let id = habit.id
        guard let model = try? context.fetch(
            FetchDescriptor<Habit>(predicate: #Predicate { $0.id == id })).first else {
            return .result(dialog: "I couldn't find that habit.")
        }
        let wasSkipped = model.isSkipped(on: .now)
        model.toggleSkip(on: .now)
        try? context.save()
        return .result(dialog: wasSkipped
            ? "Un-skipped “\(model.title)” for today."
            : "Skipped “\(model.title)” for today.")
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
        if let off = featureDialog(ChronosSettings.shared.medicationEnabled, "Medication tracking is turned off in ChronosFlow.") {
            return .result(dialog: off)
        }
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

/// AI03: port of Android AppFunctions.logMedicationSkipped — record that a scheduled dose was skipped.
/// Appends a `.skipped` DoseEvent (Phase 1A) with an optional reason; does NOT touch remaining supply.
struct SkipMedicationDoseIntent: AppIntent {
    static let title: LocalizedStringResource = "Skip Medication Dose"
    static let description = IntentDescription("Record that you skipped a scheduled medication dose.")

    @Parameter(title: "Medication") var plan: MedicationEntity
    @Parameter(title: "Reason") var reason: String?

    static var parameterSummary: some ParameterSummary {
        Summary("Skip dose for \(\.$plan)") { \.$reason }
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        if let off = featureDialog(ChronosSettings.shared.medicationEnabled, "Medication tracking is turned off in ChronosFlow.") {
            return .result(dialog: off)
        }
        let context = chronosContext()
        let id = plan.id
        guard let model = try? context.fetch(
            FetchDescriptor<MedicationPlan>(predicate: #Predicate { $0.id == id })).first else {
            return .result(dialog: "I couldn't find that medication.")
        }
        let trimmedReason = reason?.trimmingCharacters(in: .whitespacesAndNewlines)
        model.recordDose(.skipped, reason: (trimmedReason?.isEmpty == false) ? trimmedReason : nil)
        try? context.save()
        return .result(dialog: "Skipped your \(model.name) dose for today.")
    }
}

/// AI03: port of Android AppFunctions.listMedications — read back active medications with dosage and
/// reminder time. Mirrors ListHabitsIntent / ListOpenTasksIntent. Feature-gated by medicationEnabled.
struct ListMedicationsIntent: AppIntent {
    static let title: LocalizedStringResource = "List Medications"
    static let description = IntentDescription("Read back your active medications and reminder times.")

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog & ReturnsValue<String> {
        guard ChronosSettings.shared.medicationEnabled else {
            return .result(value: "Medication tracking is turned off.", dialog: "Medication tracking is turned off in ChronosFlow.")
        }
        let context = chronosContext()
        let meds = (try? context.fetch(FetchDescriptor<MedicationPlan>()))?
            .filter(\.isActive)
            .sorted { $0.reminderMinuteOfDay < $1.reminderMinuteOfDay } ?? []
        guard !meds.isEmpty else {
            return .result(value: "No medications.", dialog: "You have no medications set up yet.")
        }
        let summary = meds.map { m -> String in
            let dose = m.dosage.isEmpty ? "" : " \(m.dosage)\(m.unit.isEmpty ? "" : " \(m.unit)")"
            return "\(m.name)\(dose) at \(m.reminderMinuteOfDay.clockTime)"
        }.joined(separator: ", ")
        return .result(value: summary, dialog: "Your medications: \(summary).")
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
        if let off = featureDialog(ChronosSettings.shared.sleepEnabled, "Sleep tracking is turned off in ChronosFlow.") {
            return .result(dialog: off)
        }
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
        if let off = featureDialog(ChronosSettings.shared.journalEnabled, "Journaling is turned off in ChronosFlow.") {
            return .result(dialog: off)
        }
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
        if let off = featureDialog(ChronosSettings.shared.goalsEnabled, "Goals are turned off in ChronosFlow.") {
            return .result(dialog: off)
        }
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
        if let off = featureDialog(ChronosSettings.shared.routinesEnabled, "Routines are turned off in ChronosFlow.") {
            return .result(dialog: off)
        }
        let context = chronosContext()
        let id = routine.id
        guard let model = try? context.fetch(
            FetchDescriptor<Routine>(predicate: #Predicate { $0.id == id })).first else {
            return .result(dialog: "I couldn't find that routine.")
        }
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: .now)
        // R02: instantiate via the rollover-correct ChronosCore API. Each step's offset is treated as
        // an absolute minute-of-day (anchor 0); steps crossing midnight roll onto the following date
        // (floorDiv/floorMod over 1440) instead of folding back to the same day. Applying a routine is
        // NOT "completing" it, so we deliberately leave `lastCompletedDate` untouched (Android parity:
        // ApplyRoutineToDateUseCase places blocks; completion is tracked separately).
        let specs = model.steps.map {
            RoutineStepSpec(
                title: $0.title,
                category: $0.category,
                offsetMinute: $0.offsetMinute,
                durationMinutes: $0.durationMinutes,
                energyLevel: $0.energyLevel)
        }
        guard let placed = instantiateRoutineOnDates(
            steps: specs, startMinute: 0, anchorDate: today, calendar: calendar) else {
            return .result(dialog: "I couldn't schedule “\(model.title)”.")
        }
        var created = 0
        for (block, date) in placed {
            context.insert(TimeBlock(
                date: date,
                title: block.title,
                category: block.category,
                startMinuteOfDay: block.startMinuteOfDay,
                durationMinutes: block.durationMinutes,
                provenance: .routine,
                energyLevel: EnergyIntensity.fromLevel(block.energyLevel),
                source: "routine",
                routineID: model.id))
            created += 1
        }
        try? context.save()
        return created >= 1
            ? .result(dialog: "Applied “\(model.title)” — scheduled \(created) blocks.")
            : .result(dialog: "“\(model.title)” has no steps to schedule.")
    }
}

/// Mark a routine as completed for today WITHOUT scheduling its blocks (the inverse of applying it).
/// Android tracks routine completion separately from placement; this lets a user say "I finished my
/// morning routine" to stamp `lastCompletedDate` for streak/insights without re-instantiating blocks.
struct MarkRoutineCompleteIntent: AppIntent {
    static let title: LocalizedStringResource = "Mark Routine Complete"
    static let description = IntentDescription("Mark a saved routine as completed for today.")

    @Parameter(title: "Routine") var routine: RoutineEntity

    static var parameterSummary: some ParameterSummary { Summary("Mark routine \(\.$routine) complete") }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        if let off = featureDialog(ChronosSettings.shared.routinesEnabled, "Routines are turned off in ChronosFlow.") {
            return .result(dialog: off)
        }
        let context = chronosContext()
        let id = routine.id
        guard let model = try? context.fetch(
            FetchDescriptor<Routine>(predicate: #Predicate { $0.id == id })).first else {
            return .result(dialog: "I couldn't find that routine.")
        }
        model.lastCompletedDate = Calendar.current.startOfDay(for: .now)
        try? context.save()
        return .result(dialog: "Marked “\(model.title)” complete for today.")
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
        guard ChronosSettings.shared.habitsEnabled else {
            return .result(value: "Habits are turned off.", dialog: "Habits are turned off in ChronosFlow.")
        }
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
    static let description = IntentDescription("Start a focus session in the background, with a live timer on the Lock Screen.")
    // AI02: Android startFocusSession persists a Running session immediately and best-effort starts the
    // foreground surface; the session exists even if the app stays closed. Match that — do NOT open the app.
    static let openAppWhenRun = false

    @Parameter(title: "Duration (minutes)", default: 25) var durationMinutes: Int

    static var parameterSummary: some ParameterSummary {
        Summary("Start a \(\.$durationMinutes)-minute focus session")
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let minutes = min(max(durationMinutes, 1), 480)
        let now = Date.now
        let context = chronosContext()

        // 1. Persist the running session immediately so it survives even if the live surface fails
        //    to start (Android: the FocusSessionState.Running is saved before FocusService is touched).
        let session = FocusSession(
            date: now,
            plannedDurationMinutes: minutes,
            startedAt: now,
            isCompleted: false)
        context.insert(session)
        try? context.save()

        // 2. Best-effort: surface a Live Activity countdown on the Lock Screen / Dynamic Island.
        //    Mirrors FocusTimerModel.startLiveActivity (same gating: setting on + activities authorized).
        startBackgroundFocusActivity(minutes: minutes, startedAt: now)

        return .result(dialog: "Started a \(minutes)-minute focus session.")
    }
}

/// Best-effort Live Activity start for a session created outside the in-app `FocusTimerModel`
/// (e.g. from Siri while the app is closed). A single flat work phase ending `minutes` from now.
/// Gated identically to `FocusTimerModel.startLiveActivity`: respects the user's Live Activity
/// setting and the system authorization. Silently no-ops when unavailable.
@MainActor
private func startBackgroundFocusActivity(minutes: Int, startedAt: Date) {
    guard ChronosSettings.shared.focusLiveActivityEnabled else { return }
    guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
    let endsAt = startedAt.addingTimeInterval(TimeInterval(minutes * 60))
    let attributes = FocusActivityAttributes(blockTitle: "Focus session", totalPhaseDurationMinutes: minutes)
    let state = FocusActivityAttributes.ContentState(
        phase: .work,
        phaseEndsAt: endsAt,
        isPaused: false,
        awaitingAdvance: false,
        phaseNumber: 1,
        totalPhases: 1,
        blockTitle: "Focus session",
        blockStartsAt: startedAt,
        blockEndsAt: endsAt,
        nextBlockTitle: nil,
        completionPercent: 0,
        phaseSegments: [],
        currentPhaseIndex: 0)
    _ = try? Activity.request(attributes: attributes, content: .init(state: state, staleDate: endsAt))
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

// MARK: - Combined log (ports combined sleep+journal quick-log)

struct LogSleepAndJournalIntent: AppIntent {
    static let title: LocalizedStringResource = "Log Sleep & Journal"
    static let description = IntentDescription("Record last night's sleep and optionally add a journal note.")

    @Parameter(title: "Sleep quality (1–5)", default: 3) var quality: Int
    @Parameter(title: "Journal note", default: "") var note: String

    static var parameterSummary: some ParameterSummary {
        Summary("Log sleep quality \(\.$quality) and note \(\.$note)")
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let sleepOn = ChronosSettings.shared.sleepEnabled
        let journalOn = ChronosSettings.shared.journalEnabled
        // The combined evening log needs at least one of the two surfaces enabled.
        guard sleepOn || journalOn else {
            return .result(dialog: "Sleep tracking and journaling are both turned off in ChronosFlow.")
        }
        let context = chronosContext()
        let today = Calendar.current.startOfDay(for: .now)
        let q = clampScore(quality)

        // Sleep (only when sleep tracking is enabled)
        var sleepMsg = ""
        if sleepOn {
            let existing = (try? context.fetch(FetchDescriptor<SleepTrack>()))?
                .first { Calendar.current.isDate($0.date, inSameDayAs: today) }
            if let existing { existing.sleepQuality = q } else {
                context.insert(SleepTrack(date: today, sleepQuality: q, source: .manual))
            }
            sleepMsg = "Logged sleep quality \(q)/5."
        }

        // Journal (only if non-empty AND journaling is enabled)
        var journalMsg = ""
        let trimmed = note.trimmingCharacters(in: .whitespacesAndNewlines)
        if journalOn && !trimmed.isEmpty {
            context.insert(JournalEntry(body: trimmed))
            journalMsg = sleepMsg.isEmpty ? "Journal entry saved." : " Journal entry saved."
        }
        try? context.save()
        let dialog = (sleepMsg + journalMsg).isEmpty ? "Nothing to log." : sleepMsg + journalMsg
        return .result(dialog: dialog)
    }
}

// MARK: - Block from share (ports Android share-to-task quick-add path)

struct AddTaskFromTextIntent: AppIntent {
    static let title: LocalizedStringResource = "Add Task from Text"
    static let description = IntentDescription("Parse text into a new ChronosFlow task with smart-fill (date, priority, recurrence).")

    @Parameter(title: "Text") var text: String

    static var parameterSummary: some ParameterSummary {
        Summary("Add task from \(\.$text)")
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let context = chronosContext()
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return .result(dialog: "No text to add.") }
        // Use ChronosCore parseSmartFill to parse the text (mirrors Android taskTitleSmartFill).
        let parsed = parseSmartFill(trimmed, now: .now)
        let finalTitle = parsed.cleanedTitle.isEmpty ? trimmed : parsed.cleanedTitle
        // Map TaskPriorityHint (.high=3, .medium=2, .low=1) to the app's integer priority scale.
        let priorityInt: Int
        switch parsed.priority {
        case .high: priorityInt = 3
        case .medium: priorityInt = 2
        case .low: priorityInt = 1
        case nil: priorityInt = 0
        }
        let task = TaskItem(
            title: finalTitle,
            priority: priorityInt,
            dueDate: parsed.dueDate)
        context.insert(task)
        try? context.save()
        return .result(dialog: "Added task: \(task.title).")
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
        AppShortcut(intent: SkipHabitIntent(),
                    phrases: ["Skip a habit in \(.applicationName)", "\(.applicationName) skip habit"],
                    shortTitle: "Skip Habit", systemImageName: "arrow.uturn.forward")
        AppShortcut(intent: SkipMedicationDoseIntent(),
                    phrases: ["Skip a dose in \(.applicationName)", "\(.applicationName) skip medication"],
                    shortTitle: "Skip Dose", systemImageName: "pills")
        AppShortcut(intent: ListMedicationsIntent(),
                    phrases: ["List my medications in \(.applicationName)", "\(.applicationName) my medications"],
                    shortTitle: "List Medications", systemImageName: "list.bullet.clipboard")
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
        AppShortcut(intent: LogSleepAndJournalIntent(),
                    phrases: ["Log sleep and journal in \(.applicationName)", "\(.applicationName) evening log"],
                    shortTitle: "Evening Log", systemImageName: "moon.zzz.fill")
        AppShortcut(intent: AddTaskFromTextIntent(),
                    phrases: ["Add a task from this text in \(.applicationName)"],
                    shortTitle: "Task from Text", systemImageName: "text.badge.plus")
    }
}
