import SwiftUI

// MARK: - Card editor scaffold
//
// The shared chrome every card editor (task / medication / habit / goal / block / routine) renders inside,
// so all of them share one clean information architecture instead of each hand-rolling a dense
// `Form`:
//
//   ┌─ essentials  (title + notes — always visible)
//   ├─ quick-bar   (a ChipFlowLayout of attribute chips: 📅 Due · 🚩 Priority · 🔁 Repeat …)
//   ├─ primary     (sections the user pinned / uses often / that already hold data)
//   └─ More options (everything else, collapsed until asked for)
//
// It stays `Form`-based on purpose: keeping every field group a real `Section` preserves list
// semantics the editors rely on (swipe-to-delete, drag-to-reorder, segmented pickers, grouped
// insets). "Progressive disclosure" here means *which* sections render, not a switch to a custom
// scroll view. Disclosure memory (pin / hide / adaptive promotion) lives in `EditorLayoutStore`;
// the compact-vs-flat master switch is `ChronosSettings.adaptiveEditorEnabled`.

// MARK: Config types

/// One collapsible field group supplied by an editor. `content` is the section's rows *without* an
/// enclosing `Section` — the scaffold wraps it so it can attach the header + pin/hide menu.
struct EditorSection: Identifiable {
    let id: String
    let title: String
    let systemImage: String
    /// Optional trailing header summary, e.g. "3 steps" / "2 attached".
    var badge: String?
    /// Essentials-adjacent: never demoted under "More options" (e.g. Priority for a task).
    var alwaysPrimary: Bool
    /// The section currently holds data, so it shows without being pinned (natural for editing).
    var hasValue: Bool
    let content: AnyView

    init<C: View>(
        id: String,
        title: String,
        systemImage: String,
        badge: String? = nil,
        alwaysPrimary: Bool = false,
        hasValue: Bool = false,
        @ViewBuilder content: () -> C
    ) {
        self.id = id
        self.title = title
        self.systemImage = systemImage
        self.badge = badge
        self.alwaysPrimary = alwaysPrimary
        self.hasValue = hasValue
        self.content = AnyView(content())
    }
}

/// A quick-bar attribute chip. `id` names the section it reveals; `value` is the current setting
/// (nil = unset → muted chip; non-nil = set → brand-tinted, with an optional clear "✕").
struct EditorChip: Identifiable {
    let id: String
    let systemImage: String
    let title: String
    var value: String?
    var onClear: (() -> Void)?

    init(id: String, systemImage: String, title: String, value: String? = nil, onClear: (() -> Void)? = nil) {
        self.id = id
        self.systemImage = systemImage
        self.title = title
        self.value = value
        self.onClear = onClear
    }
}

// MARK: Scaffold

struct CardEditorScaffold<Header: View>: View {
    /// Stable editor identity for disclosure memory ("task", "medication", …).
    let kind: String
    let navTitle: String
    let chips: [EditorChip]
    let sections: [EditorSection]
    var trailingMenu: AnyView?
    var footer: AnyView?
    let saveDisabled: Bool
    let onCancel: () -> Void
    let onSave: () -> Void
    /// Non-nil enables the "Add & new" bottom-bar button (new-item mode only).
    var onAddNew: (() -> Void)?
    @ViewBuilder var header: () -> Header

    init(
        kind: String,
        navTitle: String,
        chips: [EditorChip],
        sections: [EditorSection],
        trailingMenu: AnyView? = nil,
        footer: AnyView? = nil,
        saveDisabled: Bool,
        onCancel: @escaping () -> Void,
        onSave: @escaping () -> Void,
        onAddNew: (() -> Void)? = nil,
        @ViewBuilder header: @escaping () -> Header
    ) {
        self.kind = kind
        self.navTitle = navTitle
        self.chips = chips
        self.sections = sections
        self.trailingMenu = trailingMenu
        self.footer = footer
        self.saveDisabled = saveDisabled
        self.onCancel = onCancel
        self.onSave = onSave
        self.onAddNew = onAddNew
        self.header = header
    }

    /// Sections the user explicitly revealed this session via a quick-bar chip.
    @State private var expanded: Set<String> = []
    @State private var moreExpanded = false

    private var layout: EditorLayoutStore { EditorLayoutStore.shared }
    private var adaptive: Bool { ChronosSettings.shared.adaptiveEditorEnabled }

    var body: some View {
        NavigationStack {
            Form {
                Section { header() }
                if !chips.isEmpty { chipBar }
                ForEach(primarySections) { section(for: $0) }
                if !moreSections.isEmpty { moreToggle }
                if moreExpanded { ForEach(moreSections) { section(for: $0) } }
                if let footer { Section { footer } }
            }
            .navigationTitle(navTitle)
            .toolbarTitleDisplayMode(.inline)
            .toolbar { toolbarContent }
        }
        .presentationDetents([.medium, .large])
    }

    // MARK: Placement

    /// When `adaptive` is off, every section is primary (the old flat layout). When on, a section is
    /// primary if it's essentials-adjacent, was revealed this session, already holds data, is pinned,
    /// or has been adaptively promoted — otherwise it lives under "More options".
    private func isPrimary(_ s: EditorSection) -> Bool {
        if !adaptive { return true }
        if s.alwaysPrimary { return true }
        if expanded.contains(s.id) { return true }
        if layout.isHidden(kind, s.id) { return false }
        if s.hasValue { return true }
        if layout.isPinned(kind, s.id) { return true }
        return layout.isAutoSurfaced(kind, s.id)
    }

    private var primarySections: [EditorSection] { sections.filter(isPrimary) }
    private var moreSections: [EditorSection] { sections.filter { !isPrimary($0) } }

    // MARK: Pieces

    private var chipBar: some View {
        Section {
            ChipFlowLayout(spacing: ChronosSpacing.small) {
                ForEach(chips) { chip in
                    EditorAttributeChip(chip: chip) { reveal(chip.id) }
                }
            }
            .listRowInsets(EdgeInsets(top: ChronosSpacing.small, leading: ChronosSpacing.standard,
                                      bottom: ChronosSpacing.small, trailing: ChronosSpacing.standard))
        }
    }

    private func section(for s: EditorSection) -> some View {
        Section {
            s.content
        } header: {
            HStack {
                Label(s.title, systemImage: s.systemImage)
                Spacer(minLength: ChronosSpacing.small)
                if let badge = s.badge {
                    Text(badge).font(.chronosCaption).foregroundStyle(.secondary)
                }
            }
            .contextMenu { pinHideMenu(s) }
        }
    }

    @ViewBuilder private func pinHideMenu(_ s: EditorSection) -> some View {
        Button {
            layout.togglePin(kind, s.id)
        } label: {
            Label(layout.isPinned(kind, s.id) ? "Unpin" : "Pin to top",
                  systemImage: layout.isPinned(kind, s.id) ? "pin.slash" : "pin")
        }
        Button {
            withAnimation(ChronosMotion.snappy) {
                layout.toggleHidden(kind, s.id)
                _ = expanded.remove(s.id)
            }
        } label: {
            Label(layout.isHidden(kind, s.id) ? "Show by default" : "Hide unless needed",
                  systemImage: layout.isHidden(kind, s.id) ? "eye" : "eye.slash")
        }
    }

    private var moreToggle: some View {
        Section {
            Button {
                withAnimation(ChronosMotion.snappy) { moreExpanded.toggle() }
            } label: {
                HStack {
                    Label(moreExpanded ? "Fewer options" : "More options",
                          systemImage: "slider.horizontal.3")
                    Spacer()
                    Image(systemName: moreExpanded ? "chevron.up" : "chevron.down")
                        .foregroundStyle(.secondary)
                }
            }
        } footer: {
            if !moreExpanded {
                Text("\(moreSections.count) more option\(moreSections.count == 1 ? "" : "s") — long-press any section to pin or hide it.")
                    .font(.chronosCaption)
            }
        }
    }

    @ToolbarContentBuilder private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .cancellationAction) { Button("Cancel", action: onCancel) }
        if let trailingMenu {
            ToolbarItem(placement: .topBarTrailing) { trailingMenu }
        }
        if let onAddNew {
            ToolbarItem(placement: .bottomBar) {
                Button {
                    for s in sections where s.hasValue { layout.recordOpen(kind, s.id) }
                    onAddNew()
                } label: { Label("Add & new", systemImage: "plus.circle") }
                .disabled(saveDisabled)
            }
        }
        ToolbarItem(placement: .confirmationAction) {
            Button("Save") {
                for s in sections where s.hasValue { layout.recordOpen(kind, s.id) }
                onSave()
            }
            .disabled(saveDisabled)
        }
    }

    /// Toggle a section into view from its chip. Revealing (not re-collapsing) records an "open" so
    /// the section adaptively promotes above "More options" over time.
    private func reveal(_ id: String) {
        withAnimation(ChronosMotion.snappy) {
            if expanded.contains(id) {
                _ = expanded.remove(id)
            } else {
                _ = expanded.insert(id)
                layout.recordOpen(kind, id)
            }
        }
    }
}

// MARK: - Quick-bar chip

/// A single attribute chip in the editor quick-bar. Muted when unset (shows the attribute name),
/// brand-tinted when set (shows the value) with an optional clear affordance. The clear "✕" is a
/// sibling button — not nested inside the reveal button — so both hit-targets stay reliable in a
/// `Form` row.
struct EditorAttributeChip: View {
    let chip: EditorChip
    let onTap: () -> Void

    private var isSet: Bool { chip.value != nil }

    var body: some View {
        HStack(spacing: ChronosSpacing.micro) {
            Button(action: onTap) {
                HStack(spacing: ChronosSpacing.micro) {
                    Image(systemName: chip.systemImage).font(.chronosCaption)
                    Text(isSet ? (chip.value ?? "") : chip.title).font(.chronosCaption)
                }
            }
            .buttonStyle(.plain)
            if isSet, let onClear = chip.onClear {
                Button {
                    withAnimation(ChronosMotion.snappy) { onClear() }
                } label: {
                    Image(systemName: "xmark.circle.fill").font(.caption2)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Clear \(chip.title)")
            }
        }
        .padding(.horizontal, ChronosSpacing.compact)
        .padding(.vertical, ChronosSpacing.small)
        .background(isSet ? AnyShapeStyle(ChronosColors.brandPrimary) : AnyShapeStyle(Color(.tertiarySystemFill)),
                    in: Capsule())
        .foregroundStyle(isSet ? ChronosColors.onBrand : .primary)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(isSet ? "\(chip.title): \(chip.value ?? "")" : "Set \(chip.title)")
    }
}

// MARK: - Shared chip primitives
//
// Promoted from `TaskEditorSheet` (were `private`) so every editor and the scaffold reuse them.

/// A pill-shaped single-select option chip (Android `ChronosOptionChips`).
struct SelectChip: View {
    let label: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.chronosCaption)
                .padding(.horizontal, ChronosSpacing.compact)
                .padding(.vertical, ChronosSpacing.small)
                .background(selected ? ChronosColors.brandPrimary : Color(.tertiarySystemFill), in: Capsule())
                .foregroundStyle(selected ? ChronosColors.onBrand : .primary)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// A minimal flowing wrap layout (iOS 16+ `Layout`): left-to-right, wrapping when width is exceeded.
struct ChipFlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var rowWidth: CGFloat = 0
        var rowHeight: CGFloat = 0
        var totalHeight: CGFloat = 0
        var totalWidth: CGFloat = 0

        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if rowWidth + size.width > maxWidth, rowWidth > 0 {
                totalHeight += rowHeight + spacing
                totalWidth = max(totalWidth, rowWidth - spacing)
                rowWidth = 0
                rowHeight = 0
            }
            rowWidth += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        totalHeight += rowHeight
        totalWidth = max(totalWidth, rowWidth - spacing)
        return CGSize(width: min(totalWidth, maxWidth), height: totalHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) {
        let maxWidth = bounds.width
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0

        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x + size.width > bounds.minX + maxWidth, x > bounds.minX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
