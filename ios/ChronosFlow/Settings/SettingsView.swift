import SwiftUI
import ChronosCore
import WatchConnectivity

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
                privacySyncSection
                connectedAppsSection
                screenTimeSection
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
            Toggle("Evening log reminder (20:00)", isOn: $settings.logReminderEnabled)
        } footer: {
            Text("Quiet hours suppress non-critical reminders overnight — the iOS equivalent of the Android sleep-window setting. The evening reminder nudges you to log tonight's sleep and write in your journal.")
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

    // MARK: Privacy & Sync
    //
    // The iOS analogue of Android's Privacy & Sync sidebar page (SidebarPageContent.kt). App lock is
    // always visible; sensitive-content, cloud-sync, AI-privacy and the watch link are collapsible
    // `DisclosureGroup`s whose expand state persists to the `privacy*Expanded` ChronosSettings flags
    // (default expanded), matching Android's `privacy.<section>.expanded` DataStore keys.

    private var privacySyncSection: some View {
        Section("Privacy & Sync") {
            // App lock — always visible (Android: the "App lock" section has no collapse).
            Toggle("Lock Medications with Face ID", isOn: $settings.medicationLockEnabled)
            Toggle("Import sleep from Apple Health", isOn: $settings.healthKitSleepEnabled)

            // AI privacy mode — single picker, kept always-visible. iOS ships on-device-only.
            Picker("AI data", selection: $settings.privacyMode) {
                ForEach(PrivacyMode.allCases, id: \.self) { Text($0.label).tag($0) }
            }

            sensitiveContentGroup
            cloudSyncGroup
            wearLinkGroup
        } footer: {
            Text("When on, the Medications tab requires Face ID, Touch ID, or your passcode — the iOS equivalent of the Android app-lock. Sleep import is read-only. \(settings.privacyMode.allowsOnDeviceGeneration ? "AI runs entirely on this device via Apple Intelligence." : "On-device AI generation is off — assist surfaces fall back to built-in heuristics.")")
        }
    }

    // Sensitive content — redaction of titles in widgets / notifications / watch. Default OFF.
    private var sensitiveContentGroup: some View {
        DisclosureGroup(isExpanded: $settings.privacySensitiveContentExpanded) {
            Toggle("Hide sensitive titles", isOn: $settings.sensitiveTitlesRedacted)
            Text(settings.sensitiveTitlesRedacted
                 ? "Titles are hidden on your watch and in notifications — times and counts still show."
                 : "Titles are visible. Off by default — toggle on to hide block names in widgets, notifications, and on your watch.")
                .font(.chronosCaption)
                .foregroundStyle(.secondary)
        } label: {
            Label("Sensitive content", systemImage: "eye.slash")
        }
    }

    // Cloud sync — iCloud/CloudKit mirroring toggle + last-checkpoint label.
    private var cloudSyncGroup: some View {
        DisclosureGroup(isExpanded: $settings.privacyCloudSyncExpanded) {
            Toggle("Sync with iCloud", isOn: $settings.cloudSyncEnabled)
            Text(settings.cloudSyncEnabled
                 ? "Your data mirrors to your private iCloud account and syncs across your devices. Applies on next launch."
                 : "iCloud sync is off — your data stays on this device only.")
                .font(.chronosCaption)
                .foregroundStyle(.secondary)
        } label: {
            Label("Cloud sync", systemImage: "icloud")
        }
    }

    // MARK: Apple Watch link
    //
    // Builds a ChronosCore `WearLinkStatus` from the live `WCSession.default` (paired / reachable /
    // app-installed) plus the last-published timestamp the watch sync records in App-Group defaults,
    // then renders it with the shared `wearLinkConnectionSummary` + `formatLastSyncedLabel` helpers —
    // the same labels Android's WearLinkStatusCard shows.

    private var wearLinkGroup: some View {
        DisclosureGroup(isExpanded: $settings.privacyWearLinkExpanded) {
            let status = currentWearLinkStatus
            LabeledContent("Status") {
                HStack(spacing: 4) {
                    Circle()
                        .fill(status.watchAppInstalled ? Color.green
                              : (status.watchConnected || status.watchPaired) ? Color.orange : Color.secondary)
                        .frame(width: 8, height: 8)
                    Text(wearLinkConnectionSummary(status))
                        .foregroundStyle(.secondary)
                }
            }
            Text(formatLastSyncedLabel(
                lastSyncAtMillis: status.lastPublishedAtMillis == 0 ? nil : status.lastPublishedAtMillis,
                nowMillis: Int64(Date.now.timeIntervalSince1970 * 1000)))
                .font(.chronosCaption)
                .foregroundStyle(.secondary)
            Button("Sync now") {
                PhoneWatchSync.shared.pushSnapshot()
            }
            .disabled(!status.watchConnected)
        } label: {
            Label("Apple Watch", systemImage: "applewatch")
        }
    }

    /// Snapshot the phone↔watch link from `WCSession` + the App-Group last-publish timestamp into a
    /// ChronosCore `WearLinkStatus`. Returns `.unknown` when Watch Connectivity isn't supported.
    private var currentWearLinkStatus: WearLinkStatus {
        guard WCSession.isSupported() else { return .unknown }
        let session = WCSession.default
        let lastPublished = UserDefaults(suiteName: ChronosStore.appGroup)?
            .object(forKey: Self.wearLastPublishedKey) as? Double
        return WearLinkStatus(
            watchPaired: session.isPaired,
            watchConnected: session.isReachable,
            watchAppInstalled: session.isWatchAppInstalled,
            connectedNodeName: nil,
            lastPublishedAtMillis: lastPublished.map { Int64($0 * 1000) } ?? 0
        )
    }

    /// App-Group key the watch-sync path records the last successful publish under, read here for the
    /// "Synced X ago" label. (Written by the sync layer; absent until the first push.)
    private static let wearLastPublishedKey = "watch.lastPublishedAt"

    // MARK: Connected apps (Meridian / DevTime companion interop)
    //
    // Mirrors Android's CompanionAppStatusCard (PrivacySyncSections.kt) in the "Connected apps"
    // collapsible: installed/not-installed status for the Meridian peer, a consent toggle, and a
    // medication-name sharing toggle (gated on consent). iOS has no PackageManager equivalent, so the
    // peer is detected via an App-Group shared marker (App-Group entitlement replaces signature trust).

    private var connectedAppsSection: some View {
        Section {
            DisclosureGroup(isExpanded: $settings.privacyCompanionAppExpanded) {
                LabeledContent("Meridian") {
                    HStack(spacing: 4) {
                        Circle()
                            .fill(isCompanionAppInstalled ? Color.green : Color.secondary)
                            .frame(width: 8, height: 8)
                        Text(isCompanionAppInstalled ? "Installed" : "Not installed")
                            .foregroundStyle(.secondary)
                    }
                }
                Toggle("Share tasks & events with Meridian", isOn: $settings.interopConsentGranted)
                Toggle("Share medication names", isOn: $settings.interopMedicationSharingEnabled)
                    .disabled(!settings.interopConsentGranted)
            } label: {
                Label("Connected apps", systemImage: "app.connected.to.app.below.fill")
            }
        } footer: {
            Text("Meridian (DevTime) can share tasks and events with ChronosFlow when you allow it here. Medication names are only shared if you turn that on. You grant access in each app separately.")
        }
    }

    /// Whether the Meridian companion app is present, detected via a shared App-Group marker it writes
    /// (iOS has no PackageManager query; the App-Group entitlement is the trust boundary). Re-read on
    /// every `body` pass so it reflects an install that happened while Settings was open.
    private var isCompanionAppInstalled: Bool {
        UserDefaults(suiteName: ChronosStore.appGroup)?
            .bool(forKey: "interop.\(InteropContract.peer).installed") ?? false
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
