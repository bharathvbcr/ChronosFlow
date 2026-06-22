import WidgetKit
import SwiftUI

/// The WidgetKit extension bundle — the iOS-native equivalent of the Android Glance widgets
/// (ChronosGlanceWidget, AgendaGlanceWidget, TasksGlanceWidget, HabitsGlanceWidget,
/// MedicationGlanceWidget, the home-screen focus glance) plus the Focus Live Activity
/// (Android Live Updates).
@main
struct ChronosWidgetBundle: WidgetBundle {
    var body: some Widget {
        TodayWidget()
        AgendaWidget()
        TasksWidget()
        HabitsWidget()
        MedicationWidget()
        FocusWidget()
        FocusLiveActivityWidget()
    }
}
