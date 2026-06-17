import SwiftUI
import SwiftData

/// The Today tab: a calm "what's now / next" view over the day, plus quick check-in and the
/// sleep-readiness banner that adapts the day. Ports the Today shell target.
struct TodayView: View {
    @Environment(\.modelContext) private var context
    @Query private var allBlocks: [TimeBlock]
    @Query private var tasks: [TaskItem]
    @Query private var sleepNights: [SleepTrack]
    @State private var activeSheet: TodaySheet?

    private enum TodaySheet: String, Identifiable {
        case assistant, checkIn, data, newTask, newBlock, logSleep
        var id: String { rawValue }
    }

    private var nowMinute: Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: .now)
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }

    private var todayBlocks: [TimeBlock] {
        allBlocks.filter { Calendar.current.isDateInToday($0.date) }
            .sorted { $0.startMinuteOfDay < $1.startMinuteOfDay }
    }

    private var current: TimeBlock? {
        todayBlocks.first { nowMinute >= $0.startMinuteOfDay && nowMinute < $0.startMinuteOfDay + $0.durationMinutes }
    }

    private var upNext: TimeBlock? {
        todayBlocks.first { $0.startMinuteOfDay > nowMinute }
    }

    private var lastNight: SleepTrack? {
        sleepNights.max { $0.date < $1.date }
    }

    private var readiness: SleepReadiness { deriveSleepReadiness(lastNight: lastNight) }

    private var todaysTasks: [TaskItem] {
        tasks.filter { task in
            guard let target = task.targetDate else { return false }
            return Calendar.current.isDateInToday(target)
        }
    }

    var body: some View {
        NavigationStack {
            ZStack {
                ChronosBackdrop()
                ScrollView {
                    VStack(alignment: .leading, spacing: ChronosSpacing.medium) {
                        if readiness != .unknown && readiness != .normal { readinessBanner }
                        nowCard
                        if let upNext { upNextCard(upNext) }
                        taskSection
                    }
                    .padding(ChronosSpacing.standard)
                }
            }
            .navigationTitle(Date.now.formatted(.dateTime.weekday(.wide).month().day()))
            .toolbarTitleDisplayMode(.inlineLarge)
            .chronosScrollMinimizedBar()
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { activeSheet = .assistant } label: { Image(systemName: "sparkles") }
                        .accessibilityLabel("Assistant")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    // Quick-create palette — the iOS analogue of ChronosQuickCreateCommandProvider.
                    Menu {
                        Button { activeSheet = .newTask } label: { Label("New task", systemImage: "checklist") }
                        Button { activeSheet = .newBlock } label: { Label("New time block", systemImage: "calendar.badge.plus") }
                        Button { activeSheet = .logSleep } label: { Label("Log sleep", systemImage: "moon.zzz.fill") }
                        Button { activeSheet = .checkIn } label: { Label("Mood check-in", systemImage: "face.smiling") }
                    } label: { Image(systemName: "plus.circle") }
                        .accessibilityLabel("Quick create")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button { activeSheet = .data } label: { Label("Data & backup", systemImage: "externaldrive") }
                    } label: { Image(systemName: "ellipsis.circle") }
                }
            }
            // Keep the "next block starts soon" notification aligned with today's plan (re-runs
            // whenever the day's blocks change). Gated on settings inside the scheduler.
            .task(id: todayBlocks.map(\.id)) {
                await ChronosNotifications.shared.scheduleNextBlockNotification(blocks: todayBlocks)
            }
            .sheet(item: $activeSheet) { sheet in
                switch sheet {
                case .assistant: AssistantSheet()
                case .checkIn: CheckInSheet()
                case .data: DataManagementView()
                case .newTask: TaskEditorSheet(task: nil)
                case .newBlock: TimeBlockEditorSheet(block: nil)
                case .logSleep: SleepLogSheet()
                }
            }
        }
    }

    private var readinessBanner: some View {
        ChronosGlassCard(tone: .quiet, tint: readiness == .depleted ? ChronosColors.brandAccent : ChronosColors.brandSecondary) {
            Label {
                Text(readiness == .depleted
                     ? "You slept lightly. The plan favors lighter tasks and earlier breaks today."
                     : "Well rested — a good day for demanding deep work.")
                    .font(.chronosLabel)
            } icon: {
                Image(systemName: readiness == .depleted ? "moon.zzz" : "sparkles")
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var nowCard: some View {
        ChronosGlassPanel(tint: current.map { ChronosColors.category($0.category) }) {
            VStack(alignment: .leading, spacing: ChronosSpacing.small) {
                Text("NOW").font(.chronosCaption).foregroundStyle(.secondary)
                if let current {
                    Text(current.title).font(.chronosTitleLarge)
                    Text("until \(current.plannedEndMinuteOfDay.clockTime)")
                        .font(.chronosBody).foregroundStyle(.secondary)
                    if current.category == "FOCUS" {
                        NavigationLink {
                            FocusView(prefilledBlockID: current.id)
                        } label: {
                            Label("Start focus", systemImage: "timer")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                        .padding(.top, ChronosSpacing.small)
                    }
                } else {
                    Text("Open time").font(.chronosTitle)
                    Text("Nothing scheduled right now").font(.chronosBody).foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func upNextCard(_ block: TimeBlock) -> some View {
        ChronosGlassCard {
            HStack {
                VStack(alignment: .leading) {
                    Text("UP NEXT").font(.chronosCaption).foregroundStyle(.secondary)
                    Text(block.title).font(.chronosHeadline)
                }
                Spacer()
                Text(block.startMinuteOfDay.clockTime)
                    .font(.chronosLabel).monospacedDigit().foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
        }
    }

    private var taskSection: some View {
        VStack(alignment: .leading, spacing: ChronosSpacing.small) {
            Text("Today's tasks").font(.chronosTitle)
            if todaysTasks.isEmpty {
                Text("No tasks scheduled for today").font(.chronosBody).foregroundStyle(.secondary)
            } else {
                ForEach(todaysTasks) { task in
                    Button {
                        task.isCompleted.toggle(); try? context.save()
                    } label: {
                        HStack {
                            Image(systemName: task.isCompleted ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(task.isCompleted ? ChronosColors.brandSecondary : .secondary)
                            Text(task.title).strikethrough(task.isCompleted)
                            Spacer()
                        }
                        .font(.chronosBody)
                        .padding(.vertical, ChronosSpacing.micro)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Quick mood / energy / stress / focus check-in. Ports the Android mood-energy check-in.
struct CheckInSheet: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @State private var mood = 3.0
    @State private var energy = 3.0
    @State private var stress = 3.0
    @State private var focus = 3.0
    @State private var notes = ""

    var body: some View {
        NavigationStack {
            Form {
                slider("Mood", value: $mood, system: "face.smiling")
                slider("Energy", value: $energy, system: "bolt.fill")
                slider("Stress", value: $stress, system: "wind")
                slider("Focus", value: $focus, system: "scope")
                Section { TextField("Notes (optional)", text: $notes, axis: .vertical) }
            }
            .navigationTitle("Check in")
            .toolbarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        context.insert(MoodEnergyCheckIn(
                            moodScore: Int(mood), stressScore: Int(stress),
                            energyScore: Int(energy), focusScore: Int(focus),
                            notes: notes.isEmpty ? nil : notes))
                        try? context.save()
                        dismiss()
                    }
                }
            }
        }
        .presentationDetents([.medium])
    }

    private func slider(_ label: String, value: Binding<Double>, system: String) -> some View {
        Section {
            VStack {
                HStack {
                    Label(label, systemImage: system)
                    Spacer()
                    Text("\(Int(value.wrappedValue))/5").foregroundStyle(.secondary)
                }
                Slider(value: value, in: 1...5, step: 1)
            }
        }
    }
}

#Preview {
    TodayView().modelContainer(ChronosStore.previewContainer())
}
