import Foundation

// Insights recommendations — Foundation-only port of core/ai/InsightsRecommendationsPlanner.kt.
// Produces the short list of "AI recommendations" shown on the Insights page. The heuristic
// `localRecommendations` is the deterministic fallback (and the offline path); `suggest` layers an
// optional GenAI generation on top via an injected text generator, parsing its output the same way
// Android does (one `recommendation|reason` line each, kept to the first three).
//
// Mapping from Android types: `DailyReviewSummary?` -> `PeriodSummary?`, `List<ReviewInsight>` ->
// `[ReviewFinding]`, `CompanionTrendContext` -> `CompanionTrendSections`, `AssistGenAiSource` ->
// `AssistGenAiSource` (ported below). Everything is pure: no wall-clock reads, no I/O.

// MARK: - Source

// `AssistGenAiSource` (LOCAL / GEMINI_NANO / CLOUD_GEMINI) is defined in LocalPlanningHeuristics.swift
// and shared across the planning surfaces. We reuse it here; this extension adds the stored-name
// parsing Android does when reviving a persisted source label.
extension AssistGenAiSource {
    /// Parse a stored source name, falling back to `.local` for unknown values. Mirrors Android's
    /// `runCatching { AssistGenAiSource.valueOf(name) }.getOrNull() ?: LOCAL`.
    public init(storedName: String?) {
        if let name = storedName, let parsed = AssistGenAiSource(rawValue: name) {
            self = parsed
        } else {
            self = .local
        }
    }
}

// MARK: - Recommendation

/// A single Insights recommendation: the user-facing `text` plus the `source` that produced it.
/// Mirrors Android's `InsightRecommendation`.
public struct InsightRecommendation: Sendable, Equatable, Identifiable {
    public let text: String
    public let source: AssistGenAiSource

    public var id: String { "\(source.rawValue)|\(text)" }

    public init(text: String, source: AssistGenAiSource) {
        self.text = text
        self.source = source
    }
}

// MARK: - Generator

/// Result of an optional GenAI text generation: the raw `text` (nil when unavailable) and the
/// `source` it would be attributed to. Mirrors Android's `AssistTextGeneration`.
public struct AssistTextGeneration: Sendable, Equatable {
    public let text: String?
    public let source: AssistGenAiSource

    public init(text: String?, source: AssistGenAiSource) {
        self.text = text
        self.source = source
    }
}

/// Pluggable async text generator (the iOS app injects a Gemini/Firebase-backed implementation; on
/// Windows / in tests a stub is used). Sendable so it crosses actor boundaries safely.
public protocol AssistTextGenerator: Sendable {
    func generateAssistText(prompt: String) async -> AssistTextGeneration
}

// MARK: - Planner

/// Generates Insights recommendations. The local heuristic is the source of truth and is fully
/// deterministic; `suggest` optionally enriches it with a GenAI generation when a generator is
/// supplied. Port of `InsightsRecommendationsPlanner`.
public struct InsightsRecommendationsPlanner: Sendable {
    private let generator: AssistTextGenerator?

    /// - Parameter generator: optional GenAI text generator. When nil, `suggest` returns the local
    ///   baseline (matching Android's behavior when the summary is null or generation is empty).
    public init(generator: AssistTextGenerator? = nil) {
        self.generator = generator
    }

    /// Top-level entry point. Builds the local baseline, then — only when a `summary` and a
    /// `generator` are available — asks the generator for up to three lines and parses them; an
    /// empty/blank generation falls back to the baseline. Capped to three. Mirrors `suggest`.
    public func suggest(
        summary: PeriodSummary?,
        insights: [ReviewFinding],
        trends: CompanionTrendSections = CompanionTrendSections()
    ) async -> [InsightRecommendation] {
        let baseline = localRecommendations(summary: summary, insights: insights, trends: trends)
        guard let summary, let generator else { return baseline }
        let generation = await generator.generateAssistText(
            prompt: buildPrompt(summary: summary, insights: insights, baseline: baseline, trends: trends)
        )
        let parsed = generation.text.map { parseRecommendations($0, source: generation.source) } ?? []
        return Array((parsed.isEmpty ? baseline : parsed).prefix(3))
    }

    /// The deterministic, Foundation-only heuristic. Priority order mirrors Android exactly:
    /// 1. Existing insights (take up to three, mapped to their source).
    /// 2. No summary -> a single "unlock recommendations" hint.
    /// 3. Otherwise derive from drift / missed / peak-energy / med-misses / habit dip, falling back
    ///    to a "keep the rhythm" line when nothing fired. Capped to three.
    public func localRecommendations(
        summary: PeriodSummary?,
        insights: [ReviewFinding],
        trends: CompanionTrendSections = CompanionTrendSections()
    ) -> [InsightRecommendation] {
        if !insights.isEmpty {
            // iOS `ReviewFinding`s are produced by the deterministic local heuristics, so they map
            // to `.local`. (Android keys off `ReviewInsight.assistSource`; the iOS findings model
            // carries no such field — every finding here is local.)
            return insights.prefix(3).map { finding in
                InsightRecommendation(text: finding.title, source: .local)
            }
        }
        guard let summary else {
            return [InsightRecommendation(
                text: "Complete a focus session or end-of-day review to unlock recommendations.",
                source: .local)]
        }

        var result: [InsightRecommendation] = []
        if summary.driftMinutes >= 60 {
            result.append(InsightRecommendation(
                text: "Protect fewer priorities tomorrow after \(summary.driftMinutes)m of drift.",
                source: .local))
        }
        if summary.missedMinutes >= 30 {
            result.append(InsightRecommendation(
                text: "Recover \(summary.missedMinutes)m of missed plan with a single catch-up block.",
                source: .local))
        }
        if let hour = trends.peakEnergyHour {
            result.append(InsightRecommendation(
                text: "Energy usually peaks around \(twoDigit(hour)):00 — protect tomorrow's hardest block there.",
                source: .local))
        }
        if trends.medicationMissedTotal >= 3 {
            result.append(InsightRecommendation(
                text: "\(trends.medicationMissedTotal) medication doses slipped in the last two weeks — review reminder times.",
                source: .local))
        }
        if let delta = trends.habitWeekOverWeekDelta, delta < 0 {
            result.append(InsightRecommendation(
                text: "Habit completions dipped vs the prior week — keep tomorrow's habit windows light.",
                source: .local))
        }
        if result.isEmpty {
            result.append(InsightRecommendation(
                text: "Keep today's rhythm and promote the strongest completed block into tomorrow's anchor.",
                source: .local))
        }
        return Array(result.prefix(3))
    }

    // MARK: - Prompt

    /// Builds the generation prompt from the metrics, supplied trends, insights, and baseline.
    /// Lines and wording match Android's `buildPrompt` so a shared model behaves identically.
    func buildPrompt(
        summary: PeriodSummary,
        insights: [ReviewFinding],
        baseline: [InsightRecommendation],
        trends: CompanionTrendSections
    ) -> String {
        var lines: [String] = []
        lines.append("Write up to three schedule recommendations for ChronosFlow insights.")
        lines.append("Return one recommendation per line as recommendation|reason.")
        lines.append("Use only supplied metrics. No invented tasks or medical advice.")
        lines.append("Planned minutes: \(summary.plannedMinutes)")
        lines.append("Actual minutes: \(summary.actualMinutes)")
        lines.append("Missed minutes: \(summary.missedMinutes)")
        lines.append("Drift minutes: \(summary.driftMinutes)")
        if let hour = trends.peakEnergyHour {
            lines.append("Peak energy hour over the last 14 days: \(hour):00")
        }
        if !trends.habitCompletion.isEmpty {
            lines.append("Habit completions last 7 days: \(trends.habitCompletedLastWeek); " +
                "prior 7 days: \(trends.habitCompletedPriorWeek)")
        }
        if !trends.medicationAdherence.isEmpty {
            lines.append("Medication doses last 14 days: \(trends.medicationTakenTotal) taken, " +
                "\(trends.medicationMissedTotal) missed")
        }
        for finding in insights.prefix(5) {
            lines.append("Insight: \(finding.title) — \(finding.detail)")
        }
        for recommendation in baseline {
            lines.append("Baseline: \(recommendation.text)")
        }
        // Android uses appendLine on every line, so the prompt ends with a trailing newline.
        return lines.map { $0 + "\n" }.joined()
    }

    /// Parse a generation into recommendations: trim each line (and leading list bullets), drop
    /// blanks, split on the first `|` to take the recommendation half, and keep the first three.
    /// Mirrors Android's `parseRecommendations`.
    func parseRecommendations(_ text: String, source: AssistGenAiSource) -> [InsightRecommendation] {
        let bullets = CharacterSet(charactersIn: "-*")
        var out: [InsightRecommendation] = []
        for rawLine in text.split(separator: "\n", omittingEmptySubsequences: false) {
            let trimmed = String(rawLine)
                .trimmingCharacters(in: .whitespaces)
                .trimmingCharacters(in: bullets)
                .trimmingCharacters(in: .whitespaces)
            if trimmed.isEmpty { continue }
            let recommendation = trimmed
                .split(separator: "|", maxSplits: 1, omittingEmptySubsequences: false)
                .first
                .map { $0.trimmingCharacters(in: .whitespaces) } ?? ""
            if recommendation.isEmpty { continue }
            out.append(InsightRecommendation(text: recommendation, source: source))
            if out.count == 3 { break }
        }
        return out
    }
}

/// Zero-padded two-digit string for an hour, matching Android's `%02d` formatting.
private func twoDigit(_ value: Int) -> String {
    value >= 0 && value < 10 ? "0\(value)" : "\(value)"
}
