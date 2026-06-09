plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.compose) apply false
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
                    classes("com.chronosflow.appfunctions.*")
                    classes("com.chronosflow.core.ai.di.*")
                    classes("com.chronosflow.core.ai.genai.*")
                    classes("com.chronosflow.core.data.*")
                    classes("com.chronosflow.core.data.backup.*")
                    classes("com.chronosflow.core.data.dao.*")
                    classes("com.chronosflow.core.data.datastore.*")
                    classes("com.chronosflow.core.data.di.*")
                    classes("com.chronosflow.core.data.focus.*")
                    classes("com.chronosflow.core.data.privacy.*")
                    classes("com.chronosflow.core.data.security.*")
                    classes("com.chronosflow.core.data.sync.*")
                    classes("com.chronosflow.core.domain.usecase.*")
                    classes("com.chronosflow.core.notifications.*")
                    classes("com.chronosflow.core.ui.bubble.*")
                    classes("com.chronosflow.core.ui.components.*")
                    classes("com.chronosflow.core.ui.motion.*")
                    classes("com.chronosflow.core.ui.security.*")
                    classes("com.chronosflow.core.ui.settings.*")
                    classes("com.chronosflow.core.ui.shell.*")
                    classes("com.chronosflow.core.ui.theme.*")
                    classes("com.chronosflow.di.*")
                    classes("com.chronosflow.feature.daydial.*")
                    classes("com.chronosflow.feature.daydial.delegate.*")
                    classes("com.chronosflow.feature.daydial.dial.*")
                    classes("com.chronosflow.feature.daydial.ui.*")
                    classes("com.chronosflow.feature.focus.*")
                    classes("com.chronosflow.feature.habits.*")
                    classes("com.chronosflow.feature.medication.*")
                    classes("com.chronosflow.feature.tasks.*")
                    classes("com.chronosflow.navigation.*")
                    classes("com.chronosflow.security.*")
                    classes("com.chronosflow.widget.*")
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
        ":feature:review:assembleDebug",
        ":wear:assembleDebug"
    )
}
