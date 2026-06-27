import SwiftUI

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

    @Namespace private var pillNamespace
    @State private var editor: QuickAddEditor?
    @State private var capture = ""
    @FocusState private var captureFocused: Bool

    var body: some View {
        VStack(alignment: .trailing, spacing: 10) {
            if shell.quickAddExpanded {
                quickAddMenu
                    .transition(.scale(scale: 0.85, anchor: .bottomTrailing).combined(with: .opacity))
            }
            bar
        }
        .padding(.horizontal, 16)
        .sheet(item: $editor) { quickAddEditorView(for: $0) }
    }

    // MARK: Bar

    private var bar: some View {
        HStack(spacing: 4) {
            ForEach(PrimaryTab.allCases) { tab in
                tabItem(tab)
            }
            quickAddButton
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(.regularMaterial, in: Capsule())
        .overlay(Capsule().strokeBorder(Color.primary.opacity(0.06)))
        .shadow(color: .black.opacity(0.18), radius: 12, x: 0, y: 6)
    }

    private func tabItem(_ tab: PrimaryTab) -> some View {
        let selected = shell.selectedTab == tab
        return Button {
            withAnimation(.spring(response: 0.32, dampingFraction: 0.82)) {
                shell.select(tab)
            }
        } label: {
            VStack(spacing: 3) {
                ZStack {
                    Image(systemName: tab.icon)
                        .font(.system(size: 19, weight: .semibold))
                    if tab == .focus && focusActive {
                        Circle()
                            .fill(ChronosColors.brandPrimary)
                            .frame(width: 7, height: 7)
                            .offset(x: 11, y: -10)
                    }
                    // Off-today badge on the Today tab — mirrors Android `todayBadgeValue`: when the
                    // dial is browsing another day, show that day-of-month so it reads from the bar.
                    if tab == .today, let offDay = offTodayDayOfMonth {
                        Text(offDay)
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 4)
                            .frame(minWidth: 15, minHeight: 15)
                            .background(ChronosColors.brandPrimary, in: Capsule())
                            .offset(x: 12, y: -10)
                    }
                }
                Text(tab.label)
                    .font(.caption2.weight(.medium))
            }
            .foregroundStyle(selected ? Color.white : Color.secondary)
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
        // Double-tapping Today snaps the browsed day back to today (Android TARGET_TODAY_RESET).
        .simultaneousGesture(
            tab == .today
                ? TapGesture(count: 2).onEnded {
                    withAnimation(.spring(response: 0.32, dampingFraction: 0.82)) { shell.resetToToday() }
                }
                : nil
        )
        .accessibilityAddTraits(selected ? [.isSelected, .isButton] : .isButton)
    }

    /// The day-of-month string to badge on the Today tab when the dial is off today (else nil).
    private var offTodayDayOfMonth: String? {
        guard !Calendar.current.isDateInToday(shell.selectedDate) else { return nil }
        return String(Calendar.current.component(.day, from: shell.selectedDate))
    }

    private var quickAddButton: some View {
        Button {
            withAnimation(.spring(response: 0.3, dampingFraction: 0.78)) {
                shell.quickAddExpanded.toggle()
            }
        } label: {
            Image(systemName: "plus")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 48, height: 48)
                .background(ChronosColors.brandPrimary, in: Circle())
                .rotationEffect(.degrees(shell.quickAddExpanded ? 45 : 0))
        }
        .buttonStyle(.plain)
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
                        }
                        .buttonStyle(.plain)
                        .disabled(quickCapturePreviewLabel(capture) == nil)
                    }
                }
                if let preview = quickCapturePreviewLabel(capture) {
                    Text(preview).font(.caption).foregroundStyle(.secondary)
                } else if !capture.isEmpty {
                    Text("Keep typing for a task, med, or habit.").font(.caption).foregroundStyle(.secondary)
                }
            }
            .padding(10)
            .background(Color.primary.opacity(0.05), in: RoundedRectangle(cornerRadius: 16, style: .continuous))

            ForEach(quickAddActions(settings)) { action in
                Button {
                    handle(action)
                } label: {
                    Label(action.label, systemImage: action.icon)
                        .font(.callout.weight(.medium))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.vertical, 10)
                        .padding(.horizontal, 12)
                        .background(Color.primary.opacity(0.05), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .buttonStyle(.plain)
                .foregroundStyle(.primary)
            }
        }
        .padding(10)
        .frame(maxWidth: 320)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .shadow(color: .black.opacity(0.18), radius: 14, x: 0, y: 8)
    }

    private func handle(_ action: QuickAddAction) {
        withAnimation(.easeOut(duration: 0.2)) { shell.quickAddExpanded = false }
        switch action.target {
        case .editor(let which): editor = which
        case .route(let route):  shell.open(route)
        }
    }

    private func submitCapture() {
        let trimmed = capture.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 3 else { return }
        capture = ""
        captureFocused = false
        withAnimation(.easeOut(duration: 0.2)) { shell.quickAddExpanded = false }
        editor = .typed(trimmed)
    }
}
