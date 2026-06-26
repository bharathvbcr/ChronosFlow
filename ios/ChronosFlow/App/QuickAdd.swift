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
    /// Natural-language capture → opens the task editor pre-filled (task is the default entity,
    /// matching the Android quick-capture which routes ambiguous captures to a task).
    case typed(String)

    var id: String {
        switch self {
        case .block: return "block"
        case .task: return "task"
        case .medication: return "medication"
        case .habit: return "habit"
        case .goal: return "goal"
        case .sleep: return "sleep"
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
    case .typed(let text): TaskEditorSheet(task: nil, initialTitle: text)
    }
}

// MARK: - Quick-Add actions (menu rows)

/// A single Quick-Add menu row. Mirrors `ChronosQuickAddAction`: most rows open a create editor,
/// while "Journal entry" routes to the Journal page (Android `TARGET_JOURNAL`, not an editor).
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
        actions.append(QuickAddAction(id: "journal", label: "Journal entry", icon: "book.closed.fill", target: .route(.journal)))
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

/// The live-preview label shown under the capture field (e.g. `Add task "Call mom" · tomorrow · 9am`).
func quickCapturePreviewLabel(_ text: String) -> String? {
    guard let result = quickCaptureSmartFill(text) else { return nil }
    let title = result.cleanedTitle.isEmpty ? text.trimmingCharacters(in: .whitespacesAndNewlines) : result.cleanedTitle
    if result.detections.isEmpty { return "Add task “\(title)”" }
    return "Add task “\(title)” · " + result.detections.joined(separator: " · ")
}
