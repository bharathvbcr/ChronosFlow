import SwiftUI

/// The watchOS companion app — the iOS-native analogue of the Android `wear/` module.
///
/// ARCHITECTURE: Apple Watch and iPhone do NOT share an App-Group container (App Groups are
/// device-local), so the watch CANNOT open the phone's SwiftData store. The cross-device "Data
/// Layer" is **WatchConnectivity** (`WCSession`), mirroring Android's MessageClient/DataClient: the
/// phone pushes a `WatchSnapshot` of today, the watch sends back `WatchCommand` quick actions.
/// The latest snapshot is cached to the watch's own UserDefaults for cold launch.
@main
struct ChronosWatchApp: App {
    @State private var connectivity = WatchConnectivityClient()

    var body: some Scene {
        WindowGroup {
            WatchRootView()
                .environment(connectivity)
                .task { connectivity.activate() }
        }
    }
}
