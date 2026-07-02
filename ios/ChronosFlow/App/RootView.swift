import SwiftUI
import SwiftData

/// The navigation shell — rebuilt to mirror Android's dial-centric shell
/// (`ChronosNavigationShell` / `ChronosRoute`) instead of a flat 12-tab `TabView`.
///
/// Android shows only **three** primary destinations — Plan · Today · Focus — in a floating bottom
/// pill, with an integrated Quick-Add FAB, and reaches everything else (Tasks/Habits/Goals/Meds/
/// Routines/Sleep/Journal/Review/Settings) through that FAB's create menu and a command palette.
/// `RootView` now renders exactly that: the selected primary tab fills the screen, `ShellBottomBar`
/// floats over it, secondary destinations present as dismissible pages, and `CommandPalette` is the
/// always-available jump-to-anything surface (the iOS analogue of Ctrl+K / the top-bar command action).
struct RootView: View {
    @State private var shell = ShellState()
    @AppStorage("hasOnboarded") private var hasOnboarded = false
    /// `@Observable`, so reading flags/theme here makes the shell react to Settings changes —
    /// disabled modules drop out of Quick-Add / the palette, mirroring the Android feature selector.
    @State private var settings = ChronosSettings.shared
    @State private var focus = FocusTimerModel.shared
    @Environment(\.scenePhase) private var scenePhase
    /// Share Extension handoff: pending text written by ChronosShareExtension via App Group.
    @State private var pendingShareText: String?
    @State private var showingShareTaskEditor = false
    /// Bottom inset reserved so scrollable content clears the floating shell bar
    /// (no existing ChronosSpacing token composes to this; named here to avoid a magic literal).
    private let floatingBarClearance: CGFloat = 64

    var body: some View {
        @Bindable var shell = shell

        ZStack(alignment: .bottom) {
            // The selected primary tab fills the screen (each screen owns its own NavigationStack).
            primaryContent
                .environment(shell)
                // Reserve room so scrollable content clears the floating bar (Android compact-shell
                // bottom clearance), instead of hiding behind it.
                .safeAreaInset(edge: .bottom) { Color.clear.frame(height: floatingBarClearance) }

            // The floating bar + Quick-Add FAB, over the content (Android compact shell).
            ShellBottomBar(shell: shell, settings: settings, focusActive: focus.phase != .idle)
                .padding(.bottom, ChronosSpacing.small)
        }
        // ⌘K toggles the command palette on hardware keyboards (Android Ctrl+K). Hidden zero-size
        // button — the shortcut is the only way to reach it.
        .background {
            Button("Toggle command palette") { shell.commandPaletteShown.toggle() }
                .keyboardShortcut("k", modifiers: .command)
                .hidden()
        }
        .preferredColorScheme(settings.themeMode.colorScheme)
        .fullScreenCover(isPresented: Binding(get: { !hasOnboarded }, set: { hasOnboarded = !$0 })) {
            OnboardingView { hasOnboarded = true }
        }
        // Secondary destinations present as dismissible pages over the active tab (Android's
        // pages-on-top-of-Day model; swipe-down / Done returns to the day).
        .sheet(item: $shell.presentedRoute) { route in
            routeView(for: route)
        }
        // The global command palette (jump to any destination / create / ask the assistant).
        .sheet(isPresented: $shell.commandPaletteShown) {
            CommandPalette(shell: shell, settings: settings)
        }
        // Create editors / assistant requested from the command palette are presented here at the
        // root so the palette dismisses first instead of stacking sheets.
        .sheet(item: $shell.pendingEditor) { quickAddEditorView(for: $0) }
        .sheet(isPresented: $shell.showAssistant) { AssistantSheet() }
        // Foreground-refresh the proactive digest the widget/notifications read (Nano-style
        // "foreground writes, background reads"), mirroring the Android ProactiveAssistRefresher.
        .task { await ProactiveDigest.refresh() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                Task { await ProactiveDigest.refresh() }
                // Apply any focus controls (Live Activity / focus widget) queued while we were
                // backgrounded — drained app-wide so it works on any tab, not just Focus.
                ChronosFocusCommandRouter.drain()
                // Re-arm the background sync cadence on each foreground (mirrors auto-backup).
                ChronosBackgroundSync.scheduleAll()
                // Mirror the latest day to the paired watch (Android Wear Data Layer parity).
                PhoneWatchSync.shared.pushSnapshot()
            }
        }
        // Handle deep links — widget taps (chronosflow://section) and the chronosflow://add-task URL
        // posted by ChronosShareExtension. Mirrors the Android notification-launch path.
        .onOpenURL { url in handleDeepLink(url) }
        // Share Extension task editor — pre-fills the title with the text shared from another app.
        .sheet(isPresented: $showingShareTaskEditor) {
            if let text = pendingShareText {
                TaskEditorSheet(task: nil, initialTitle: text)
            }
        }
    }

    /// The active primary tab's screen. Plan/Today/Focus are the only top-level destinations.
    @ViewBuilder
    private var primaryContent: some View {
        switch shell.selectedTab {
        case .plan:  DayDialScreen()
        case .today: TodayView()
        case .focus: FocusView()
        }
    }

    /// The view for a secondary destination. Each already wraps itself in a `NavigationStack`, so it
    /// presents cleanly as a sheet with its own title/toolbar and a swipe-down dismiss.
    @ViewBuilder
    private func routeView(for route: ShellRoute) -> some View {
        switch route {
        // The DayDial shortcut dismisses the Tasks page and lands on the Plan tab
        // (Android `onOpenDayDial`); `select` already clears `presentedRoute`.
        case .tasks:      TasksView(onOpenDayDial: { shell.select(.plan) })
        case .habits:     HabitsView()
        case .goals:      GoalsView()
        case .medication: MedicationView()
        case .routines:   RoutinesView()
        case .sleep:      SleepView()
        case .journal:    JournalView()
        case .review:     InsightsView()
        case .settings:   SettingsView()
        case .data:       DataManagementView()
        }
    }

    /// Maps a `chronosflow://<host>` URL to a primary tab or a secondary route (widgets, shortcuts,
    /// Siri), and handles `chronosflow://add-task` posted by ChronosShareExtension. Disabled features
    /// fall back to the Today tab rather than a blank destination (Android feature-flag gating).
    private func handleDeepLink(_ url: URL) {
        guard url.scheme == "chronosflow" else { return }
        switch url.host() {
        case "today": shell.select(.today)
        case "plan":  shell.select(.plan)
        case "focus": shell.select(.focus)
        case "add-task":
            // Check for pending shared text (written by ChronosShareExtension) and open task editor.
            let defaults = UserDefaults(suiteName: "group.com.chronosflow.shared")
            if let text = defaults?.string(forKey: "share.pendingText"),
               let ts = defaults?.double(forKey: "share.pendingTimestamp"),
               Date().timeIntervalSince1970 - ts < 30 {
                defaults?.removeObject(forKey: "share.pendingText")
                pendingShareText = text
                showingShareTaskEditor = true
            }
        default:
            // Any other host maps to a secondary route when its feature is enabled.
            if let route = ShellRoute.fromDeepLinkHost(url.host()), route.isEnabled(settings) {
                shell.open(route)
            } else {
                shell.select(.today)
            }
        }
    }
}

#Preview {
    RootView()
        .modelContainer(ChronosStore.previewContainer())
}
