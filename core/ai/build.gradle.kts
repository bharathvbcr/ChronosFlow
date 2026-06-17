import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.kover)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

extensions.configure<LibraryExtension> {
    buildTypes { debug { enableUnitTestCoverage = true; enableAndroidTestCoverage = true } }
    namespace = "com.chronosflow.core.ai"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:domain"))
    implementation(libs.androidx.appsearch)
    implementation(libs.androidx.appsearch.local.storage)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.mlkit.genai.prompt)
    implementation(libs.mlkit.genai.summarization)
    implementation(libs.mlkit.genai.proofreading)
    implementation(libs.mlkit.genai.rewriting)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ai)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.guava)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
}

kover {
    reports {
        filters {
            excludes {
                classes("com.chronosflow.core.ai.*_Factory")
                classes("com.chronosflow.core.ai.*_GeneratedInjector")
            }
        }
    }
}
