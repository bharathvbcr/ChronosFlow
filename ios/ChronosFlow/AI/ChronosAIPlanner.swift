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
    func generatePlan(date: Date, existingBlocks: [TimeBlock], readiness: SleepReadiness) async {
        guard isAvailable else { return }
        state = .thinking
        suggestion = nil

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

        do {
            let response = try await session.respond(
                to: prompt,
                generating: AIPlanSuggestion.self,
                options: GenerationOptions(temperature: GenerationProfile.deterministic.temperature)
            )
            suggestion = response.content
            state = .done
        } catch {
            state = .failed(error.localizedDescription)
        }
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
