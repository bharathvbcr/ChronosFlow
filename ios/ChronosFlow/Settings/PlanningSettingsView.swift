import SwiftUI

// MARK: - PlanningSettingsView (AI & planning)
//
// The iOS analogue of Android's AI_SETTINGS sidebar page (SidebarPageContent.kt lines 909–972).
// On iOS the on-device Foundation Models path is the only inference route, so the Android "Privacy"
// section (cloud toggle + privacy-mode chips + preview-model toggle) collapses to the on-device
// planning controls; the Android "Planning" section maps 1:1 — planning style plus the three
// behaviour toggles (Protect focus blocks / Add breaks automatically / Preserve manual blocks).

struct PlanningSettingsView: View {
    @State private var settings = ChronosSettings.shared

    var body: some View {
        Form {
            Section {
                Toggle("On-device AI planning", isOn: $settings.aiEnabled)
            } footer: {
                Text("Planning runs entirely on this device via Apple Intelligence — no task, habit, or block titles leave your phone. (On Android this section also offers an opt-in cloud route; iOS is on-device only.)")
            }

            if settings.aiEnabled {
                planningSection
            }
        }
        .navigationTitle("AI & planning")
        .navigationBarTitleDisplayMode(.inline)
    }

    // Mirrors Android's "Planning" section: style picker + the three CheckboxSettings.
    private var planningSection: some View {
        Section {
            Picker("Planning style", selection: $settings.planningStyle) {
                ForEach(PlanningStyle.allCases) { Text($0.label).tag($0) }
            }
            Text(settings.planningStyle.detail)
                .font(.chronosCaption)
                .foregroundStyle(.secondary)

            Toggle("Auto-apply AI day plan", isOn: $settings.autoApplyPlan)

            // Android parity: SidebarPageContent.kt lines 961–963.
            Toggle("Protect focus blocks", isOn: $settings.protectFocusBlocks)
            Toggle("Add breaks automatically", isOn: $settings.addBreaksAutomatically)
            Toggle("Preserve manual blocks", isOn: $settings.preserveManualBlocks)
        } header: {
            Text("Planning")
        } footer: {
            Text(planningFooter)
        }
    }

    private var planningFooter: String {
        var lines: [String] = []
        lines.append(settings.autoApplyPlan
                     ? "AI plans are applied automatically. You can still review and undo."
                     : "AI never changes your plan silently — you review every suggestion before it’s applied.")
        if settings.protectFocusBlocks {
            lines.append("Existing focus blocks are kept when the plan regenerates.")
        }
        if settings.preserveManualBlocks {
            lines.append("Blocks you placed or edited by hand are never overwritten.")
        }
        return lines.joined(separator: " ")
    }
}

#Preview {
    NavigationStack { PlanningSettingsView() }
}
