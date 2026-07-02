import SwiftUI

/// The global command palette — the iOS rebuild of Android's `CommandPaletteDialog`
/// (Ctrl+K / the top-bar command action in `DayDialTopBar`).
///
/// On Android the compact bottom bar only carries Plan/Today/Focus, so the command palette is the
/// primary way to reach Tasks/Habits/Goals/Meds/Routines/Sleep/Journal/Review/Settings on a phone.
/// This sheet provides that: a filterable list of **navigate** commands (every enabled destination),
/// **create** commands (mirroring Quick-Add), and an **Ask the assistant** row.
struct CommandPalette: View {
    let shell: ShellState
    @Bindable var settings: ChronosSettings
    @Environment(\.dismiss) private var dismiss

    @State private var query = ""

    private struct Command: Identifiable {
        let id: String
        let label: String
        let icon: String
        let perform: () -> Void
    }

    private var navigateCommands: [Command] {
        var commands = PrimaryTab.allCases.map { tab in
            Command(id: "nav.\(tab.rawValue)", label: tab.label, icon: tab.icon) {
                shell.select(tab); dismiss()
            }
        }
        for route in ShellRoute.allCases where route.isEnabled(settings) {
            commands.append(Command(id: "nav.\(route.rawValue)", label: route.label, icon: route.icon) {
                shell.open(route); dismiss()
            })
        }
        return commands
    }

    private var createCommands: [Command] {
        quickAddActions(settings).map { action in
            Command(id: "create.\(action.id)", label: action.label, icon: action.icon) {
                switch action.target {
                // Dismiss the palette and present the editor at the shell root (Android dismisses the
                // palette on execute; presenting locally would stack the editor over the palette).
                case .editor(let which): shell.pendingEditor = which; dismiss()
                case .route(let route):  shell.open(route); dismiss()
                }
            }
        }
    }

    private var assistantCommands: [Command] {
        guard settings.aiEnabled else { return [] }
        return [Command(id: "assistant", label: "Ask the assistant", icon: "sparkles") {
            shell.showAssistant = true; dismiss()
        }]
    }

    // MARK: Recents (Android CommandSearchViewModel recent-command boosting)

    /// Most-recent-first executed command ids, capped like Android's `MAX_RECENT_COMMANDS`.
    private static let recentIDsKey = "commandPalette.recentCommandIDs"
    private static let maxRecents = 8

    private func recordRecent(_ id: String) {
        var ids = UserDefaults.standard.stringArray(forKey: Self.recentIDsKey) ?? []
        ids.removeAll { $0 == id }
        ids.insert(id, at: 0)
        UserDefaults.standard.set(Array(ids.prefix(Self.maxRecents)), forKey: Self.recentIDsKey)
    }

    /// Recently executed commands (still enabled), surfaced above "Go to" on the empty query.
    private var recentCommands: [Command] {
        let ids = UserDefaults.standard.stringArray(forKey: Self.recentIDsKey) ?? []
        guard !ids.isEmpty else { return [] }
        let all = navigateCommands + createCommands + assistantCommands
        return ids.compactMap { id in all.first { $0.id == id } }
    }

    private func filter(_ commands: [Command]) -> [Command] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        guard !q.isEmpty else { return commands }
        return commands.filter { $0.label.lowercased().contains(q) }
    }

    var body: some View {
        let nav = filter(navigateCommands)
        let create = filter(createCommands)
        let assist = filter(assistantCommands)
        let trimmedQuery = query.trimmingCharacters(in: .whitespaces)
        return NavigationStack {
            ZStack {
                ChronosBackdrop()
                if nav.isEmpty && create.isEmpty && assist.isEmpty && !trimmedQuery.isEmpty {
                    ContentUnavailableView.search(text: query)
                } else {
                    List {
                        // Empty-query extras, in Android's priority order: the proactive digest
                        // outranks recents, which outrank the plain catalog.
                        if trimmedQuery.isEmpty {
                            digestSection
                            section("Recent", recentCommands)
                        }
                        section("Go to", nav)
                        section("Create", create)
                        section("Assistant", assist)
                    }
                    .listStyle(.insetGrouped)
                    .scrollContentBackground(.hidden)
                }
            }
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search commands")
            .navigationTitle("Commands")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    /// The cached daily digest, shown when the palette opens with no query so the first thing the
    /// user sees is a one-line read on their day (Android `proactiveDigestCommand`). Cache-only —
    /// no live inference on the palette-open path. Tapping it opens the review.
    @ViewBuilder
    private var digestSection: some View {
        if settings.insightsEnabled,
           let digest = ProactiveDigest.cachedHeadline() ?? ProactiveDigest.cachedLine() {
            Section("Today") {
                Button {
                    shell.open(.review); dismiss()
                } label: {
                    Label {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Today at a glance")
                            Text(digest).font(.chronosCaption).foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "sparkles")
                    }
                }
                .foregroundStyle(.primary)
            }
        }
    }

    @ViewBuilder
    private func section(_ title: String, _ commands: [Command]) -> some View {
        if !commands.isEmpty {
            Section(title) {
                ForEach(commands) { command in
                    Button {
                        recordRecent(command.id)
                        command.perform()
                    } label: {
                        Label(command.label, systemImage: command.icon)
                    }
                    .foregroundStyle(.primary)
                }
            }
        }
    }
}
