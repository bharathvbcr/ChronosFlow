import SwiftUI
import SwiftData

/// The floating bottom bar — the iOS rebuild of Android's `ChronosCompactFloatingBottomBar`.
///
/// A single rounded pill containing the **three** primary tabs (Plan · Today · Focus) with a
/// sliding selection highlight, plus an integrated Quick-Add "+" FAB at the trailing end. Tapping
/// the FAB expands a create menu **in place above the bar** (Android `ChronosQuickAddMenu`) instead
/// of presenting a separate modal sheet — this is the core flow that made the shells feel different.
struct ShellBottomBar: View {
    let shell: ShellState
    @Bindable var settings: ChronosSettings
    /// Drives the Focus tab's "session running" dot, mirroring Android's `focusActive` badge.
    var focusActive: Bool

    /// Feeds the Today tab's missed-count badge fallback (Android `missedBlocksCount`).
    @Query private var allBlocks: [TimeBlock]

    @Namespace private var pillNamespace
    @State private var editor: QuickAddEditor?
    @State private var capture = ""
    @FocusState private var captureFocused: Bool
    /// Increments on a quick-capture submit to fire a one-shot haptic tick.
    @State private var captureFeedback = 0

    var body: some View {
        VStack(alignment: .trailing, spacing: ChronosSpacing.compact) {
            if shell.quickAddExpanded {
                quickAddMenu
                    .transition(.scale(scale: 0.85, anchor: .bottomTrailing).combined(with: .opacity))
            }
            bar
        }
        .padding(.horizontal, ChronosSpacing.standard)
        .sheet(item: $editor) { quickAddEditorView(for: $0) }
        // Follow the SleepView .sensoryFeedback convention (no ad-hoc UIImpactFeedbackGenerator).
        .sensoryFeedback(.impact, trigger: shell.quickAddExpanded) { _, new in new }
        .sensoryFeedback(.impact, trigger: captureFeedback)
    }

    // MARK: Bar

    private var bar: some View {
        HStack(spacing: ChronosSpacing.micro) {
            ForEach(PrimaryTab.allCases) { tab in
                tabItem(tab)
            }
            quickAddButton
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(.regularMaterial, in: Capsule())
        .overlay(Capsule().strokeBorder(Color.primary.opacity(0.06)))
        .shadow(color: ChronosColors.shellShadow, radius: 12, x: 0, y: 6)
    }

    private func tabItem(_ tab: PrimaryTab) -> some View {
        let selected = shell.selectedTab == tab
        return Button {
            withAnimation(ChronosMotion.snappy) {
                shell.select(tab)
            }
        } label: {
            VStack(spacing: 3) {
                ZStack {
                    Image(systemName: tab.icon)
                        .font(.system(.title3, design: .rounded).weight(.semibold))
                        .imageScale(.large)
                    if tab == .focus && focusActive {
                        Circle()
                            .fill(ChronosColors.brandPrimary)
                            .frame(width: 7, height: 7)
                            .offset(x: 11, y: -10)
                            .accessibilityHidden(true)
                    }
                    // Today-tab badge — mirrors Android `todayBadgeValue`: when the dial is browsing
                    // another day, show that day-of-month; on today, fall back to the missed count.
                    if tab == .today, let badge = todayBadgeValue {
                        Text(badge)
                            .font(.system(.caption2, design: .rounded).weight(.bold))
                            .foregroundStyle(ChronosColors.onBrand)
                            .padding(.horizontal, 4)
                            .frame(minWidth: 15, minHeight: 15)
                            .background(ChronosColors.brandPrimary, in: Capsule())
                            .offset(x: 12, y: -10)
                            .accessibilityHidden(true)
                    }
                }
                Text(tab.label)
                    .font(.caption2.weight(.medium))
            }
            .foregroundStyle(selected ? ChronosColors.onBrand : Color.secondary)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 7)
            .background {
                if selected {
                    Capsule()
                        .fill(ChronosColors.brandPrimary)
                        .matchedGeometryEffect(id: "selectionPill", in: pillNamespace)
                }
            }
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .pressable()
        // Double-tapping Today snaps the browsed day back to today (Android TARGET_TODAY_RESET).
        .simultaneousGesture(
            tab == .today
                ? TapGesture(count: 2).onEnded {
                    withAnimation(ChronosMotion.snappy) { shell.resetToToday() }
                }
                : nil
        )
        .accessibilityAddTraits(selected ? [.isSelected, .isButton] : .isButton)
        .accessibilityValue(accessibilityValue(for: tab))
        // Reword "double tap" — VoiceOver speaks that as its own activation cue.
        .accessibilityHint(tab == .today && offTodayDayOfMonth != nil ? "Activate twice to jump back to today" : "")
    }

    /// The day-of-month string to badge on the Today tab when the dial is off today (else nil).
    private var offTodayDayOfMonth: String? {
        guard !Calendar.current.isDateInToday(shell.selectedDate) else { return nil }
        return String(Calendar.current.component(.day, from: shell.selectedDate))
    }

    /// Today's blocks whose scheduled end has passed without a recorded completion — the same
    /// done rule the Today tab uses (`actualEndMinuteOfDay != nil` = done).
    private var missedTodayCount: Int {
        let now = Calendar.current.dateComponents([.hour, .minute], from: .now)
        let nowMinute = (now.hour ?? 0) * 60 + (now.minute ?? 0)
        return allBlocks.filter {
            Calendar.current.isDateInToday($0.date)
                && $0.plannedEndMinuteOfDay <= nowMinute
                && $0.actualEndMinuteOfDay == nil
        }.count
    }

    /// Android `todayBadgeValue`: the viewed-date indicator outranks the missed-count fallback.
    private var todayBadgeValue: String? {
        if let offDay = offTodayDayOfMonth { return offDay }
        let missed = missedTodayCount
        guard missed > 0 else { return nil }
        return missed > 99 ? "99+" : String(missed)
    }

    /// Spoken status for a tab — the browsed day on Today when off-today, the running state on Focus.
    private func accessibilityValue(for tab: PrimaryTab) -> String {
        switch tab {
        case .today where offTodayDayOfMonth != nil:
            return "Viewing \(Self.browsedDayFormatter.string(from: shell.selectedDate))"
        case .today where missedTodayCount > 0:
            return "\(missedTodayCount) missed block\(missedTodayCount == 1 ? "" : "s") today"
        case .focus where focusActive:
            return "Focus session running"
        default:
            return ""
        }
    }

    private static let browsedDayFormatter: DateFormatter = {
        let f = DateFormatter()
        f.setLocalizedDateFormatFromTemplate("MMMMd")
        return f
    }()

    private var quickAddButton: some View {
        Button {
            withAnimation(ChronosMotion.bouncy) {
                shell.quickAddExpanded.toggle()
            }
        } label: {
            Image(systemName: "plus")
                .font(.system(size: 20, weight: .semibold, design: .rounded))
                .imageScale(.large)
                .foregroundStyle(ChronosColors.onBrand)
                .frame(width: 48, height: 48)
                .background(ChronosColors.brandPrimary, in: Circle())
                .rotationEffect(.degrees(shell.quickAddExpanded ? 45 : 0))
        }
        .buttonStyle(.plain)
        .pressable()
        .accessibilityLabel(shell.quickAddExpanded ? "Close quick create" : "Open quick create")
    }

    // MARK: Quick-Add menu

    private var quickAddMenu: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Natural-language quick-capture with live preview (Android ChronosQuickCaptureInput).
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Image(systemName: "sparkle.magnifyingglass")
                        .foregroundStyle(ChronosColors.brandPrimary)
                    TextField("Type a task, med, habit, or goal", text: $capture)
                        .submitLabel(.done)
                        .focused($captureFocused)
                        .onSubmit(submitCapture)
                    if !capture.isEmpty {
                        Button(action: submitCapture) {
                            Image(systemName: "arrow.up.circle.fill")
                                .font(.title3)
                                .foregroundStyle(quickCapturePreviewLabel(capture) == nil ? Color.secondary : ChronosColors.brandPrimary)
                                .frame(minWidth: 48, minHeight: 48)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .disabled(quickCapturePreviewLabel(capture) == nil)
                        .accessibilityLabel("Add captured item")
                    }
                }
                if let preview = quickCapturePreviewLabel(capture) {
                    Text(preview).font(.caption).foregroundStyle(.secondary)
                } else if !capture.isEmpty {
                    Text("Keep typing for a task, med, or habit.").font(.caption).foregroundStyle(.secondary)
                }
            }
            .padding(ChronosSpacing.compact)
            .background(Color.primary.opacity(0.05), in: RoundedRectangle(cornerRadius: ChronosRadius.medium, style: .continuous))

            ForEach(quickAddActions(settings)) { action in
                Button {
                    handle(action)
                } label: {
                    Label(action.label, systemImage: action.icon)
                        .font(.callout.weight(.medium))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.vertical, 10)
                        .padding(.horizontal, 12)
                        .background(Color.primary.opacity(0.05), in: RoundedRectangle(cornerRadius: ChronosRadius.medium, style: .continuous))
                }
                .buttonStyle(.plain)
                .foregroundStyle(.primary)
                .pressable()
            }
        }
        .padding(10)
        .frame(maxWidth: 320)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: ChronosRadius.extraLarge, style: .continuous))
        .shadow(color: ChronosColors.shellShadow, radius: 14, x: 0, y: 8)
    }

    private func handle(_ action: QuickAddAction) {
        withAnimation(ChronosMotion.smooth) { shell.quickAddExpanded = false }
        switch action.target {
        case .editor(let which): editor = which
        case .route(let route):  shell.open(route)
        }
    }

    private func submitCapture() {
        let trimmed = capture.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 3 else { return }
        captureFeedback += 1
        capture = ""
        captureFocused = false
        withAnimation(ChronosMotion.smooth) { shell.quickAddExpanded = false }
        // Question-shaped captures go to the assistant (Android's ask-assistant palette row);
        // everything else opens the editor its classification names.
        if quickCaptureIsQuestion(trimmed) {
            shell.showAssistant = true
        } else {
            editor = .typed(trimmed)
        }
    }
}
