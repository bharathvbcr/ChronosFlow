import SwiftUI

// MARK: - AppearanceSettingsView
//
// The iOS analogue of Android's APPEARANCE sidebar page (SidebarPageContent.kt lines 1243–1303):
// theme mode, backdrop style, dynamic color, glass surfaces, reduce motion, and increase contrast.
// Pushed from the Settings index as its own screen so it gets dedicated real estate + a back
// affordance, mirroring Android's full-screen sidebar page rather than a single shared Form section.

struct AppearanceSettingsView: View {
    @State private var settings = ChronosSettings.shared

    var body: some View {
        Form {
            Section {
                Picker("Theme", selection: $settings.themeMode) {
                    ForEach(ThemeMode.allCases) { Text($0.label).tag($0) }
                }
                Picker("Backdrop", selection: $settings.backdrop) {
                    ForEach(BackdropStyle.allCases) { Text($0.label).tag($0) }
                }
            } header: {
                Text("Theme")
            } footer: {
                Text("Appearance, motion, and dial density. The backdrop applies live behind the app — mirrors the Android Appearance page.")
            }

            Section("Surfaces & motion") {
                Toggle("Dynamic color", isOn: $settings.dynamicColorEnabled)
                Toggle("Liquid Glass surfaces", isOn: $settings.glassEnabled)
                Toggle("Reduce motion", isOn: $settings.reduceMotionPreference)
                Toggle("Increase contrast", isOn: $settings.increaseContrast)
            }

            Section {
                Toggle("Compact editors", isOn: $settings.adaptiveEditorEnabled)
            } header: {
                Text("Editing")
            } footer: {
                Text(settings.adaptiveEditorEnabled
                     ? "Task, medication, habit, goal, and block forms open compact — the essentials plus a row of quick-add chips, with the rest under “More options.” Sections you use often surface automatically; long-press any section to pin or hide it."
                     : "Editors show every field at once. Turn on to open forms compact with advanced options tucked away.")
            }
        }
        .navigationTitle("Appearance")
        .navigationBarTitleDisplayMode(.inline)
    }
}

#Preview {
    NavigationStack { AppearanceSettingsView() }
}
