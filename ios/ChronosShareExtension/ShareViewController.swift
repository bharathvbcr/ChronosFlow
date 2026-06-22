import UIKit
import Social
import UniformTypeIdentifiers

/// Share Extension principal class. Receives shared text / URLs / files (.txt, .ics) from other
/// apps and writes the resulting task-title candidates to the App Group UserDefaults so the main
/// ChronosFlow app can pick them up on next launch or foreground.
///
/// This is the iOS analogue of Android's share-to-task route (ACTION_SEND / ACTION_SEND_MULTIPLE /
/// PROCESS_TEXT → parseSharedTextLaunch + splitSharedTaskLines):
/// - A **single** title pre-fills the add-task sheet (`share.pendingText`, deep-link
///   `chronosflow://add-task`).
/// - **2+** titles open the bulk-import review sheet (`share.pendingBulkTasks`, routed to the Tasks
///   tab via `chronosflow://tasks`, where `TasksView` drains them into `TaskBulkImportSheet`).
///
/// The decomposition logic (multi-line text, .txt blobs, .ics VTODO SUMMARY parsing, the 200-item
/// cap) is the canonical, cross-platform `ChronosCore.splitSharedTaskLines`. The share extension
/// target is intentionally NOT linked against the SwiftData-bearing app code, so the same algorithm
/// is mirrored here in `splitSharedTaskLines(_:)` — kept byte-for-byte equivalent to the ChronosCore
/// implementation (which carries the shared XCTest). The main-app bulk-import path imports the
/// ChronosCore version directly.
class ShareViewController: UIViewController {

    private static let appGroup = "group.com.chronosflow.shared"
    /// Mirrors `ChronosCore.maxBulkImportTasks` / Android `MAX_BULK_IMPORT_TASKS`.
    private static let maxBulkImportTasks = 200

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        collectSharedText { [weak self] text, sourceApp in
            guard let self else { return }
            let candidates = text.map { Self.splitSharedTaskLines($0) } ?? []
            self.persistAndOpen(candidates: candidates, sourceApp: sourceApp)
            self.extensionContext?.completeRequest(returningItems: nil)
        }
    }

    // MARK: - Decomposition (mirror of ChronosCore.splitSharedTaskLines)

    /// Splits a shared text blob into task-title candidates. ICS / VCALENDAR → VTODO SUMMARY values;
    /// otherwise one candidate per non-blank line with leading "- " / "* " / "+ " bullets stripped.
    /// Capped at ``maxBulkImportTasks``. Kept identical to `ChronosCore.splitSharedTaskLines`.
    static func splitSharedTaskLines(_ text: String) -> [String] {
        if text.contains("BEGIN:VCALENDAR") {
            return extractVtodoSummaries(text)
        }
        return text
            .split(separator: "\n", omittingEmptySubsequences: false)
            .map { line -> String in
                var trimmed = line.trimmingCharacters(in: .whitespaces)
                for prefix in ["- ", "* ", "+ "] where trimmed.hasPrefix(prefix) {
                    trimmed = String(trimmed.dropFirst(prefix.count))
                    break
                }
                return trimmed.trimmingCharacters(in: .whitespaces)
            }
            .filter { !$0.isEmpty }
            .prefix(maxBulkImportTasks)
            .map { $0 }
    }

    /// Extracts SUMMARY values from VTODO blocks in an ICS text. VEVENTs are ignored.
    private static func extractVtodoSummaries(_ icsText: String) -> [String] {
        var summaries: [String] = []
        var inVtodo = false
        for rawLine in icsText.split(separator: "\n", omittingEmptySubsequences: false) {
            let trimmed = rawLine.trimmingCharacters(in: .whitespaces)
            if trimmed == "BEGIN:VTODO" {
                inVtodo = true
            } else if trimmed == "END:VTODO" {
                inVtodo = false
            } else if inVtodo && trimmed.hasPrefix("SUMMARY") {
                if let colon = trimmed.firstIndex(of: ":") {
                    summaries.append(trimmed[trimmed.index(after: colon)...]
                        .trimmingCharacters(in: .whitespaces))
                }
            }
        }
        return Array(summaries.prefix(maxBulkImportTasks))
    }

    // MARK: - Persist + route

    /// Routes the parsed candidates to the right entry point: nothing → just close; one → add-sheet
    /// pre-fill; many → bulk-import review sheet. Clears the opposite key so a stale single share
    /// can't shadow a fresh bulk one and vice-versa.
    private func persistAndOpen(candidates: [String], sourceApp: String?) {
        let defaults = UserDefaults(suiteName: Self.appGroup)
        defaults?.removeObject(forKey: "share.pendingText")
        defaults?.removeObject(forKey: "share.pendingBulkTasks")

        guard !candidates.isEmpty else { return }
        let now = Date().timeIntervalSince1970
        defaults?.set(now, forKey: "share.pendingTimestamp")
        // Provenance label (Android "Shared from [App Name]" chip). nil when unknown.
        if let sourceApp, !sourceApp.isEmpty {
            defaults?.set(sourceApp, forKey: "share.sourceApp")
        } else {
            defaults?.removeObject(forKey: "share.sourceApp")
        }

        let deepLink: String
        if candidates.count == 1 {
            defaults?.set(candidates[0], forKey: "share.pendingText")
            deepLink = "chronosflow://add-task"
        } else {
            defaults?.set(candidates, forKey: "share.pendingBulkTasks")
            // Route to the Tasks tab; TasksView drains `share.pendingBulkTasks` on appear and
            // presents the bulk-import review sheet. (RootView's deep-link switch handles `tasks`;
            // a dedicated bulk host would require a RootView change, which TasksView avoids by
            // owning the pending-bulk drain itself.)
            deepLink = "chronosflow://tasks"
        }
        defaults?.synchronize()

        if let url = URL(string: deepLink) { openHostApp(url) }
    }

    /// Opens the host app by walking the responder chain to a `UIApplication` (the supported way for
    /// an app extension to launch its container app).
    private func openHostApp(_ url: URL) {
        var responder: UIResponder? = self
        while responder != nil {
            if let application = responder as? UIApplication {
                application.open(url, options: [:], completionHandler: nil)
                return
            }
            responder = responder?.next
        }
    }

    // MARK: - Extraction

    /// Gathers shared content from every attachment across all input items and concatenates it into
    /// one text blob (so SEND_MULTIPLE / multi-attachment shares are all considered), then hands it
    /// plus the source-app label to `completion`. Supported, in priority order per attachment:
    /// .ics file → .txt / file URL → plain text → URL.
    private func collectSharedText(completion: @escaping (String?, String?) -> Void) {
        guard let items = extensionContext?.inputItems as? [NSExtensionItem], !items.isEmpty else {
            completion(nil, nil); return
        }
        // Some apps put a human-readable source name on the item's attributedTitle / contentText.
        let sourceApp = sourceAppLabel(from: items)

        var providers: [NSItemProvider] = []
        for item in items { providers.append(contentsOf: item.attachments ?? []) }
        guard !providers.isEmpty else { completion(nil, sourceApp); return }

        let group = DispatchGroup()
        // Preserve attachment order so bulk lines stay in the user's original order.
        var pieces = [String?](repeating: nil, count: providers.count)

        for (index, provider) in providers.enumerated() {
            group.enter()
            loadText(from: provider) { piece in
                pieces[index] = piece
                group.leave()
            }
        }

        group.notify(queue: .main) {
            let combined = pieces.compactMap { $0 }
                .filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
                .joined(separator: "\n")
            completion(combined.isEmpty ? nil : combined, sourceApp)
        }
    }

    /// Loads one attachment as text. Files (.ics / .txt / any file URL) are read from disk (bounded);
    /// plain text and URLs fall back to their string value. Calls `completion` exactly once.
    private func loadText(from provider: NSItemProvider, completion: @escaping (String?) -> Void) {
        let calendarType = UTType("public.calendar") ?? UTType.data

        if provider.hasItemConformingToTypeIdentifier(UTType.fileURL.identifier) {
            provider.loadItem(forTypeIdentifier: UTType.fileURL.identifier) { data, _ in
                let url = (data as? URL) ?? (data as? Data).flatMap { URL(dataRepresentation: $0, relativeTo: nil) }
                DispatchQueue.main.async { completion(url.flatMap(Self.readFile)) }
            }
            return
        }
        if provider.hasItemConformingToTypeIdentifier(UTType.text.identifier)
            || provider.hasItemConformingToTypeIdentifier(calendarType.identifier) {
            // Calendar / text payloads may arrive as a Data blob or a String.
            let identifier = provider.hasItemConformingToTypeIdentifier(calendarType.identifier)
                ? calendarType.identifier : UTType.text.identifier
            provider.loadItem(forTypeIdentifier: identifier) { data, _ in
                DispatchQueue.main.async { completion(Self.coerceToText(data)) }
            }
            return
        }
        if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
            provider.loadItem(forTypeIdentifier: UTType.plainText.identifier) { data, _ in
                DispatchQueue.main.async { completion(Self.coerceToText(data)) }
            }
            return
        }
        if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
            provider.loadItem(forTypeIdentifier: UTType.url.identifier) { data, _ in
                DispatchQueue.main.async { completion((data as? URL)?.absoluteString) }
            }
            return
        }
        completion(nil)
    }

    /// Reads a shared file (a security-scoped resource from another app) as UTF-8 text, bounded to
    /// the same ceiling Android applies (`SHARED_IMPORT_MAX_CHARS`-class guard) so a malicious or huge
    /// attachment can't blow up the extension. Returns nil on any failure.
    private static func readFile(_ url: URL) -> String? {
        let needsScope = url.startAccessingSecurityScopedResource()
        defer { if needsScope { url.stopAccessingSecurityScopedResource() } }
        guard let data = try? Data(contentsOf: url) else { return nil }
        // Bound the read (200 KB ≈ well past 200 short task lines / a normal .ics export).
        let bounded = data.count > 200_000 ? data.prefix(200_000) : data[...]
        return String(data: Data(bounded), encoding: .utf8)
            ?? String(data: Data(bounded), encoding: .isoLatin1)
    }

    /// Coerces an attachment payload (String, Data, or NSAttributedString) into plain text.
    private static func coerceToText(_ data: NSSecureCoding?) -> String? {
        switch data {
        case let s as String: return s
        case let d as Data: return String(data: d, encoding: .utf8)
        case let a as NSAttributedString: return a.string
        case let u as URL: return u.absoluteString
        default: return nil
        }
    }

    /// Best-effort source-app label for the "Shared from …" provenance chip (Android parity). Apps
    /// don't reliably expose their name to a share extension; we use the item's `attributedTitle`
    /// (the share-sheet subject) when present, which many apps set to a recognisable label.
    private func sourceAppLabel(from items: [NSExtensionItem]) -> String? {
        for item in items {
            if let title = item.attributedTitle?.string.trimmingCharacters(in: .whitespacesAndNewlines),
               !title.isEmpty {
                return title
            }
        }
        return nil
    }
}
