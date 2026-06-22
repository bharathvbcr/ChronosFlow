import SwiftUI
import UIKit
import UserNotifications

/// First-run onboarding — a paged welcome that introduces the Chronos Dial, lets the user pick which
/// trackers they want, and primes the runtime permissions those choices actually need (notifications
/// and, when Sleep is on, HealthKit sleep import). Mirrors the Android first-run onboarding
/// (`ChronosOnboarding.kt`): intro → feature selector → permission priming, each permission shown
/// with a plain-language reason and a rationale path for a prior denial.
struct OnboardingView: View {
    @Environment(\.dismiss) private var dismiss
    var onFinish: () -> Void
    @State private var page = 0
    /// `@Observable` settings — the selector toggles write straight through to the App-Group store,
    /// mirroring the Android feature-selector page that writes `ChronosFeatureFlags`.
    @State private var settings = ChronosSettings.shared

    private let pages: [Page] = [
        Page(icon: "clock.circle.fill", title: "Your day, as a dial",
             body: "ChronosFlow turns your schedule, tasks, habits, medication, and focus sessions into one calm 24-hour plan."),
        Page(icon: "checklist", title: "Plan and protect your time",
             body: "Drag blocks on the dial, fill free time, and start focus sessions from your plan. AI suggestions are always shown for review first."),
        Page(icon: "chart.bar.xaxis", title: "Learn and improve",
             body: "Track sleep, mood, and habits; review what actually happened; and adapt tomorrow around how you really live."),
        Page(icon: "bell.badge.fill", title: "Stay on track",
             body: "Allow notifications for medication, habit windows, and focus nudges. You can change this anytime in Settings."),
    ]

    /// Total pages = the informational pages, the feature-selector page, and the trailing
    /// permission-priming page (Android parity: intro → choose trackers → permissions).
    private var pageCount: Int { pages.count + 2 }
    private var selectorIndex: Int { pages.count }
    private var permissionsIndex: Int { pages.count + 1 }

    var body: some View {
        ZStack {
            ChronosBackdrop()
            VStack {
                TabView(selection: $page) {
                    ForEach(pages.indices, id: \.self) { i in
                        pageView(pages[i]).tag(i)
                    }
                    featureSelectorPage.tag(selectorIndex)
                    permissionsPage.tag(permissionsIndex)
                }
                .tabViewStyle(.page(indexDisplayMode: .always))

                Button(action: advance) {
                    Text(page == pageCount - 1 ? "Get started" : "Continue")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .padding(.horizontal, ChronosSpacing.large)
                .padding(.bottom, ChronosSpacing.medium)

                if page < pageCount - 1 {
                    Button("Skip") { finish() }
                        .font(.chronosCaption)
                        .padding(.bottom, ChronosSpacing.standard)
                }
            }
        }
    }

    // MARK: - Feature selector

    /// The feature selector. Toggles bind directly to `ChronosSettings.shared`, so flipping one
    /// persists immediately (the store writes through, recording the graduation marker) and the shell
    /// adds/removes that tab. Mirrors Android's seven onboarding trackers — Insights uses the same
    /// "Daily review & insights" wording, and the AI planning assistant row is included.
    private var featureSelectorPage: some View {
        VStack(spacing: ChronosSpacing.medium) {
            VStack(spacing: ChronosSpacing.small) {
                Image(systemName: "square.grid.2x2.fill")
                    .font(.system(size: 56))
                    .foregroundStyle(ChronosColors.brandPrimary)
                    .padding(ChronosSpacing.medium)
                    .background(.ultraThinMaterial, in: Circle())
                Text("Choose your tools").font(.chronosTitleLarge).multilineTextAlignment(.center)
                Text("Turn on what fits your life. You can change any of these later in Settings.")
                    .font(.chronosBody).foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, ChronosSpacing.large)
            }
            .padding(.top, ChronosSpacing.large)

            ScrollView {
                VStack(spacing: ChronosSpacing.small) {
                    featureRow("Habits", "heart.fill", "Build streaks for daily routines like water, reading, or exercise.", isOn: $settings.habitsEnabled)
                    featureRow("Medications", "pills.fill", "Get dose reminders and keep an adherence history.", isOn: $settings.medicationEnabled)
                    featureRow("Goals", "flag.fill", "Break long-term goals into milestones you can track.", isOn: $settings.goalsEnabled)
                    featureRow("Journal", "book.closed.fill", "Capture quick notes and reflect on how your day went.", isOn: $settings.journalEnabled)
                    featureRow("Sleep", "moon.zzz.fill", "Log sleep and see trends — optionally imported from HealthKit.", isOn: $settings.sleepEnabled)
                    featureRow("Routines", "repeat", "Step-by-step morning and evening flows.", isOn: $settings.routinesEnabled)
                    featureRow("Daily review & insights", "chart.bar.xaxis", "End-of-day summaries and weekly trends from your schedule.", isOn: $settings.insightsEnabled)
                    featureRow("AI planning assistant", "sparkles", "On-device suggestions to help plan and adjust your day.", isOn: $settings.aiEnabled)
                }
                .padding(.horizontal, ChronosSpacing.large)
            }
        }
        .padding(.bottom, ChronosSpacing.large)
    }

    private func featureRow(_ title: String, _ icon: String, _ detail: String, isOn: Binding<Bool>) -> some View {
        Toggle(isOn: isOn) {
            HStack(spacing: ChronosSpacing.standard) {
                Image(systemName: icon)
                    .font(.title3)
                    .foregroundStyle(ChronosColors.brandPrimary)
                    .frame(width: 28)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.chronosBody)
                    Text(detail).font(.chronosCaption).foregroundStyle(.secondary)
                }
            }
        }
        .padding(ChronosSpacing.standard)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: ChronosRadius.large, style: .continuous))
    }

    // MARK: - Permission priming

    /// The final page primes only the runtime permissions the user's tracker choices require — exactly
    /// like Android's `OnboardingPermissionsPage`. The notification reason text is built dynamically
    /// from the selected trackers, the HealthKit sleep card is surfaced only when Sleep is on AND the
    /// device has HealthKit, and a prior notification denial routes through a rationale alert before
    /// re-prompting (Android NOTIF-001 parity).
    private var permissionsPage: some View {
        PermissionsPrimingPage(settings: settings)
    }

    private func pageView(_ page: Page) -> some View {
        VStack(spacing: ChronosSpacing.medium) {
            Spacer()
            Image(systemName: page.icon)
                .font(.system(size: 72))
                .foregroundStyle(ChronosColors.brandPrimary)
                .padding(ChronosSpacing.large)
                .background(.ultraThinMaterial, in: Circle())
            Text(page.title).font(.chronosTitleLarge).multilineTextAlignment(.center)
            Text(page.body).font(.chronosBody).foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, ChronosSpacing.large)
            Spacer()
        }
        .padding()
    }

    private func advance() {
        if page < pageCount - 1 {
            withAnimation(ChronosMotion.smooth) { page += 1 }
        } else {
            finish()
        }
    }

    private func finish() {
        onFinish()
        dismiss()
    }

    private struct Page { let icon: String; let title: String; let body: String }
}

// MARK: - PermissionsPrimingPage

/// Permission-priming page: a notifications card (always) plus a HealthKit sleep card (only when Sleep
/// is enabled and HealthKit is present). Each card explains *why* the permission is needed, shows an
/// enable button, and flips to a granted checkmark once authorized. The notifications card consults the
/// current authorization status: a `.denied` state shows a rationale alert (pointing at Settings)
/// before falling through to the system prompt, mirroring Android's `shouldShowRequestPermissionRationale`.
private struct PermissionsPrimingPage: View {
    let settings: ChronosSettings

    @State private var notificationsGranted = false
    @State private var showNotificationRationale = false
    @State private var sleepImporter = HealthKitSleepImporter()

    /// True only when the Sleep tracker is on AND the device actually has HealthKit, so we never show a
    /// dead card on an iPad without Health (Android parity: sleepEnabled && sleepDataSource.isAvailable()).
    private var showSleepCard: Bool {
        settings.sleepEnabled && sleepImporter.availability != .unavailable
    }

    var body: some View {
        VStack(spacing: ChronosSpacing.medium) {
            VStack(spacing: ChronosSpacing.small) {
                Image(systemName: "bell.badge.fill")
                    .font(.system(size: 56))
                    .foregroundStyle(ChronosColors.brandPrimary)
                    .padding(ChronosSpacing.medium)
                    .background(.ultraThinMaterial, in: Circle())
                Text("Permissions you'll need").font(.chronosTitleLarge).multilineTextAlignment(.center)
                Text("We only ask for what your choices require, and you can grant these later in Settings. Nothing here is required to start using the planner.")
                    .font(.chronosBody).foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, ChronosSpacing.large)
            }
            .padding(.top, ChronosSpacing.large)

            ScrollView {
                VStack(spacing: ChronosSpacing.small) {
                    PermissionCard(
                        icon: "bell.badge.fill",
                        title: "Reminders & alerts",
                        why: "ChronosFlow notifies you about \(notificationReasonsText). Without this, those reminders stay silent.",
                        granted: notificationsGranted,
                        grantedLabel: "Reminders enabled",
                        actionLabel: "Enable reminders",
                        onGrant: requestNotifications)

                    if showSleepCard {
                        PermissionCard(
                            icon: "moon.zzz.fill",
                            title: "Import sleep from HealthKit",
                            why: "Sleep nights are imported read-only to help plan your day. You can still log sleep by hand if you skip this.",
                            granted: sleepImporter.availability == .available,
                            grantedLabel: "Sleep import connected",
                            actionLabel: "Connect HealthKit",
                            onGrant: { Task { await sleepImporter.requestAuthorization() } })
                    }
                }
                .padding(.horizontal, ChronosSpacing.large)
            }
        }
        .padding(.bottom, ChronosSpacing.large)
        .task { await refreshNotificationStatus() }
        .alert("Enable reminders?", isPresented: $showNotificationRationale) {
            Button("Open Settings") {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
            Button("Not now", role: .cancel) { }
        } message: {
            Text("ChronosFlow uses notifications for medication, habit windows, and focus nudges. You previously declined, so enable them in Settings → ChronosFlow → Notifications to receive reminders.")
        }
    }

    /// Natural-language reason text limited to the trackers the user kept on. Mirrors Android's
    /// `notificationReasons(...)` + `humanJoin(...)`: block starts/breaks always apply, then the
    /// medication / habit / review reasons are appended for the features still enabled.
    private var notificationReasonsText: String {
        var reasons = ["block start times and breaks"]
        if settings.medicationEnabled { reasons.append("medication doses") }
        if settings.habitsEnabled { reasons.append("habit nudges") }
        if settings.insightsEnabled { reasons.append("your end-of-day review") }
        return humanJoin(reasons)
    }

    /// Joins phrases into natural English: "a", "a and b", or "a, b, and c" (Android `humanJoin` parity).
    private func humanJoin(_ items: [String]) -> String {
        switch items.count {
        case 0: return ""
        case 1: return items[0]
        case 2: return "\(items[0]) and \(items[1])"
        default: return items.dropLast().joined(separator: ", ") + ", and " + (items.last ?? "")
        }
    }

    /// Reflect the live authorization status so the card shows the granted checkmark when the user has
    /// already allowed notifications (e.g. re-entering onboarding after granting in Settings).
    private func refreshNotificationStatus() async {
        let s = await UNUserNotificationCenter.current().notificationSettings()
        notificationsGranted = s.authorizationStatus == .authorized || s.authorizationStatus == .provisional
    }

    /// Request notifications, gating on the current status. `.denied` (a prior decline) cannot be
    /// re-prompted by iOS, so surface a rationale pointing at Settings; otherwise the standard
    /// `requestAuthorization()` shows the system prompt (a no-op when already authorized).
    private func requestNotifications() {
        Task {
            let status = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
            if status == .denied {
                showNotificationRationale = true
                return
            }
            await ChronosNotifications.shared.requestAuthorization()
            await refreshNotificationStatus()
        }
    }
}

// MARK: - PermissionCard

/// A single permission's priming card — icon + title + plain-language reason, then either an enable
/// button or a granted checkmark. Matches the Android `PermissionCard` layout/state pattern.
private struct PermissionCard: View {
    let icon: String
    let title: String
    let why: String
    let granted: Bool
    let grantedLabel: String
    let actionLabel: String
    let onGrant: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: ChronosSpacing.small) {
            HStack(spacing: ChronosSpacing.standard) {
                Image(systemName: icon)
                    .font(.title3)
                    .foregroundStyle(ChronosColors.brandPrimary)
                    .frame(width: 28)
                Text(title).font(.chronosBody.weight(.semibold))
            }
            Text(why).font(.chronosCaption).foregroundStyle(.secondary)
            if granted {
                HStack(spacing: ChronosSpacing.micro) {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(ChronosColors.brandPrimary)
                    Text(grantedLabel).font(.chronosCaption.weight(.medium))
                        .foregroundStyle(ChronosColors.brandPrimary)
                }
                .padding(.top, ChronosSpacing.micro)
            } else {
                Button(actionLabel, action: onGrant)
                    .buttonStyle(.bordered)
                    .padding(.top, ChronosSpacing.micro)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(ChronosSpacing.standard)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: ChronosRadius.large, style: .continuous))
    }
}
