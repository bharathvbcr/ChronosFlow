import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties

plugins {
    alias(libs.plugins.kover)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Shared release signing: the phone (:app) and watch (:wear) must be signed with the SAME key so
// the Wearable Data Layer pairs them. Credentials live in the gitignored root keystore.properties
// (see keystore.properties.example); absent that file the release variant is simply left unsigned.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

extensions.configure<ApplicationExtension> {
    if (keystorePropsFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        debug { enableUnitTestCoverage = true; enableAndroidTestCoverage = true }
        if (keystorePropsFile.exists()) {
            release { signingConfig = signingConfigs.getByName("release") }
        }
    }
    namespace = "com.ChronosFlow.VBCR.wear"
    compileSdk = 37

    defaultConfig {
        // Must match the phone app's applicationId so Wear OS pairs the two and the
        // Wearable Data Layer can mirror state between them.
        applicationId = "com.ChronosFlow.VBCR"
        minSdk = 26
        targetSdk = 37
        // Wear OS multi-APK delivery requires a versionCode distinct from (and conventionally higher
        // than) the phone APK's; derive it from the shared base in a reserved +100000 band so the two
        // never collide as the version increments. See gradle.properties.
        versionCode = providers.gradleProperty("chronos.versionCode").map(String::toInt).orElse(1).get() + 100000
        versionName = providers.gradleProperty("chronos.versionName").orElse("0.1.0").get()
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
    implementation(libs.androidx.wear.watchface.complications.datasource.ktx)
    implementation(libs.kotlinx.coroutines.core)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.wear.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
}
