import SwiftUI
import ChronosCore

/// Vertically-paged watch UI — Today · Tasks · Habits · (Meds) · Focus — mirroring the Android Wear
/// pager. All pages read the cached `WatchSnapshot` from `WatchConnectivityClient` (the phone's
/// mirror) and send one-tap `WatchCommand`s back. There is NO local store — WatchConnectivity is the
/// data layer. The Meds page is inserted only when the phone reports meds today (Android parity:
/// `HomePage.MEDS` is conditional on `summary.meds.isNotEmpty() || medsDueCount > 0`).
struct WatchRootView: View {
    @Environment(WatchConnectivityClient.self) private var client

    private var hasMeds: Bool {
        let snap = client.snapshot
        return !(snap?.medications.isEmpty ?? true) || (snap?.medsDueCount ?? 0) > 0
    }

    var body: some View {
        TabView {
            WatchTodayPage()
            WatchTasksPage()
            WatchHabitsPage()
            if hasMeds { WatchMedsPage() }
            WatchFocusPage()
        }
        .tabViewStyle(.verticalPage)
        .containerBackground(WatchTokens.brandPrimary.gradient, for: .tabView)
        .onAppear {
            // Pull-on-open: ask the phone for a fresh snapshot as soon as the watch UI appears.
            // Mirrors Android Wear's TYPE_SYNC message sent when the watch app is opened.
            client.sendSyncRequest()
        }
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

// MARK: - Day-dial ring
//
// The watch take on the phone's DayDial: today mapped onto a 24-hour ring (midnight at the top,
// clockwise). Each scheduled block is an arc — the one happening now in full primary, past ones
// dimmed grey, upcoming dimmed primary — and a tertiary dot marks the current time, so "what does
// my day look like" is answered visually. 1:1 port of Android Wear's `DayDialRing.kt` Canvas logic.
struct WatchDayDialRing: View {
    var blocks: [WatchBlock]
    var nowMinute: Int

    var body: some View {
        Canvas { ctx, size in
            let stroke: CGFloat = 5
            let inset = stroke / 2
            let rect = CGRect(x: inset, y: inset, width: size.width - stroke, height: size.height - stroke)
            let center = CGPoint(x: size.width / 2, y: size.height / 2)

            // Background track (dim full circle).
            ctx.stroke(Path(ellipseIn: rect),
                       with: .color(WatchTokens.brandPrimary.opacity(0.08)),
                       style: StrokeStyle(lineWidth: stroke))

            for block in blocks {
                let sweep = max(0, block.endMinute - block.startMinute)
                guard sweep > 0 else { continue }
                let isCurrent = nowMinute >= block.startMinute && nowMinute < block.endMinute
                let color: Color = isCurrent
                    ? WatchTokens.brandPrimary
                    : (block.endMinute <= nowMinute
                        ? Color.white.opacity(0.22)                  // past
                        : WatchTokens.brandPrimary.opacity(0.45))    // upcoming
                var arc = Path()
                arc.addArc(center: center,
                           radius: rect.width / 2,
                           startAngle: .degrees(angleOf(block.startMinute)),
                           endAngle: .degrees(angleOf(block.startMinute + min(sweep, 1440))),
                           clockwise: false)
                ctx.stroke(arc, with: .color(color),
                           style: StrokeStyle(lineWidth: stroke, lineCap: .round))
            }

            // Current-time marker dot (tertiary/accent).
            let radius = (min(size.width, size.height) - stroke) / 2
            let a = angleOf(nowMinute % 1440) * .pi / 180
            let dot = CGPoint(x: center.x + cos(a) * radius, y: center.y + sin(a) * radius)
            ctx.fill(Path(ellipseIn: CGRect(x: dot.x - stroke * 0.8, y: dot.y - stroke * 0.8,
                                            width: stroke * 1.6, height: stroke * 1.6)),
                     with: .color(WatchTokens.brandAccent))
        }
        .padding(2)
    }

    /// Minute-of-day → polar angle, midnight at the top (–90°), clockwise. Mirrors Android's
    /// `angleOf(minute) = minute / 1440f * 360f - 90f`.
    private func angleOf(_ minute: Int) -> Double { Double(minute) / 1440 * 360 - 90 }
}

// MARK: - Today

struct WatchTodayPage: View {
    @Environment(WatchConnectivityClient.self) private var client

    private var blocks: [WatchBlock] {
        (client.snapshot?.blocks ?? []).sorted { $0.startMinute < $1.startMinute }
    }
    private var now: Int { watchNowMinuteOfDay }
    private var current: WatchBlock? {
        blocks.first { now >= $0.startMinute && now < $0.startMinute + $0.durationMinutes }
    }
    /// Next upcoming non-break event (Android `nextTitle`).
    private var nextEvent: WatchBlock? { blocks.first { $0.startMinute > now && !$0.isBreak } }
    /// Next upcoming break (Android `nextBreakStartMinute`/`nextBreakTitle`).
    private var nextBreak: WatchBlock? { blocks.first { $0.startMinute > now && $0.isBreak } }

    /// "Synced Nm/Nh ago" when the mirror is stale enough that its time-relative claims may rot;
    /// nil when fresh or never synced. Mirrors Android's NowScreen stale warning.
    private var staleLabel: String? {
        guard let received = client.snapshot?.receivedAtMillis, received > 0 else { return nil }
        return WearFormat.syncAgeLabel(receivedAtMillis: received,
                                       nowMillis: Int64(Date.now.timeIntervalSince1970 * 1000))
    }

    /// Doses past their reminder and still untaken — surfaced as a red alert here so urgency reaches
    /// the today page without swiping to Meds (Android NowScreen parity).
    private var overdueMedCount: Int {
        (client.snapshot?.medications ?? []).filter { $0.isOverdue(nowMinute: now) }.count
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: WatchSpacing.small) {
                Text("Today").font(.headline)

                if client.snapshot == nil {
                    switch client.syncState {
                    case .syncing:
                        WatchEmptyState(icon: "arrow.triangle.2.circlepath", message: "Syncing…")
                            .frame(height: 80)
                    case .unreachable:
                        WatchEmptyState(icon: "iphone.slash", message: "Can't reach iPhone")
                            .frame(height: 80)
                    case .idle:
                        WatchEmptyState(icon: "iphone.slash", message: "Open ChronosFlow on iPhone to sync")
                            .frame(height: 80)
                    }
                } else {
                    // 24h day-dial ring with the current/next cards laid over it.
                    ZStack {
                        WatchDayDialRing(blocks: blocks, nowMinute: now)
                            .frame(height: 96)
                        if let current {
                            VStack(spacing: 1) {
                                Text(current.title).font(.caption2).bold().lineLimit(1)
                                Text(WearFormat.remainingLabel(endMinute: current.endMinute, nowMinute: now))
                                    .font(.caption2).foregroundStyle(WatchTokens.brandPrimary)
                            }
                            .padding(.horizontal, 4)
                        } else {
                            Text("Open time").font(.caption2).foregroundStyle(.secondary)
                        }
                    }

                    if let stale = staleLabel {
                        Label(stale, systemImage: "exclamationmark.triangle.fill")
                            .font(.caption2).foregroundStyle(.red)
                    }
                    if overdueMedCount > 0 {
                        Label("\(overdueMedCount) med\(overdueMedCount == 1 ? "" : "s") overdue",
                              systemImage: "pills.fill")
                            .font(.caption2).foregroundStyle(.red)
                    }

                    if let current {
                        blockCard(title: current.title,
                                  detail: WearFormat.windowLabel(startMinute: current.startMinute,
                                                                 endMinute: current.endMinute),
                                  tint: WatchTokens.category(current.category))
                    }
                    if let nextEvent {
                        blockCard(title: "Next · \(nextEvent.title)",
                                  detail: WearFormat.startsInLabel(startMinute: nextEvent.startMinute, nowMinute: now),
                                  tint: WatchTokens.category(nextEvent.category))
                    }
                    if let nextBreak {
                        let label = nextBreak.title.isEmpty || nextBreak.title.caseInsensitiveCompare("Break") == .orderedSame
                            ? "Break \(WearFormat.startsInLabel(startMinute: nextBreak.startMinute, nowMinute: now))"
                            : "Break \(WearFormat.startsInLabel(startMinute: nextBreak.startMinute, nowMinute: now)) · \(nextBreak.title)"
                        Text(label).font(.caption2).foregroundStyle(.secondary)
                    }

                    if let digest = client.snapshot?.digest, !digest.isEmpty {
                        Text(digest).font(.caption2).foregroundStyle(.secondary)
                            .padding(.top, WatchSpacing.micro)
                    }

                    HStack {
                        stat("\(client.snapshot?.tasks.count ?? 0)", "tasks")
                        let habits = client.snapshot?.habits ?? []
                        stat("\(habits.filter(\.doneToday).count)/\(habits.count)", "habits")
                        if (client.snapshot?.medsDueCount ?? 0) > 0 {
                            stat("\(client.snapshot?.medsDueCount ?? 0)", "due")
                        }
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

// MARK: - Meds

/// Per-dose medication page. Mirrors Android Wear's `MedsScreen`: "N due" / "All taken" header,
/// privacy-hidden note when the title array was redacted, and a one-tap "Taken" control per dose
/// sorted untaken-earliest-first with an overdue marker. Marking a dose sends `WatchCommand.markDose`
/// back to the phone, which records a TAKEN `DoseEvent`.
struct WatchMedsPage: View {
    @Environment(WatchConnectivityClient.self) private var client

    private var now: Int { watchNowMinuteOfDay }
    private var meds: [WatchMed] { sortMedsForGlance(client.snapshot?.medications ?? []) }
    private var dueCount: Int { client.snapshot?.medsDueCount ?? 0 }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: WatchSpacing.small) {
                Text("Medication").font(.headline).frame(maxWidth: .infinity, alignment: .leading)

                if dueCount > 0 {
                    Text("\(dueCount) due").font(.caption).foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity)
                } else if !meds.isEmpty {
                    Text("✓ All taken").font(.caption).bold().foregroundStyle(WatchTokens.brandSecondary)
                        .frame(maxWidth: .infinity)
                }

                if meds.isEmpty {
                    if dueCount > 0 {
                        // Titles redacted for privacy but doses are still due — say so (Android parity).
                        Text("Hidden for privacy").font(.caption2).foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity)
                    } else {
                        Text("No medication today").font(.caption).foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity)
                    }
                }

                ForEach(meds) { med in
                    Button {
                        if !med.taken { client.send(.markDose(id: med.id)) }
                    } label: {
                        HStack {
                            Image(systemName: med.taken ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(med.taken ? WatchTokens.brandSecondary : .secondary)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(med.name).font(.caption).lineLimit(2)
                                let overdue = med.isOverdue(nowMinute: now)
                                Text((overdue ? "Overdue · " : "")
                                     + "\(med.doseLabel) · \(WearFormat.minuteOfDay(med.reminderMinute))")
                                    .font(.caption2)
                                    .foregroundStyle(overdue ? Color.red : .secondary)
                                    .lineLimit(1)
                            }
                            Spacer()
                        }
                    }
                    .buttonStyle(.plain)
                    .disabled(med.taken)
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
