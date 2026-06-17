import Foundation
import Observation
import SwiftData
import WatchConnectivity

/// The phone half of the watchOS Data Layer — the iOS-native analogue of the Android phone-side
/// Wear `DataClient`/`MessageClient` bridge. It activates a `WCSession`, builds a `WatchSnapshot`
/// of *today* from the shared SwiftData store, and ships it to the paired watch via
/// `updateApplicationContext` (latest-state, coalesced) plus an immediate `sendMessage` when the
/// watch is reachable. Incoming `WatchCommand`s mutate the same store the widget intents touch, and
/// focus controls are forwarded to the running `FocusTimerModel` via `FocusCommandBridge`.
///
/// IMPORTANT (caller wiring — see report): call `PhoneWatchSync.shared.activate()` once at launch
/// and `PhoneWatchSync.shared.pushSnapshot()` whenever the data changes / the app becomes active.
@Observable
@MainActor
final class PhoneWatchSync: NSObject, WCSessionDelegate {
    static let shared = PhoneWatchSync()

    /// True once the watch reports it is reachable (for an optional "open on watch" affordance).
    private(set) var isWatchReachable = false

    private var session: WCSession? {
        WCSession.isSupported() ? .default : nil
    }

    private override init() { super.init() }

    /// Activate the session. Safe to call multiple times; only the first activation does work.
    func activate() {
        guard let session else { return }
        session.delegate = self
        if session.activationState != .activated {
            session.activate()
        }
    }

    // MARK: Snapshot push (phone → watch)

    /// Build a fresh snapshot from the shared store and send it to the watch. Uses
    /// `updateApplicationContext` (the watch always gets the *latest* state even if it was asleep),
    /// and additionally `sendMessage` when reachable so an awake watch updates instantly.
    func pushSnapshot() {
        guard let session, session.activationState == .activated else { return }
        let snapshot = buildSnapshot()
        guard let data = try? JSONEncoder().encode(snapshot) else { return }
        let payload: [String: Any] = [WatchSyncPayload.snapshot: data]

        // Latest-state channel — coalesced, survives the watch being asleep.
        try? session.updateApplicationContext(payload)

        // Immediate nudge for a reachable, foregrounded watch.
        if session.isReachable {
            session.sendMessage(payload, replyHandler: nil, errorHandler: nil)
        }
    }

    /// Snapshot today's blocks/tasks/habits + any running focus session from the shared store.
    private func buildSnapshot() -> WatchSnapshot {
        let context = ChronosStore.shared.mainContext
        let cal = Calendar.current
        let today = cal.startOfDay(for: .now)

        let blocks: [WatchBlock] = (try? context.fetch(FetchDescriptor<TimeBlock>()))?
            .filter { cal.isDate($0.date, inSameDayAs: today) }
            .sorted { $0.startMinuteOfDay < $1.startMinuteOfDay }
            .map {
                WatchBlock(id: $0.id,
                           title: $0.title,
                           startMinute: $0.startMinuteOfDay,
                           durationMinutes: $0.durationMinutes,
                           category: $0.category,
                           isCompleted: $0.actualEndMinuteOfDay != nil)
            } ?? []

        // Open tasks (incomplete) plus anything due/targeted today, highest priority first.
        let tasks: [WatchTask] = (try? context.fetch(FetchDescriptor<TaskItem>()))?
            .filter { !$0.isCompleted }
            .sorted { $0.priority > $1.priority }
            .prefix(20)
            .map { WatchTask(id: $0.id, title: $0.title, priority: $0.priority, isCompleted: $0.isCompleted) } ?? []

        let habits: [WatchHabit] = (try? context.fetch(FetchDescriptor<Habit>()))?
            .filter(\.isActive)
            .map { WatchHabit(id: $0.id, title: $0.title, doneToday: $0.isCompleted(on: .now)) } ?? []

        return WatchSnapshot(date: today,
                             blocks: blocks,
                             tasks: tasks,
                             habits: habits,
                             focus: currentFocusState())
    }

    /// The phone's live focus state, if any. The `FocusTimerModel` is in-memory in the app process,
    /// so we read the same Live-Activity mirror the focus bridge maintains in App-Group defaults.
    /// When no session is broadcasting, returns nil and the watch shows "Start focus".
    private func currentFocusState() -> WatchFocusState? {
        // The running FocusTimerModel publishes its phase to App-Group UserDefaults (see
        // `publishFocusState` below, called from the app). Absence => no active session.
        guard let data = UserDefaults(suiteName: ChronosStore.appGroup)?
            .data(forKey: Self.focusStateKey),
              let state = try? JSONDecoder().decode(WatchFocusState.self, from: data) else {
            return nil
        }
        return state
    }

    /// App-Group key the app writes the current focus state under so this sync (and a cold push)
    /// can mirror it to the watch. The app calls `PhoneWatchSync.publishFocusState(_:)` from the
    /// FocusTimerModel's Live-Activity update hook (see report).
    static let focusStateKey = "watch.focusState"

    /// Persist the current focus state (or clear it) so the next `pushSnapshot()` mirrors it to the
    /// watch. Call from the app whenever the live focus session changes; pass nil when it ends.
    nonisolated static func publishFocusState(_ state: WatchFocusState?) {
        let defaults = UserDefaults(suiteName: ChronosStore.appGroup) ?? .standard
        if let state, let data = try? JSONEncoder().encode(state) {
            defaults.set(data, forKey: focusStateKey)
        } else {
            defaults.removeObject(forKey: focusStateKey)
        }
    }

    // MARK: Command handling (watch → phone)

    private func handle(_ command: WatchCommand) {
        let context = ChronosStore.shared.mainContext
        switch command {
        case .completeTask(let id):
            if let task = try? context.fetch(
                FetchDescriptor<TaskItem>(predicate: #Predicate { $0.id == id })).first {
                task.isCompleted = true
                task.updatedAt = .now
                try? context.save()
            }
        case .toggleHabit(let id):
            if let habit = try? context.fetch(
                FetchDescriptor<Habit>(predicate: #Predicate { $0.id == id })).first {
                habit.toggleCompletion(on: .now)
                try? context.save()
            }
        case .markDose(let id):
            if let plan = try? context.fetch(
                FetchDescriptor<MedicationPlan>(predicate: #Predicate { $0.id == id })).first,
               !plan.isTaken(on: .now) {
                plan.acknowledgeDose()
                try? context.save()
            }
        case .startFocus(let blockID):
            FocusCommandBridge.post(.start(blockID: blockID))
        case .togglePauseFocus:
            FocusCommandBridge.post(.togglePause)
        case .stopFocus:
            FocusCommandBridge.post(.stop)
        }
        // Reflect the mutation back to the watch immediately.
        pushSnapshot()
    }

    private func decodeAndHandle(_ payload: [String: Any]) {
        guard let data = payload[WatchSyncPayload.command] as? Data,
              let command = try? JSONDecoder().decode(WatchCommand.self, from: data) else { return }
        handle(command)
    }

    // MARK: WCSessionDelegate
    //
    // Delegate callbacks are nonisolated (WCSession calls them off the main actor); hop to the main
    // actor before touching the store or @Observable state.

    nonisolated func session(_ session: WCSession,
                             activationDidCompleteWith activationState: WCSessionActivationState,
                             error: Error?) {
        Task { @MainActor in
            self.isWatchReachable = session.isReachable
            // Push current state as soon as the link is up.
            if activationState == .activated { self.pushSnapshot() }
        }
    }

    nonisolated func sessionReachabilityDidChange(_ session: WCSession) {
        Task { @MainActor in
            self.isWatchReachable = session.isReachable
            if session.isReachable { self.pushSnapshot() }
        }
    }

    nonisolated func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        Task { @MainActor in self.decodeAndHandle(message) }
    }

    nonisolated func session(_ session: WCSession,
                             didReceiveMessage message: [String: Any],
                             replyHandler: @escaping ([String: Any]) -> Void) {
        Task { @MainActor in
            self.decodeAndHandle(message)
            replyHandler([:])
        }
    }

    nonisolated func session(_ session: WCSession,
                             didReceiveApplicationContext applicationContext: [String: Any]) {
        Task { @MainActor in self.decodeAndHandle(applicationContext) }
    }

    // Required no-op stubs for the iOS side of WCSessionDelegate (watch (de)activation / re-pair).
    nonisolated func sessionDidBecomeInactive(_ session: WCSession) {}
    nonisolated func sessionDidDeactivate(_ session: WCSession) {
        // Re-activate so a newly paired watch reconnects.
        session.activate()
    }
}
