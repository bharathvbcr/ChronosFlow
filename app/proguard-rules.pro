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
