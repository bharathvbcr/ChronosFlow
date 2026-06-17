import SwiftUI

/// Vertically-paged watch UI — Today · Tasks · Habits · Focus — mirroring the Android Wear pager.
/// All pages read the cached `WatchSnapshot` from `WatchConnectivityClient` (the phone's mirror) and
/// send one-tap `WatchCommand`s back. There is NO local store — WatchConnectivity is the data layer.
struct WatchRootView: View {
    @Environment(WatchConnectivityClient.self) private var client

    var body: some View {
        TabView {
            WatchTodayPage().tag(0)
            WatchTasksPage().tag(1)
            WatchHabitsPage().tag(2)
            WatchFocusPage().tag(3)
        }
        .tabViewStyle(.verticalPage)
        .containerBackground(WatchTokens.brandPrimary.gradient, for: .tabView)
    }
}

// MARK: - Shared helpers

/// Minute-of-day → short clock string (self-contained; the watch shares only Models + DTOs).
func watchClock(_ minuteOfDay: Int) -> String {
    let m = ((minuteOfDay % 1440) + 1440) % 1440
    var comps = DateComponents(); comps.hour = m / 60; comps.minute = m % 60
    let date = Calendar.current.date(from: comps) ?? .now
    return date.formatted(.dateTime.hour().minute())
}

var watchNowMinuteOfDay: Int {
    let c = Calendar.current.dateComponents([.hour, .minute], from: .now)
    return (c.hour ?? 0) * 60 + (c.minute ?? 0)
}

/// Placeholder shown before the first sync / when the phone hasn't pushed anything yet.
private struct WatchEmptyState: View {
    var icon: String
    var message: String
    var body: some View {
        VStack(spacing: WatchSpacing.small) {
            Image(systemName: icon).font(.title3).foregroundStyle(.secondary)
            Text(message).font(.caption2).foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Today

struct WatchTodayPage: View {
    @Environment(WatchConnectivityClient.self) private var client

    private var blocks: [WatchBlock] {
        (client.snapshot?.blocks ?? []).sorted { $0.startMinute < $1.startMinute }
    }
    private var current: WatchBlock? {
        blocks.first { watchNowMinuteOfDay >= $0.startMinute && watchNowMinuteOfDay < $0.startMinute + $0.durationMinutes }
    }
    private var next: WatchBlock? { blocks.first { $0.startMinute > watchNowMinuteOfDay } }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: WatchSpacing.small) {
                Text("Today").font(.headline)

                if client.snapshot == nil {
                    WatchEmptyState(icon: "iphone.slash", message: "Open ChronosFlow on iPhone to sync")
                        .frame(height: 80)
                } else {
                    if let current {
                        blockCard(title: current.title,
                                  detail: "until \(watchClock(current.endMinute))",
                                  tint: WatchTokens.category(current.category))
                    } else {
                        blockCard(title: "Open time", detail: "nothing scheduled", tint: .secondary)
                    }
                    if let next {
                        blockCard(title: "Next · \(next.title)",
                                  detail: "at \(watchClock(next.startMinute))",
                                  tint: WatchTokens.category(next.category))
                    }
                    HStack {
                        stat("\(client.snapshot?.tasks.count ?? 0)", "tasks")
                        let habits = client.snapshot?.habits ?? []
                        stat("\(habits.filter(\.doneToday).count)/\(habits.count)", "habits")
                    }
                    .padding(.top, WatchSpacing.micro)
                }
            }
            .padding(.horizontal, WatchSpacing.micro)
        }
    }

    private func blockCard(title: String, detail: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.caption).bold().lineLimit(1)
            Text(detail).font(.caption2).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(WatchSpacing.small)
        .background(tint.opacity(0.22), in: .rect(cornerRadius: 10))
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(spacing: 1) {
            Text(value).font(.caption).bold()
            Text(label).font(.caption2).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Tasks

struct WatchTasksPage: View {
    @Environment(WatchConnectivityClient.self) private var client

    private var open: [WatchTask] {
        (client.snapshot?.tasks ?? []).filter { !$0.isCompleted }.sorted { $0.priority > $1.priority }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: WatchSpacing.small) {
                Text("Tasks").font(.headline).frame(maxWidth: .infinity, alignment: .leading)
                if open.isEmpty {
                    Text("All clear").font(.caption).foregroundStyle(.secondary)
                }
                ForEach(open) { task in
                    Button {
                        client.send(.completeTask(id: task.id))
                    } label: {
                        HStack {
                            Image(systemName: "circle").foregroundStyle(.secondary)
                            Text(task.title).font(.caption).lineLimit(2)
                            Spacer()
                            if task.priority >= 3 {
                                Image(systemName: "exclamationmark").font(.caption2)
                                    .foregroundStyle(WatchTokens.brandAccent)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, WatchSpacing.micro)
        }
    }
}

// MARK: - Habits

struct WatchHabitsPage: View {
    @Environment(WatchConnectivityClient.self) private var client

    private var habits: [WatchHabit] {
        (client.snapshot?.habits ?? []).sorted { lhs, rhs in
            if lhs.doneToday != rhs.doneToday { return !lhs.doneToday }
            return lhs.title < rhs.title
        }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: WatchSpacing.small) {
                Text("Habits").font(.headline).frame(maxWidth: .infinity, alignment: .leading)
                if habits.isEmpty {
                    Text("No habits").font(.caption).foregroundStyle(.secondary)
                }
                ForEach(habits) { habit in
                    Button {
                        client.send(.toggleHabit(id: habit.id))
                    } label: {
                        HStack {
                            Image(systemName: habit.doneToday ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(habit.doneToday ? WatchTokens.brandSecondary : .secondary)
                            Text(habit.title).font(.caption).lineLimit(1)
                            Spacer()
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, WatchSpacing.micro)
        }
    }
}

// MARK: - Focus

struct WatchFocusPage: View {
    @Environment(WatchConnectivityClient.self) private var client

    /// The current block (if running) to offer a "Start focus" CTA when idle.
    private var startableBlock: WatchBlock? {
        let blocks = client.snapshot?.blocks ?? []
        return blocks.first { watchNowMinuteOfDay >= $0.startMinute && watchNowMinuteOfDay < $0.startMinute + $0.durationMinutes }
            ?? blocks.first { $0.startMinute > watchNowMinuteOfDay }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: WatchSpacing.compact) {
                Text("Focus").font(.headline).frame(maxWidth: .infinity, alignment: .leading)

                if let focus = client.snapshot?.focus {
                    activeFocus(focus)
                } else if let block = startableBlock {
                    idleFocus(block)
                } else {
                    Text("No block to focus on").font(.caption).foregroundStyle(.secondary)
                }
            }
            .padding(.horizontal, WatchSpacing.micro)
        }
    }

    private func activeFocus(_ focus: WatchFocusState) -> some View {
        VStack(spacing: WatchSpacing.small) {
            Text(focus.blockTitle).font(.caption).bold().lineLimit(1)
            if focus.totalPhases > 1 {
                Text("Phase \(focus.phaseNumber) of \(focus.totalPhases)")
                    .font(.caption2).foregroundStyle(.secondary)
            }

            // Live countdown — watchOS renders this without ticking from our code.
            if focus.awaitingAdvance {
                Text("Tap to continue").font(.title3).bold()
                    .foregroundStyle(WatchTokens.brandPrimary)
            } else if focus.isPaused {
                Text("Paused").font(.title3).bold().foregroundStyle(.secondary)
            } else {
                // Guard against a stale snapshot whose phase end is already past (range must be
                // ordered); clamp to "now" so the countdown shows 0 rather than crashing.
                let end = max(focus.phaseEndsAt, Date.now)
                Text(timerInterval: Date.now...end, countsDown: true)
                    .font(.system(.title2, design: .rounded, weight: .bold).monospacedDigit())
                    .foregroundStyle(WatchTokens.brandPrimary)
            }

            HStack(spacing: WatchSpacing.small) {
                Button {
                    client.send(.togglePauseFocus)
                } label: {
                    Label(focus.isPaused ? "Resume" : "Pause",
                          systemImage: focus.isPaused ? "play.fill" : "pause.fill")
                        .labelStyle(.iconOnly)
                }
                .tint(WatchTokens.brandSecondary)

                Button(role: .destructive) {
                    client.send(.stopFocus)
                } label: {
                    Label("Stop", systemImage: "stop.fill").labelStyle(.iconOnly)
                }
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(WatchSpacing.small)
        .frame(maxWidth: .infinity)
        .background(WatchTokens.brandPrimary.opacity(0.18), in: .rect(cornerRadius: 12))
    }

    private func idleFocus(_ block: WatchBlock) -> some View {
        VStack(spacing: WatchSpacing.small) {
            Text(block.title).font(.caption).bold().lineLimit(1)
            Text("\(block.durationMinutes) min").font(.caption2).foregroundStyle(.secondary)
            Button {
                client.send(.startFocus(blockID: block.id))
            } label: {
                Label("Start focus", systemImage: "timer").font(.caption)
            }
            .buttonStyle(.borderedProminent)
            .tint(WatchTokens.brandPrimary)
        }
        .padding(WatchSpacing.small)
        .frame(maxWidth: .infinity)
        .background(WatchTokens.category(block.category).opacity(0.18), in: .rect(cornerRadius: 12))
    }
}
