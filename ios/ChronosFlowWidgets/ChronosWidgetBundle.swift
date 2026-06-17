import WidgetKit
import SwiftUI

/// The WidgetKit extension bundle — the iOS-native equivalent of the Android Glance widgets
/// (ChronosGlanceWidget, TasksGlanceWidget, HabitsGlanceWidget, MedicationGlanceWidget) plus the
/// Focus Live Activity (Android Live Updates).
@main
struct ChronosWidgetBundle: WidgetBundle {
    var body: some Widget {
        TodayWidget()
        TasksWidget()
        HabitsWidget()
        MedicationWidget()
        FocusLiveActivityWidget()
    }
}
