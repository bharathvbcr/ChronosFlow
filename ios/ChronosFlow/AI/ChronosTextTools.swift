import Foundation
import FoundationModels

// MARK: - On-device text tools (proofread / rewrite / summarize)
//
// The iOS-native analogue of the Android ML Kit GenAI text tools (MlKitTextToolsGateway:
// Proofreading / Rewriting / Summarization). On iOS the same on-device Foundation Models session
// performs the transform from a tight instruction, so a note can be cleaned up, reshaped, or
// condensed without leaving the device. Used from the task editor's notes field.

enum ChronosTextOp: String, CaseIterable, Identifiable {
    // Mirrors the Android ML Kit text-tools enums (TextToolsGenAi.kt): proofreading + the full
    // RewriteStyle set (REPHRASE→shorten/elaborate/friendly/professional + EMOJIFY) + the two
    // SummaryStyle variants (ONE_BULLET, THREE_BULLETS). Foundation Models has no Summarizer/
    // Rewriter option enums, so each variant is differentiated by its own instruction string.
    case proofread, shorten, elaborate, friendly, professional, emojify, summarizeOneBullet, summarizeThreeBullets
    var id: String { rawValue }

    var label: String {
        switch self {
        case .proofread: "Proofread"
        case .shorten: "Shorten"
        case .elaborate: "Elaborate"
        case .friendly: "Friendly"
        case .professional: "Professional"
        case .emojify: "Emojify"
        case .summarizeOneBullet: "Summarize"
        case .summarizeThreeBullets: "Key points"
        }
    }

    var systemImage: String {
        switch self {
        case .proofread: "text.badge.checkmark"
        case .shorten: "arrow.down.right.and.arrow.up.left"
        case .elaborate: "text.append"
        case .friendly: "face.smiling"
        case .professional: "briefcase"
        case .emojify: "face.smiling.inverse"
        case .summarizeOneBullet: "list.bullet.rectangle"
        case .summarizeThreeBullets: "list.bullet"
        }
    }

    /// The instruction handed to the model. Mirrors the Android rewrite styles + proofreading +
    /// the one/three-bullet summary variants. Foundation Models learns multi-bullet output from the
    /// instruction text in place of ML Kit's structured SummaryStyle/RewriteStyle option enums.
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
        case .emojify:
            "Add a few fitting emojis to the text to make it more expressive, while preserving the wording and meaning. Return only the emojified text."
        case .summarizeOneBullet:
            "Summarize into one short line. Return only the summary."
        case .summarizeThreeBullets:
            "Summarize into exactly three short bullet points, one per line, each starting with \"• \". Return only the bullet points."
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
    /// Honors `ChronosSettings.privacyMode` (N01): when DISABLED, no on-device generation runs.
    func run(_ op: ChronosTextOp, on input: String) async -> String? {
        await transform(key: op.rawValue, instruction: op.instruction,
                        temperature: GenerationProfile.balanced.temperature, input: input)
    }

    /// Expand a terse journal note into a fuller first-person reflection of one or two sentences.
    /// Android parity: `JournalViewModel.expandText` → `journalExpandPrompt`, run at the CREATIVE
    /// profile. Not a `ChronosTextOp` case on purpose — the journal-voiced rewrite shouldn't appear
    /// in the generic notes-rewrite menus that iterate `ChronosTextOp.allCases`.
    func expandReflection(_ input: String) async -> String? {
        await transform(
            key: "journalExpand",
            instruction: "Expand this short personal journal note into a fuller, natural "
                + "first-person reflection of one or two sentences. Keep the original meaning, "
                + "tone, and any facts; do not invent events. Return only the rewritten note.",
            temperature: GenerationProfile.creative.temperature,
            input: input)
    }

    private func transform(key: String, instruction: String, temperature: Double, input: String) async -> String? {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard ChronosSettings.shared.privacyMode.allowsOnDeviceGeneration,
              isAvailable, !trimmed.isEmpty, !isWorking else { return nil }
        // Replay an identical transform without a cold inference.
        if let cached = cache.value(op: key, input: trimmed) { return cached }
        isWorking = true
        defer { isWorking = false }
        let session = LanguageModelSession(instructions: Instructions { instruction })
        do {
            let response = try await session.respond(
                to: trimmed,
                options: GenerationOptions(temperature: temperature))
            let out = response.content.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !out.isEmpty else { return nil }
            cache.store(op: key, input: trimmed, result: out)
            return out
        } catch {
            return nil
        }
    }
}
