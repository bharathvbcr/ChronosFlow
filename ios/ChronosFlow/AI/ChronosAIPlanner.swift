import Foundation
import FoundationModels
import ChronosCore

// MARK: - On-device AI planner
//
// The iOS-native equivalent of the Android Gemini Nano stack (ChronosAIPlanner + MlKitGeminiNanoGateway).
// On iOS this is Apple Intelligence's Foundation Models framework: a `LanguageModelSession` over the
// on-device system model, with `@Generable` guided generation giving us typed, schema-valid output —
// the structural analogue of the Android "corrective JSON retry" planner. Everything runs on device.

/// A structured plan suggestion the model returns. `@Generable` forces schema-valid output.
@Generable
struct AIPlanSuggestion {
    @Guide(description: "A short, encouraging one-line summary of the suggested plan.")
    var summary: String

    @Guide(description: "Between 1 and 6 suggested time blocks for the day.")
    var blocks: [AISuggestedBlock]
}

@Generable
struct AISuggestedBlock {
    @Guide(description: "A concise title, e.g. 'Deep work', 'Lunch', 'Workout'.")
    var title: String

    @Guide(description: "One of: FOCUS, WORK, STUDY, BREAK, MEAL, ROUTINE, EXERCISE.")
    var category: String

    @Guide(description: "Start time as minute-of-day, an integer from 0 to 1439 (e.g. 540 = 9:00 AM).")
    var startMinuteOfDay: Int

    @Guide(description: "Duration in minutes, an integer from 15 to 240.")
    var durationMinutes: Int

    @Guide(description: "One short sentence explaining why this block was placed here.")
    var rationale: String
}

@MainActor
@Observable
final class ChronosAIPlanner {
    enum State: Equatable {
        case unavailable(String)
        case ready
        case thinking
        case done
        case failed(String)
    }

    var state: State = .ready
    var suggestion: AIPlanSuggestion?

    private var session: LanguageModelSession?

    init() {
        switch SystemLanguageModel.default.availability {
        case .available:
            state = .ready
        case .unavailable(.deviceNotEligible):
            state = .unavailable("This device doesn't support Apple Intelligence.")
        case .unavailable(.appleIntelligenceNotEnabled):
            state = .unavailable("Turn on Apple Intelligence in Settings to use AI planning.")
        case .unavailable(.modelNotReady):
            state = .unavailable("The on-device model is still downloading. Try again shortly.")
        case .unavailable:
            state = .unavailable("On-device AI is unavailable right now.")
        }
    }

    var isAvailable: Bool { if case .unavailable = state { false } else { true } }

    /// Generate a balanced day plan around the existing fixed blocks and free windows.
    /// Mirrors the Android "generate a balanced day plan / find open deep-work windows" intents.
    ///
    /// Privacy mode (N01 / Android parity: `PrivacyMode` ON_DEVICE_ONLY / CLOUD_ALLOWED / DISABLED):
    ///   - `.disabled` — no generation at all; we surface the local-only message and stop.
    ///   - `.cloudAllowed` — structural placeholder for a future cloud provider. iOS has no cloud
    ///     gateway yet (Foundation Models is on-device), so this falls back to the on-device path
    ///     exactly like `.onDeviceOnly`. When a cloud route graduates it slots in ahead of the
    ///     on-device attempt here.
    ///   - `.onDeviceOnly` — Foundation Models on-device.
    ///
    /// Layered generation (mirrors the Android cloud→Nano→local cascade, minus the cloud rung):
    ///   1. Foundation Models guided `@Generable` generation (typed, schema-valid).
    ///   2. If that throws / the model is unavailable, a raw-text prompt assembled by ChronosCore's
    ///      `PlanningPromptBuilder`, parsed with the tolerant `DayPlanResponseParser` (with one
    ///      `jsonRepairPrompt` corrective re-ask — the iOS analogue of the Android corrective retry).
    ///   3. If everything on-device fails, ChronosCore's deterministic `LocalPlanningHeuristics`.
    func generatePlan(date: Date, existingBlocks: [TimeBlock], readiness: SleepReadiness) async {
        let mode = ChronosSettings.shared.privacyMode
        guard mode.allowsOnDeviceGeneration else {
            // DISABLED: no generation. Mirror Android's "AI off" — the planner produces nothing.
            state = .unavailable("AI planning is turned off. Enable it in Settings to suggest a plan.")
            suggestion = nil
            return
        }

        state = .thinking
        suggestion = nil

        // 1 & 2: on-device generation, only when the model is actually available.
        if isAvailable {
            // CLOUD_ALLOWED structural placeholder: a cloud attempt would go here, ahead of the
            // on-device session. iOS has no cloud gateway, so we proceed straight to on-device.
            if let generated = await generateOnDevice(
                date: date, existingBlocks: existingBlocks, readiness: readiness
            ) {
                suggestion = generated
                state = .done
                return
            }
        }

        // 3: deterministic offline fallback (model unavailable, or both on-device paths failed).
        suggestion = localFallbackSuggestion(date: date, existingBlocks: existingBlocks)
        state = .done
    }

    /// On-device generation: guided `@Generable` first, then a `PlanningPromptBuilder` text prompt
    /// run through `DayPlanResponseParser` with a single corrective re-ask. Returns `nil` when both
    /// on-device attempts fail so the caller can drop to local heuristics.
    private func generateOnDevice(
        date: Date, existingBlocks: [TimeBlock], readiness: SleepReadiness
    ) async -> AIPlanSuggestion? {
        let instructions = Instructions {
            """
            You are ChronosFlow's day planner. You suggest a calm, balanced 24-hour plan as time \
            blocks. Respect the user's existing fixed blocks — never overlap them. Place demanding \
            FOCUS/STUDY work in the user's free windows during high-energy hours. Always include \
            breaks and a meal. Keep suggestions realistic and humane.
            """
        }
        let session = LanguageModelSession(instructions: instructions)
        self.session = session

        let busy = existingBlocks
            .sorted { $0.startMinuteOfDay < $1.startMinuteOfDay }
            .map { "\($0.title) [\($0.category)] \($0.startMinuteOfDay.clockTime)-\($0.plannedEndMinuteOfDay.clockTime)" }
            .joined(separator: "; ")
        let free = PlannerMath.freeWindows(in: existingBlocks)
            .map { "\($0.startMinute.clockTime) for \($0.durationMinutes)m" }
            .joined(separator: "; ")
        let readinessHint = switch readiness {
            case .depleted: "The user slept poorly — favor lighter tasks and earlier, more frequent breaks."
            case .rested: "The user is well rested — a good day for demanding deep work."
            default: "Sleep readiness is normal."
        }

        let prompt = """
        Existing fixed blocks: \(busy.isEmpty ? "none" : busy).
        Free windows: \(free.isEmpty ? "none" : free).
        \(readinessHint)
        Suggest time blocks that fill the free windows without overlapping the fixed blocks.
        """

        let temperature = ChronosSettings.shared.plannerProfile.temperature

        // Attempt 1: guided generation — typed and schema-valid, no parsing needed.
        do {
            let response = try await session.respond(
                to: prompt,
                generating: AIPlanSuggestion.self,
                options: GenerationOptions(temperature: temperature)
            )
            return response.content
        } catch {
            // Fall through to the text + corrective-parse path below.
        }

        // Attempt 2: raw-text prompt via ChronosCore's PlanningPromptBuilder, parsed by
        // DayPlanResponseParser, with one jsonRepairPrompt corrective re-ask.
        return await generateViaTextParse(
            date: date, existingBlocks: existingBlocks, readiness: readiness, temperature: temperature)
    }

    /// Raw-text generation + tolerant parse. Builds the prompt with `PlanningPromptBuilder`, asks the
    /// model for plain text, and recovers a plan with `DayPlanResponseParser`. On a parse miss it does
    /// one corrective re-ask (`jsonRepairPrompt`) before giving up — the iOS analogue of the Android
    /// corrective JSON retry. Returns `nil` if no usable plan survives (caller drops to heuristics).
    private func generateViaTextParse(
        date: Date, existingBlocks: [TimeBlock], readiness: SleepReadiness, temperature: Double
    ) async -> AIPlanSuggestion? {
        let calendar = Calendar.current
        let timezone = calendar.timeZone.identifier
        let existing = existingBlocks.map {
            PlanExistingBlock(id: $0.id,
                              startMinuteOfDay: $0.startMinuteOfDay,
                              durationMinutes: $0.durationMinutes)
        }
        let existingLabels = existingBlocks
            .sorted { $0.startMinuteOfDay < $1.startMinuteOfDay }
            .map { "- \($0.title) (\($0.category)) \($0.startMinuteOfDay)-\($0.plannedEndMinuteOfDay)m" }
        let preferences = readinessPreference(readiness)

        let prompt = PlanningPromptBuilder.dayPlanPrompt(
            userPreferences: preferences,
            date: date,
            calendar: calendar,
            timezone: timezone,
            review: nil,
            existingBlocks: existing,
            existingBlockLabels: existingLabels
        )
        let fallbackExplanation = "ChronosFlow generated this on-device. Review every suggestion before applying it."

        let session = LanguageModelSession()
        let options = GenerationOptions(temperature: temperature)

        guard let raw = try? await session.respond(to: prompt, options: options).content else {
            return nil
        }
        if let parsed = DayPlanResponseParser.parse(
            raw: raw, timezone: timezone, fallbackExplanation: fallbackExplanation) {
            return suggestion(from: parsed)
        }

        // Corrective re-ask: echo the prompt + the malformed reply and demand bare JSON.
        let repairPrompt = PlanningPromptBuilder.jsonRepairPrompt(
            originalPrompt: prompt, malformedResponse: raw)
        guard let repaired = try? await session.respond(to: repairPrompt, options: options).content,
              let parsed = DayPlanResponseParser.parse(
                raw: repaired, timezone: timezone, fallbackExplanation: fallbackExplanation)
        else { return nil }
        return suggestion(from: parsed)
    }

    /// Deterministic, model-free plan from ChronosCore's `LocalPlanningHeuristics`. Used when the
    /// on-device model is unavailable or both generation attempts fail (Android parity: the local
    /// heuristic fallback when Gemini is unavailable).
    private func localFallbackSuggestion(date: Date, existingBlocks: [TimeBlock]) -> AIPlanSuggestion {
        let calendar = Calendar.current
        let structured = LocalPlanningHeuristics.generateIdealDayPlan(
            packageName: "com.ChronosFlow.VBCR",
            userPreferences: "balanced day",
            date: date,
            calendar: calendar,
            currentTimeZone: calendar.timeZone.identifier
        )
        return suggestion(from: structured)
    }

    /// Maps a free-text planning preference from the night's sleep readiness, so the offline prompt
    /// nudges the same way the guided prompt's `readinessHint` does.
    private func readinessPreference(_ readiness: SleepReadiness) -> String {
        switch readiness {
        case .depleted: "recovery, low energy"
        case .rested: "balanced day with deep work"
        default: "balanced day"
        }
    }

    /// Converts a ChronosCore `StructuredDayPlanSuggestion` into the UI-facing `AIPlanSuggestion`.
    /// The `materialize` step re-clamps and re-checks overlaps, so this is a straight field map.
    private func suggestion(from structured: ChronosCore.StructuredDayPlanSuggestion) -> AIPlanSuggestion {
        AIPlanSuggestion(
            summary: structured.reason,
            blocks: structured.proposedBlocks.map {
                AISuggestedBlock(
                    title: $0.title,
                    category: $0.category,
                    startMinuteOfDay: $0.startMinuteOfDay,
                    durationMinutes: $0.durationMinutes,
                    rationale: structured.explanation)
            })
    }

    /// Turn accepted suggestions into real TimeBlocks (provenance = .ai), skipping any that
    /// would overlap an existing block — the planner shows suggestions for review before applying.
    ///
    /// Readiness-aware (Part B): the model is only *hinted* about readiness in the prompt, so we also
    /// enforce it deterministically. After a DEPLETED night, demanding blocks (FOCUS/WORK/STUDY/TASK)
    /// that the model placed before the grogginess floor are shifted past it via
    /// `ChronosCore.applyReadiness`, treated as obstacles against the existing plan. Other readiness
    /// levels are a no-op.
    func materialize(
        _ suggestion: AIPlanSuggestion,
        on date: Date,
        existing: [TimeBlock],
        readiness: SleepReadiness = .unknown
    ) -> [TimeBlock] {
        // First materialize candidate (clamped, non-overlapping) blocks.
        var candidates: [(suggestion: AISuggestedBlock, start: Int, duration: Int)] = []
        for s in suggestion.blocks {
            let start = min(max(s.startMinuteOfDay, 0), 1439)
            let duration = min(max(s.durationMinutes, 1), 1440)
            let overlaps = existing.contains { b in
                start < b.startMinuteOfDay + b.durationMinutes &&
                b.startMinuteOfDay < start + duration
            }
            guard !overlaps else { continue }
            candidates.append((s, start, duration))
        }

        // Deterministic readiness shift. Demanding := high-energy categories; existing blocks are
        // immovable obstacles so AI blocks never land on the user's locked plan.
        let coreReadiness = ChronosCore.SleepReadiness(rawValue: readiness.rawValue) ?? .unknown
        let demandingCategories: Set<String> = ["FOCUS", "WORK", "STUDY", "TASK", "DEEP WORK"]
        let obstacles = existing.map {
            ReadinessPlanBlock(id: "existing-\($0.id)", startMinute: $0.startMinuteOfDay,
                               durationMinutes: $0.durationMinutes,
                               energyLevel: 1, isFixed: true)
        }
        let planBlocks = candidates.enumerated().map { idx, c in
            ReadinessPlanBlock(
                id: "ai-\(idx)",
                startMinute: c.start,
                durationMinutes: c.duration,
                energyLevel: demandingCategories.contains(c.suggestion.category.uppercased())
                    ? ReadinessSchedule.demandingEnergyLevel : 1,
                isFixed: false)
        }
        let shifted = applyReadiness(to: obstacles + planBlocks, readiness: coreReadiness)
        let shiftedByID = Dictionary(uniqueKeysWithValues: shifted.map { ($0.id, $0.startMinute) })

        return candidates.enumerated().map { idx, c in
            let start = shiftedByID["ai-\(idx)"] ?? c.start
            return TimeBlock(
                date: date, title: c.suggestion.title, category: c.suggestion.category,
                startMinuteOfDay: min(max(start, 0), 1439), durationMinutes: c.duration,
                provenance: .ai, flexibility: .movable, source: "ai")
        }
    }
}
