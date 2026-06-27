import Foundation

// MARK: - Focus command observer (Darwin → Foundation bridge)
//
// Completes the app-side half of the `FocusCommandBridge` hand-off (see WidgetIntents.swift).
// `FocusCommandBridge.post` writes a queued command to the App-Group store and fires a Darwin
// notification. The Focus UI already drains the queue on appear and on scene-activate; this observer
// additionally forwards that Darwin notification into a Foundation `Notification` so a Live Activity
// control tapped while the app is ALREADY foregrounded (sitting on the Focus tab) is applied
// immediately, without waiting for a tab-appear or foreground event.

enum FocusCommandObserver {
    /// Posted on the main thread whenever a cross-process focus command arrives.
    static let didReceive = Notification.Name("com.chronosflow.focus.command.didReceive")

    private static var registered = false

    /// Register the Darwin observer once. Idempotent — safe to call from `FocusView.onAppear`.
    @MainActor
    static func startIfNeeded() {
        guard !registered else { return }
        registered = true

        // C callback: must capture nothing. It only references global/static symbols, then bounces
        // to the main thread to post the Foundation notification SwiftUI observes.
        let callback: CFNotificationCallback = { _, _, _, _, _ in
            DispatchQueue.main.async {
                NotificationCenter.default.post(name: FocusCommandObserver.didReceive, object: nil)
            }
        }

        CFNotificationCenterAddObserver(
            CFNotificationCenterGetDarwinNotifyCenter(),
            nil,
            callback,
            (FocusCommandBridge.darwinName as String) as CFString,
            nil,
            .deliverImmediately)
    }
}
