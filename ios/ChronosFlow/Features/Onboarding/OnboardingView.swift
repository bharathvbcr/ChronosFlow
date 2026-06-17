import SwiftUI

/// First-run onboarding — a paged welcome that introduces the Chronos Dial, asks for notification
/// permission, and hands off to the app. Mirrors the Android first-run onboarding pass.
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

    /// Total pages = the informational pages plus the trailing feature-selector page.
    private var pageCount: Int { pages.count + 1 }
    private var selectorIndex: Int { pages.count }

    var body: some View {
        ZStack {
            ChronosBackdrop()
            VStack {
                TabView(selection: $page) {
                    ForEach(pages.indices, id: \.self) { i in
                        pageView(pages[i]).tag(i)
                    }
                    featureSelectorPage.tag(selectorIndex)
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

    /// The 7-feature selector. Toggles bind directly to `ChronosSettings.shared`, so flipping one
    /// persists immediately (the store writes through) and the shell adds/removes that tab.
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
                    featureRow("Habits", "heart.fill", "Build and track recurring routines", isOn: $settings.habitsEnabled)
                    featureRow("Goals", "flag.fill", "Set longer-term targets and progress", isOn: $settings.goalsEnabled)
                    featureRow("Medications", "pills.fill", "Reminders and refill tracking", isOn: $settings.medicationEnabled)
                    featureRow("Journal", "book.closed.fill", "Capture notes and reflections", isOn: $settings.journalEnabled)
                    featureRow("Sleep", "moon.zzz.fill", "Log nights and sleep quality", isOn: $settings.sleepEnabled)
                    featureRow("Routines", "repeat", "Step-by-step morning and evening flows", isOn: $settings.routinesEnabled)
                    featureRow("Insights", "chart.bar.xaxis", "Review trends and what actually happened", isOn: $settings.insightsEnabled)
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
            // Request notification authorization when leaving the notifications page (the last
            // informational page, just before the feature selector).
            if page == pages.count - 1 {
                Task { await ChronosNotifications.shared.requestAuthorization() }
            }
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
