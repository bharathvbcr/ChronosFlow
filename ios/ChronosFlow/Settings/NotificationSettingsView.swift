import SwiftUI
import UIKit
import UserNotifications

// MARK: - NotificationSettingsView
//
// The iOS analogue of Android's NOTIFICATIONS sidebar page (SidebarPageContent.kt lines 1131–1241):
// block/break reminders, medication/habit/task reminders, the Focus Live Activity, quiet hours
// (the iOS equivalent of Android's sleep-window), and the evening sleep-&-journal log reminder.
// All toggles are always visible (Android shows them un-collapsed too).

struct NotificationSettingsView: View {
    @State private var settings = ChronosSettings.shared
    /// OS-level notification authorization. Without this, denying the system prompt leaves every
    /// toggle below a silent no-op — reminders are scheduled but iOS drops them.
    @State private var authStatus: UNAuthorizationStatus = .authorized
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL

    var body: some View {
        Form {
            if authStatus == .denied {
                Section {
                    Label {
                        Text("Notifications are turned off for ChronosFlow — the reminders below are saved but iOS won't deliver them until you turn notifications on.")
                            .font(.chronosCaption)
                    } icon: {
                        Image(systemName: "bell.slash.fill").foregroundStyle(ChronosColors.warning)
                    }
                    Button("Open Settings") {
                        if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                    }
                }
            }
            Section {
                Toggle("Reminders", isOn: $settings.remindersEnabled)
                if settings.remindersEnabled {
                    Toggle("Medication reminders", isOn: $settings.medicationRemindersEnabled)
                    Toggle("Habit reminders", isOn: $settings.habitRemindersEnabled)
                    Toggle("Task reminders", isOn: $settings.taskRemindersEnabled)
                    Toggle("Current block Live Activity", isOn: $settings.currentBlockLiveActivityEnabled)
                    if settings.currentBlockLiveActivityEnabled {
                        Toggle("Fold reminders into Live Activity", isOn: $settings.foldRemindersIntoLiveActivity)
                    }
                    Toggle("Focus Live Activity", isOn: $settings.focusLiveActivityEnabled)
                }
            } header: {
                Text("Reminders")
            } footer: {
                Text("Block-start, break, and missed-block nudges — the iOS analogue of Android's notification toggles. When Current block Live Activity is on, the passive block-start alert is replaced by a single live surface on the Lock Screen.\n\nFold reminders shows the most-urgent due task, habit, and dose as an action chip inside that live surface and quiets their separate banners — one place to act instead of a stack of alerts.")
            }

            if settings.remindersEnabled {
                Section {
                    DatePicker("Quiet hours start",
                               selection: minuteBinding(\.quietHoursStartMinute),
                               displayedComponents: .hourAndMinute)
                    DatePicker("Quiet hours end",
                               selection: minuteBinding(\.quietHoursEndMinute),
                               displayedComponents: .hourAndMinute)
                } header: {
                    Text("Quiet hours")
                } footer: {
                    Text(quietHoursFooter)
                }
                .onChange(of: settings.quietHoursStartMinute) { _, _ in
                    refreshQuietHoursReminders()
                }
                .onChange(of: settings.quietHoursEndMinute) { _, _ in
                    refreshQuietHoursReminders()
                }
            }

            Section {
                // Android parity: the "Log sleep & journal" notification toggle (logReminderEnabled).
                Toggle("Log sleep & journal reminder", isOn: $settings.logReminderEnabled)
            } footer: {
                Text("An evening (20:00) nudge to log tonight's sleep and write in your journal.")
            }
        }
        .navigationTitle("Notifications")
        .navigationBarTitleDisplayMode(.inline)
        .task { await refreshAuthStatus() }
        // Re-check when returning from iOS Settings so the banner clears once the user grants access.
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { Task { await refreshAuthStatus() } }
        }
        .onChange(of: settings.currentBlockLiveActivityEnabled) { _, enabled in
            if enabled {
                BlockLiveActivityCoordinator.refreshToday()
                ChronosBackgroundSync.scheduleAll()
            } else {
                BlockLiveActivityCoordinator.refreshToday()
                ChronosBackgroundSync.cancelBlockLive()
            }
            Task {
                await ChronosNotifications.shared.refreshFoldableReminders(settings: settings)
            }
        }
        .onChange(of: settings.foldRemindersIntoLiveActivity) { _, _ in
            BlockLiveActivityCoordinator.refreshToday()
            Task {
                await ChronosNotifications.shared.refreshFoldableReminders(settings: settings)
            }
        }
        .onChange(of: settings.remindersEnabled) { _, _ in
            BlockLiveActivityCoordinator.refreshToday()
            Task {
                await ChronosNotifications.shared.refreshFoldableReminders(settings: settings)
            }
        }
        .onChange(of: settings.medicationRemindersEnabled) { _, _ in
            refreshFoldableRemindersAndLiveActivity()
        }
        .onChange(of: settings.taskRemindersEnabled) { _, _ in
            refreshFoldableRemindersAndLiveActivity()
        }
        .onChange(of: settings.habitRemindersEnabled) { _, _ in
            refreshFoldableRemindersAndLiveActivity()
        }
        .onChange(of: settings.focusLiveActivityEnabled) { _, enabled in
            FocusTimerModel.shared.applyFocusLiveActivitySetting(enabled: enabled)
        }
        .onChange(of: settings.logReminderEnabled) { _, enabled in
            Task {
                if enabled {
                    await ChronosNotifications.shared.scheduleLogReminder(settings: settings)
                } else {
                    ChronosNotifications.shared.cancel(idPrefix: "log-reminder")
                }
            }
        }
    }

    private func refreshFoldableRemindersAndLiveActivity() {
        BlockLiveActivityCoordinator.refreshToday()
        Task {
            await ChronosNotifications.shared.refreshFoldableReminders(settings: settings)
        }
    }

    private func refreshQuietHoursReminders() {
        Task {
            await ChronosNotifications.shared.refreshFoldableReminders(settings: settings)
        }
    }

    private func refreshAuthStatus() async {
        authStatus = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
    }

    /// Live read-out of the configured quiet-hours window (or a note when start == end disables it),
    /// mirroring Android's computed sleep-window status line.
    private var quietHoursFooter: String {
        let base = "Quiet hours suppress non-critical reminders overnight — the iOS equivalent of the Android sleep-window setting."
        let start = settings.quietHoursStartMinute, end = settings.quietHoursEndMinute
        if start == end {
            return base + "\n\nStart and end are the same time, so quiet hours are off."
        }
        let minutes = (end - start + 1440) % 1440
        let h = minutes / 60, m = minutes % 60
        let dur = m == 0 ? "\(h)h" : "\(h)h \(m)m"
        return base + "\n\nQuiet from \(start.clockTime) to \(end.clockTime) — \(dur)."
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
    NavigationStack { NotificationSettingsView() }
}
