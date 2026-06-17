import Foundation
import SwiftData
import FoundationModels

// MARK: - Proactive assist digest cache
//
// The iOS-native analogue of the Android ProactiveAssistCache + ProactiveAssistForegroundRefresher.
// On-device generation is best done while the app is foregrounded, so we compute a short digest on
// each foreground and cache it to the App-Group `UserDefaults`. Background surfaces — the Today
// widget here (and notifications, as on Android) — then READ the cached copy without doing live
// inference, exactly mirroring the Android "foreground writes, background reads" split.
//
// The factual line is always computed deterministically (counts never hallucinate); when Apple
// Intelligence is available we additionally cache a short encouraging headline.

enum ProactiveDigest {
    // App-Group keys (read directly by the widget to avoid a cross-target dependency).
    static let lineKey = "assist.digest.line"
    static let headlineKey = "assist.digest.headline"
    static let dateKey = "assist.digest.date"   // start-of-day epoch seconds

    private static var defaults: UserDefaults {
        UserDefaults(suiteName: ChronosStore.appGroup) ?? .standard
    }

    /// Recompute the factual line every foreground; refresh the AI headline only when stale
    /// (new day, or older than ~3h) so we don't burn inference on every resume.
    @MainActor
    static func refresh() async {
        let context = ChronosStore.shared.mainContext
        let facts = computeFacts(context)
        let store = defaults
        store.set(facts.line, forKey: lineKey)

        let todayStart = Calendar.current.startOfDay(for: .now).timeIntervalSince1970
        let lastStamp = store.object(forKey: dateKey) as? Double ?? 0
        let headlineStale = (todayStart - lastStamp) > 3 * 3600 || store.string(forKey: headlineKey) == nil
        store.set(todayStart, forKey: dateKey)

        guard headlineStale else { return }
        if let headline = await generateHeadline(facts: facts) {
            store.set(headline, forKey: headlineKey)
        } else {
            // Local fallback headline (mirrors the Android deterministic fallback).
            store.set(facts.fallbackHeadline, forKey: headlineKey)
        }
    }

    /// Cached factual line (e.g. "2 of 5 blocks done · 3 tasks open"), if present.
    static func cachedLine() -> String? { defaults.string(forKey: lineKey) }
    /// Cached encouraging headline, if present.
    static func cachedHeadline() -> String? { defaults.string(forKey: headlineKey) }

    // MARK: Facts

    struct Facts {
        let blocksDone: Int, blocksTotal: Int
        let openTasks: Int
        let habitsDone: Int, habitsTotal: Int
        let medsDue: Int

        var line: String {
            var parts: [String] = []
            if blocksTotal > 0 { parts.append("\(blocksDone) of \(blocksTotal) blocks done") }
            if openTasks > 0 { parts.append("\(openTasks) task\(openTasks == 1 ? "" : "s") open") }
            if habitsTotal > 0 { parts.append("\(habitsDone)/\(habitsTotal) habits") }
            if medsDue > 0 { parts.append("\(medsDue) dose\(medsDue == 1 ? "" : "s") due") }
            return parts.isEmpty ? "Nothing scheduled yet today." : parts.joined(separator: " · ")
        }

        var fallbackHeadline: String {
            if blocksTotal == 0 && openTasks == 0 { return "A clear day — plan something kind to yourself." }
            if medsDue > 0 { return "A few doses still to take today." }
            if blocksTotal > 0 && blocksDone == blocksTotal { return "Every block done — nicely paced." }
            return "Steady progress — keep your rhythm."
        }
    }

    @MainActor
    private static func computeFacts(_ context: ModelContext) -> Facts {
        let today = Calendar.current.isDateInToday
        let blocks = ((try? context.fetch(FetchDescriptor<TimeBlock>())) ?? []).filter { today($0.date) }
        let tasks = ((try? context.fetch(FetchDescriptor<TaskItem>())) ?? [])
        let habits = ((try? context.fetch(FetchDescriptor<Habit>())) ?? []).filter(\.isActive)
        let meds = ((try? context.fetch(FetchDescriptor<MedicationPlan>())) ?? []).filter(\.isActive)
        return Facts(
            blocksDone: blocks.filter { $0.actualStartMinuteOfDay != nil }.count,
            blocksTotal: blocks.count,
            openTasks: tasks.filter { !$0.isCompleted }.count,
            habitsDone: habits.filter { $0.isCompleted(on: .now) }.count,
            habitsTotal: habits.count,
            medsDue: meds.filter { !$0.isTaken(on: .now) }.count)
    }

    // MARK: AI headline (optional enrichment)

    @MainActor
    private static func generateHeadline(facts: Facts) async -> String? {
        guard case .available = SystemLanguageModel.default.availability else { return nil }
        let session = LanguageModelSession(instructions: Instructions {
            """
            You write a single short, warm, encouraging headline (max 8 words) about the user's day \
            so far. Be factual — never invent numbers. Return only the headline, no punctuation-heavy \
            flourishes.
            """
        })
        do {
            let response = try await session.respond(
                to: "Today so far: \(facts.line). Write the headline.",
                options: GenerationOptions(temperature: GenerationProfile.balanced.temperature))
            let text = response.content.trimmingCharacters(in: .whitespacesAndNewlines)
            return text.isEmpty ? nil : text
        } catch {
            return nil
        }
    }
}
