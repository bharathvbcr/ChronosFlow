import Foundation
import SwiftData
import UserNotifications
import ChronosCore

/// Local-notification scheduling — the iOS-native equivalent of the Android notification stack
/// (core/notifications): medication reminders with Take/Snooze/Skip dose actions, task & habit
/// reminders with Complete/Snooze, refill-soon warnings, quiet-hours suppression, threaded
/// grouping, and a best-effort current/next-block status notification.
///
/// The companion `ChronosNotificationDelegate` (below) handles the action responses, mutating
/// the App-Group `ChronosStore.shared` context the same way the App Intents do.
@MainActor
final class ChronosNotifications {
    static let shared = ChronosNotifications()
    private let center = UNUserNotificationCenter.current()

    // MARK: Category + action identifiers (parallel to the Android notification actions).

    static let medicationCategory = "MEDICATION_REMINDER"
    static let taskCategory = "TASK_REMINDER"
    static let habitCategory = "HABIT_REMINDER"
    static let currentBlockCategory = "CURRENT_BLOCK"

    // Medication actions.
    static let takeAction = "MED_TAKE"
    static let snoozeAction = "MED_SNOOZE"
    static let skipAction = "MED_SKIP"
    // Task actions.
    static let completeTaskAction = "TASK_COMPLETE"
    static let snoozeTaskAction = "TASK_SNOOZE"
    // Habit actions.
    static let markHabitAction = "HABIT_DONE"
    static let snoozeHabitAction = "HABIT_SNOOZE"

    // Thread identifiers so the system groups reminders by type (Android: channel groups).
    static let medicationThread = "thread.medication"
    static let taskThread = "thread.tasks"
    static let habitThread = "thread.habits"
    static let blockThread = "thread.blocks"
    static let logReminderThread = "thread.log"

    // Log-reminder category (passive, no actions — Android: AlarmRequestType.LOG_REMINDER).
    static let logReminderCategory = "LOG_REMINDER"

    /// How many days of remaining supply still triggers a "refill soon" line (Android refillThreshold).
    static let refillWarningThresholdDays = 3
    /// Snooze interval for every reminder type (matches the Android 15-minute snooze).
    static let snoozeMinutes = 15

    // MARK: Authorization + category registration

    func requestAuthorization() async {
        registerCategories()
        _ = try? await center.requestAuthorization(options: [.alert, .sound, .badge])
    }

    private func registerCategories() {
        // Medication: Taken / Snooze 15m / Skip.
        let take = UNNotificationAction(identifier: Self.takeAction, title: "Taken",
                                        options: [.authenticationRequired])
        let medSnooze = UNNotificationAction(identifier: Self.snoozeAction,
                                             title: "Snooze \(Self.snoozeMinutes)m")
        let skip = UNNotificationAction(identifier: Self.skipAction, title: "Skip",
                                        options: [.destructive])
        let medication = UNNotificationCategory(
            identifier: Self.medicationCategory, actions: [take, medSnooze, skip],
            intentIdentifiers: [], options: [])

        // Task: Complete / Snooze.
        let completeTask = UNNotificationAction(identifier: Self.completeTaskAction, title: "Complete")
        let taskSnooze = UNNotificationAction(identifier: Self.snoozeTaskAction,
                                              title: "Snooze \(Self.snoozeMinutes)m")
        let task = UNNotificationCategory(
            identifier: Self.taskCategory, actions: [completeTask, taskSnooze],
            intentIdentifiers: [], options: [])

        // Habit: Mark done / Snooze.
        let markHabit = UNNotificationAction(identifier: Self.markHabitAction, title: "Mark done")
        let habitSnooze = UNNotificationAction(identifier: Self.snoozeHabitAction,
                                               title: "Snooze \(Self.snoozeMinutes)m")
        let habit = UNNotificationCategory(
            identifier: Self.habitCategory, actions: [markHabit, habitSnooze],
            intentIdentifiers: [], options: [])

        // Current/next block: passive, no actions (tapping opens the app).
        let currentBlock = UNNotificationCategory(
            identifier: Self.currentBlockCategory, actions: [],
            intentIdentifiers: [], options: [])

        // Log reminder: passive 20:00 nudge for sleep & journal (Android: LOG_REMINDER channel).
        let logReminder = UNNotificationCategory(
            identifier: Self.logReminderCategory, actions: [],
            intentIdentifiers: [], options: [])

        center.setNotificationCategories([medication, task, habit, currentBlock, logReminder])
    }

    // MARK: Medication

    /// Schedule daily reminders for each of a medication plan's reminder minutes.
    /// Gated on `medicationRemindersEnabled` (and the master `remindersEnabled`); fire times that
    /// land inside quiet hours are shifted to the window end. A low remaining supply adds a
    /// "refill soon" line to the body.
    func scheduleMedication(_ plan: MedicationPlan, settings: ChronosSettings = .shared) async {
        await requestAuthorization()
        cancel(idPrefix: "med-\(plan.id)-")
        guard settings.remindersEnabled, settings.medicationRemindersEnabled else { return }

        let refillLine = refillSoonLine(for: plan)
        for minute in plan.reminderMinutes {
            let fireMinute = QuietHours.nextAllowedMinute(
                minute: minute,
                startMinute: settings.quietHoursStartMinute,
                endMinute: settings.quietHoursEndMinute)

            let content = UNMutableNotificationContent()
            content.title = "Time for \(plan.name)"
            content.subtitle = "\(plan.dosage) \(plan.unit)".trimmingCharacters(in: .whitespaces)
            var bodyLines: [String] = []
            if plan.takeWithFood { bodyLines.append("Take with food.") }
            if let notes = plan.notes, !notes.isEmpty { bodyLines.append(notes) }
            if let refillLine { bodyLines.append(refillLine) }
            content.body = bodyLines.isEmpty ? "Mark it taken once you've had your dose." : bodyLines.joined(separator: "\n")
            content.sound = .default
            content.categoryIdentifier = Self.medicationCategory
            content.threadIdentifier = Self.medicationThread
            content.interruptionLevel = .timeSensitive
            content.userInfo = ["planID": plan.id, "section": "medication"]

            let trigger = UNCalendarNotificationTrigger(
                dateMatching: clockComponents(fireMinute), repeats: true)
            let request = UNNotificationRequest(
                identifier: "med-\(plan.id)-\(minute)", content: content, trigger: trigger)
            try? await center.add(request)
        }
    }

    /// The "refill soon" line, or nil when supply is healthy / untracked.
    private func refillSoonLine(for plan: MedicationPlan) -> String? {
        guard let remaining = plan.remainingDoses else { return nil }
        let perDay = max(plan.reminderMinutes.count, 1)
        guard let days = daysUntilRefill(remainingDoses: remaining, dosesPerDay: perDay) else { return nil }
        guard days <= Self.refillWarningThresholdDays else { return nil }
        if days <= 0 { return "Refill needed — you're out of \(plan.name)." }
        if days == 1 { return "Refill soon — about 1 day of \(plan.name) left." }
        return "Refill soon — about \(days) days of \(plan.name) left."
    }

    // MARK: Task

    /// Schedule a one-shot reminder for a task with a due date/time. Gated on `taskRemindersEnabled`.
    /// The fire time is the task's due date, shifted out of quiet hours when necessary. Completed
    /// or past-due tasks are skipped.
    func scheduleTask(_ task: TaskItem, settings: ChronosSettings = .shared) async {
        await requestAuthorization()
        cancel(idPrefix: "task-\(task.id)")
        guard settings.remindersEnabled, settings.taskRemindersEnabled else { return }
        guard !task.isCompleted, let due = task.dueDate else { return }

        let fireDate = shiftedOutOfQuietHours(due, settings: settings)
        guard fireDate > Date() else { return }  // don't schedule for the past

        let content = UNMutableNotificationContent()
        content.title = task.title
        content.subtitle = task.priority > 0 ? "\(task.priorityLabel) priority" : ""
        var bodyLines: [String] = []
        if let detail = task.detail, !detail.isEmpty { bodyLines.append(detail) }
        if !task.checklist.isEmpty {
            let done = task.checklist.filter(\.isDone).count
            bodyLines.append("Checklist: \(done)/\(task.checklist.count) done.")
        }
        content.body = bodyLines.isEmpty ? "This task is due now." : bodyLines.joined(separator: "\n")
        content.sound = .default
        content.categoryIdentifier = Self.taskCategory
        content.threadIdentifier = Self.taskThread
        content.interruptionLevel = task.priority >= 3 ? .timeSensitive : .active
        content.userInfo = ["taskID": task.id, "section": "tasks"]

        let trigger = UNCalendarNotificationTrigger(
            dateMatching: Calendar.current.dateComponents(
                [.year, .month, .day, .hour, .minute], from: fireDate),
            repeats: false)
        try? await center.add(UNNotificationRequest(
            identifier: "task-\(task.id)", content: content, trigger: trigger))
    }

    // MARK: Habit

    /// Schedule a daily habit-window reminder at the window start. Gated on `habitRemindersEnabled`.
    func scheduleHabit(_ habit: Habit, settings: ChronosSettings = .shared) async {
        await requestAuthorization()
        cancel(idPrefix: "habit-\(habit.id)")
        guard settings.remindersEnabled, settings.habitRemindersEnabled else { return }

        let fireMinute = QuietHours.nextAllowedMinute(
            minute: habit.windowStartMinute,
            startMinute: settings.quietHoursStartMinute,
            endMinute: settings.quietHoursEndMinute)

        let content = UNMutableNotificationContent()
        content.title = habit.title
        content.subtitle = habit.streakCount > 0 ? "\(habit.streakCount)-day streak" : ""
        content.body = habit.streakCount > 0
            ? "Your habit window is open — keep your \(habit.streakCount)-day streak going."
            : "Your habit window is open — a great time to start a streak."
        content.sound = .default
        content.categoryIdentifier = Self.habitCategory
        content.threadIdentifier = Self.habitThread
        content.interruptionLevel = .active
        content.userInfo = ["habitID": habit.id, "section": "habits"]

        let trigger = UNCalendarNotificationTrigger(
            dateMatching: clockComponents(fireMinute), repeats: true)
        try? await center.add(UNNotificationRequest(
            identifier: "habit-\(habit.id)", content: content, trigger: trigger))
    }

    // MARK: Current / next block (best-effort status notification)

    /// Best-effort "current/next block" notification: schedule a passive notification at the
    /// next block's start so the user gets a timely heads-up. iOS has no always-on ongoing
    /// notification, so this is the closest native analogue to the Android current-block pill.
    /// Pass today's blocks (sorted or not); the soonest upcoming block is used.
    func scheduleNextBlockNotification(blocks: [TimeBlock], settings: ChronosSettings = .shared) async {
        await requestAuthorization()
        cancel(idPrefix: "block-next")
        guard settings.remindersEnabled else { return }

        let now = Date()
        let cal = Calendar.current
        let nowMinute = cal.component(.hour, from: now) * 60 + cal.component(.minute, from: now)

        let today = blocks
            .filter { cal.isDateInToday($0.date) }
            .sorted { $0.startMinuteOfDay < $1.startMinuteOfDay }
        guard let next = today.first(where: { $0.startMinuteOfDay > nowMinute }) else { return }

        // Concrete fire date at the next block's start time today.
        var comps = cal.dateComponents([.year, .month, .day], from: now)
        comps.hour = next.startMinuteOfDay / 60
        comps.minute = next.startMinuteOfDay % 60
        guard let fireDate = cal.date(from: comps), fireDate > now else { return }

        let content = UNMutableNotificationContent()
        content.title = "Up next: \(next.title)"
        let endMinute = next.plannedEndMinuteOfDay
        content.body = "Starts at \(next.startMinuteOfDay.clockTime), until \(endMinute.clockTime)."
        content.sound = nil  // passive status nudge
        content.categoryIdentifier = Self.currentBlockCategory
        content.threadIdentifier = Self.blockThread
        content.interruptionLevel = .passive
        content.userInfo = ["blockID": next.id, "section": "today"]

        let trigger = UNCalendarNotificationTrigger(
            dateMatching: cal.dateComponents([.year, .month, .day, .hour, .minute], from: fireDate),
            repeats: false)
        try? await center.add(UNNotificationRequest(
            identifier: "block-next", content: content, trigger: trigger))
    }

    // MARK: Log reminder (sleep & journal)

    /// Schedule the 20:00 daily "log sleep & journal" reminder. Fires at 20:00, shifted out of
    /// quiet hours when necessary. Repeats daily. The Android analogue is
    /// `AlarmRequestType.LOG_REMINDER` → deep-link `:logsleep → DAY_TARGET_SLEEP`.
    func scheduleLogReminder(settings: ChronosSettings = .shared) async {
        await requestAuthorization()
        cancel(idPrefix: "log-reminder")
        guard settings.remindersEnabled, settings.logReminderEnabled else { return }

        let fireMinute = QuietHours.nextAllowedMinute(
            minute: 20 * 60,  // 20:00
            startMinute: settings.quietHoursStartMinute,
            endMinute: settings.quietHoursEndMinute)

        let content = UNMutableNotificationContent()
        content.title = "Log your day"
        content.body = "Take a moment to log tonight's sleep and write in your journal."
        content.sound = .default
        content.categoryIdentifier = Self.logReminderCategory
        content.threadIdentifier = Self.logReminderThread
        content.interruptionLevel = .active
        content.userInfo = ["section": "sleep"]

        let trigger = UNCalendarNotificationTrigger(
            dateMatching: clockComponents(fireMinute), repeats: true)
        try? await center.add(UNNotificationRequest(
            identifier: "log-reminder", content: content, trigger: trigger))
    }

    // MARK: Cancellation

    func cancel(idPrefix: String) {
        center.getPendingNotificationRequests { requests in
            let ids = requests.map(\.identifier).filter { $0.hasPrefix(idPrefix) }
            self.center.removePendingNotificationRequests(withIdentifiers: ids)
        }
    }

    // MARK: Helpers

    /// DateComponents for a daily repeating trigger at a minute-of-day.
    private func clockComponents(_ minute: Int) -> DateComponents {
        let m = QuietHours.normalizeMinute(minute)
        var date = DateComponents()
        date.hour = m / 60
        date.minute = m % 60
        return date
    }

    /// Shift a concrete fire `date` out of the quiet-hours window. When the minute-of-day lands
    /// inside quiet hours it is moved to the window end (possibly rolling to the next day for the
    /// pre-midnight portion of a wrapping window).
    private func shiftedOutOfQuietHours(_ date: Date, settings: ChronosSettings) -> Date {
        let cal = Calendar.current
        let minute = cal.component(.hour, from: date) * 60 + cal.component(.minute, from: date)
        let start = settings.quietHoursStartMinute
        let end = settings.quietHoursEndMinute
        guard QuietHours.isQuiet(minute: minute, startMinute: start, endMinute: end) else { return date }

        let allowed = QuietHours.nextAllowedMinute(minute: minute, startMinute: start, endMinute: end)
        let rollsToNextDay = QuietHours.nextAllowedRollsToNextDay(
            minute: minute, startMinute: start, endMinute: end)
        let base = rollsToNextDay ? cal.date(byAdding: .day, value: 1, to: date) ?? date : date
        var comps = cal.dateComponents([.year, .month, .day], from: base)
        comps.hour = allowed / 60
        comps.minute = allowed % 60
        return cal.date(from: comps) ?? date
    }
}

// MARK: - Delegate (action handling)

/// Handles notification action responses — Taken/Skip/Snooze for medication, Complete/Snooze for
/// tasks and habits — mutating the App-Group `ChronosStore.shared` context. Mirrors the Android
/// `MedicationActionReceiver` / `TaskActionReceiver` / `HabitActionReceiver`.
///
/// NOTE for ChronosFlowApp wiring (cannot be done from this file per the constraints): set this as
/// the notification-center delegate at app launch, e.g. in the `ChronosFlowApp.init`:
///
///     UNUserNotificationCenter.current().delegate = ChronosNotificationDelegate.shared
///
/// Keep a strong reference (the shared singleton below does this). Without this line the action
/// buttons fire but the dose/task/habit mutations never run.
@MainActor
final class ChronosNotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    static let shared = ChronosNotificationDelegate()

    /// Show medication/task/habit alerts even while the app is foregrounded (Android parity:
    /// these are time-sensitive reminders, not silent).
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .sound, .list]
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        let info = response.notification.request.content.userInfo
        let context = ChronosStore.shared.mainContext

        switch response.actionIdentifier {
        case ChronosNotifications.takeAction:
            handleMedication(info, context: context) { $0.acknowledgeDose() }
        case ChronosNotifications.skipAction:
            handleMedication(info, context: context) { $0.missedCount += 1 }
        case ChronosNotifications.snoozeAction:
            if let plan = medication(from: info, context: context) {
                await ChronosNotifications.shared.rescheduleSnoozed(
                    identifier: "med-snooze-\(plan.id)",
                    title: "Time for \(plan.name)",
                    body: "Snoozed dose · \(plan.dosage) \(plan.unit)",
                    category: ChronosNotifications.medicationCategory,
                    thread: ChronosNotifications.medicationThread,
                    userInfo: ["planID": plan.id, "section": "medication"])
            }

        case ChronosNotifications.completeTaskAction:
            handleTask(info, context: context) { $0.isCompleted = true; $0.updatedAt = .now }
        case ChronosNotifications.snoozeTaskAction:
            if let task = task(from: info, context: context) {
                await ChronosNotifications.shared.rescheduleSnoozed(
                    identifier: "task-snooze-\(task.id)",
                    title: task.title,
                    body: "Snoozed task reminder.",
                    category: ChronosNotifications.taskCategory,
                    thread: ChronosNotifications.taskThread,
                    userInfo: ["taskID": task.id, "section": "tasks"])
            }

        case ChronosNotifications.markHabitAction:
            handleHabit(info, context: context) { $0.toggleCompletion(on: .now) }
        case ChronosNotifications.snoozeHabitAction:
            if let habit = habit(from: info, context: context) {
                await ChronosNotifications.shared.rescheduleSnoozed(
                    identifier: "habit-snooze-\(habit.id)",
                    title: habit.title,
                    body: "Snoozed habit reminder.",
                    category: ChronosNotifications.habitCategory,
                    thread: ChronosNotifications.habitThread,
                    userInfo: ["habitID": habit.id, "section": "habits"])
            }

        default:
            break  // default tap (open app) — handled by the app's deep-link layer.
        }
    }

    // MARK: Fetch + mutate helpers

    private func medication(from info: [AnyHashable: Any], context: ModelContext) -> MedicationPlan? {
        guard let id = info["planID"] as? String else { return nil }
        return (try? context.fetch(FetchDescriptor<MedicationPlan>(
            predicate: #Predicate { $0.id == id })))?.first
    }

    private func task(from info: [AnyHashable: Any], context: ModelContext) -> TaskItem? {
        guard let id = info["taskID"] as? String else { return nil }
        return (try? context.fetch(FetchDescriptor<TaskItem>(
            predicate: #Predicate { $0.id == id })))?.first
    }

    private func habit(from info: [AnyHashable: Any], context: ModelContext) -> Habit? {
        guard let id = info["habitID"] as? String else { return nil }
        return (try? context.fetch(FetchDescriptor<Habit>(
            predicate: #Predicate { $0.id == id })))?.first
    }

    private func handleMedication(
        _ info: [AnyHashable: Any], context: ModelContext, _ mutate: (MedicationPlan) -> Void
    ) {
        guard let plan = medication(from: info, context: context) else { return }
        mutate(plan)
        try? context.save()
    }

    private func handleTask(
        _ info: [AnyHashable: Any], context: ModelContext, _ mutate: (TaskItem) -> Void
    ) {
        guard let item = task(from: info, context: context) else { return }
        mutate(item)
        try? context.save()
    }

    private func handleHabit(
        _ info: [AnyHashable: Any], context: ModelContext, _ mutate: (Habit) -> Void
    ) {
        guard let h = habit(from: info, context: context) else { return }
        mutate(h)
        try? context.save()
    }
}

extension ChronosNotifications {
    /// Re-schedule a snoozed reminder `snoozeMinutes` from now (one-shot).
    func rescheduleSnoozed(
        identifier: String, title: String, body: String,
        category: String, thread: String, userInfo: [String: String]
    ) async {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        content.categoryIdentifier = category
        content.threadIdentifier = thread
        content.interruptionLevel = .timeSensitive
        content.userInfo = userInfo

        let trigger = UNTimeIntervalNotificationTrigger(
            timeInterval: TimeInterval(Self.snoozeMinutes * 60), repeats: false)
        try? await center.add(UNNotificationRequest(
            identifier: identifier, content: content, trigger: trigger))
    }
}
