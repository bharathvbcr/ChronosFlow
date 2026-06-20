# Room entities are accessed via reflection by the Room query compiler.
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# InteropContract column-name arrays are accessed by string index in MatrixCursor.addRow() calls
# inside InteropProvider and would be renamed/stripped by R8 without this rule.
-keep class com.ChronosFlow.VBCR.interop.InteropContract { *; }
-keep class com.ChronosFlow.VBCR.interop.InteropContract$* { *; }

# WearActionContract path strings are compared at runtime in WearActionListenerService.
-keep class com.ChronosFlow.VBCR.core.domain.wear.WearActionContract { *; }

# AppFunctions metadata is read via reflection by the AppFunctions runtime.
-keep @androidx.appfunctions.AppFunction class * { *; }

# Hilt, Room, Kotlin Serialization, and Compose ship their own consumer ProGuard rules via
# the AAR, so no explicit keep rules are needed for those libraries.

# ---------------------------------------------------------------------------
# Parcelable: keep CREATOR fields so NavBackStack, Bundle extras, and widget
# results survive process-death state restoration in R8 full mode.
# ---------------------------------------------------------------------------
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ---------------------------------------------------------------------------
# Kotlinx Coroutines — keep the dispatcher factory & exception handler so the
# main-thread dispatcher is discoverable at runtime after R8 shrinking.
# ---------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ---------------------------------------------------------------------------
# Hilt — keep generated component entry-points that are looked up by class name.
# ---------------------------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }

# ---------------------------------------------------------------------------
# Kotlinx Serialization — annotation attributes are needed at runtime by the
# generated serializers; inner classes hold the companion descriptor.
# ---------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# ---------------------------------------------------------------------------
# Wear OS — keep classes that the OS resolves by name from manifests or tiles.
# (The phone :app rules do not cover :wear classes, but these broad keep rules
# ensure any Wear types that end up on the phone side-channel are also safe.)
# ---------------------------------------------------------------------------
-keep class androidx.wear.** { *; }
-keep class * extends androidx.wear.tiles.TileService { *; }
