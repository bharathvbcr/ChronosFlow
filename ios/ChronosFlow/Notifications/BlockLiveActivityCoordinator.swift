import ActivityKit
import ChronosCore
import Foundation
import SwiftData

/// Keeps a single schedule Live Activity in sync with today's plan — the iOS-native equivalent of
/// Android's `CurrentBlockNotificationCoordinator`. Replaces the passive `block-next` local
/// notification so the user sees one live surface instead of multiple alerts.
@MainActor
enum BlockLiveActivityCoordinator {
  private static let upNextLookaheadMinutes = 120
  /// Max interval between stale-date wakeups for mid-block progress/subtitle refresh.
  private static let progressRefreshInterval: TimeInterval = 5 * 60
  /// In-app refresh cadence while the app is foregrounded (Android boundary-alarm parity for progress/subtitle).
  private static let foregroundRefreshInterval: TimeInterval = 60
  /// Self-heal window when focus ends without cleanup (Android `FOCUS_SUPPRESS_CAP_MS` parity).
  private static let focusSuppressCap: TimeInterval = 6 * 60 * 60
  private static let focusSuppressKey = "blockLive.focusSuppressedUntil"
  private static var activity: Activity<BlockActivityAttributes>?
  private static var foregroundTimer: Timer?
  private static var focusDefaults: UserDefaults {
    UserDefaults(suiteName: ChronosStore.appGroup) ?? .standard
  }

  /// Refresh from today's blocks in the shared store. Safe to call on launch, foreground, and
  /// background stale-date wakeups — the app-wide entry point (Android: coordinator.refresh()).
  static func refreshToday(settings: ChronosSettings = .shared) {
    let cal = Calendar.current
    guard cal.isDateInToday(.now) else {
      end(dismissalPolicy: .immediate)
      return
    }
    let nowMinute = cal.component(.hour, from: .now) * 60 + cal.component(.minute, from: .now)
    let today = cal.startOfDay(for: .now)
    let context = ChronosStore.shared.mainContext
    let blocks = (try? context.fetch(FetchDescriptor<TimeBlock>()))?
      .filter { cal.isDate($0.date, inSameDayAs: today) }
      .sorted { $0.startMinuteOfDay < $1.startMinuteOfDay } ?? []
    refresh(blocks: blocks, nowMinute: nowMinute, settings: settings)
  }

  /// Refresh the schedule live activity from today's blocks. Call when the plan changes or on a
  /// timer while viewing Today. No-ops when the setting is off, focus owns the live surface, or
  /// ActivityKit is unavailable.
  static func refresh(blocks: [TimeBlock], nowMinute: Int, settings: ChronosSettings = .shared) {
    reconcileActivityReference()
    guard settings.remindersEnabled, settings.currentBlockLiveActivityEnabled else {
      end(dismissalPolicy: .immediate)
      return
    }
    guard !isFocusSuppressingBlockLive else {
      end(dismissalPolicy: .immediate)
      return
    }
    guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }

    // The most-imminent actionable reminders (dose/task/habit) folded onto this surface, ranked and
    // capped. The first rides as the primary chip; any others stack beneath it.
    let reminders = foldedReminders(nowMinute: nowMinute, settings: settings)
    let eligible = eligibleBlocks(blocks)
    let active = eligible.first { nowMinute >= $0.startMinuteOfDay && nowMinute < $0.startMinuteOfDay + $0.durationMinutes }
    let next = eligible.first { $0.startMinuteOfDay > nowMinute }

    if let active {
      var state = activeState(active: active, blocks: eligible, nowMinute: nowMinute, settings: settings)
      state.reminders = reminders
      upsert(state: state, blocks: eligible, nowMinute: nowMinute)
      return
    }

    if let next, shouldShowUpNext(nextStartMinute: next.startMinuteOfDay, nowMinute: nowMinute) {
      let gapStart = previousBlockEndMinute(blocks: eligible, nowMinute: nowMinute) ?? nowMinute
      var state = upNextState(next: next, blocks: eligible, gapStartMinute: gapStart, nowMinute: nowMinute, settings: settings)
      state.reminders = reminders
      upsert(state: state, blocks: eligible, nowMinute: nowMinute)
      return
    }

    // No block context, but reminders are pending — keep the live surface alive as a reminder card so
    // it stands in for the separate med/task/habit banners (the "aggressive fold").
    if !reminders.isEmpty {
      upsert(state: reminderState(reminders), blocks: blocks, nowMinute: nowMinute)
      return
    }

    end(
      dismissalPolicy: .immediate,
      scheduleNextWakeAt: nextScheduleBoundaryDate(blocks: eligible, nowMinute: nowMinute))
  }

  /// Focus session started — yield the live surface to the focus Live Activity.
  static func onFocusStarted() {
    renewFocusSuppression()
    end(dismissalPolicy: .immediate)
  }

  /// Roll focus suppression forward while a session is live (cheap; survives process death).
  static func renewFocusSuppression() {
    let until = Date.now.addingTimeInterval(focusSuppressCap).timeIntervalSince1970
    focusDefaults.set(until, forKey: focusSuppressKey)
  }

  /// Focus session ended — restore the schedule surface if a block is current.
  static func onFocusEnded() {
    focusDefaults.removeObject(forKey: focusSuppressKey)
    refreshToday()
  }

  /// Tick the schedule surface on a fixed cadence while the app is foregrounded — block transitions,
  /// progress subtitles, and folded reminders stay fresh without relying on stale-date wakeups alone.
  static func startForegroundRefresh() {
    guard foregroundTimer == nil else { return }
    foregroundTimer = Timer.scheduledTimer(withTimeInterval: foregroundRefreshInterval, repeats: true) { _ in
      Task { @MainActor in refreshToday() }
    }
  }

  static func stopForegroundRefresh() {
    foregroundTimer?.invalidate()
    foregroundTimer = nil
  }

  /// Observe stale Live Activity content and re-render. Call once at app launch.
  static func startStaleObserver() {
    guard !staleObserverStarted else { return }
    staleObserverStarted = true
    for activity in Activity<BlockActivityAttributes>.activities {
      observeStaleContent(of: activity)
    }
    Task {
      for await activity in Activity<BlockActivityAttributes>.activityUpdates {
        observeStaleContent(of: activity)
      }
    }
  }

  // MARK: - Private

  private static var staleObserverStarted = false

  /// True while focus owns the live surface — in-memory phase or persisted suppression after a kill.
  private static var isFocusSuppressingBlockLive: Bool {
    if FocusTimerModel.shared.isFocusSessionLive { return true }
    let until = focusDefaults.double(forKey: focusSuppressKey)
    return until > Date.now.timeIntervalSince1970
  }

  private static func notifyWatchSnapshotChanged() {
    PhoneWatchSync.shared.pushSnapshot()
  }

  private static func observeStaleContent(of activity: Activity<BlockActivityAttributes>) {
    Task {
      for await state in activity.activityStateUpdates {
        if state == .stale {
          refreshToday()
        }
      }
    }
  }

  /// Adopt a surviving ActivityKit instance after relaunch and collapse duplicate schedule surfaces.
  private static func reconcileActivityReference() {
    let existing = Activity<BlockActivityAttributes>.activities
    if let tracked = activity, existing.contains(where: { $0.id == tracked.id }) {
      // tracked reference is still valid
    } else {
      activity = existing.first
    }
    for duplicate in existing.dropFirst() {
      Task { await duplicate.end(nil, dismissalPolicy: .immediate) }
    }
  }

  private static func upsert(
    state: BlockActivityAttributes.ContentState,
    blocks: [TimeBlock] = [],
    nowMinute: Int = Calendar.current.component(.hour, from: .now) * 60
      + Calendar.current.component(.minute, from: .now)
  ) {
    reconcileActivityReference()
    let dayLabel = Calendar.current.isDateInToday(.now) ? "Today" : Date.now.formatted(date: .abbreviated, time: .omitted)
    let attributes = BlockActivityAttributes(dayLabel: dayLabel)
    // Stale at the next schedule boundary, periodic progress refresh, or countdown end — whichever
    // is soonest — so ActivityKit re-wakes near block transitions even when the app is backgrounded.
    let stale = staleDate(for: state, blocks: blocks, nowMinute: nowMinute)
    let content = ActivityContent(state: state, staleDate: stale)

    if let activity {
      Task { await activity.update(content) }
    } else {
      activity = try? Activity.request(attributes: attributes, content: content)
    }
    ChronosBackgroundSync.scheduleBlockLive(at: stale)
    notifyWatchSnapshotChanged()
  }

  private static func staleDate(
    for state: BlockActivityAttributes.ContentState,
    blocks: [TimeBlock],
    nowMinute: Int
  ) -> Date {
    let now = Date.now
    var candidates: [Date] = [state.endsAt, nextMidnightDate(from: now)]
    if let boundary = nextScheduleBoundaryDate(blocks: blocks, nowMinute: nowMinute) {
      candidates.append(boundary)
    }
    candidates.append(now.addingTimeInterval(progressRefreshInterval))
    return candidates.filter { $0 > now }.min() ?? state.endsAt
  }

  /// Start of the next local calendar day — end stale Live Activities at midnight when backgrounded.
  private static func nextMidnightDate(from now: Date = .now) -> Date {
    let cal = Calendar.current
    let startToday = cal.startOfDay(for: now)
    return cal.date(byAdding: .day, value: 1, to: startToday)
      ?? now.addingTimeInterval(24 * 60 * 60)
  }

  /// The next block start/end (minute-of-day) after `nowMinute`, as an absolute Date today.
  /// Also includes the up-next lookahead window opening when the next block is still far off.
  private static func nextScheduleBoundaryDate(blocks: [TimeBlock], nowMinute: Int) -> Date? {
    let cal = Calendar.current
    let today = cal.startOfDay(for: .now)
    let eligible = eligibleBlocks(blocks)
    var boundaryMinutes: [Int] = []
    for block in eligible {
      if block.startMinuteOfDay > nowMinute {
        boundaryMinutes.append(block.startMinuteOfDay)
      }
      let end = block.startMinuteOfDay + block.durationMinutes
      if nowMinute >= block.startMinuteOfDay && end > nowMinute {
        boundaryMinutes.append(end)
      }
    }
    if let next = eligible.first(where: { $0.startMinuteOfDay > nowMinute }) {
      let windowOpen = next.startMinuteOfDay - upNextLookaheadMinutes
      if nowMinute < windowOpen {
        boundaryMinutes.append(windowOpen)
      }
    }
    guard let nextMinute = boundaryMinutes.min() else { return nil }
    return cal.date(byAdding: .minute, value: nextMinute, to: today)
  }

  private static func end(
    dismissalPolicy: ActivityUIDismissalPolicy,
    scheduleNextWakeAt: Date? = nil
  ) {
    activity = nil
    if let scheduleNextWakeAt, scheduleNextWakeAt > .now {
      ChronosBackgroundSync.scheduleBlockLive(at: scheduleNextWakeAt)
    } else {
      ChronosBackgroundSync.cancelBlockLive()
    }
    for live in Activity<BlockActivityAttributes>.activities {
      Task { await live.end(nil, dismissalPolicy: dismissalPolicy) }
    }
    notifyWatchSnapshotChanged()
  }

  private static func eligibleBlocks(_ blocks: [TimeBlock]) -> [TimeBlock] {
    blocks
      .filter { $0.occupiesScheduleTime && $0.actualEndMinuteOfDay == nil }
      .sorted { $0.startMinuteOfDay < $1.startMinuteOfDay }
  }

  private static func activeState(
    active: TimeBlock,
    blocks: [TimeBlock],
    nowMinute: Int,
    settings: ChronosSettings
  ) -> BlockActivityAttributes.ContentState {
    let endMinute = active.startMinuteOfDay + active.durationMinutes
    let remaining = upcomingBlockCount(blocks: blocks, nowMinute: nowMinute)
    let redact = settings.sensitiveTitlesRedacted
    let title = redact ? "Current block" : (active.title.isEmpty ? "Current block" : active.title)
    let window = "\(active.startMinuteOfDay.clockTime)–\(endMinute.clockTime)"
    let upcoming = selectUpcomingGlances(blocks: blocks, nowMinute: nowMinute)
    let subtitle: String
    if redact {
      subtitle = redactedBody(window: window, upcoming: upcoming)
    } else {
      subtitle = activeBody(window: window, upcoming: upcoming, remaining: remaining)
    }
    let total = max(active.durationMinutes, 1)
    let elapsed = max(0, nowMinute - active.startMinuteOfDay)
    let progress = min(1, Double(elapsed) / Double(total))
    let endsAt = blockEndDate(endMinute: endMinute, nowMinute: nowMinute)
    return .init(
      mode: .active,
      title: title,
      subtitle: subtitle,
      headerLabel: headerLabel(remaining: remaining, upNext: false),
      blockID: active.id,
      progress: progress,
      endsAt: endsAt,
      category: active.category,
      remainingBlockCount: remaining)
  }

  private static func upNextState(
    next: TimeBlock,
    blocks: [TimeBlock],
    gapStartMinute: Int,
    nowMinute: Int,
    settings: ChronosSettings
  ) -> BlockActivityAttributes.ContentState {
    let redact = settings.sensitiveTitlesRedacted
    let title = redact ? "Up next" : (next.title.isEmpty ? "Up next" : next.title)
    let following = selectUpcomingGlances(blocks: blocks, nowMinute: next.startMinuteOfDay)
    let subtitle = upNextBody(nextStartMinute: next.startMinuteOfDay, following: following, redact: redact)
    let gapTotal = max(next.startMinuteOfDay - gapStartMinute, 1)
    let elapsed = max(0, nowMinute - gapStartMinute)
    let progress = min(1, Double(elapsed) / Double(gapTotal))
    let endsAt = blockEndDate(endMinute: next.startMinuteOfDay, nowMinute: nowMinute)
    return .init(
      mode: .upNext,
      title: title,
      subtitle: subtitle,
      headerLabel: "Up next",
      progress: progress,
      endsAt: endsAt,
      category: next.category,
      remainingBlockCount: upcomingBlockCount(blocks: blocks, nowMinute: nowMinute))
  }

  private static func headerLabel(remaining: Int, upNext: Bool) -> String {
    if upNext { return "Up next" }
    guard remaining > 0 else { return "Now" }
    return "Now · \(remaining) to go"
  }

  private static func shouldShowUpNext(nextStartMinute: Int, nowMinute: Int) -> Bool {
    (nextStartMinute - nowMinute) >= 0 && (nextStartMinute - nowMinute) <= upNextLookaheadMinutes
  }

  private static func previousBlockEndMinute(blocks: [TimeBlock], nowMinute: Int) -> Int? {
    blocks
      .map { $0.startMinuteOfDay + $0.durationMinutes }
      .filter { $0 <= nowMinute }
      .max()
  }

  private static func upcomingBlockCount(blocks: [TimeBlock], nowMinute: Int) -> Int {
    blocks.count { $0.startMinuteOfDay > nowMinute }
  }

  private struct UpcomingGlance {
    let title: String
    let startMinute: Int
    let isBreak: Bool
  }

  private static func selectUpcomingGlances(blocks: [TimeBlock], nowMinute: Int) -> [UpcomingGlance] {
    let upcoming = blocks.filter { $0.startMinuteOfDay > nowMinute }
    let nextEvent = upcoming.first { !$0.isBreakBlock }
    let nextBreak = upcoming.first { $0.isBreakBlock }
    return [nextEvent, nextBreak]
      .compactMap { $0 }
      .map { UpcomingGlance(title: $0.title, startMinute: $0.startMinuteOfDay, isBreak: $0.isBreakBlock) }
      .sorted { $0.startMinute < $1.startMinute }
  }

  private static func activeBody(window: String, upcoming: [UpcomingGlance], remaining: Int) -> String {
    if upcoming.isEmpty { return remaining <= 0 ? "\(window) · Last block of the day" : window }
    let tail = upcoming.map { glanceLabel($0, redact: false) }.joined(separator: " · ")
    return "\(window) · \(tail)"
  }

  private static func redactedBody(window: String, upcoming: [UpcomingGlance]) -> String {
    if upcoming.isEmpty { return window }
    let tail = upcoming.map { glanceLabel($0, redact: true) }.joined(separator: " · ")
    return "\(window) · \(tail)"
  }

  private static func upNextBody(nextStartMinute: Int, following: [UpcomingGlance], redact: Bool) -> String {
    let base = "Starts at \(nextStartMinute.clockTime)"
    guard let glance = following.first else { return base }
    return "\(base) · \(glanceLabel(glance, redact: redact))"
  }

  private static func glanceLabel(_ glance: UpcomingGlance, redact: Bool) -> String {
    let label = glance.isBreak ? "Break" : "Next"
    let time = glance.startMinute.clockTime
    if redact { return "\(label) at \(time)" }
    let name = glance.title.trimmingCharacters(in: .whitespaces)
    if name.isEmpty || name.caseInsensitiveCompare(label) == .orderedSame {
      return "\(label) at \(time)"
    }
    return "\(label): \(name) at \(time)"
  }

  private static func blockEndDate(endMinute: Int, nowMinute: Int) -> Date {
    let cal = Calendar.current
    let minutesLeft = max(endMinute - nowMinute, 0)
    return cal.date(byAdding: .minute, value: minutesLeft, to: .now) ?? .now
  }

  // MARK: - Folded reminders
  //
  // The "aggressive fold": instead of firing a separate banner for every due medication dose, task,
  // and habit, the single most-imminent actionable one rides inside this Live Activity as a chip with
  // an action button. The standalone notifications are demoted to passive (see ChronosNotifications)
  // so the live surface — not a stack of alerts — is where the user acts.

  /// How many folded reminder chips the live surface carries at once — mirrors ChronosCore `maxFoldedReminders`.
  static let maxFoldedReminders = ChronosCore.maxFoldedReminders

  private static func reminderState(
    _ reminders: [BlockActivityAttributes.ReminderChip]
  ) -> BlockActivityAttributes.ContentState {
    let primary = reminders[0]
    let header = reminders.count > 1
      ? "\(reminders.count) reminders due"
      : (primary.isOverdue ? "Reminder · overdue" : "Reminder · now")
    return .init(
      mode: .reminder,
      title: primary.title,
      subtitle: primary.detail,
      headerLabel: header,
      progress: 0,
      // No countdown in reminder mode; keep this in the future so `upsert`'s staleDate resolves to the
      // periodic refresh (not the past, which would render the card stale/dimmed immediately).
      endsAt: .now.addingTimeInterval(progressRefreshInterval),
      category: primary.kind.categoryKey,
      remainingBlockCount: 0,
      reminders: reminders)
  }

  /// The reminders to surface, ranked medication first (health-critical), then task, then habit, and
  /// within a kind the most-overdue one — capped at `maxFoldedReminders`. Empty when folding is off
  /// or nothing is due yet today. The first element is the primary chip / `.reminder`-mode headline.
  private static func foldedReminders(
    nowMinute: Int, settings: ChronosSettings
  ) -> [BlockActivityAttributes.ReminderChip] {
    FoldedReminderLiveResolver.resolveReminderChips(
      nowMinute: nowMinute,
      settings: settings,
      context: ChronosStore.shared.mainContext)
  }
}

private extension TimeBlock {
  /// Mirrors `TimeBlock.occupiesScheduleTime()` — all-day calendar imports are excluded.
  var occupiesScheduleTime: Bool {
    !(provenance == .calendar && calendarEventID != nil &&
      (category.uppercased() == "CALENDAR_ALL_DAY" || durationMinutes >= 1440))
  }

  /// Mirrors Android `BlockCategories.isBreak` — only the BREAK category, not sleep/meal.
  var isBreakBlock: Bool { category.uppercased() == "BREAK" }
}

extension FocusTimerModel {
  /// True while a focus session is live (running or paused) — the schedule Live Activity yields.
  var isFocusSessionLive: Bool {
    switch phase {
    case .work, .shortBreak, .longBreak: true
    default: false
    }
  }
}
