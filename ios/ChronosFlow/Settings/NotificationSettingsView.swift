import SwiftUI

// MARK: - NotificationSettingsView
//
// The iOS analogue of Android's NOTIFICATIONS sidebar page (SidebarPageContent.kt lines 1131–1241):
// block/break reminders, medication/habit/task reminders, the Focus Live Activity, quiet hours
// (the iOS equivalent of Android's sleep-window), and the evening sleep-&-journal log reminder.
// All toggles are always visible (Android shows them un-collapsed too).

struct NotificationSettingsView: View {
    @State private var settings = ChronosSettings.shared

    var body: some View {
        Form {
            Section {
                Toggle("Reminders", isOn: $settings.remindersEnabled)
                if settings.remindersEnabled {
                    Toggle("Medication reminders", isOn: $settings.medicationRemindersEnabled)
                    Toggle("Habit reminders", isOn: $settings.habitRemindersEnabled)
                    Toggle("Task reminders", isOn: $settings.taskRemindersEnabled)
                    Toggle("Focus Live Activity", isOn: $settings.focusLiveActivityEnabled)
                }
            } header: {
                Text("Reminders")
            } footer: {
                Text("Block-start, break, and missed-block nudges — the iOS analogue of Android's notification toggles.")
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
                    Text("Quiet hours suppress non-critical reminders overnight — the iOS equivalent of the Android sleep-window setting.")
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
