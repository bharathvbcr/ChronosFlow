import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.kover)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

extensions.configure<LibraryExtension> {
    buildTypes { debug { enableUnitTestCoverage = true; enableAndroidTestCoverage = true } }
    namespace = "com.chronosflow.feature.focus"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:notifications"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.play.services.wearable)
    ksp(libs.hilt.compiler)

    testImplementation(project(":core:ai"))
    testImplementation(project(":core:data"))
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

kover {
    reports {
        filters {
            excludes {
                classes("com.chronosflow.feature.focus.FocusService*")
                classes("com.chronosflow.feature.focus.FocusViewModel*")
                classes("com.chronosflow.feature.focus.FocusScreen*")
                classes("com.chronosflow.feature.focus.ComposableSingletons*")
            }
        }
    }
}
