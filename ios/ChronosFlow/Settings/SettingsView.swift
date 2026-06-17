import SwiftUI

// MARK: - SettingsView
//
// The iOS-native analogue of Android's settings sidebar pages (Appearance, AI Settings,
// Notifications, Privacy/Sync) plus the developer feature-flag set. A single grouped `Form`
// bound to the `@Observable` `ChronosSettings.shared` — every toggle persists immediately to
// the App-Group `UserDefaults`, so widgets and App Intents observe the same prefs.

struct SettingsView: View {
    /// Read the shared store directly; `@Observable` makes property access in `body` reactive.
    @State private var settings = ChronosSettings.shared

    var body: some View {
        NavigationStack {
            Form {
                appearanceSection
                aiSection
                notificationsSection
                privacySection
                featuresSection
                dataSection
                aboutSection
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inlineLarge)
        }
    }

    // MARK: Appearance

    private var appearanceSection: some View {
        Section("Appearance") {
            Picker("Theme", selection: $settings.themeMode) {
                ForEach(ThemeMode.allCases) { Text($0.label).tag($0) }
            }
            Picker("Backdrop", selection: $settings.backdrop) {
                ForEach(BackdropStyle.allCases) { Text($0.label).tag($0) }
            }
            Toggle("Dynamic color", isOn: $settings.dynamicColorEnabled)
            Toggle("Liquid Glass surfaces", isOn: $settings.glassEnabled)
            Toggle("Reduce motion", isOn: $settings.reduceMotionPreference)
            Toggle("Increase contrast", isOn: $settings.increaseContrast)
        } footer: {
            Text("Theme, backdrop, and motion match the Android Appearance settings. Reduce motion also follows your system accessibility setting.")
        }
    }

    // MARK: AI & planning

    private var aiSection: some View {
        Section("AI & planning") {
            Toggle("On-device AI planning", isOn: $settings.aiEnabled)
            if settings.aiEnabled {
                Picker("Planning style", selection: $settings.planningStyle) {
                    ForEach(PlanningStyle.allCases) { Text($0.label).tag($0) }
                }
                Text(settings.planningStyle.detail)
                    .font(.chronosCaption)
                    .foregroundStyle(.secondary)
                Toggle("Auto-apply AI day plan", isOn: $settings.autoApplyPlan)
            }
        } footer: {
            Text(settings.autoApplyPlan
                 ? "AI plans are applied automatically. You can still review and undo."
                 : "AI never changes your plan silently — you review every suggestion before it’s applied. Planning runs entirely on device via Apple Intelligence.")
        }
    }

    // MARK: Notifications

    private var notificationsSection: some View {
        Section("Notifications") {
            Toggle("Reminders", isOn: $settings.remindersEnabled)
            if settings.remindersEnabled {
                Toggle("Medication reminders", isOn: $settings.medicationRemindersEnabled)
                Toggle("Habit reminders", isOn: $settings.habitRemindersEnabled)
                Toggle("Task reminders", isOn: $settings.taskRemindersEnabled)
                Toggle("Focus Live Activity", isOn: $settings.focusLiveActivityEnabled)
                quietHoursRow
            }
        } footer: {
            Text("Quiet hours suppress non-critical reminders overnight — the iOS equivalent of the Android sleep-window setting.")
        }
    }

    private var quietHoursRow: some View {
        Group {
            DatePicker("Quiet hours start",
                       selection: minuteBinding(\.quietHoursStartMinute),
                       displayedComponents: .hourAndMinute)
            DatePicker("Quiet hours end",
                       selection: minuteBinding(\.quietHoursEndMinute),
                       displayedComponents: .hourAndMinute)
        }
    }

    // MARK: Privacy & security

    private var privacySection: some View {
        Section("Privacy & security") {
            Toggle("Lock Medications with Face ID", isOn: $settings.medicationLockEnabled)
            Toggle("Import sleep from Apple Health", isOn: $settings.healthKitSleepEnabled)
        } footer: {
            Text("When on, the Medications tab requires Face ID, Touch ID, or your passcode — the iOS equivalent of the Android app-lock. Sleep import is read-only.")
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
            Text("Turn modules on or off. Disabled modules are hidden from the tab bar — mirrors the Android onboarding feature selector.")
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

    /// Bridges a minute-of-day Int setting to a `DatePicker`'s `Date` binding (date part ignored).
    private func minuteBinding(_ keyPath: ReferenceWritableKeyPath<ChronosSettings, Int>) -> Binding<Date> {
        Binding(
            get: {
                let minutes = settings[keyPath: keyPath]
                return Calendar.current.date(
                    bySettingHour: minutes / 60, minute: minutes % 60, second: 0, of: .now) ?? .now
            },
            set: { newDate in
                let comps = Calendar.current.dateComponents([.hour, .minute], from: newDate)
                settings[keyPath: keyPath] = (comps.hour ?? 0) * 60 + (comps.minute ?? 0)
            }
        )
    }
}

#Preview {
    SettingsView()
}
