import Foundation

// Shared-task import — the deterministic, cross-platform decomposition of a shared text blob (from
// another app's share sheet, a pasted multi-line list, or an attached .txt / .ics file) into
// individual task-title candidates. This is the iOS port of the Android
// `splitSharedTaskLines` / `extractVtodoSummaries` helpers in
// core/notifications/NotificationLaunchIntent.kt, kept byte-for-byte equivalent so the two
// platforms route the same shared content to the same place (a single candidate pre-fills the
// add-task sheet; 2+ candidates open the bulk-import review sheet).
//
// Pure Foundation; fully testable off-device.

/// Maximum number of task titles accepted from a single bulk import (mirrors Android
/// `MAX_BULK_IMPORT_TASKS`).
public let maxBulkImportTasks = 200

/// Splits a text blob (from a shared file or multi-line clipboard) into individual task-title
/// candidates. Handles two formats automatically, exactly like the Android `splitSharedTaskLines`:
/// - **ICS / VCALENDAR**: extracts SUMMARY lines from VTODO blocks only (VEVENTs are ignored).
/// - **Plain text**: one candidate per non-blank line, with common markdown / list bullet prefixes
///   ("- ", "* ", "+ ") stripped so a task list copied from a notes app arrives clean.
/// Results are trimmed, blanks dropped, and capped at ``maxBulkImportTasks``.
public func splitSharedTaskLines(_ text: String) -> [String] {
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

/// Extracts SUMMARY values from VTODO blocks in an ICS text. VEVENTs are ignored. Mirrors the
/// Android private `extractVtodoSummaries`.
private func extractVtodoSummaries(_ icsText: String) -> [String] {
    var summaries: [String] = []
    var inVtodo = false
    for rawLine in icsText.split(separator: "\n", omittingEmptySubsequences: false) {
        let trimmed = rawLine.trimmingCharacters(in: .whitespaces)
        switch true {
        case trimmed == "BEGIN:VTODO":
            inVtodo = true
        case trimmed == "END:VTODO":
            inVtodo = false
        case inVtodo && trimmed.hasPrefix("SUMMARY"):
            // SUMMARY may carry parameters (e.g. "SUMMARY;LANGUAGE=en:Buy milk"); split on the
            // first colon so only the value is kept. Mirrors Android's `indexOf(':')` handling.
            if let colon = trimmed.firstIndex(of: ":") {
                let value = trimmed[trimmed.index(after: colon)...]
                    .trimmingCharacters(in: .whitespaces)
                summaries.append(value)
            }
        default:
            break
        }
    }
    return Array(summaries.prefix(maxBulkImportTasks))
}
