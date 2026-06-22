import WidgetKit
import SwiftUI

// MARK: - Watch complication / Smart Stack widget
//
// The watchOS-native analogue of the Android Wear OngoingActivity / tile glance. It renders the
// current-or-next time block (or a live focus countdown) on the watch face complications and in the
// Smart Stack. Data comes from the `WatchSnapshot` the watch app caches into the on-watch App Group
// (`WatchSyncPayload.cacheKey`) — the widget never opens a store or talks to the phone directly.

struct NextBlockEntry: TimelineEntry {
    let date: Date
    let current: WatchBlock?
    let next: WatchBlock?
    let focus: WatchFocusState?
    /// Doses still due today (mirrors the snapshot's `medsDueCount`) — surfaced as a footer line in
    /// the rectangular form when non-zero, so the Smart Stack glance flags medication without a
    /// dedicated complication.
    var medsDueCount: Int = 0
}

struct NextBlockProvider: TimelineProvider {
    func placeholder(in context: Context) -> NextBlockEntry {
        NextBlockEntry(date: .now,
                       current: WatchBlock(id: "1", title: "Deep work", startMinute: 540, durationMinutes: 90, category: "FOCUS", isCompleted: false),
                       next: nil, focus: nil)
    }

    func getSnapshot(in context: Context, completion: @escaping (NextBlockEntry) -> Void) {
        completion(loadEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<NextBlockEntry>) -> Void) {
        let entry = loadEntry()
        // Refresh at the next block boundary if we know one, else in 15 minutes.
        let now = nowMinute()
        let nextBoundary = entry.next?.startMinute ?? entry.current.map { $0.startMinute + $0.durationMinutes }
        let refresh: Date
        if let nextBoundary, nextBoundary > now,
           let date = Calendar.current.date(bySettingHour: nextBoundary / 60, minute: nextBoundary % 60, second: 0, of: .now) {
            refresh = date
        } else {
            refresh = Calendar.current.date(byAdding: .minute, value: 15, to: .now) ?? .now
        }
        completion(Timeline(entries: [entry], policy: .after(refresh)))
    }

    private func nowMinute() -> Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: .now)
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }

    private func loadEntry() -> NextBlockEntry {
        let defaults = UserDefaults(suiteName: WatchSyncPayload.watchAppGroup) ?? .standard
        let snapshot: WatchSnapshot? = (defaults.data(forKey: WatchSyncPayload.cacheKey))
            .flatMap { try? JSONDecoder().decode(WatchSnapshot.self, from: $0) }
        let now = nowMinute()
        let blocks = snapshot?.blocks ?? []
        let current = blocks.first { now >= $0.startMinute && now < $0.startMinute + $0.durationMinutes }
        let next = blocks.first { $0.startMinute > now }
        return NextBlockEntry(date: .now, current: current, next: next, focus: snapshot?.focus,
                              medsDueCount: snapshot?.medsDueCount ?? 0)
    }
}

// MARK: - Views

struct NextBlockWidgetView: View {
    @Environment(\.widgetFamily) private var family
    var entry: NextBlockEntry

    var body: some View {
        switch family {
        case .accessoryInline: inlineView
        case .accessoryCircular: circularView
        case .accessoryCorner: cornerView
        default: rectangularView   // .accessoryRectangular + Smart Stack
        }
    }

    // Inline (above the watch face time)
    @ViewBuilder private var inlineView: some View {
        if let focus = entry.focus {
            Label {
                Text("Focus · \(focus.phaseEndsAt, style: .timer)")
            } icon: { Image(systemName: "timer") }
        } else if let b = headline {
            Label(b.title, systemImage: icon(for: b))
        } else {
            Label("Open time", systemImage: "circle.dashed")
        }
    }

    // Circular (gauge-style)
    private var circularView: some View {
        ZStack {
            AccessoryWidgetBackground()
            if let focus = entry.focus {
                VStack(spacing: 0) {
                    Image(systemName: "timer").font(.caption2)
                    Text(focus.phaseEndsAt, style: .timer)
                        .font(.system(size: 11, weight: .semibold)).monospacedDigit()
                        .multilineTextAlignment(.center)
                }
            } else if let b = headline {
                VStack(spacing: 0) {
                    Image(systemName: icon(for: b)).font(.caption2)
                    Text(clock(b.startMinute)).font(.system(size: 11, weight: .medium))
                }
            } else {
                Image(systemName: "circle.dashed")
            }
        }
    }

    // Corner (curved face corner)
    @ViewBuilder private var cornerView: some View {
        if let focus = entry.focus {
            Image(systemName: "timer")
                .widgetLabel { Text("Focus · \(focus.phaseEndsAt, style: .timer)") }
        } else if let b = headline {
            Image(systemName: icon(for: b))
                .widgetLabel { Text(b.title) }
        } else {
            Image(systemName: "circle.dashed").widgetLabel { Text("Open time") }
        }
    }

    // Rectangular (Smart Stack / large complication)
    private var rectangularView: some View {
        VStack(alignment: .leading, spacing: 2) {
            if let focus = entry.focus {
                Text(focus.isPaused ? "FOCUS · PAUSED"
                                    : (focus.awaitingAdvance ? "FOCUS · TAP TO CONTINUE" : "FOCUS"))
                    .font(.caption2).foregroundStyle(.secondary)
                Text(focus.blockTitle).font(.headline).lineLimit(1)
                if focus.isPaused {
                    Text("Paused").font(.caption)
                } else {
                    Text(focus.phaseEndsAt, style: .timer).font(.caption).monospacedDigit()
                }
            } else if let current = entry.current {
                Text("NOW").font(.caption2).foregroundStyle(.secondary)
                Text(current.title).font(.headline).lineLimit(1)
                Text("until \(clock(current.startMinute + current.durationMinutes))")
                    .font(.caption).foregroundStyle(.secondary)
            } else if let next = entry.next {
                Text("UP NEXT").font(.caption2).foregroundStyle(.secondary)
                Text(next.title).font(.headline).lineLimit(1)
                Text("at \(clock(next.startMinute))").font(.caption).foregroundStyle(.secondary)
            } else {
                Text("Open time").font(.headline)
                Text("No blocks scheduled").font(.caption).foregroundStyle(.secondary)
            }
            if entry.medsDueCount > 0 {
                Label("\(entry.medsDueCount) dose\(entry.medsDueCount == 1 ? "" : "s") due",
                      systemImage: "pills.fill")
                    .font(.caption2).foregroundStyle(.red)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .containerBackground(.fill.tertiary, for: .widget)
    }

    private var headline: WatchBlock? { entry.current ?? entry.next }

    private func icon(for block: WatchBlock) -> String {
        switch block.category.uppercased() {
        case "FOCUS", "WORK", "STUDY": "timer"
        case "BREAK": "cup.and.saucer.fill"
        case "MEAL": "fork.knife"
        case "EXERCISE", "WORKOUT": "figure.run"
        case "SLEEP": "moon.zzz.fill"
        case "MEDICATION": "pills.fill"
        default: "calendar"
        }
    }

    private func clock(_ minute: Int) -> String {
        let m = ((minute % 1440) + 1440) % 1440
        return String(format: "%d:%02d", m / 60, m % 60)
    }
}

struct NextBlockComplication: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "ChronosNextBlockComplication", provider: NextBlockProvider()) { entry in
            NextBlockWidgetView(entry: entry)
        }
        .configurationDisplayName("Next Block")
        .description("Your current or next time block — or a live focus countdown.")
        .supportedFamilies([.accessoryInline, .accessoryCircular, .accessoryCorner, .accessoryRectangular])
    }
}
