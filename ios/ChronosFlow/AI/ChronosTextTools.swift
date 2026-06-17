import Foundation
import FoundationModels

// MARK: - On-device text tools (proofread / rewrite / summarize)
//
// The iOS-native analogue of the Android ML Kit GenAI text tools (MlKitTextToolsGateway:
// Proofreading / Rewriting / Summarization). On iOS the same on-device Foundation Models session
// performs the transform from a tight instruction, so a note can be cleaned up, reshaped, or
// condensed without leaving the device. Used from the task editor's notes field.

enum ChronosTextOp: String, CaseIterable, Identifiable {
    case proofread, shorten, elaborate, friendly, professional, summarize
    var id: String { rawValue }

    var label: String {
        switch self {
        case .proofread: "Proofread"
        case .shorten: "Shorten"
        case .elaborate: "Elaborate"
        case .friendly: "Friendly"
        case .professional: "Professional"
        case .summarize: "Summarize"
        }
    }

    var systemImage: String {
        switch self {
        case .proofread: "text.badge.checkmark"
        case .shorten: "arrow.down.right.and.arrow.up.left"
        case .elaborate: "text.append"
        case .friendly: "face.smiling"
        case .professional: "briefcase"
        case .summarize: "list.bullet.rectangle"
        }
    }

    /// The instruction handed to the model. Mirrors the Android rewrite styles + proofreading.
    fileprivate var instruction: String {
        switch self {
        case .proofread:
            "Correct spelling, grammar, and punctuation. Preserve the meaning and tone. Return only the corrected text."
        case .shorten:
            "Rewrite more concisely while preserving the key information. Return only the rewritten text."
        case .elaborate:
            "Expand with a little more helpful detail while staying on topic. Return only the rewritten text."
        case .friendly:
            "Rewrite in a warm, friendly tone. Return only the rewritten text."
        case .professional:
            "Rewrite in a clear, professional tone. Return only the rewritten text."
        case .summarize:
            "Summarize into one short line. Return only the summary."
        }
    }
}

@MainActor
@Observable
final class ChronosTextTools {
    /// Non-nil when on-device AI can't run; the UI hides the tools or shows this.
    let unavailableReason: String?
    var isWorking = false

    /// Replay cache so a repeated transform of unchanged text returns instantly (Android parity:
    /// `GenAiResponseCache`). In-memory, bounded; nothing is persisted off-device.
    @ObservationIgnored private let cache = ChronosResponseCache()

    init() {
        switch SystemLanguageModel.default.availability {
        case .available: unavailableReason = nil
        case .unavailable(.deviceNotEligible): unavailableReason = "This device doesn't support Apple Intelligence."
        case .unavailable(.appleIntelligenceNotEnabled): unavailableReason = "Turn on Apple Intelligence to use text tools."
        case .unavailable(.modelNotReady): unavailableReason = "The on-device model is still downloading."
        case .unavailable: unavailableReason = "On-device AI is unavailable right now."
        }
    }

    var isAvailable: Bool { unavailableReason == nil }

    /// Run a text op on `input`, returning the transformed text (or `nil` on failure/empty).
    func run(_ op: ChronosTextOp, on input: String) async -> String? {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard isAvailable, !trimmed.isEmpty, !isWorking else { return nil }
        // Replay an identical transform without a cold inference.
        if let cached = cache.value(op: op.rawValue, input: trimmed) { return cached }
        isWorking = true
        defer { isWorking = false }
        let session = LanguageModelSession(instructions: Instructions { op.instruction })
        do {
            let response = try await session.respond(
                to: trimmed,
                options: GenerationOptions(temperature: GenerationProfile.balanced.temperature))
            let out = response.content.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !out.isEmpty else { return nil }
            cache.store(op: op.rawValue, input: trimmed, result: out)
            return out
        } catch {
            return nil
        }
    }
}
