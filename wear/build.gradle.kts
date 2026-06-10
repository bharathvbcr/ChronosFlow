import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.kover)
    alias(libs.plugins.android.application)
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
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
}
