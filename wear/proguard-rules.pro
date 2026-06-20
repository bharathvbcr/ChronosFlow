# Keep all TileService subclasses — they are instantiated by the Wear OS system by class name
# (the manifest <service> entry) and would otherwise be renamed/removed by R8.
-keep class * extends androidx.wear.tiles.TileService { *; }

# Keep all ChronosFlow Wear tile providers by package prefix so newly added providers are also
# protected without requiring an update to this file.
-keep class com.ChronosFlow.VBCR.wear.**TileProvider { *; }
-keep class com.ChronosFlow.VBCR.wear.**TileService { *; }

# Keep OngoingActivity — its builder is called reflectively by the Wear OS system.
-keep class androidx.wear.ongoing.OngoingActivity { *; }
-keep class androidx.wear.ongoing.OngoingActivity$Builder { *; }
-keep class androidx.wear.ongoing.Status { *; }
-keep class androidx.wear.ongoing.Status$Builder { *; }

# Keep WearActionListenerService (and any other DataLayerListenerService subclasses) which are
# invoked by the Wearable Data Layer by class name from the manifest.
-keep class * extends com.google.android.gms.wearable.WearableListenerService { *; }

# Wear Complication datasources are referenced by the system via manifest meta-data.
-keep class * extends androidx.wear.watchface.complications.datasource.ComplicationDataSourceService { *; }

# Parcelable CREATOR fields — needed for any Parcelable passed through intents or
# the Wearable Data Layer (e.g. navigation back-stack restoration, bundle extras).
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Kotlinx Coroutines internal dispatch mechanism.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Hilt entry points for the Wear module (no Application subclass in wear, but HiltViewModel is used).
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }

# Wear Compose Material3 resources accessed by name at runtime.
-keep class androidx.wear.compose.** { *; }
