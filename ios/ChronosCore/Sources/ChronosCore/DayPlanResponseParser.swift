import Foundation

// DayPlanResponseParser — tolerant, corrective parser for model day-plan JSON.
//
// Faithful port of `core/ai/.../genai/DayPlanResponseParser.kt`. Small on-device models often wrap
// their JSON in prose / markdown fences and emit trailing commas, so the parser:
//   1. extracts the outermost `{ … }` object from arbitrary surrounding text,
//   2. strips trailing commas (string-aware — never touches a comma inside a quoted value),
//   3. parses with Foundation's `JSONSerialization`, and
//   4. coerces each block with the same defaults/clamps as Android (`org.json` `optString/optInt`).
//
// Pure and deterministic. Block ids are minted via an injected `idProvider` (default a fresh UUID)
// so tests can pin them; Android uses `UUID.randomUUID()` here. Foundation only. Reuses
// `BlockFlexibility` from Enums.swift and the `StructuredDayPlanSuggestion` / `ProposedSuggestionBlock`
// / `BlockProvenance` types declared in LocalPlanningHeuristics.swift.

public enum DayPlanResponseParser {

    /// Parses a raw model reply into a structured plan, or `nil` when no usable plan can be recovered.
    /// Returns `nil` when there is no JSON object, when it can't be parsed, or when it yields zero
    /// blocks — exactly the cases Android returns `null` for (callers then fall back to heuristics).
    public static func parse(
        raw: String,
        timezone: String,
        fallbackExplanation: String,
        idProvider: () -> String = { UUID().uuidString }
    ) -> StructuredDayPlanSuggestion? {
        guard let extracted = extractJsonObject(raw) else { return nil }
        let jsonText = stripTrailingCommas(extracted)

        guard let data = jsonText.data(using: .utf8),
              let parsed = try? JSONSerialization.jsonObject(with: data),
              let root = parsed as? [String: Any]
        else { return nil }

        let blocksArray = (root["blocks"] as? [Any]) ?? []
        var blocks: [ProposedSuggestionBlock] = []
        for item in blocksArray {
            guard let object = item as? [String: Any] else { continue }
            blocks.append(toSuggestion(object, timezone: timezone, idProvider: idProvider))
        }
        if blocks.isEmpty { return nil }

        // `ifBlank` parity: treat whitespace-only strings as blank, like Kotlin's `String.ifBlank`.
        let reason = optString(root["reason"])
        let explanation = optString(root["explanation"])
        return StructuredDayPlanSuggestion(
            proposedBlocks: blocks,
            reason: reason.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "AI-generated day plan" : reason,
            conflictsResolved: stringList(root["conflictsResolved"]),
            requireConfirmation: true,
            explanation: explanation.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? fallbackExplanation : explanation
        )
    }

    // MARK: - Block coercion (parity with `JSONObject.toSuggestion`)

    private static func toSuggestion(
        _ object: [String: Any],
        timezone: String,
        idProvider: () -> String
    ) -> ProposedSuggestionBlock {
        let flexibilityRaw = optStringDefault(object["flexibility"], default: "MOVABLE")
        let flexibility = BlockFlexibility(rawValue: flexibilityRaw.lowercased()) ?? .movable
        let start = min(max(optInt(object["startMinuteOfDay"], default: 9 * 60), 0), 1439)
        let duration = min(max(optInt(object["durationMinutes"], default: 45), 5), 240)
        return ProposedSuggestionBlock(
            id: idProvider(),
            title: optStringDefault(object["title"], default: "Suggested block"),
            category: optStringDefault(object["category"], default: "WORK"),
            startMinuteOfDay: start,
            durationMinutes: duration,
            provenance: .aiSuggested,
            flexibility: flexibility,
            isLocked: flexibility == .fixed,
            isProtected: optBool(object["isProtected"], default: false),
            timezone: timezone
        )
    }

    // MARK: - org.json `optXxx` analogues

    /// Mirrors `org.json` `optString(name)` with empty-string default: returns "" for missing/null
    /// and stringifies numbers/bools the way org.json does.
    private static func optString(_ value: Any?) -> String {
        optStringDefault(value, default: "")
    }

    private static func optStringDefault(_ value: Any?, default fallback: String) -> String {
        guard let value, !(value is NSNull) else { return fallback }
        if let s = value as? String { return s }
        if let n = value as? NSNumber {
            // org.json renders integral numbers without a trailing ".0"; NSNumber.stringValue
            // already does the same (integers stringify without a fractional part).
            return n.stringValue
        }
        return String(describing: value)
    }

    /// Mirrors `optInt(name, default)`: accepts a JSON number or a numeric string, else the default.
    private static func optInt(_ value: Any?, default fallback: Int) -> Int {
        guard let value, !(value is NSNull) else { return fallback }
        if let n = value as? NSNumber { return n.intValue }
        if let s = value as? String, let parsed = Int(s.trimmingCharacters(in: .whitespaces)) { return parsed }
        if let s = value as? String, let parsed = Double(s.trimmingCharacters(in: .whitespaces)) { return Int(parsed) }
        return fallback
    }

    /// Mirrors `optBoolean(name, default)`: accepts a JSON bool or "true"/"false" string.
    private static func optBool(_ value: Any?, default fallback: Bool) -> Bool {
        guard let value, !(value is NSNull) else { return fallback }
        if let b = value as? Bool { return b }
        if let n = value as? NSNumber { return n.boolValue }
        if let s = value as? String {
            switch s.lowercased() {
            case "true": return true
            case "false": return false
            default: return fallback
            }
        }
        return fallback
    }

    /// Mirrors `JSONArray?.toStringList()`: drops blank entries; tolerates a missing/non-array value.
    private static func stringList(_ value: Any?) -> [String] {
        guard let array = value as? [Any] else { return [] }
        return array.compactMap { element -> String? in
            let s = optString(element)
            return s.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : s
        }
    }

    // MARK: - Pre-processing (string-aware trailing-comma strip + object extraction)

    /// Removes JSON trailing commas (`,` immediately before a `}` or `]`), which small on-device
    /// models often emit and which strict parsers reject. String-aware so a comma inside a quoted
    /// value (e.g. `"do x, then y"`) is never touched. Faithful port of `stripTrailingCommas`.
    static func stripTrailingCommas(_ json: String) -> String {
        let chars = Array(json)
        var out = String()
        out.reserveCapacity(chars.count)
        var inString = false
        var escaped = false
        var i = 0
        while i < chars.count {
            let c = chars[i]
            if inString {
                out.append(c)
                if escaped {
                    escaped = false
                } else if c == "\\" {
                    escaped = true
                } else if c == "\"" {
                    inString = false
                }
                i += 1
                continue
            }
            if c == "\"" {
                inString = true
                out.append(c)
                i += 1
                continue
            }
            if c == "," {
                var j = i + 1
                while j < chars.count, chars[j].isWhitespace { j += 1 }
                if j < chars.count, chars[j] == "}" || chars[j] == "]" {
                    i += 1 // drop the trailing comma
                    continue
                }
            }
            out.append(c)
            i += 1
        }
        return out
    }

    /// Extracts the outermost `{ … }` object from arbitrary text (prose, markdown fences). Returns the
    /// already-trimmed string when it is itself a `{…}` object, else the substring from the first `{`
    /// to the last `}`. `nil` when no object delimiters are found. Faithful port of `extractJsonObject`.
    static func extractJsonObject(_ raw: String) -> String? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.hasPrefix("{") && trimmed.hasSuffix("}") { return trimmed }
        guard let start = trimmed.firstIndex(of: "{"),
              let end = trimmed.lastIndex(of: "}"),
              start < end
        else { return nil }
        return String(trimmed[start...end])
    }
}
