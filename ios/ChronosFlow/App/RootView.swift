import SwiftUI
import SwiftData

/// The navigation shell. Mirrors `ChronosNavigationShell` / `ChronosRoute.shellDestinations`:
/// Plan · Today · Focus · Tasks · Habits · Goals · Meds · Review(Insights).
///
/// Uses the iOS 18+ `Tab` API with `.sidebarAdaptable` so it becomes a tab bar on iPhone
/// (with automatic overflow) and a sidebar on iPad — the native equivalent of the Android
/// compact-vs-expanded shell (`compactShellDestinations` / `expandedShellDestinations`).
struct RootView: View {
    @State private var selection: ShellTab = .today
    @AppStorage("hasOnboarded") private var hasOnboarded = false
    /// `@Observable`, so reading flags/theme here makes the shell react to Settings changes —
    /// disabled modules drop out of the tab bar, mirroring the Android onboarding feature selector.
    @State private var settings = ChronosSettings.shared
    @Environment(\.scenePhase) private var scenePhase
    /// Share Extension handoff: pending text written by ChronosShareExtension via App Group.
    @State private var pendingShareText: String?
    @State private var showingShareTaskEditor = false

    var body: some View {
        TabView(selection: $selection) {
            Tab("Today", systemImage: "sun.max", value: ShellTab.today) {
                TodayView()
            }
            Tab("Plan", systemImage: "calendar.day.timeline.left", value: ShellTab.plan) {
                DayDialScreen()
            }
            Tab("Focus", systemImage: "timer", value: ShellTab.focus) {
                FocusView()
            }
            Tab("Tasks", systemImage: "checklist", value: ShellTab.tasks) {
                TasksView()
            }

            TabSection("Track") {
                if settings.habitsEnabled {
                    Tab("Habits", systemImage: "heart.fill", value: ShellTab.habits) {
                        HabitsView()
                    }
                }
                if settings.goalsEnabled {
                    Tab("Goals", systemImage: "flag.fill", value: ShellTab.goals) {
                        GoalsView()
                    }
                }
                if settings.medicationEnabled {
                    Tab("Meds", systemImage: "pills.fill", value: ShellTab.medication) {
                        MedicationView()
                    }
                }
                if settings.routinesEnabled {
                    Tab("Routines", systemImage: "repeat", value: ShellTab.routines) {
                        RoutinesView()
                    }
                }
                if settings.sleepEnabled {
                    Tab("Sleep", systemImage: "moon.zzz.fill", value: ShellTab.sleep) {
                        SleepView()
                    }
                }
                if settings.journalEnabled {
                    Tab("Journal", systemImage: "book.closed.fill", value: ShellTab.journal) {
                        JournalView()
                    }
                }
            }

            if settings.insightsEnabled {
                Tab("Review", systemImage: "chart.bar.xaxis", value: ShellTab.review) {
                    InsightsView()
                }
            }

            Tab("Settings", systemImage: "gearshape", value: ShellTab.settings) {
                SettingsView()
            }
        }
        .tabViewStyle(.sidebarAdaptable)
        .preferredColorScheme(settings.themeMode.colorScheme)
        .fullScreenCover(isPresented: Binding(get: { !hasOnboarded }, set: { hasOnboarded = !$0 })) {
            OnboardingView { hasOnboarded = true }
        }
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
        // Handle deep links — including widget taps (chronosflow://section) and the
        // chronosflow://add-task URL posted by ChronosShareExtension.
        // Mirrors the Android notification-launch path (routeForNotificationLaunch / INITIAL_SECTION).
        .onOpenURL { url in handleDeepLink(url) }
        // Share Extension task editor — pre-fills the title with the text shared from another app.
        .sheet(isPresented: $showingShareTaskEditor) {
            if let text = pendingShareText {
                TaskEditorSheet(task: nil, initialTitle: text)
            }
        }
    }

    /// Maps a `chronosflow://<section>` URL to a tab selection (widgets, shortcuts, Siri).
    /// Also handles `chronosflow://add-task` posted by ChronosShareExtension.
    private func handleDeepLink(_ url: URL) {
        guard url.scheme == "chronosflow" else { return }
        switch url.host() {
        case "today":      selection = .today
        case "plan":       selection = .plan
        case "focus":      selection = .focus
        case "tasks":      selection = .tasks
        case "habits":     selection = .habits
        case "goals":      selection = .goals
        case "medication": selection = .medication
        case "sleep":      selection = .sleep
        case "journal":    selection = .journal
        case "review":     selection = .review
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
            break
        }
    }
}

enum ShellTab: Hashable {
    case today, plan, focus, tasks, habits, goals, medication, routines, sleep, journal, review, settings
}

#Preview {
    RootView()
        .modelContainer(ChronosStore.previewContainer())
}
