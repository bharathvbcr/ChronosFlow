import WidgetKit
import SwiftUI
import SwiftData

// MARK: - Today widget (now / next block)

struct TodayEntry: TimelineEntry {
    let date: Date
    let current: WidgetBlock?
    let next: WidgetBlock?
    let blockCount: Int
    /// The proactive digest line cached by the app on foreground (Android ProactiveAssistCache).
    let digest: String?
}

/// A flattened, Codable snapshot of a block for the widget (avoids passing @Model across processes).
struct WidgetBlock {
    let id: String
    let title: String
    let category: String
    let startMinute: Int
    let endMinute: Int
}

struct TodayProvider: TimelineProvider {
    func placeholder(in context: Context) -> TodayEntry {
        TodayEntry(date: .now,
                   current: WidgetBlock(id: "1", title: "Deep work", category: "FOCUS", startMinute: 540, endMinute: 660),
                   next: WidgetBlock(id: "2", title: "Lunch", category: "MEAL", startMinute: 750, endMinute: 795),
                   blockCount: 6,
                   digest: "2 of 6 blocks done · 3 tasks open")
    }

    func getSnapshot(in context: Context, completion: @escaping (TodayEntry) -> Void) {
        completion(loadEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TodayEntry>) -> Void) {
        let entry = loadEntry()
        // Refresh at the next block boundary, else in 15 minutes.
        let refresh = Calendar.current.date(byAdding: .minute, value: 15, to: .now) ?? .now
        completion(Timeline(entries: [entry], policy: .after(refresh)))
    }

    private func nowMinute() -> Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: .now)
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }

    private func loadEntry() -> TodayEntry {
        // A fresh ModelContext is usable on whatever thread the timeline runs on.
        let context = ModelContext(ChronosStore.makeContainer())
        let now = nowMinute()
        let blocks = (try? context.fetch(FetchDescriptor<TimeBlock>()))?
            .filter { Calendar.current.isDateInToday($0.date) }
            .sorted { $0.startMinuteOfDay < $1.startMinuteOfDay } ?? []
        let current = blocks.first { now >= $0.startMinuteOfDay && now < $0.startMinuteOfDay + $0.durationMinutes }
        let next = blocks.first { $0.startMinuteOfDay > now }
        let digest = (UserDefaults(suiteName: ChronosStore.appGroup) ?? .standard).string(forKey: "assist.digest.line")
        return TodayEntry(
            date: .now,
            current: current.map { WidgetBlock(id: $0.id, title: $0.title, category: $0.category,
                                               startMinute: $0.startMinuteOfDay, endMinute: $0.plannedEndMinuteOfDay) },
            next: next.map { WidgetBlock(id: $0.id, title: $0.title, category: $0.category,
                                         startMinute: $0.startMinuteOfDay, endMinute: $0.plannedEndMinuteOfDay) },
            blockCount: blocks.count,
            digest: digest)
    }
}

struct TodayWidgetView: View {
    var entry: TodayEntry
    var body: some View {
        // Wrap the entire card in a Link so tapping anywhere opens the Today section in the app.
        Link(destination: URL(string: "chronosflow://today")!) {
            VStack(alignment: .leading, spacing: 6) {
                if let current = entry.current {
                    Text("NOW").font(.caption2).foregroundStyle(.secondary)
                    Text(current.title).font(.headline)
                    Text("until \(current.endMinute.clockTime)").font(.caption).foregroundStyle(.secondary)
                    focusButton(for: current)
                } else if let next = entry.next {
                    Text("UP NEXT").font(.caption2).foregroundStyle(.secondary)
                    Text(next.title).font(.headline)
                    Text("at \(next.startMinute.clockTime)").font(.caption).foregroundStyle(.secondary)
                    focusButton(for: next)
                } else {
                    Text("Open time").font(.headline)
                }
                Spacer()
                if let digest = entry.digest, !digest.isEmpty {
                    Text(digest).font(.caption2).foregroundStyle(.secondary).lineLimit(2)
                } else {
                    Text("\(entry.blockCount) blocks today").font(.caption2).foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        }
        .containerBackground(.fill.tertiary, for: .widget)
    }

    /// Start a focus session for the block (Android FocusStart) — hands off to the app via the bridge.
    @ViewBuilder private func focusButton(for block: WidgetBlock) -> some View {
        Button(intent: StartFocusIntent(blockID: block.id)) {
            Label("Focus", systemImage: "timer").font(.caption2)
        }
        .buttonStyle(.bordered)
        .tint(ChronosColors.brandPrimary)
    }
}

struct TodayWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ChronosTodayWidget", provider: TodayProvider()) { entry in
            TodayWidgetView(entry: entry)
        }
        .configurationDisplayName("Today")
        .description("Your current and upcoming time blocks.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

#Preview(as: .systemMedium) {
    TodayWidget()
} timeline: {
    TodayEntry(
        date: .now,
        current: WidgetBlock(id: "1", title: "Deep work", category: "FOCUS", startMinute: 540, endMinute: 660),
        next: WidgetBlock(id: "2", title: "Lunch", category: "MEAL", startMinute: 750, endMinute: 795),
        blockCount: 6,
        digest: "2 of 6 blocks done · 3 tasks open"
    )
    TodayEntry(date: .now, current: nil, next: nil, blockCount: 0, digest: nil)
}
