import SwiftUI
import SwiftData
import ChronosCore

/// The shell's create-anything surface — the iOS analogue of Android's Quick-Add FAB
/// (`quickAddActionsFor` + `ChronosQuickAddMenu` in `ChronosNavigationShell.kt`).
///
/// Android floats a "+" FAB above the bottom bar that expands **in place** into a menu of create
/// actions (New block / task / med / habit / goal / Journal entry / Log sleep) plus a
/// natural-language "type a task, med, habit, or goal" capture with a live preview. This file owns
/// the reusable model (`QuickAddEditor`, `QuickAddAction`, the smart-fill preview) and the editor
/// presenter; the expanding menu UI lives in `ShellBottomBar`, which renders over the floating bar
/// exactly like Android rather than as a separate modal sheet.

// MARK: - Editors

enum QuickAddEditor: Identifiable, Hashable {
    case block
    case task
    case medication
    case habit
    case goal
    case sleep
    /// "Journal entry" opens the composer directly for today (Android `JournalComposerSheet`),
    /// not the Journal page.
    case journalEntry
    /// Natural-language capture → classified by `CaptureIntentClassifier` and routed to the
    /// matching editor pre-filled (task is the fallback entity, as on Android).
    case typed(String)

    var id: String {
        switch self {
        case .block: return "block"
        case .task: return "task"
        case .medication: return "medication"
        case .habit: return "habit"
        case .goal: return "goal"
        case .sleep: return "sleep"
        case .journalEntry: return "journalEntry"
        case .typed: return "typed"
        }
    }
}

/// Presents the editor sheet for a chosen `QuickAddEditor`. Used by the shell so the create flow is
/// identical whether it was launched from the Quick-Add menu or the command palette.
@ViewBuilder
func quickAddEditorView(for which: QuickAddEditor) -> some View {
    switch which {
    case .block:           TimeBlockEditorSheet(block: nil)
    case .task:            TaskEditorSheet(task: nil)
    case .medication:      MedicationEditorSheet(plan: nil)
    case .habit:           HabitEditorSheet()
    case .goal:            GoalEditorSheet(goal: nil)
    case .sleep:           SleepLogSheet()
    case .journalEntry:    QuickAddJournalComposer()
    case .typed(let text): quickCaptureEditorView(text)
    }
}

/// Routes a typed capture to the editor its classification names (Android `CaptureIntentClassifier`
/// → editor routing): medication and habit editors open pre-filled with the smart-fill cleaned
/// title; everything else (task, and focus, which has no create editor) stays on the task editor,
/// which runs its own smart-fill over the raw text.
@ViewBuilder
private func quickCaptureEditorView(_ text: String) -> some View {
    let cleaned = quickCaptureSmartFill(text)?.cleanedTitle ?? ""
    let prefill = cleaned.isEmpty ? text : cleaned
    switch CaptureIntentClassifier.classify(text).first?.type ?? .task {
    case .medication:  MedicationEditorSheet(prefillName: prefill)
    case .habit:       HabitEditorSheet(initialTitle: prefill)
    case .task, .focus: TaskEditorSheet(task: nil, initialTitle: text)
    }
}

/// Presents the journal composer directly (Android quick-add opens `JournalComposerSheet`), with
/// the streak computed the same way `JournalView` does so the editor's streak chip matches.
private struct QuickAddJournalComposer: View {
    @Query private var entries: [JournalEntry]

    /// Consecutive written days ending today (or yesterday when today is unwritten) — mirrors
    /// `JournalView.currentStreak`.
    private var currentStreak: Int {
        let cal = Calendar.current
        let today = cal.startOfDay(for: .now)
        let entryDates = Set(entries.map { cal.startOfDay(for: $0.entryDate) })
        var streak = entryDates.contains(today) ? 1 : 0
        var cursor = cal.date(byAdding: .day, value: -1, to: today) ?? today
        while entryDates.contains(cursor) {
            streak += 1
            cursor = cal.date(byAdding: .day, value: -1, to: cursor) ?? cursor
        }
        return streak
    }

    var body: some View {
        JournalEditorSheet(initialDate: .now, streak: currentStreak)
    }
}

// MARK: - Quick-Add actions (menu rows)

/// A single Quick-Add menu row. Mirrors `ChronosQuickAddAction`: every row opens a create editor
/// ("Journal entry" opens the composer directly, matching Android's `JournalComposerSheet`).
struct QuickAddAction: Identifiable {
    enum Target {
        case editor(QuickAddEditor)
        case route(ShellRoute)
    }

    let id: String
    let label: String
    let icon: String
    let target: Target
}

/// The feature-flag-gated action list, in Android's order
/// (block · task · med · habit · goal · journal · sleep). Mirrors `quickAddActionsFor`.
func quickAddActions(_ settings: ChronosSettings) -> [QuickAddAction] {
    var actions: [QuickAddAction] = [
        QuickAddAction(id: "block", label: "New block", icon: "calendar.badge.plus", target: .editor(.block)),
        QuickAddAction(id: "task", label: "New task", icon: "checklist", target: .editor(.task)),
    ]
    if settings.medicationEnabled {
        actions.append(QuickAddAction(id: "med", label: "New medication", icon: "pills.fill", target: .editor(.medication)))
    }
    if settings.habitsEnabled {
        actions.append(QuickAddAction(id: "habit", label: "New habit", icon: "heart.fill", target: .editor(.habit)))
    }
    if settings.goalsEnabled {
        actions.append(QuickAddAction(id: "goal", label: "New goal", icon: "flag.fill", target: .editor(.goal)))
    }
    if settings.journalEnabled {
        actions.append(QuickAddAction(id: "journal", label: "Journal entry", icon: "book.closed.fill", target: .editor(.journalEntry)))
    }
    if settings.sleepEnabled {
        actions.append(QuickAddAction(id: "sleep", label: "Log sleep", icon: "moon.zzz.fill", target: .editor(.sleep)))
    }
    return actions
}

// MARK: - Natural-language quick-capture preview

/// Parses the typed quick-capture into a `SmartFillResult` (≥3 chars), mirroring Android's
/// `previewQuickCaptureCommand`.
func quickCaptureSmartFill(_ text: String) -> SmartFillResult? {
    let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
    guard trimmed.count >= 3 else { return nil }
    return parseSmartFill(trimmed, now: .now)
}

/// Question-shaped captures ("what should I do next?") route to the assistant instead of an
/// editor, mirroring the Android palette's ask-assistant row.
func quickCaptureIsQuestion(_ text: String) -> Bool {
    text.trimmingCharacters(in: .whitespacesAndNewlines).hasSuffix("?")
}

/// The entity word for the capture preview, from the classifier's top suggestion. Focus captures
/// read (and route) as tasks — iOS quick-add has no focus create editor.
private func quickCaptureEntityWord(_ text: String) -> String {
    switch CaptureIntentClassifier.classify(text).first?.type ?? .task {
    case .medication: return "medication"
    case .habit:      return "habit"
    case .task, .focus: return "task"
    }
}

/// The live-preview label shown under the capture field
/// (e.g. `Add task "Call mom" · tomorrow · 9am`, `Add medication "vitamin d"`).
func quickCapturePreviewLabel(_ text: String) -> String? {
    guard let result = quickCaptureSmartFill(text) else { return nil }
    let title = result.cleanedTitle.isEmpty ? text.trimmingCharacters(in: .whitespacesAndNewlines) : result.cleanedTitle
    if quickCaptureIsQuestion(text) { return "Ask the assistant" }
    let entity = quickCaptureEntityWord(text)
    if result.detections.isEmpty { return "Add \(entity) “\(title)”" }
    return "Add \(entity) “\(title)” · " + result.detections.joined(separator: " · ")
}
