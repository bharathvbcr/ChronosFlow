import SwiftUI
import SwiftData

/// Propose moving movable, overlapping blocks into free time, shown for review before applying.
/// The packing mirrors ChronosCore.resolveConflicts (unit-tested off-device).
struct ConflictResolveSheet: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    let blocks: [TimeBlock]

    private var moves: [Move] { computeMoves() }

    var body: some View {
        NavigationStack {
            ZStack {
                ChronosBackdrop()
                if moves.isEmpty {
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
            .navigationTitle("Resolve conflicts")
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } }
                if !moves.isEmpty {
                    ToolbarItem(placement: .confirmationAction) { Button("Apply", action: apply) }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private struct Move: Identifiable {
        let id: String
        let title: String
        let fromStart: Int
        let toStart: Int
    }

    /// Greedy resolver mirroring ChronosCore.resolveConflicts: walk by start; relocate movable
    /// (non-fixed, non-locked) blocks that overlap the occupied frontier to the earliest free slot.
    private func computeMoves() -> [Move] {
        let dayStart = 6 * 60, dayEnd = 23 * 60
        let sorted = blocks.sorted { $0.startMinuteOfDay < $1.startMinuteOfDay }
        var occupied: [(start: Int, end: Int)] = []
        var result: [Move] = []

        func overlaps(_ s: Int, _ e: Int) -> Bool { occupied.contains { s < $0.end && $0.start < e } }
        func firstFree(_ dur: Int) -> Int? {
            var cursor = dayStart
            for span in occupied.sorted(by: { $0.start < $1.start }) {
                if span.start - cursor >= dur, cursor + dur <= dayEnd { return cursor }
                cursor = max(cursor, span.end)
            }
            return cursor + dur <= dayEnd ? cursor : nil
        }

        for b in sorted {
            let end = b.startMinuteOfDay + b.durationMinutes
            let movable = b.flexibility != .fixed && !b.isLocked
            if !overlaps(b.startMinuteOfDay, end) {
                occupied.append((b.startMinuteOfDay, end))
            } else if movable, let to = firstFree(b.durationMinutes) {
                result.append(Move(id: b.id, title: b.title, fromStart: b.startMinuteOfDay, toStart: to))
                occupied.append((to, to + b.durationMinutes))
            } else {
                occupied.append((b.startMinuteOfDay, end))
            }
        }
        return result
    }

    private func apply() {
        let byID = Dictionary(uniqueKeysWithValues: blocks.map { ($0.id, $0) })
        for move in moves { byID[move.id]?.updateStart(move.toStart) }
        try? context.save()
        dismiss()
    }
}
