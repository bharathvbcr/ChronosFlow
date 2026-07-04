import AppIntents
import SwiftUI

// Shared visual language for BOTH Live Activities (schedule block + focus session) so the Lock Screen
// and Dynamic Island read as one system: a leading category accent band, a capsule progress rail, a
// consistent header, and the folded-reminder action chip. Tokens come from ChronosTokens.swift — no
// hex literals live here.

// MARK: - Palette

enum LiveActivityChrome {
    /// The accent color for a schedule category (matches the app-wide `ChronosColors.category`).
    static func accent(_ category: String) -> Color { ChronosColors.category(category) }

    /// Coral "needs attention" highlight for an overdue reminder / ending-soon phase.
    static let urgent = ChronosColors.brandAccent

    /// Legible dark Lock Screen background tint (system draws over the wallpaper — keep it high
    /// contrast; color comes from the accent band, progress rail, and chip, not the backdrop).
    static let cardBackground = Color.black.opacity(0.55)

    /// A faint accent wash placed behind the header row so the card still reads as "tinted glass"
    /// without hurting text contrast.
    static func headerWash(_ accent: Color) -> Color { accent.opacity(0.16) }
}

// MARK: - Building blocks

/// A leading vertical accent band — the shared "what kind of thing is this" cue on both LA cards.
struct LAAccentBand: View {
    var color: Color
    var height: CGFloat = 46

    var body: some View {
        Capsule()
            .fill(color.gradient)
            .frame(width: 4, height: height)
    }
}

/// The uppercase category/eyebrow label above the title (e.g. "NOW · 3 TO GO", "FOCUS", "REMINDER").
struct LAHeaderLabel: View {
    var text: String
    var color: Color = .secondary

    var body: some View {
        Text(text)
            .font(.caption2.weight(.semibold))
            .textCase(.uppercase)
            .foregroundStyle(color)
            .lineLimit(1)
    }
}

/// A flat capsule progress rail with a tintable fill — shared by the block card and the focus flat bar.
struct LAProgressBar: View {
    var progress: Double
    var color: Color
    var height: CGFloat = 6

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Color.white.opacity(0.14))
                Capsule()
                    .fill(color.gradient)
                    .frame(width: max(height, geo.size.width * min(max(progress, 0), 1)))
            }
        }
        .frame(height: height)
        .accessibilityHidden(true)
    }
}

// MARK: - Folded reminder chip

/// The inline action chip that carries one due medication / task / habit into the schedule Live
/// Activity — the surface that replaces a stack of separate banners. `compact` trims it for the
/// Dynamic Island expanded region.
struct ReminderChipView: View {
    var chip: BlockActivityAttributes.ReminderChip
    var compact: Bool = false

    private var tint: Color {
        chip.isOverdue ? LiveActivityChrome.urgent : ChronosColors.category(chip.kind.categoryKey)
    }

    var body: some View {
        HStack(spacing: ChronosSpacing.small) {
            Image(systemName: chip.kind.symbol)
                .font(.caption)
                .foregroundStyle(tint)
                .frame(width: 18)

            VStack(alignment: .leading, spacing: 1) {
                Text(chip.title)
                    .font(.caption.weight(.semibold))
                    .lineLimit(1)
                if !compact {
                    Text(chip.detail)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: ChronosSpacing.small)

            actionButton
        }
        .padding(.horizontal, ChronosSpacing.small)
        .padding(.vertical, 6)
        .background(tint.opacity(0.16), in: Capsule())
        .overlay(Capsule().strokeBorder(tint.opacity(0.35), lineWidth: 0.5))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(chip.title), \(chip.detail)")
        .accessibilityHint("Double tap to \(chip.kind.actionTitle.lowercased())")
    }

    @ViewBuilder
    private var actionButton: some View {
        switch chip.kind {
        case .medication:
            chipButton(intent: MarkDoseTakenIntent(planID: chip.entityID))
        case .task:
            chipButton(intent: CompleteTaskIntent(taskID: chip.entityID))
        case .habit:
            chipButton(intent: ToggleHabitIntent(habitID: chip.entityID))
        }
    }

    private func chipButton(intent: some AppIntent) -> some View {
        Button(intent: intent) {
            Text(chip.kind.actionTitle)
                .font(.caption2.weight(.bold))
                .padding(.horizontal, 4)
        }
        .buttonStyle(.borderedProminent)
        .buttonBorderShape(.capsule)
        .tint(tint)
        .controlSize(.small)
    }
}

/// Standalone action button for `.reminder` mode where the chip headline is already the card title.
struct ReminderActionButton: View {
    var chip: BlockActivityAttributes.ReminderChip
    var compact: Bool = false

    private var tint: Color {
        chip.isOverdue ? LiveActivityChrome.urgent : ChronosColors.category(chip.kind.categoryKey)
    }

    var body: some View {
        HStack {
            if !compact {
                Label(chip.kind.actionTitle, systemImage: chip.kind.symbol)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(tint)
            }
            Spacer(minLength: ChronosSpacing.small)
            switch chip.kind {
            case .medication:
                chipButton(intent: MarkDoseTakenIntent(planID: chip.entityID))
            case .task:
                chipButton(intent: CompleteTaskIntent(taskID: chip.entityID))
            case .habit:
                chipButton(intent: ToggleHabitIntent(habitID: chip.entityID))
            }
        }
    }

    private func chipButton(intent: some AppIntent) -> some View {
        Button(intent: intent) {
            Text(chip.kind.actionTitle)
                .font(.caption2.weight(.bold))
                .padding(.horizontal, compact ? 4 : 8)
        }
        .buttonStyle(.borderedProminent)
        .buttonBorderShape(.capsule)
        .tint(tint)
        .controlSize(.small)
    }
}

/// Renders the folded reminders beneath the block card: up to `maxVisible` action chips, then a
/// compact "＋N more due" caption once the fold is capped, so the surface conveys "several things are
/// due" without growing into an unbounded list. Shared by the Lock Screen and the Dynamic Island
/// expanded region. In `.reminder` mode the primary chip is already the card headline, so callers
/// pass `dropFirst: 1` to stack only the remainder.
struct FoldedRemindersView: View {
    var reminders: [BlockActivityAttributes.ReminderChip]
    var dropFirst: Int = 0
    var maxVisible: Int = 2
    var compact: Bool = false

    private var shown: [BlockActivityAttributes.ReminderChip] {
        Array(reminders.dropFirst(dropFirst).prefix(maxVisible))
    }
    private var overflow: Int {
        max(0, reminders.count - dropFirst - maxVisible)
    }

    var body: some View {
        if !shown.isEmpty || overflow > 0 {
            VStack(alignment: .leading, spacing: 4) {
                ForEach(shown, id: \.self) { chip in
                    ReminderChipView(chip: chip, compact: compact)
                }
                if overflow > 0 {
                    Label("\(overflow) more due", systemImage: "ellipsis.circle")
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(.secondary)
                        .padding(.leading, 2)
                }
            }
        }
    }
}

/// The Dynamic Island minimal/compact glyph for a folded reminder (no room for the chip itself).
struct ReminderGlyph: View {
    var chip: BlockActivityAttributes.ReminderChip
    var body: some View {
        Image(systemName: chip.kind.symbol)
            .foregroundStyle(chip.isOverdue ? LiveActivityChrome.urgent : ChronosColors.category(chip.kind.categoryKey))
    }
}
