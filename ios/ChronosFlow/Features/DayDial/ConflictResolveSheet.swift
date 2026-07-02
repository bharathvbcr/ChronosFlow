import SwiftUI
import SwiftData
import ChronosCore
import FoundationModels

/// Propose moving movable, overlapping blocks into free time, shown for review before applying.
/// The packing mirrors `ChronosCore.resolveConflicts` (unit-tested off-device).
///
/// Two modes (matching the Android Plan menu split):
///  • `.deterministic` — Android's **Fix schedule**: apply the moves, done.
///  • `.repairWithAI`  — Android's **Repair with AI**: apply the moves, then if blocks still overlap
///    (immovable/fixed/locked), hand the leftovers to the on-device planner for human-readable ideas.
///
/// On apply it reports the committed `[BlockStartChange]` back to the caller so the Day Dial can
/// record the move as one undoable `ResolveConflictsCommand` batch.
struct ConflictResolveSheet: View {
    enum Mode { case deterministic, repairWithAI }

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    let blocks: [TimeBlock]
    var mode: Mode = .deterministic
    /// Reports the per-block (original → new) start changes the user applied, for the undo stack.
    var onApplied: ([BlockStartChange]) -> Void = { _ in }

    /// After applying the deterministic moves, how many overlaps still remain (immovable blocks).
    /// Drives the Repair-with-AI fallback panel.
    @State private var unresolvedCount = 0
    @State private var didApply = false
    @State private var applyFeedback = false

    private var moves: [Move] { computeMoves() }

    var body: some View {
        NavigationStack {
            ZStack {
                ChronosBackdrop()
                content
            }
            .navigationTitle(mode == .repairWithAI ? "Repair with AI" : "Fix schedule")
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } }
                if !moves.isEmpty && !didApply {
                    ToolbarItem(placement: .confirmationAction) { Button("Apply", action: apply) }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .sensoryFeedback(.success, trigger: applyFeedback)
    }

    @ViewBuilder private var content: some View {
        if didApply {
            // Post-apply: either all clear, or a Repair-with-AI fallback for the immovable leftovers.
            if unresolvedCount > 0 && mode == .repairWithAI {
                ConflictAIFallback(unresolvedCount: unresolvedCount, blocks: stillOverlapping())
            } else if unresolvedCount > 0 {
                ContentUnavailableView("Still \(unresolvedCount) overlap\(unresolvedCount == 1 ? "" : "s")",
                                       systemImage: "exclamationmark.triangle",
                                       description: Text("The remaining overlaps involve fixed or locked blocks. Try 'Repair with AI' for ideas, or adjust them manually."))
            } else {
                ContentUnavailableView("Schedule cleared", systemImage: "checkmark.circle.fill",
                                       description: Text("All movable overlaps were resolved."))
            }
        } else if moves.isEmpty {
            ContentUnavailableView("Nothing to move", systemImage: "checkmark.circle",
                                   description: Text("The overlaps involve fixed or locked blocks that can't be moved automatically."))
        } else {
            ScrollView {
                VStack(alignment: .leading, spacing: ChronosSpacing.compact) {
                    Text("Move \(moves.count) flexible block\(moves.count == 1 ? "" : "s") into open time to clear overlaps.")
                        .font(.chronosCaption).foregroundStyle(.secondary)
                    ForEach(moves) { move in
                        ChronosGlassCard(tint: ChronosColors.brandPrimary) {
                            HStack {
                                Text(move.title).font(.chronosLabel)
                                Spacer()
                                Text("\(move.fromStart.clockTime) → \(move.toStart.clockTime)")
                                    .font(.chronosCaption).foregroundStyle(.secondary)
                            }
                            .frame(maxWidth: .infinity)
                        }
                    }
                }
                .padding(ChronosSpacing.standard)
            }
        }
    }

    private struct Move: Identifiable {
        let id: String
        let title: String
        let fromStart: Int
        let toStart: Int
    }

    /// Greedy resolver via `ChronosCore.resolveConflicts`: relocate movable (non-fixed, non-locked)
    /// blocks that overlap the occupied frontier into the earliest free slot.
    private func computeMoves() -> [Move] {
        let resolvable = blocks.map {
            ResolvableBlock(id: $0.id, startMinute: $0.startMinuteOfDay,
                            durationMinutes: $0.durationMinutes,
                            isMovable: $0.flexibility != .fixed && !$0.isLocked)
        }
        let byID = Dictionary(uniqueKeysWithValues: blocks.map { ($0.id, $0) })
        return resolveConflicts(resolvable).map { suggestion in
            Move(id: suggestion.blockID,
                 title: byID[suggestion.blockID]?.title ?? "Block",
                 fromStart: suggestion.fromStart,
                 toStart: suggestion.toStart)
        }
    }

    private func apply() {
        let byID = Dictionary(uniqueKeysWithValues: blocks.map { ($0.id, $0) })
        var changes: [BlockStartChange] = []
        for move in moves {
            byID[move.id]?.updateStart(move.toStart)
            changes.append(BlockStartChange(blockId: move.id,
                                            originalStartMinute: move.fromStart,
                                            newStartMinute: move.toStart))
        }
        try? context.save()
        onApplied(changes)

        // Recount overlaps post-move. Anything left is structurally unmovable (fixed/locked).
        // `PlannerMath` is qualified — ChronosCore exports a same-named type, so the bare name is ambiguous.
        unresolvedCount = ChronosFlow.PlannerMath.conflicts(in: blocks).count
        applyFeedback.toggle()
        withAnimation(ChronosMotion.smooth) { didApply = true }
        // Deterministic mode just confirms and closes once there's nothing more to do.
        if mode == .deterministic && unresolvedCount == 0 { dismiss() }
    }

    /// Blocks still involved in an overlap after the deterministic pass — fed to the AI fallback.
    private func stillOverlapping() -> [TimeBlock] {
        let conflictIDs = Set(ChronosFlow.PlannerMath.conflicts(in: blocks).flatMap { [$0.firstID, $0.secondID] })
        return blocks.filter { conflictIDs.contains($0.id) }
            .sorted { $0.startMinuteOfDay < $1.startMinuteOfDay }
    }
}

/// On-device AI fallback for overlaps the deterministic resolver can't fix (immovable/fixed/locked).
/// Runs Apple Intelligence's `LanguageModelSession` for free-form advice — the structural analogue
/// of Android's `conflictRepairManualMessage` → AI chat hand-off. When the on-device model is
/// unavailable it degrades to a deterministic heuristic message (no silent failure).
private struct ConflictAIFallback: View {
    let unresolvedCount: Int
    let blocks: [TimeBlock]

    @State private var advice: String?
    @State private var isThinking = false
    @State private var errorMessage: String?

    /// Whether the on-device system model can run a chat session at all.
    private var modelAvailable: Bool {
        if case .available = SystemLanguageModel.default.availability { return true }
        return false
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ChronosSpacing.compact) {
                Label("\(unresolvedCount) overlap\(unresolvedCount == 1 ? "" : "s") couldn't be auto-moved",
                      systemImage: "exclamationmark.triangle.fill")
                    .font(.chronosLabel).foregroundStyle(ChronosColors.brandAccent)
                Text("These involve fixed or locked blocks. Here are the conflicting blocks:")
                    .font(.chronosCaption).foregroundStyle(.secondary)
                ForEach(blocks) { block in
                    ChronosGlassCard(tint: ChronosColors.category(block.category)) {
                        HStack {
                            Text(block.title).font(.chronosLabel)
                            Spacer()
                            Text("\(block.startMinuteOfDay.clockTime)–\(block.plannedEndMinuteOfDay.clockTime)")
                                .font(.chronosCaption).foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }

                if let advice {
                    ChronosGlassCard(tint: ChronosColors.brandPrimary) {
                        Label(advice, systemImage: "sparkles")
                            .font(.chronosBody).frame(maxWidth: .infinity, alignment: .leading)
                    }
                } else if isThinking {
                    HStack(spacing: ChronosSpacing.small) {
                        ProgressView()
                        Text("Thinking on device…").font(.chronosCaption).foregroundStyle(.secondary)
                    }
                } else {
                    Button { Task { await askForAdvice() } } label: {
                        Label("Get ideas", systemImage: "wand.and.stars")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent).controlSize(.large).pressable()
                }
                if let errorMessage {
                    Text(errorMessage).font(.chronosCaption).foregroundStyle(ChronosColors.brandAccent)
                }
            }
            .padding(ChronosSpacing.standard)
        }
    }

    private func askForAdvice() async {
        // Offline fallback: a deterministic heuristic so the user always gets actionable guidance.
        guard modelAvailable else {
            advice = heuristicAdvice()
            return
        }
        isThinking = true
        errorMessage = nil
        let instructions = Instructions {
            """
            You are ChronosFlow's scheduling assistant. The user has overlapping calendar blocks that \
            can't be moved automatically because they're fixed or locked. Briefly (2-3 sentences) \
            suggest concrete ways to resolve the conflict: shortening one block, unlocking and moving \
            one, or merging them. Be calm and practical.
            """
        }
        let session = LanguageModelSession(instructions: instructions)
        let prompt = "Overlapping fixed/locked blocks:\n" + blocks
            .map { "- \($0.title): \($0.startMinuteOfDay.clockTime)–\($0.plannedEndMinuteOfDay.clockTime)" }
            .joined(separator: "\n")
        do {
            let response = try await session.respond(
                to: prompt,
                options: GenerationOptions(temperature: GenerationProfile.creative.temperature))
            advice = response.content
        } catch {
            // Degrade to the heuristic rather than leaving the user stuck.
            advice = heuristicAdvice()
            errorMessage = "Used offline suggestions (\(error.localizedDescription))."
        }
        isThinking = false
    }

    /// Deterministic guidance when the model can't run — name the earliest pair and suggest a fix.
    private func heuristicAdvice() -> String {
        guard let first = blocks.first else {
            return "Edit one of the conflicting blocks to a free time, or unlock it so it can be moved automatically."
        }
        let second = blocks.dropFirst().first
        if let second {
            return "‘\(first.title)’ and ‘\(second.title)’ overlap and can't be moved automatically. Try shortening one, or unlock one block so ‘Fix schedule’ can relocate it to free time."
        }
        return "‘\(first.title)’ can't be moved automatically. Unlock it (or change it from Fixed) so ‘Fix schedule’ can place it in free time."
    }
}
