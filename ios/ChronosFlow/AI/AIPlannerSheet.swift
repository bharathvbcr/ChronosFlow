import SwiftUI
import SwiftData
import ChronosCore

/// Review-before-apply sheet for the on-device AI day planner. Mirrors the Android rule that
/// AI suggestions are always shown for review before they change the plan.
struct AIPlannerSheet: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @State private var planner = ChronosAIPlanner()
    @Query private var nights: [SleepTrack]

    let date: Date
    let existingBlocks: [TimeBlock]

    private var readiness: SleepReadiness {
        deriveSleepReadiness(lastNight: nights.max { $0.date < $1.date })
    }

    var body: some View {
        NavigationStack {
            ZStack {
                ChronosBackdrop()
                content
            }
            .navigationTitle("AI day plan")
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } }
            }
        }
        .presentationDetents([.large])
    }

    @ViewBuilder private var content: some View {
        switch planner.state {
        case .unavailable(let message):
            ContentUnavailableView {
                Label("AI unavailable", systemImage: "sparkles.slash")
            } description: {
                Text(message)
            }
        case .thinking:
            VStack(spacing: ChronosSpacing.medium) {
                ProgressView().controlSize(.large)
                Text("Planning your day on device…").font(.chronosBody).foregroundStyle(.secondary)
            }
        case .ready, .failed:
            VStack(spacing: ChronosSpacing.medium) {
                Image(systemName: "sparkles").font(.system(size: 48))
                    .foregroundStyle(ChronosColors.brandPrimary)
                Text("Generate a balanced plan around your fixed blocks, on device.")
                    .multilineTextAlignment(.center).font(.chronosBody).foregroundStyle(.secondary)
                if case .failed(let m) = planner.state {
                    Text(m).font(.chronosCaption).foregroundStyle(ChronosColors.danger)
                }
                Button {
                    Task { await planner.generatePlan(date: date, existingBlocks: existingBlocks, readiness: readiness) }
                } label: {
                    Label("Generate plan", systemImage: "wand.and.stars").frame(maxWidth: 240)
                }
                .buttonStyle(.borderedProminent).controlSize(.large)
            }
            .padding(ChronosSpacing.medium)
        case .done:
            if let suggestion = planner.suggestion { suggestionList(suggestion) }
        }
    }

    private func suggestionList(_ suggestion: AIPlanSuggestion) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ChronosSpacing.compact) {
                ChronosGlassCard(tint: ChronosColors.brandPrimary) {
                    Label(suggestion.summary, systemImage: "sparkles")
                        .font(.chronosLabel).frame(maxWidth: .infinity, alignment: .leading)
                }
                ForEach(Array(suggestion.blocks.enumerated()), id: \.offset) { _, block in
                    ChronosGlassCard(tint: ChronosColors.category(block.category)) {
                        VStack(alignment: .leading, spacing: ChronosSpacing.micro) {
                            HStack {
                                Text(block.title).font(.chronosHeadline)
                                Spacer()
                                Text("\(block.startMinuteOfDay.clockTime) · \(block.durationMinutes)m")
                                    .font(.chronosCaption).foregroundStyle(.secondary)
                            }
                            Text(block.rationale).font(.chronosCaption).foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                Button {
                    // Planning toggles come from Settings → Planning at apply time (read-only here);
                    // they mirror Android's protect-focus / auto-breaks / preserve-manual flags.
                    let settings = ChronosSettings.shared
                    let plan = planner.materialize(
                        suggestion, on: date, existing: existingBlocks,
                        readiness: readiness,
                        toggles: PlanningToggles(
                            protectFocusBlocks: settings.protectFocusBlocks,
                            addBreaksAutomatically: settings.addBreaksAutomatically,
                            preserveManualBlocks: settings.preserveManualBlocks))
                    // Regeneration may replace unprotected blocks a suggestion overlaps.
                    for block in existingBlocks where plan.displacedExistingIDs.contains(block.id) {
                        context.delete(block)
                    }
                    plan.created.forEach(context.insert)
                    try? context.save()
                    dismiss()
                } label: {
                    Label("Apply to plan", systemImage: "checkmark").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent).controlSize(.large)
                .padding(.top, ChronosSpacing.small)
            }
            .padding(ChronosSpacing.standard)
        }
    }
}
