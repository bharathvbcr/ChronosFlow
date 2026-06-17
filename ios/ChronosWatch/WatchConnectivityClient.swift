import Foundation
import Observation
import WatchConnectivity
import WidgetKit

/// The watch half of the cross-device Data Layer (the iOS-native analogue of the Android Wear
/// `DataClient`/`MessageClient` watch side). It activates a `WCSession`, receives the phone's
/// `WatchSnapshot` (via application-context for latest-state and via live messages when reachable),
/// caches it to local `UserDefaults` for cold launch, and sends `WatchCommand` quick actions back.
///
/// Apple Watch and iPhone do NOT share an App-Group container, so this is the ONLY channel — the
/// watch never opens the phone's SwiftData store.
@Observable
@MainActor
final class WatchConnectivityClient: NSObject, WCSessionDelegate {
    /// Latest snapshot from the phone (or the cold-launch cached copy). `nil` until the first sync.
    private(set) var snapshot: WatchSnapshot?

    /// Whether the phone is currently reachable (live messaging available).
    private(set) var isPhoneReachable = false

    /// Cache into the on-watch App Group (shared with the watch's WidgetKit extension on the same
    /// device) so the complication / Smart Stack widget renders the latest snapshot. Falls back to
    /// `.standard` if the group container isn't provisioned.
    private let defaults = UserDefaults(suiteName: WatchSyncPayload.watchAppGroup) ?? .standard
    private var cacheKey: String { WatchSyncPayload.cacheKey }

    private var session: WCSession? {
        WCSession.isSupported() ? .default : nil
    }

    override init() {
        super.init()
        loadCachedSnapshot()
    }

    /// Activate the session. Call once when the app launches.
    func activate() {
        guard let session else { return }
        session.delegate = self
        if session.activationState != .activated {
            session.activate()
        }
    }

    // MARK: Commands (watch → phone)

    /// Send a quick action to the phone. Uses `sendMessage` when reachable (instant) and otherwise
    /// `transferUserInfo` (queued, guaranteed FIFO delivery when the phone next wakes).
    func send(_ command: WatchCommand) {
        guard let session, session.activationState == .activated,
              let data = try? JSONEncoder().encode(command) else { return }
        let payload: [String: Any] = [WatchSyncPayload.command: data]

        applyOptimistically(command)

        if session.isReachable {
            session.sendMessage(payload, replyHandler: nil, errorHandler: { [weak self] _ in
                // Fall back to the queued channel if the live send fails.
                Task { @MainActor in self?.queue(payload) }
            })
        } else {
            queue(payload)
        }
    }

    private func queue(_ payload: [String: Any]) {
        session?.transferUserInfo(payload)
    }

    /// Reflect the action locally right away so the UI feels instant; the phone's authoritative
    /// snapshot follows and overwrites this when it arrives.
    private func applyOptimistically(_ command: WatchCommand) {
        guard var snap = snapshot else { return }
        switch command {
        case .completeTask(let id):
            snap.tasks.removeAll { $0.id == id }
        case .toggleHabit(let id):
            if let i = snap.habits.firstIndex(where: { $0.id == id }) {
                snap.habits[i].doneToday.toggle()
            }
        case .togglePauseFocus:
            snap.focus?.isPaused.toggle()
        case .stopFocus:
            snap.focus = nil
        case .markDose, .startFocus:
            break // no local snapshot field to flip; wait for the phone echo
        }
        snapshot = snap
    }

    // MARK: Snapshot intake (phone → watch)

    private func ingest(_ payload: [String: Any]) {
        guard let data = payload[WatchSyncPayload.snapshot] as? Data,
              let snap = try? JSONDecoder().decode(WatchSnapshot.self, from: data) else { return }
        snapshot = snap
        defaults.set(data, forKey: cacheKey) // persist for cold launch + the widget extension
        // Refresh the watch complication / Smart Stack widget with the new snapshot.
        WidgetCenter.shared.reloadAllTimelines()
    }

    private func loadCachedSnapshot() {
        guard let data = defaults.data(forKey: cacheKey),
              let snap = try? JSONDecoder().decode(WatchSnapshot.self, from: data) else { return }
        snapshot = snap
    }

    // MARK: WCSessionDelegate (callbacks arrive off the main actor — hop back)

    nonisolated func session(_ session: WCSession,
                             activationDidCompleteWith activationState: WCSessionActivationState,
                             error: Error?) {
        Task { @MainActor in self.isPhoneReachable = session.isReachable }
    }

    nonisolated func sessionReachabilityDidChange(_ session: WCSession) {
        Task { @MainActor in self.isPhoneReachable = session.isReachable }
    }

    nonisolated func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        Task { @MainActor in self.ingest(message) }
    }

    nonisolated func session(_ session: WCSession,
                             didReceiveApplicationContext applicationContext: [String: Any]) {
        Task { @MainActor in self.ingest(applicationContext) }
    }
}
