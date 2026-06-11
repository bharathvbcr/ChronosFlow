import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.kover)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

extensions.configure<ApplicationExtension> {
    buildTypes { debug { enableUnitTestCoverage = true; enableAndroidTestCoverage = true } }
    namespace = "com.chronosflow.wear"
    compileSdk = 37

    defaultConfig {
        // Must match the phone app's applicationId so Wear OS pairs the two and the
        // Wearable Data Layer can mirror state between them.
        applicationId = "com.chronosflow"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("benchmark") {
            initWith(getByName("release"))
            isDebuggable = false
            isMinifyEnabled = false
            matchingFallbacks += listOf("release")
            // Debug-signed so it side-loads for on-watch perf checks; debug builds carry
            // Jacoco instrumentation + debuggable overhead that makes Compose feel laggy.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material)
    implementation(libs.androidx.wear.ongoing)
    implementation(libs.play.services.wearable)
    implementation(libs.guava)

    // Wear OS Compose Material3 app surface.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)
    implementation(libs.androidx.wear.remote.interactions)
    implementation(libs.kotlinx.coroutines.core)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.wear.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
}
