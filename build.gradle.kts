plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.kover)
}

dependencies {
    kover(project(":core:ai"))
    kover(project(":core:domain"))
    kover(project(":feature:focus"))
    // Coverage currently targets owned logic modules and wearable for deterministic reliability.
    // Feature modules are added selectively as their logic coverage thresholds are stabilized.
    kover(project(":wear"))
}

    kover {
        reports {
            filters {
                excludes {
                    classes("androidx.appfunctions.internal.*")
                    classes("androidx.appfunctions.service.internal.*")
                    classes("appfunctions_aggregated_deps.*")
                    classes("com.ChronosFlow.VBCR.appfunctions.*")
                    classes("com.ChronosFlow.VBCR.core.ai.di.*")
                    classes("com.ChronosFlow.VBCR.core.ai.genai.*")
                    classes("com.ChronosFlow.VBCR.core.data.*")
                    classes("com.ChronosFlow.VBCR.core.data.backup.*")
                    classes("com.ChronosFlow.VBCR.core.data.dao.*")
                    classes("com.ChronosFlow.VBCR.core.data.datastore.*")
                    classes("com.ChronosFlow.VBCR.core.data.di.*")
                    classes("com.ChronosFlow.VBCR.core.data.focus.*")
                    classes("com.ChronosFlow.VBCR.core.data.privacy.*")
                    classes("com.ChronosFlow.VBCR.core.data.security.*")
                    classes("com.ChronosFlow.VBCR.core.data.sync.*")
                    classes("com.ChronosFlow.VBCR.core.domain.usecase.*")
                    classes("com.ChronosFlow.VBCR.core.notifications.*")
                    classes("com.ChronosFlow.VBCR.core.ui.bubble.*")
                    classes("com.ChronosFlow.VBCR.core.ui.components.*")
                    classes("com.ChronosFlow.VBCR.core.ui.motion.*")
                    classes("com.ChronosFlow.VBCR.core.ui.security.*")
                    classes("com.ChronosFlow.VBCR.core.ui.settings.*")
                    classes("com.ChronosFlow.VBCR.core.ui.shell.*")
                    classes("com.ChronosFlow.VBCR.core.ui.theme.*")
                    classes("com.ChronosFlow.VBCR.di.*")
                    classes("com.ChronosFlow.VBCR.feature.daydial.*")
                    classes("com.ChronosFlow.VBCR.feature.daydial.delegate.*")
                    classes("com.ChronosFlow.VBCR.feature.daydial.dial.*")
                    classes("com.ChronosFlow.VBCR.feature.daydial.ui.*")
                    classes("com.ChronosFlow.VBCR.feature.focus.*")
                    classes("com.ChronosFlow.VBCR.feature.habits.*")
                    classes("com.ChronosFlow.VBCR.feature.medication.*")
                    classes("com.ChronosFlow.VBCR.feature.tasks.*")
                    classes("com.ChronosFlow.VBCR.navigation.*")
                    classes("com.ChronosFlow.VBCR.security.*")
                    classes("com.ChronosFlow.VBCR.widget.*")
                    classes("dagger.hilt.internal.aggregatedroot.codegen.*")
                    classes("hilt_aggregated_deps.*")
                }
            }
            total {
                xml { onCheck = true }
                html { onCheck = true }
            }
        }
    }

    tasks.register("chronosCiCheck") {
    group = "verification"
    description = "Runs non-emulator CI checks, module isolation builds, benchmark assembly, and test APK assembly."

    dependsOn(
        ":app:assembleDebug",
        ":app:testDebugUnitTest",
        ":app:assembleDebugAndroidTest",
        ":benchmark:assembleDebug",
        ":core:ai:assembleDebug",
        ":core:data:assembleDebug",
        ":core:domain:assembleDebug",
        ":core:notifications:assembleDebug",
        ":core:ui:assembleDebug",
        ":feature:daydial:assembleDebug",
        ":feature:daydial:testDebugUnitTest",
        ":feature:daydial:assembleDebugAndroidTest",
        ":feature:focus:assembleDebug",
        ":feature:tasks:assembleDebug",
        ":feature:habits:assembleDebug",
        ":feature:medication:assembleDebug",
        ":wear:assembleDebug"
    )
}
