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
    @State private var editor: QuickAddEditor?
    @State private var showAssistant = false

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
                case .editor(let which): editor = which
                case .route(let route):  shell.open(route); dismiss()
                }
            }
        }
    }

    private var assistantCommands: [Command] {
        guard settings.aiEnabled else { return [] }
        return [Command(id: "assistant", label: "Ask the assistant", icon: "sparkles") {
            showAssistant = true
        }]
    }

    private func filter(_ commands: [Command]) -> [Command] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        guard !q.isEmpty else { return commands }
        return commands.filter { $0.label.lowercased().contains(q) }
    }

    var body: some View {
        NavigationStack {
            List {
                section("Go to", filter(navigateCommands))
                section("Create", filter(createCommands))
                section("Assistant", filter(assistantCommands))
            }
            .listStyle(.insetGrouped)
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search commands")
            .navigationTitle("Commands")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .sheet(item: $editor) { quickAddEditorView(for: $0) }
            .sheet(isPresented: $showAssistant) { AssistantSheet() }
        }
    }

    @ViewBuilder
    private func section(_ title: String, _ commands: [Command]) -> some View {
        if !commands.isEmpty {
            Section(title) {
                ForEach(commands) { command in
                    Button {
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
