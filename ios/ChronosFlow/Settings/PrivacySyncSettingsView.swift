import SwiftUI
import ChronosCore
import WatchConnectivity

// MARK: - PrivacySyncSettingsView
//
// The iOS analogue of Android's PRIVACY_SYNC sidebar page (SidebarPageContent.kt lines 974–1129).
// App lock is always visible (Android's App-Lock card has no collapse); the remaining areas are
// collapsible `DisclosureGroup`s whose expand state persists to the `privacy*Expanded` ChronosSettings
// flags (default expanded), matching Android's `privacy.<section>.expanded` DataStore keys:
//   • App permissions   (medication lock + HealthKit sleep import)   — Android: "App permissions"
//   • Sensitive content (title redaction)                            — Android: "Sensitive content"
//   • Cloud sync        (iCloud mirroring)                           — Android: "Cloud sync"
//   • Apple Watch       (Wear OS link status)                        — Android: "Watch"
// Connected apps (interop) and Calendar management are pushed as their own screens via NavigationLink,
// matching Android's dedicated Connected-apps card and CALENDARS sidebar page.

struct PrivacySyncSettingsView: View {
    @State private var settings = ChronosSettings.shared

    var body: some View {
        Form {
            appPermissionsSection
            aiPrivacySection
            sensitiveContentSection
            cloudSyncSection
            wearLinkSection
            connectionsSection
        }
        .navigationTitle("Privacy & Sync")
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: App lock + App permissions

    // App lock — always visible (Android: the "App lock" card has no collapse), plus a collapsible
    // "App permissions" group wrapping the medication-lock + HealthKit sleep toggles. Android parity:
    // PrivacyPermissionsSection inside the "App permissions" DisclosureGroup (expand state persisted).
    private var appPermissionsSection: some View {
        Section {
            Toggle("Lock Medications with Face ID", isOn: $settings.medicationLockEnabled)

            DisclosureGroup(isExpanded: $settings.privacyAppPermissionsExpanded) {
                Toggle("Import sleep from Apple Health", isOn: $settings.healthKitSleepEnabled)
                Text(settings.healthKitSleepEnabled
                     ? "Sleep nights are imported read-only from Apple Health."
                     : "Sleep import is off. Turn on to read your nightly sleep from Apple Health (read-only).")
                    .font(.chronosCaption)
                    .foregroundStyle(.secondary)
            } label: {
                Label("App permissions", systemImage: "lock.shield")
            }
        } header: {
            Text("App lock")
        } footer: {
            Text("When on, the Medications tab requires Face ID, Touch ID, or your passcode — the iOS equivalent of the Android app-lock. Sleep import is read-only.")
        }
    }

    // AI privacy mode — single picker, kept always-visible. iOS ships on-device-only.
    private var aiPrivacySection: some View {
        Section {
            Picker("AI data", selection: $settings.privacyMode) {
                ForEach(PrivacyMode.allCases, id: \.self) { Text($0.label).tag($0) }
            }
        } footer: {
            Text(settings.privacyMode.allowsOnDeviceGeneration
                 ? "AI runs entirely on this device via Apple Intelligence."
                 : "On-device AI generation is off — assist surfaces fall back to built-in heuristics.")
        }
    }

    // Sensitive content — redaction of titles in widgets / notifications / watch. Default OFF.
    private var sensitiveContentSection: some View {
        Section {
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
    }

    // Cloud sync — iCloud/CloudKit mirroring toggle.
    private var cloudSyncSection: some View {
        Section {
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
    }

    // MARK: Apple Watch link
    //
    // Builds a ChronosCore `WearLinkStatus` from the live `WCSession.default` (paired / reachable /
    // app-installed) plus the last-published timestamp the watch sync records in App-Group defaults,
    // then renders it with the shared `wearLinkConnectionSummary` + `formatLastSyncedLabel` helpers —
    // the same labels Android's WearLinkStatusCard shows.

    private var wearLinkSection: some View {
        Section {
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
    }

    // MARK: Connected apps + Calendar (own screens)

    private var connectionsSection: some View {
        Section {
            NavigationLink {
                ConnectedAppsView()
            } label: {
                Label("Connected apps", systemImage: "app.connected.to.app.below.fill")
            }
            NavigationLink {
                CalendarManagementView()
            } label: {
                Label("Calendars", systemImage: "calendar")
            }
        } footer: {
            Text("Connect a companion app (Meridian / DevTime) to share tasks and events, or manage your device calendar import and sync.")
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
}

#Preview {
    NavigationStack { PrivacySyncSettingsView() }
}
