package com.ChronosFlow.VBCR.core.domain.model

/**
 * Shared SharedPreferences coordinates for the pre-generated proactive daily digest. The generator
 * in `core:ai` writes these keys while the app is foregrounded; the notification delivery path in
 * `core:notifications` reads them at alarm time (no live inference). Both modules depend only on
 * `core:domain`, so keeping the coordinates here is the single source of truth.
 */
object ProactiveDigestKeys {
    const val PREFERENCES_NAME = "chronos_preferences"
    const val KEY_TEXT = "proactive_assist_text"
    const val KEY_SOURCE = "proactive_assist_source"
    const val KEY_GENERATED_AT = "proactive_assist_generated_at"
    const val KEY_FOR_DATE = "proactive_assist_for_date"
}
