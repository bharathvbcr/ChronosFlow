import SwiftUI
import SwiftData
import ChronosCore

/// The global command palette — the iOS rebuild of Android's `CommandPaletteDialog`
/// (Ctrl+K / the top-bar command action in `DayDialTopBar`).
///
/// On Android the compact bottom bar only carries Plan/Today/Focus, so the command palette is the
/// primary way to reach Tasks/Habits/Goals/Meds/Routines/Sleep/Journal/Review/Settings on a phone.
/// This sheet provides that: a filterable list of **navigate** commands (every enabled destination),
/// **create** commands (mirroring Quick-Add), an **Ask the assistant** row, and — when searching — a
/// **Your data** section that finds your tasks/habits/goals/meds/routines/journal by name (ranked by
/// `ChronosCore.rankCommands`) and jumps to the owning screen (Android's `CommandSearchViewModel`
/// entity search; iOS approximates its `SemanticPlanningIndex` with the deterministic ranker).
struct CommandPalette: View {
    let shell: ShellState
    @Bindable var settings: ChronosSettings
    @Environment(\.dismiss) private var dismiss

    @State private var query = ""

    // Personal-scale datasets, so an unbounded fetch is fine for a name search.
    @Query private var allTasks: [TaskItem]
    @Query private var allHabits: [Habit]
    @Query private var allGoals: [Goal]
    @Query private var allMeds: [MedicationPlan]
    @Query private var allRoutines: [Routine]
    @Query private var allJournal: [JournalEntry]

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

    // MARK: Entity search (Android CommandSearchViewModel "Your data")

    /// A searchable app entity mapped to the screen that owns it.
    private struct Entity {
        let candidate: CommandAssistCandidate
        let label: String
        let typeLabel: String
        let icon: String
        let route: ShellRoute
    }

    /// Every enabled entity as a ranker candidate keyed by its owning route + id.
    private var searchableEntities: [Entity] {
        var entities: [Entity] = []
        func add(_ items: [Entity]) { entities.append(contentsOf: items) }
        if ShellRoute.tasks.isEnabled(settings) {
            add(allTasks.map {
                Entity(candidate: .init(id: "task.\($0.id)", title: $0.title, keywords: ["task", "todo"]),
                       label: $0.title, typeLabel: "Task", icon: "checklist", route: .tasks)
            })
        }
        if ShellRoute.habits.isEnabled(settings) {
            add(allHabits.map {
                Entity(candidate: .init(id: "habit.\($0.id)", title: $0.title, keywords: ["habit", "routine"]),
                       label: $0.title, typeLabel: "Habit", icon: "repeat", route: .habits)
            })
        }
        if ShellRoute.goals.isEnabled(settings) {
            add(allGoals.map {
                Entity(candidate: .init(id: "goal.\($0.id)", title: $0.title, keywords: ["goal", "target"]),
                       label: $0.title, typeLabel: "Goal", icon: "target", route: .goals)
            })
        }
        if ShellRoute.medication.isEnabled(settings) {
            add(allMeds.map {
                Entity(candidate: .init(id: "med.\($0.id)", title: $0.name, keywords: ["medication", "med", "dose"]),
                       label: $0.name, typeLabel: "Medication", icon: "pills", route: .medication)
            })
        }
        if ShellRoute.routines.isEnabled(settings) {
            add(allRoutines.map {
                Entity(candidate: .init(id: "routine.\($0.id)", title: $0.title, keywords: ["routine", "template"]),
                       label: $0.title, typeLabel: "Routine", icon: "list.bullet.rectangle", route: .routines)
            })
        }
        if ShellRoute.journal.isEnabled(settings) {
            add(allJournal.map {
                let snippet = String($0.body.prefix(60))
                return Entity(candidate: .init(id: "journal.\($0.id)", title: snippet, keywords: ["journal", "entry", "reflection"]),
                              label: snippet.isEmpty ? "Journal entry" : snippet, typeLabel: "Journal", icon: "book.closed", route: .journal)
            })
        }
        return entities
    }

    /// Name-matched entities for the current query, ranked by `ChronosCore.rankCommands` (the offline
    /// baseline; the assistant sheet remains the generative surface). Empty for queries under 3 chars.
    private var entityResults: [Command] {
        let entities = searchableEntities
        let ranked = rankCommands(query: query, candidates: entities.map(\.candidate), limit: 8)
        let byID = Dictionary(entities.map { ($0.candidate.id, $0) }, uniquingKeysWith: { a, _ in a })
        return ranked.compactMap { id in
            guard let entity = byID[id] else { return nil }
            return Command(id: entity.candidate.id, label: entity.label, icon: entity.icon) {
                shell.open(entity.route); dismiss()
            }
        }
    }

    var body: some View {
        let nav = filter(navigateCommands)
        let create = filter(createCommands)
        let assist = filter(assistantCommands)
        let entities = entityResults
        let trimmedQuery = query.trimmingCharacters(in: .whitespaces)
        return NavigationStack {
            ZStack {
                ChronosBackdrop()
                if nav.isEmpty && create.isEmpty && assist.isEmpty && entities.isEmpty && !trimmedQuery.isEmpty {
                    ContentUnavailableView.search(text: query)
                } else {
                    List {
                        // Empty-query extras, in Android's priority order: the proactive digest
                        // outranks recents, which outrank the plain catalog.
                        if trimmedQuery.isEmpty {
                            digestSection
                            section("Recent", recentCommands)
                        }
                        // A name search most likely targets one of the user's items, so "Your data"
                        // leads when present (Android surfaces entity hits above the command catalog).
                        entitySection(entities)
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

    /// "Your data" hits. Unlike `section`, taps here are not recorded as recent commands — a recent
    /// list of one-off entity ids would never resurface and would crowd out real command recents.
    @ViewBuilder
    private func entitySection(_ commands: [Command]) -> some View {
        if !commands.isEmpty {
            Section("Your data") {
                ForEach(commands) { command in
                    Button { command.perform() } label: {
                        Label(command.label, systemImage: command.icon).lineLimit(1)
                    }
                    .foregroundStyle(.primary)
                }
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
