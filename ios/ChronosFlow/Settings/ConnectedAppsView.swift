import SwiftUI
import ChronosCore

// MARK: - ConnectedAppsView
//
// The iOS analogue of Android's "Connected apps" card (CompanionAppStatusCard in
// PrivacySyncSections.kt, surfaced in the PRIVACY_SYNC sidebar page lines 1068–1077). Surfaces the
// DevTime/Meridian companion interop:
//   • installed / not-installed status for the peer (detected via the shared App-Group marker —
//     iOS has no PackageManager query, so the App-Group entitlement is the trust boundary),
//   • the last-sync timestamp + result message recorded by `InteropSync` in App-Group defaults,
//   • a manual "Sync now" action that runs the same `InteropSync.runSync()` pipeline the BGTask uses,
//   • the consent toggle (`ChronosSettings.interopConsentGranted`) that gates `runSync()`, and a
//     gated-on-consent medication-name sharing toggle (Android: "Share medication names…").
// Import-only MVP, exactly like Android's InteropSyncManager.

struct ConnectedAppsView: View {
    @State private var settings = ChronosSettings.shared
    /// Local mirror of the last-run labels so the view refreshes after a manual "Sync now".
    @State private var lastSyncDate: Date? = InteropSync.lastSyncDate
    @State private var lastResult: String? = InteropSync.lastResult
    @State private var isSyncing = false

    /// Drives success/failure haptics after a manual "Sync now" completes.
    private enum SyncFeedback: Equatable { case success, failure }
    @State private var syncFeedback: SyncFeedback?

    var body: some View {
        Form {
            statusSection
            consentSection
        }
        .navigationTitle("Connected apps")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear(perform: refreshStatus)
        .sensoryFeedback(.success, trigger: syncFeedback) { _, new in new == .success }
        .sensoryFeedback(.error, trigger: syncFeedback) { _, new in new == .failure }
    }

    // MARK: Status

    private var statusSection: some View {
        Section {
            LabeledContent("Meridian") {
                HStack(spacing: 4) {
                    Circle()
                        .fill(isCompanionAppInstalled ? ChronosColors.success : Color.secondary)
                        .frame(width: 8, height: 8)
                    Text(isCompanionAppInstalled ? "Installed" : "Not installed")
                        .foregroundStyle(.secondary)
                }
            }

            // Last-sync label, mirroring Android's relative "Synced X ago" copy.
            Text(formatLastSyncedLabel(lastSyncAt: lastSyncDate, now: .now))
                .font(.chronosCaption)
                .foregroundStyle(.secondary)

            if let lastResult {
                Text(lastResult)
                    .font(.chronosCaption)
                    .foregroundStyle(.secondary)
            }

            Button {
                syncNow()
            } label: {
                if isSyncing {
                    HStack(spacing: ChronosSpacing.small) {
                        ProgressView()
                        Text("Syncing…")
                    }
                } else {
                    Label("Sync now", systemImage: "arrow.triangle.2.circlepath")
                }
            }
            .disabled(isSyncing || !settings.interopConsentGranted)
        } header: {
            Text("Companion app")
        } footer: {
            Text(isCompanionAppInstalled
                 ? "Meridian (DevTime) is installed. Tasks and events are shared securely between the two apps."
                 : "Meridian (DevTime) isn’t installed. ChronosFlow runs on its own — install Meridian to share tasks and events between the apps.")
        }
    }

    // MARK: Consent

    private var consentSection: some View {
        Section {
            Toggle("Share tasks & events with Meridian", isOn: $settings.interopConsentGranted)
            Toggle("Share medication names", isOn: $settings.interopMedicationSharingEnabled)
                .disabled(!settings.interopConsentGranted)
        } footer: {
            Text(settings.interopConsentGranted
                 ? "ChronosFlow imports shared tasks and events from Meridian. \(settings.interopMedicationSharingEnabled ? "Medication names are visible to Meridian." : "Medication data is not shared (default).")"
                 : "Sharing is off. Turn it on to let ChronosFlow import tasks and events Meridian shares with it. You grant access in each app separately.")
        }
    }

    // MARK: Actions

    /// Run the same deterministic import pipeline the BGTask uses, then refresh the surfaced labels.
    /// Consent-gated inside `runSync()`, so a manual trigger with consent off is a silent no-op.
    private func syncNow() {
        isSyncing = true
        syncFeedback = nil
        Task { @MainActor in
            let result = await InteropSync.runSync()
            syncFeedback = result.isSuccess ? .success : .failure
            refreshStatus()
            isSyncing = false
        }
    }

    private func refreshStatus() {
        lastSyncDate = InteropSync.lastSyncDate
        lastResult = InteropSync.lastResult
    }

    /// Whether the Meridian companion app is present, detected via a shared App-Group marker it writes
    /// (iOS has no PackageManager query; the App-Group entitlement is the trust boundary).
    private var isCompanionAppInstalled: Bool {
        UserDefaults(suiteName: ChronosStore.appGroup)?
            .bool(forKey: "interop.\(InteropContract.peer).installed") ?? false
    }
}

#Preview {
    NavigationStack { ConnectedAppsView() }
}
