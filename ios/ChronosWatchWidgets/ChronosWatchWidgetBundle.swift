import WidgetKit
import SwiftUI

/// The watchOS WidgetKit extension bundle — face complications + Smart Stack widget for ChronosFlow,
/// the iOS-native analogue of the Android Wear tiles/ongoing glance.
@main
struct ChronosWatchWidgetBundle: WidgetBundle {
    var body: some Widget {
        NextBlockComplication()
    }
}
