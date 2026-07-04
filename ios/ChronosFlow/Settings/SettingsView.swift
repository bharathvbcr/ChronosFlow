import SwiftUI
import ChronosCore

// MARK: - SettingsView
//
// The iOS-native analogue of Android's settings sidebar pages. Android splits settings into four
// discrete full-screen sidebar pages (Appearance, AI Settings, Notifications, Privacy & Sync), each
// routed to via Day(target=TARGET_APPEARANCE / TARGET_AI_SETTINGS / TARGET_NOTIFICATIONS /
// TARGET_PRIVACY_SYNC). This view is the tiered index that mirrors that model: a top-level list of
// the four settings pages (each a NavigationLink to its own screen), plus the device-local sections
// that aren't part of an Android sidebar page (Screen Time, Features feature-flags, Data, About).
//
// `struct SettingsView: View` keeps its no-arg init — the navigation shell instantiates it as
// `SettingsView()`. All controls bind to the `@Observable` `ChronosSettings.shared`; every toggle
// persists immediately to the App-Group `UserDefaults`, so widgets and App Intents observe the same
// prefs. The per-page Form bodies live in the dedicated *SettingsView files.

struct SettingsView: View {
    /// Read the shared store directly; `@Observable` makes property access in `body` reactive.
    @State private var settings = ChronosSettings.shared

    var body: some View {
        NavigationStack {
            chromedSurface
        }
    }

    private var chromedSurface: some View {
        settingsSurface
            .navigationTitle("Settings")
            .chronosScrollMinimizedBar()
            .chronosCommandPaletteToolbar()
    }

    private var settingsSurface: some View {
        ZStack {
            ChronosBackdrop()
            List {
                settingsPagesSection
                screenTimeSection
                featuresSection
                dataSection
                aboutSection
            }
            .scrollContentBackground(.hidden)
        }
    }

    // MARK: Settings pages (the four Android sidebar pages, each its own screen)

    private var settingsPagesSection: some View {
        Section {
            NavigationLink {
                AppearanceSettingsView()
            } label: {
                pageRow("Appearance", "Theme, backdrop, glass, and motion",
                        systemImage: "paintpalette")
            }
            NavigationLink {
                PlanningSettingsView()
            } label: {
                pageRow("AI & planning", "On-device planning style and behaviour",
                        systemImage: "sparkles")
            }
            NavigationLink {
                NotificationSettingsView()
            } label: {
                pageRow("Notifications", "Reminders, quiet hours, and log nudges",
                        systemImage: "bell.badge")
            }
            NavigationLink {
                PrivacySyncSettingsView()
            } label: {
                pageRow("Privacy & Sync", "App lock, sync, calendars, and connected apps",
                        systemImage: "lock.shield")
            }
        }
    }

    /// A settings-index tile: icon + title + one-line description, matching the Android sidebar tiles.
    private func pageRow(_ title: String, _ subtitle: String, systemImage: String) -> some View {
        Label {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                Text(subtitle)
                    .font(.chronosCaption)
                    .foregroundStyle(.secondary)
            }
        } icon: {
            Image(systemName: systemImage)
        }
    }

    // MARK: Screen Time

    private var screenTimeSection: some View {
        Section("Screen Time") {
            Toggle("Track screen time usage", isOn: $settings.screenTimeEnabled)
        } footer: {
            Text("Reads app usage from Screen Time to surface productive vs. wasted time in Insights. Opt-in and off by default — requires Screen Time access in Settings.")
        }
    }

    // MARK: Features (the graduated feature flags)

    private var featuresSection: some View {
        Section("Features") {
            Toggle("Habits", isOn: $settings.habitsEnabled)
            Toggle("Goals", isOn: $settings.goalsEnabled)
            Toggle("Medications", isOn: $settings.medicationEnabled)
            Toggle("Journal", isOn: $settings.journalEnabled)
            Toggle("Sleep", isOn: $settings.sleepEnabled)
            Toggle("Routines", isOn: $settings.routinesEnabled)
            Toggle("Insights", isOn: $settings.insightsEnabled)
        } footer: {
            Text("Turn modules on or off. Turning a module off hides its tab but keeps all your logged data — turn it back on anytime.")
        }
    }

    // MARK: Data

    private var dataSection: some View {
        Section("Data") {
            NavigationLink {
                DataManagementView()
            } label: {
                Label("Backup & restore", systemImage: "externaldrive")
            }
        }
    }

    private var aboutSection: some View {
        Section {
            LabeledContent("Version", value: appVersion)
            LabeledContent("On-device AI", value: "Apple Intelligence")
        } header: {
            Text("About")
        } footer: {
            Text("ChronosFlow for iOS — a native SwiftUI port. Your data stays on your device unless you turn on iCloud sync.")
        }
    }

    private var appVersion: String {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
        let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
        return "\(v) (\(b))"
    }
}

#Preview {
    SettingsView()
}
