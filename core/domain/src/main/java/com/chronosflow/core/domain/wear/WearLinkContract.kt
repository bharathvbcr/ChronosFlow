package com.chronosflow.core.domain.wear

/**
 * Shared constants for discovering the phone ↔ watch link over the Wearable Data Layer.
 *
 * The watch advertises [WEAR_APP_CAPABILITY] via its `res/values/wear.xml`
 * `android_wear_capabilities` string-array; the phone queries it with the CapabilityClient to tell
 * a watch that actually has the ChronosFlow app installed (and so can receive the mirror) apart
 * from a watch that is merely paired. Keep the literal in the wear XML in sync with this constant.
 */
object WearLinkContract {
    const val WEAR_APP_CAPABILITY = "chronos_wear_app"
}
