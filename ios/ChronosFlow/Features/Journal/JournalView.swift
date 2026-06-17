import SwiftUI
import SwiftData
import ChronosCore

/// The Journal tab: one primary daily reflection plus history. Ports the companion journal layer,
/// including guided prompts, on-device dictation, answered-prompt tracking, clean-on-save, and a
/// polished history list (expand-in-place, inline cap, relative "last saved"). The pure text logic
/// lives in `ChronosCore.JournalText`.
struct JournalView: View {
    @Environment(\.modelContext) private var context
    @Query(sort: \JournalEntry.entryDate, order: .reverse) private var entries: [JournalEntry]
    @State private var draft = ""
    @State private var dictation = DictationController()

    /// History is capped inline; the rest is summarised in a footer (UX: no infinite walls of text).
    private let inlineHistoryCap = 10

    private var todayEntry: JournalEntry? {
        entries.first { Calendar.current.isDateInToday($0.entryDate) && $0.isPrimary }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: ChronosSpacing.medium) {
                    composerCard
                    historySection
                }
                .padding(ChronosSpacing.standard)
            }
            .background { ChronosBackdrop() }
            .navigationTitle("Journal")
            .chronosScrollMinimizedBar()
            .onAppear { draft = todayEntry?.body ?? "" }
            .onChange(of: dictation.transcript) { _, new in
                guard !new.isEmpty else { return }
                appendDictation(new)
                dictation.clearTranscript()
            }
            .onDisappear { dictation.stop() }
        }
    }

    // MARK: Composer

    private var answeredKeys: Set<String> { journalAnsweredPromptKeys(draft) }
    private var promptsPresent: Int { journalPromptsPresentCount(draft) }
    private var canSave: Bool { journalCanSave(draft) }
    private var isOnlyPrompts: Bool { journalIsOnlyPrompts(draft) }

    private var composerCard: some View {
        ChronosGlassPanel {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                Text("Today's reflection").font(.chronosTitle)

                if let saved = todayEntry {
                    Text(journalLastSavedLabel(updatedAt: saved.updatedAt, now: .now))
                        .font(.chronosCaption).foregroundStyle(.secondary)
                }

                promptChips

                TextField("What shaped your day?", text: $draft, axis: .vertical)
                    .lineLimit(4...12)
                    .font(.chronosBody)

                HStack {
                    dictationButton
                    Spacer()
                    Text(journalMeterLabel(draft))
                        .font(.chronosCaption).foregroundStyle(.secondary)
                }

                if isOnlyPrompts {
                    Text("Add your own words below the prompts to save.")
                        .font(.chronosCaption).foregroundStyle(.secondary)
                }

                Button("Save entry", action: save)
                    .buttonStyle(.borderedProminent)
                    .disabled(!canSave)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var promptChips: some View {
        JournalChipFlow(spacing: ChronosSpacing.small) {
            ForEach(journalPrompts) { prompt in
                let answered = answeredKeys.contains(prompt.key)
                Button {
                    withAnimation(ChronosMotion.smooth) {
                        draft = journalBodyWithPrompt(draft, question: prompt.question)
                    }
                } label: {
                    HStack(spacing: 4) {
                        if answered { Image(systemName: "checkmark.circle.fill").font(.caption2) }
                        Text(prompt.label)
                    }
                    .font(.chronosCaption)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(.thinMaterial, in: Capsule())
                    .overlay(answered ? Capsule().strokeBorder(ChronosColors.brandSecondary, lineWidth: 1) : nil)
                }
                .buttonStyle(.plain)
            }
            Button {
                withAnimation(ChronosMotion.smooth) {
                    draft = journalBodyWithGuidedTemplate(draft)
                }
            } label: {
                Label("Guided", systemImage: "list.bullet.rectangle")
                    .font(.chronosCaption)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(ChronosColors.brandPrimary.opacity(0.18), in: Capsule())
            }
            .buttonStyle(.plain)
        }
    }

    @ViewBuilder
    private var dictationButton: some View {
        switch dictation.availability {
        case .unsupported:
            EmptyView()
        case .available, .denied:
            Button {
                dictation.toggle()
            } label: {
                Label(dictation.isRecording ? "Listening…" : "Dictate",
                      systemImage: dictation.isRecording ? "waveform" : "mic.fill")
                    .font(.chronosCaption)
                    .foregroundStyle(dictation.isRecording ? ChronosColors.brandAccent : .secondary)
                    .symbolEffect(.variableColor, isActive: dictation.isRecording)
            }
            .buttonStyle(.plain)
            .disabled(dictation.availability == .denied)
        }
    }

    private func appendDictation(_ transcript: String) {
        let pieces = [draft.trimmingCharacters(in: .whitespacesAndNewlines),
                      transcript.trimmingCharacters(in: .whitespacesAndNewlines)]
            .filter { !$0.isEmpty }
        draft = pieces.joined(separator: " ")
    }

    private func save() {
        let cleaned = journalCleanForSave(draft)
        guard !cleaned.isEmpty else { return }
        if let todayEntry {
            todayEntry.body = cleaned
            todayEntry.updatedAt = .now
        } else {
            context.insert(JournalEntry(body: cleaned))
        }
        try? context.save()
        draft = cleaned
    }

    // MARK: History

    private var history: [JournalEntry] {
        entries.filter { !Calendar.current.isDateInToday($0.entryDate) }
    }

    @ViewBuilder
    private var historySection: some View {
        if history.isEmpty {
            ContentUnavailableView(
                "No past reflections",
                systemImage: "book.closed",
                description: Text("Your saved reflections from earlier days will appear here."))
                .padding(.top, ChronosSpacing.large)
        } else {
            Text("History").font(.chronosTitle)
            ForEach(history.prefix(inlineHistoryCap)) { entry in
                JournalHistoryCard(entry: entry)
            }
            if history.count > inlineHistoryCap {
                Text("Showing \(inlineHistoryCap) of \(history.count) reflections")
                    .font(.chronosCaption).foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.top, ChronosSpacing.micro)
            }
        }
    }
}

/// A single past reflection: collapses long bodies to ~4 lines with a "Show more" toggle.
private struct JournalHistoryCard: View {
    let entry: JournalEntry
    @State private var expanded = false

    var body: some View {
        ChronosGlassCard {
            VStack(alignment: .leading, spacing: ChronosSpacing.micro) {
                Text(entry.entryDate.formatted(.dateTime.weekday().month().day()))
                    .font(.chronosCaption).foregroundStyle(.secondary)
                Text(entry.body)
                    .font(.chronosBody)
                    .lineLimit(expanded ? nil : 4)
                if isLong {
                    Button(expanded ? "Show less" : "Show more") {
                        withAnimation(ChronosMotion.smooth) { expanded.toggle() }
                    }
                    .font(.chronosCaption)
                    .buttonStyle(.plain)
                    .foregroundStyle(ChronosColors.brandPrimary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    /// Heuristic for whether a "Show more" affordance is worth offering (long bodies / many lines).
    private var isLong: Bool {
        entry.body.count > 220 || entry.body.filter { $0 == "\n" }.count >= 4
    }
}

// MARK: - Flow layout

/// A minimal flowing wrap layout for the prompt chips (iOS 16+ `Layout`). Lays children out
/// left-to-right, wrapping to a new row when the proposed width is exceeded. (Local copy — the
/// Tasks editor has an equivalent private one; kept separate to avoid cross-feature coupling.)
private struct JournalChipFlow: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var rowWidth: CGFloat = 0
        var rowHeight: CGFloat = 0
        var totalHeight: CGFloat = 0
        var totalWidth: CGFloat = 0

        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if rowWidth + size.width > maxWidth, rowWidth > 0 {
                totalHeight += rowHeight + spacing
                totalWidth = max(totalWidth, rowWidth - spacing)
                rowWidth = 0
                rowHeight = 0
            }
            rowWidth += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        totalHeight += rowHeight
        totalWidth = max(totalWidth, rowWidth - spacing)
        return CGSize(width: min(totalWidth, maxWidth), height: totalHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) {
        let maxWidth = bounds.width
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0

        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x + size.width > bounds.minX + maxWidth, x > bounds.minX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}

#Preview { JournalView().modelContainer(ChronosStore.previewContainer()) }
