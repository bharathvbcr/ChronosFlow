import Foundation

/// Pure notification userInfo → `chronosflow://` URL resolution (unit-testable; mirrors
/// `ChronosNotifications.deepLinkURL(from:)` in the app target).
public enum NotificationDeepLink {
    private static let secondaryRouteHosts: Set<String> = [
        "tasks", "habits", "goals", "medication", "routines", "sleep", "journal", "review", "settings",
    ]

    public static func resolveURL(from userInfo: [AnyHashable: Any]) -> URL? {
        if let raw = userInfo["deepLink"] as? String, let url = URL(string: raw) { return url }
        if let taskID = userInfo["taskID"] as? String {
            return URL(string: "chronosflow://tasks?id=\(taskID)")
        }
        if let planID = userInfo["planID"] as? String {
            return URL(string: "chronosflow://medication?id=\(planID)")
        }
        if let habitID = userInfo["habitID"] as? String {
            return URL(string: "chronosflow://habits?id=\(habitID)")
        }
        if let goalID = userInfo["goalID"] as? String {
            return URL(string: "chronosflow://goals?id=\(goalID)")
        }
        if let blockID = userInfo["blockID"] as? String {
            return URL(string: "chronosflow://focus?blockId=\(blockID)")
        }
        if let section = userInfo["section"] as? String, section == "sleep" {
            return URL(string: "chronosflow://sleep?log=1")
        }
        if let section = userInfo["section"] as? String, secondaryRouteHosts.contains(section) {
            return URL(string: "chronosflow://\(section)")
        }
        return nil
    }
}
