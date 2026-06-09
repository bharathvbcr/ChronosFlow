import com.android.build.api.dsl.TestExtension

plugins {
    alias(libs.plugins.android.test)
}

extensions.configure<TestExtension> {
    namespace = "com.chronosflow.benchmark"
    compileSdk = 37

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        minSdk = 26
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("benchmark") {
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("debug")
        }
    }

    packaging {
        jniLibs.keepDebugSymbols += listOf(
            "**/libbenchmarkNative.so",
            "**/libtracing_perfetto.so"
        )
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.benchmark.macro)
}
