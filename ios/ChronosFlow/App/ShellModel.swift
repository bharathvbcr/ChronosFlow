import SwiftUI

/// The iOS shell's information architecture, rebuilt to mirror Android's dial-centric shell
/// (`ChronosRoute` / `ChronosNavigationShell.kt`) instead of a flat 12-tab `TabView`.
///
/// Android shows only **three** primary destinations in its compact bottom bar — Plan · Today ·
/// Focus (`compactShellDestinations`, `showInCompact = true`) — and reaches everything else
/// (Tasks/Habits/Goals/Meds/Routines/Sleep/Journal/Review/Settings) through the Quick-Add FAB
/// menu and the command palette. That dial-first model is the single biggest reason the apps
/// "felt different"; this type models the same split so `RootView` can render it natively.

// MARK: - Primary tabs (the floating bottom bar)

/// The three primary destinations in the floating bottom bar. Mirrors Android's
/// `compactShellDestinations` (Plan/Today/Focus only).
enum PrimaryTab: String, CaseIterable, Identifiable {
    case plan, today, focus

    var id: String { rawValue }

    var label: String {
        switch self {
        case .plan:  return "Plan"
        case .today: return "Today"
        case .focus: return "Focus"
        }
    }

    /// SF Symbols chosen to read like the Android shell icons (EventNote / Today / Timer).
    var icon: String {
        switch self {
        case .plan:  return "calendar.day.timeline.left"
        case .today: return "sun.max"
        case .focus: return "timer"
        }
    }
}

// MARK: - Secondary destinations (Quick-Add + command palette only)

/// Destinations that are NOT in the bottom bar. On Android these are `showInCompact = false`
/// shell destinations reached via the Quick-Add menu / command palette (plus the Settings &
/// Data pages). On iOS they present as dismissible pages over the active primary tab.
enum ShellRoute: String, Identifiable, Hashable, CaseIterable {
    case tasks, habits, goals, medication, routines, sleep, journal, review, settings, data

    var id: String { rawValue }

    var label: String {
        switch self {
        case .tasks:      return "Tasks"
        case .habits:     return "Habits"
        case .goals:      return "Goals"
        case .medication: return "Medication"
        case .routines:   return "Routines"
        case .sleep:      return "Sleep"
        case .journal:    return "Journal"
        case .review:     return "Review"
        case .settings:   return "Settings"
        case .data:       return "Backup & Data"
        }
    }

    var icon: String {
        switch self {
        case .tasks:      return "checklist"
        case .habits:     return "heart.fill"
        case .goals:      return "flag.fill"
        case .medication: return "pills.fill"
        case .routines:   return "repeat"
        case .sleep:      return "moon.zzz.fill"
        case .journal:    return "book.closed.fill"
        case .review:     return "chart.bar.xaxis"
        case .settings:   return "gearshape"
        case .data:       return "externaldrive"
        }
    }

    /// Feature-flag gating — mirrors `ChronosRoute.isAvailable(featureFlags)`. Settings, Data and
    /// Tasks are always available; the rest follow their module toggle.
    func isEnabled(_ settings: ChronosSettings) -> Bool {
        switch self {
        case .tasks, .settings, .data: return true
        case .habits:     return settings.habitsEnabled
        case .goals:      return settings.goalsEnabled
        case .medication: return settings.medicationEnabled
        case .routines:   return settings.routinesEnabled
        case .sleep:      return settings.sleepEnabled
        case .journal:    return settings.journalEnabled
        case .review:     return settings.insightsEnabled
        }
    }

    /// Maps a `chronosflow://<host>` deep-link / widget section to a secondary route, if any.
    static func fromDeepLinkHost(_ host: String?) -> ShellRoute? {
        switch host {
        case "tasks":      return .tasks
        case "habits":     return .habits
        case "goals":      return .goals
        case "medication": return .medication
        case "routines":   return .routines
        case "sleep":      return .sleep
        case "journal":    return .journal
        case "review":     return .review
        case "settings":   return .settings
        default:           return nil
        }
    }
}

// MARK: - Shell state

/// Observable owner of the shell's navigation selection. Injected into the environment so any
/// screen (e.g. a toolbar command-palette button) can drive navigation, mirroring how Android's
/// `ChronosNavigationState` is a single shared source of truth.
@Observable
@MainActor
final class ShellState {
    /// The selected primary tab (Plan/Today/Focus). Defaults to Today like the Android shell.
    var selectedTab: PrimaryTab = .today

    /// A secondary destination presented over the current tab (nil = none). Set from the
    /// Quick-Add menu, the command palette, or a deep link.
    var presentedRoute: ShellRoute?

    /// Whether the Quick-Add menu is expanded above the bar (Android `quickAddExpanded`).
    var quickAddExpanded = false

    /// Whether the global command palette sheet is showing (Android Ctrl+K / top-bar action).
    var commandPaletteShown = false

    /// Open a secondary destination, closing any transient shell chrome first (matches Android's
    /// `closeQuickAdd()` on navigate).
    func open(_ route: ShellRoute) {
        quickAddExpanded = false
        commandPaletteShown = false
        presentedRoute = route
    }

    /// Select a primary tab, closing transient chrome (Android `onShellDestinationSelected`).
    func select(_ tab: PrimaryTab) {
        quickAddExpanded = false
        commandPaletteShown = false
        selectedTab = tab
    }
}

// MARK: - Command-palette toolbar entry

extension View {
    /// Adds the command-palette button to a primary screen's toolbar — the iOS placement of
    /// Android's top-bar `ChronosCommandPaletteAction`. Reads the shell from the environment, so it
    /// is a no-op in previews where no `ShellState` is injected.
    func chronosCommandPaletteToolbar() -> some View {
        modifier(CommandPaletteToolbarModifier())
    }
}

private struct CommandPaletteToolbarModifier: ViewModifier {
    @Environment(ShellState.self) private var shell: ShellState?

    func body(content: Content) -> some View {
        content.toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button { shell?.commandPaletteShown = true } label: {
                    Image(systemName: "magnifyingglass")
                }
                .accessibilityLabel("Open command palette")
            }
        }
    }
}
