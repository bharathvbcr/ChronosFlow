# ChronosFlow — Privacy Policy

_Last updated: 16 June 2026_

ChronosFlow ("the app", "we") is a personal day-planning app for Android and Wear OS.
This policy explains what data the app accesses, why, where it is stored, and the
choices you have. It applies to the ChronosFlow app published under the package
`com.chronosflow` and its companion Wear OS app.

**Plain-language summary:** ChronosFlow is built to run on your device. Your plans,
tasks, habits, medication logs, mood check-ins, sleep data, and screen-time data are
stored **locally on your device**. We do not operate accounts, we do not have servers
that collect your personal planning data, and we do not sell or share your data with
third parties or advertisers. The only time information leaves your device is when
**you** explicitly trigger it (optional cloud AI suggestions, exporting a backup, or
syncing to your own paired watch).

---

## 1. Who is responsible for your data

ChronosFlow is developed and published by the ChronosFlow developer
(contact: **bharath.vbcr@gmail.com**). For privacy questions or data requests, email
that address.

## 2. What data the app handles, and why

All of the following is stored **on your device** unless a section says otherwise.

| Data | Why it is used | Leaves your device? |
|------|----------------|---------------------|
| Time blocks, tasks, habits, day templates, focus sessions | Core planning features | No |
| Medication plans, schedules, and dose logs | Reminders and adherence tracking | No |
| Mood / energy / stress check-ins | Energy-aware planning and insights | No |
| Sleep sessions (via Health Connect) | Sleep-aware next-day planning and trends | No (read-only import) |
| Per-app screen-time (via Usage Access) | "Focused vs. distracted" insights | No |
| Calendar events | Showing and syncing fixed commitments on the dial | Stays within your device's calendar |
| Contacts | Optionally linking a contact to a task | No |
| Journal entries, goals, routines | Companion tracking features | No |

ChronosFlow has **no user accounts, no login, and no analytics or advertising SDKs.**
We do not collect device identifiers, location, usage telemetry, or crash analytics
tied to you.

## 3. Sensitive permissions explained

ChronosFlow requests the following sensitive permissions. Each is **optional**, granted
by you through the system, and used only for the stated purpose:

- **Health Connect — Sleep (`health.READ_SLEEP`, `health.READ_HEALTH_DATA_IN_BACKGROUND`):**
  Read-only access to sleep sessions you have stored in Health Connect, used to adjust
  next-day planning (e.g. deferring demanding work after a poor night) and to show sleep
  trends. ChronosFlow **never writes** health data and **never shares** it. Background
  access is used solely to keep sleep trends current via an on-device sync job. You can
  revoke this at any time in Health Connect or Android settings.

- **Usage Access (`PACKAGE_USAGE_STATS`):** Reads on-device app usage statistics (the
  same data that powers Android Digital Wellbeing) to calculate focused-vs-distracted
  screen time in Insights. This is **off by default**, opt-in, and you grant it in
  system settings. The data is processed on-device and never transmitted.

- **Calendar (`READ_CALENDAR`, `WRITE_CALENDAR`):** Reads your calendar to display fixed
  commitments on the dial and, when you choose, writes planned blocks back to your
  calendar. Calendar data stays within your device's calendar provider.

- **Contacts (`READ_CONTACTS`):** Used only when you link a contact to a task. Contact
  data is not uploaded.

- **Notifications (`POST_NOTIFICATIONS`, `POST_PROMOTED_NOTIFICATIONS`):** Used for
  reminders, focus-session progress, and live "now" updates.

- **Exact alarms (`SCHEDULE_EXACT_ALARM`):** Used so medication and focus reminders fire
  at the precise time you set.

- **Biometric (`USE_BIOMETRIC`):** Used to lock sensitive areas (medication, data export)
  behind your device authentication. ChronosFlow does not see or store your biometric data.

- **Foreground service (`FOREGROUND_SERVICE_SPECIAL_USE`):** Keeps an active focus-session
  timer running while the app is in the background. It runs only during a session you start.

## 4. On-device AI

ChronosFlow generates planning suggestions using **on-device AI (Gemini Nano via Android
ML Kit GenAI / AICore)**. This processing happens **entirely on your device** — your plan
data is not sent anywhere for on-device suggestions.

**Optional cloud AI:** If — and only if — a cloud AI provider is configured for your build
(Firebase AI Logic / Gemini), and you choose to use cloud-assisted suggestions, the
relevant prompt text is sent to Google's Gemini API to generate a response. This is
**dormant and disabled by default** in standard builds. When enabled and used, that
request is governed by
[Google's Privacy Policy](https://policies.google.com/privacy) and the
[Firebase AI / Gemini API terms](https://firebase.google.com/terms/data-processing-terms).
Every AI suggestion — on-device or cloud — is staged for your explicit review before it
changes your plan.

## 5. Backups, export, and device transfer

- **Your own backups:** You can export your data to a file you control and restore it
  later. These exports stay where you put them.
- **Android system backup:** The app supports Android's backup/restore and device-transfer
  mechanisms (`allowBackup`). Database encryption keys are **excluded** from backup so that
  encrypted data cannot be restored without your device. System backups are handled by
  Android/Google under your device's backup settings, not by us.
- **Encryption at rest:** Sensitive on-device data can be stored in an encrypted database
  (SQLCipher), and sensitive screens can be locked behind biometric/device authentication.

## 6. Companion Wear OS app

The Wear OS app mirrors your day summary, focus controls, and reminders to your paired
watch over the Android **Wearable Data Layer**. This is a direct device-to-device channel
between your phone and your own watch; the data is not sent to us or to third parties. An
option to hide sensitive titles on the watch and in notifications is available.

## 7. Data sharing and selling

We **do not** sell your personal data, and we **do not** share it with advertisers, data
brokers, or analytics providers. The only outbound flows are the optional, user-initiated
ones described above (cloud AI, your own exports, your own paired watch).

## 8. Data retention and deletion

Your data lives on your device for as long as you keep the app installed. To delete it:

- Delete individual items in the app, or
- Uninstall the app (this removes the on-device database), and
- Revoke any granted permissions in Android settings / Health Connect.

Because we do not store your personal data on any server, there is no server-side copy for
us to delete.

## 9. Children

ChronosFlow is not directed at children and does not knowingly collect data from children.

## 10. Changes to this policy

If this policy changes, the "Last updated" date above will change and the revised policy
will be published at the same location.

## 11. Contact

Questions or requests: **bharath.vbcr@gmail.com**
