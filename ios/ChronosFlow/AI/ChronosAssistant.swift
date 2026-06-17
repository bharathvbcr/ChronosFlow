import Foundation
import FoundationModels
import SwiftData

// MARK: - On-device conversational assistant (iOS 27 Foundation Models tool calling)
//
// The iOS-native equivalent of the Android `ConversationalAssistant` + `AppFunctions` surface.
// iOS 27 expands the Foundation Models framework with full tool calling on the larger on-device
// model: we give the `LanguageModelSession` a set of `Tool`s that read and mutate the SwiftData
// store, so a free-text request like "block 90 minutes of deep work at 9am and mark Read done"
// is turned into real actions — the model decides which tools to invoke and the framework feeds
// the results back into the transcript. Everything runs on device.

/// One turn in the assistant transcript.
struct ChronosChatMessage: Identifiable, Equatable {
    enum Role { case user, assistant }
    let id = UUID()
    let role: Role
    var text: String
}

@MainActor
@Observable
final class ChronosAssistant {
    var messages: [ChronosChatMessage] = []
    var isResponding = false
    /// The partially-streamed assistant reply for the in-flight turn (cumulative text). Empty when
    /// no turn is streaming. The UI shows this as a live bubble below `messages`.
    var currentReply = ""
    /// Non-nil when on-device AI can't run; the UI shows this instead of the composer.
    let unavailableReason: String?

    private let session: LanguageModelSession

    init() {
        switch SystemLanguageModel.default.availability {
        case .available:
            unavailableReason = nil
        case .unavailable(.deviceNotEligible):
            unavailableReason = "This device doesn't support Apple Intelligence."
        case .unavailable(.appleIntelligenceNotEnabled):
            unavailableReason = "Turn on Apple Intelligence in Settings to chat with the assistant."
        case .unavailable(.modelNotReady):
            unavailableReason = "The on-device model is still downloading. Try again shortly."
        case .unavailable:
            unavailableReason = "On-device AI is unavailable right now."
        }

        let tools: [any Tool] = [
            TodayScheduleTool(),
            AddTimeBlockTool(),
            AddTaskTool(),
            CompleteHabitTool(),
            LogMoodTool(),
        ]
        let instructions = Instructions {
            """
            You are ChronosFlow's planning assistant. You help the user shape a calm, balanced day.
            Use the tools to read today's schedule and to make changes when the user asks — schedule \
            time blocks, add tasks, mark habits done, or log a mood check-in. Prefer reading the \
            schedule before scheduling something so you don't overlap existing blocks. Times are \
            minute-of-day (540 = 9:00 AM). After acting, confirm what you did in one short, warm \
            sentence. If a request is ambiguous, ask a brief clarifying question instead of guessing.
            """
        }
        session = LanguageModelSession(tools: tools, instructions: instructions)
    }

    var isAvailable: Bool { unavailableReason == nil }

    /// Send a user turn, run the (tool-calling) model, and STREAM its reply.
    ///
    /// The transcript persists across turns inside `session`, so this is multi-turn. We use the
    /// Foundation Models streaming API (`session.streamResponse(to:)`), whose `ResponseStream<String>`
    /// emits cumulative snapshots; we assign each to `currentReply` for a live-typing bubble, then
    /// fold the final text into `messages`. Tool calls the model makes mid-stream run transparently;
    /// the framework feeds their results back into the same transcript.
    ///
    /// API NOTE (verify once in Xcode — see ios/README "suspicious APIs"): the streamed element is
    /// read as `snapshot.content`, matching the released `ResponseStream<String>.Snapshot { content,
    /// rawContent }`. If the shipping SDK instead yields the partial value directly, change the loop
    /// body to `currentReply = snapshot`. Either way, if streaming fails at runtime we fall back to
    /// the (confirmed) non-streaming `respond(to:)` API below, so the assistant still works.
    func send(_ text: String) async {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !isResponding, isAvailable else { return }
        messages.append(ChronosChatMessage(role: .user, text: trimmed))
        isResponding = true
        currentReply = ""
        defer {
            isResponding = false
            currentReply = ""
        }
        do {
            let stream = session.streamResponse(to: trimmed)
            for try await snapshot in stream {
                currentReply = snapshot.content
            }
            appendAssistant(currentReply)
        } catch {
            // Streaming failed (e.g. transient model error) — fall back to a single-shot response,
            // whose `Response.content: String` is the stable, confirmed Foundation Models API.
            if let reply = await respondOnce(to: trimmed) {
                appendAssistant(reply)
            } else {
                messages.append(ChronosChatMessage(
                    role: .assistant,
                    text: "Sorry — I couldn't complete that. \(error.localizedDescription)"))
            }
        }
    }

    /// Non-streaming fallback turn. Uses the same multi-turn `session` so the transcript is intact.
    private func respondOnce(to prompt: String) async -> String? {
        guard let response = try? await session.respond(to: prompt) else { return nil }
        let text = response.content.trimmingCharacters(in: .whitespacesAndNewlines)
        return text.isEmpty ? nil : text
    }

    private func appendAssistant(_ text: String) {
        let finalText = text.trimmingCharacters(in: .whitespacesAndNewlines)
        messages.append(ChronosChatMessage(role: .assistant, text: finalText.isEmpty ? "Done." : finalText))
    }
}

// MARK: - Tools
//
// Each tool's mutation hops to the main actor to touch `ChronosStore.shared.mainContext`, mirroring
// how the App Intents in `ChronosAppIntents` write to the same shared SwiftData container.

/// Read today's planned blocks and open-task count so the model can plan around them.
struct TodayScheduleTool: Tool {
    let name = "getTodaySchedule"
    let description = "Read the user's planned time blocks and open task count for today."

    @Generable
    struct Arguments {
        @Guide(description: "Set true to also include blocks that have already finished today.")
        var includeFinished: Bool
    }

    func call(arguments: Arguments) async throws -> ToolOutput {
        let summary = await MainActor.run {
            let context = ChronosStore.shared.mainContext
            let blocks = ((try? context.fetch(FetchDescriptor<TimeBlock>())) ?? [])
                .filter { Calendar.current.isDateInToday($0.date) }
                .sorted { $0.startMinuteOfDay < $1.startMinuteOfDay }
            let openTasks = ((try? context.fetch(FetchDescriptor<TaskItem>())) ?? [])
                .filter { !$0.isCompleted }
            let lines = blocks.isEmpty
                ? "no blocks scheduled"
                : blocks.map { "\($0.title) [\($0.category)] \($0.startMinuteOfDay.clockTime)–\($0.plannedEndMinuteOfDay.clockTime)" }
                    .joined(separator: "; ")
            return "Today's blocks: \(lines). Open tasks: \(openTasks.count)."
        }
        return ToolOutput(summary)
    }
}

/// Schedule a new time block on today's plan.
struct AddTimeBlockTool: Tool {
    let name = "addTimeBlock"
    let description = "Schedule a new time block on today's plan."

    @Generable
    struct Arguments {
        @Guide(description: "Short block title, e.g. 'Deep work', 'Lunch', 'Workout'.")
        var title: String
        @Guide(description: "One of: FOCUS, WORK, STUDY, BREAK, MEAL, ROUTINE, EXERCISE.")
        var category: String
        @Guide(description: "Start time as minute-of-day, 0 to 1439 (540 = 9:00 AM).")
        var startMinuteOfDay: Int
        @Guide(description: "Duration in minutes, 15 to 240.")
        var durationMinutes: Int
    }

    func call(arguments: Arguments) async throws -> ToolOutput {
        let start = min(max(arguments.startMinuteOfDay, 0), 1439)
        let duration = min(max(arguments.durationMinutes, 1), 1440)
        let title = arguments.title
        let category = arguments.category.uppercased()
        await MainActor.run {
            let context = ChronosStore.shared.mainContext
            context.insert(TimeBlock(
                date: .now, title: title, category: category,
                startMinuteOfDay: start, durationMinutes: duration,
                provenance: .ai, flexibility: .movable, source: "assistant"))
            try? context.save()
        }
        return ToolOutput("Scheduled “\(title)” at \(start.clockTime) for \(duration) min.")
    }
}

/// Add a to-do task to the user's task list.
struct AddTaskTool: Tool {
    let name = "addTask"
    let description = "Add a to-do task to the user's task list."

    @Generable
    struct Arguments {
        @Guide(description: "The task title.")
        var title: String
        @Guide(description: "Priority: 0 none, 1 low, 2 medium, 3 high.")
        var priority: Int
    }

    func call(arguments: Arguments) async throws -> ToolOutput {
        let title = arguments.title
        let priority = min(max(arguments.priority, 0), 3)
        await MainActor.run {
            let context = ChronosStore.shared.mainContext
            context.insert(TaskItem(title: title, priority: priority))
            try? context.save()
        }
        return ToolOutput("Added task “\(title)”.")
    }
}

/// Mark one of the user's active habits as done for today.
struct CompleteHabitTool: Tool {
    let name = "completeHabit"
    let description = "Mark one of the user's habits as done for today, by name."

    @Generable
    struct Arguments {
        @Guide(description: "The habit name to mark complete (a fuzzy match is fine).")
        var habitTitle: String
    }

    func call(arguments: Arguments) async throws -> ToolOutput {
        let query = arguments.habitTitle.lowercased()
        let result = await MainActor.run { () -> String in
            let context = ChronosStore.shared.mainContext
            let habits = ((try? context.fetch(FetchDescriptor<Habit>())) ?? []).filter(\.isActive)
            guard let match = habits.first(where: {
                let t = $0.title.lowercased()
                return t.contains(query) || query.contains(t)
            }) else {
                return "I couldn't find an active habit matching “\(arguments.habitTitle)”."
            }
            if match.isCompleted(on: .now) {
                return "“\(match.title)” is already marked done today."
            }
            match.toggleCompletion(on: .now)
            try? context.save()
            return "Marked “\(match.title)” done — streak is now \(match.streakCount)."
        }
        return ToolOutput(result)
    }
}

/// Record a quick mood / energy check-in.
struct LogMoodTool: Tool {
    let name = "logMood"
    let description = "Record a quick mood and energy check-in."

    @Generable
    struct Arguments {
        @Guide(description: "Mood from 1 (low) to 5 (great).")
        var mood: Int
        @Guide(description: "Energy from 1 (drained) to 5 (energized).")
        var energy: Int
    }

    func call(arguments: Arguments) async throws -> ToolOutput {
        let mood = min(max(arguments.mood, 1), 5)
        let energy = min(max(arguments.energy, 1), 5)
        await MainActor.run {
            let context = ChronosStore.shared.mainContext
            context.insert(MoodEnergyCheckIn(moodScore: mood, energyScore: energy))
            try? context.save()
        }
        return ToolOutput("Logged a check-in: mood \(mood)/5, energy \(energy)/5.")
    }
}
