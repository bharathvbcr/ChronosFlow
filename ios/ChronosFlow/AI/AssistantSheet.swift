import SwiftUI

/// A lightweight chat surface over `ChronosAssistant` — the iOS port of the Android conversational
/// assistant / command palette. The model can act on the day through its tools, so replies often
/// reflect real changes (a new block, a completed habit). Presented as a sheet from Today.
struct AssistantSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var assistant = ChronosAssistant()
    @State private var draft = ""
    @State private var suggestionTapped = 0
    @FocusState private var composerFocused: Bool

    /// Stable id for the in-flight streaming bubble, so we can scroll it into view as it grows.
    private let streamID = "chronos.assistant.stream"

    private let suggestions = [
        "What's my day look like?",
        "Block 90 min of deep work at 9am",
        "Add a task to call the dentist",
        "Mark my reading habit done",
    ]

    var body: some View {
        NavigationStack {
            ZStack {
                ChronosBackdrop()
                if let reason = assistant.unavailableReason {
                    unavailable(reason)
                } else {
                    conversation
                }
            }
            .navigationTitle("Assistant")
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Done") { dismiss() } }
            }
        }
    }

    private var conversation: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: ChronosSpacing.small) {
                        if assistant.messages.isEmpty { intro }
                        ForEach(assistant.messages) { message in
                            bubble(message).id(message.id)
                        }
                        if assistant.isResponding {
                            if assistant.currentReply.isEmpty {
                                ProgressView().padding(.vertical, ChronosSpacing.small)
                            } else {
                                // Live, partially-streamed reply (cumulative snapshots).
                                streamingBubble(assistant.currentReply).id(streamID)
                            }
                        }
                    }
                    .padding(ChronosSpacing.standard)
                }
                .onChange(of: assistant.messages.count) {
                    if let last = assistant.messages.last {
                        withAnimation(ChronosMotion.smooth) { proxy.scrollTo(last.id, anchor: .bottom) }
                    }
                }
                .onChange(of: assistant.currentReply) {
                    withAnimation(ChronosMotion.smooth) { proxy.scrollTo(streamID, anchor: .bottom) }
                }
            }
            composer
        }
    }

    private var intro: some View {
        VStack(alignment: .leading, spacing: ChronosSpacing.medium) {
            Label("Ask me to shape your day", systemImage: "sparkles")
                .font(.chronosTitle)
                .foregroundStyle(ChronosColors.brandAccent)
            Text("I can read your plan and make changes — schedule blocks, add tasks, mark habits done, or log a check-in.")
                .font(.chronosLabel)
                .foregroundStyle(.secondary)
            ForEach(suggestions, id: \.self) { suggestion in
                Button {
                    suggestionTapped += 1
                    send(suggestion)
                } label: {
                    Text(suggestion)
                        .font(.chronosLabel)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.vertical, ChronosSpacing.small)
                        .padding(.horizontal, ChronosSpacing.standard)
                        .background(.thinMaterial, in: .rect(cornerRadius: ChronosRadius.medium))
                }
                .buttonStyle(.plain)
                .pressable()
                .sensoryFeedback(.impact, trigger: suggestionTapped)
            }
        }
        .padding(.vertical, ChronosSpacing.medium)
    }

    private func bubble(_ message: ChronosChatMessage) -> some View {
        let isUser = message.role == .user
        return Text(message.text)
            .font(.chronosLabel)
            .padding(.vertical, ChronosSpacing.small)
            .padding(.horizontal, ChronosSpacing.standard)
            .background(
                isUser ? AnyShapeStyle(ChronosColors.brandAccent.opacity(0.22)) : AnyShapeStyle(.thinMaterial),
                in: .rect(cornerRadius: ChronosRadius.medium))
            .frame(maxWidth: .infinity, alignment: isUser ? .trailing : .leading)
    }

    /// The live assistant bubble for the in-flight turn (cumulative streamed text).
    private func streamingBubble(_ text: String) -> some View {
        Text(text)
            .font(.chronosLabel)
            .padding(.vertical, ChronosSpacing.small)
            .padding(.horizontal, ChronosSpacing.standard)
            .background(.thinMaterial, in: .rect(cornerRadius: ChronosRadius.medium))
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var composer: some View {
        HStack(spacing: ChronosSpacing.small) {
            TextField("Message", text: $draft, axis: .vertical)
                .focused($composerFocused)
                .lineLimit(1...4)
                .padding(.vertical, ChronosSpacing.small)
                .padding(.horizontal, ChronosSpacing.standard)
                .background(.thinMaterial, in: .capsule)
                .onSubmit { send(draft) }
            Button { send(draft) } label: {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.system(size: 30))
                    .symbolRenderingMode(.hierarchical)
                    .pressable()
            }
            .disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty || assistant.isResponding)
            .accessibilityLabel("Send")
            .accessibilityHint("Sends your message to the assistant")
        }
        .padding(ChronosSpacing.standard)
        .background(.bar)
        .sensoryFeedback(.impact, trigger: assistant.messages.count)
    }

    private func unavailable(_ reason: String) -> some View {
        ContentUnavailableView {
            Label("Assistant unavailable", systemImage: "sparkles.slash")
        } description: {
            Text(reason)
        } actions: {
            Button("Open Settings") {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
            .buttonStyle(.borderedProminent)
        }
    }

    private func send(_ text: String) {
        let toSend = text
        draft = ""
        composerFocused = false
        Task { await assistant.send(toSend) }
    }
}

#Preview {
    AssistantSheet()
}
