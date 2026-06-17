import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties

plugins {
    alias(libs.plugins.kover)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val enableDebugCoverage = providers.gradleProperty("chronos.enableDebugCoverage")
    .map(String::toBoolean)
    .orElse(false)

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
    namespace = "com.chronosflow"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.chronosflow"
        minSdk = 26
        targetSdk = 37
        versionCode = providers.gradleProperty("chronos.versionCode").map(String::toInt).orElse(1).get()
        versionName = providers.gradleProperty("chronos.versionName").orElse("0.1.0").get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    lint {
        lintConfig = file("lint.xml")
    }

    buildFeatures {
        buildConfig = true
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs.keepDebugSymbols += "**/libandroidx.graphics.path.so"
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = enableDebugCoverage.get()
            enableAndroidTestCoverage = enableDebugCoverage.get()
        }
        if (keystorePropsFile.exists()) {
            release { signingConfig = signingConfigs.getByName("release") }
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = false
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // Reinstall over existing com.chronosflow (adb install -r) instead of uninstall-first deploys.
    installation {
        installOptions += "-r"
    }
}

ksp {
    arg("appfunctions:aggregateAppFunctions", "true")
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:domain"))
    implementation(project(":core:ai"))
    implementation(project(":core:ui"))
    implementation(project(":core:notifications"))
    implementation(project(":feature:daydial"))
    implementation(project(":feature:focus"))
    implementation(project(":feature:tasks"))
    implementation(project(":feature:habits"))
    implementation(project(":feature:goals"))
    implementation(project(":feature:medication"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.android)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.biometric)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    // Wearable Data Layer: mirrors the widget day summary to the paired watch's tiles.
    implementation(libs.play.services.wearable)

    // AppFunctions
    implementation(libs.androidx.appfunctions)
    implementation(libs.androidx.appfunctions.service)
    ksp(libs.androidx.appfunctions.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
