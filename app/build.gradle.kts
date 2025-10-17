import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.androidx.room)
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
}

val enableLabelHeuristicsProperty =
    (project.findProperty("alfred.enableLabelHeuristics") as? String)
        ?.trim()
        ?.lowercase()
val enableLabelHeuristics =
    when (enableLabelHeuristicsProperty) {
        "1", "true", "on", "yes" -> true
        else -> false
    }

android {
    namespace = "com.swooby.alfred"
    compileSdk = 36

    defaultConfig {
        val appId = "com.swooby.alfred2025"

        applicationId = appId
        versionCode = 1
        versionName = "1.0"

        minSdk = 34
        targetSdk = 36

        vectorDrawables { useSupportLibrary = true }

        // Used in EventCard
        buildConfigField("boolean", "ENABLE_LABEL_HEURISTICS", enableLabelHeuristics.toString())

        // Used in some Composable Previews
        buildConfigField("String", "PACKAGE_NAME", "\"$appId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
            optIn.add("kotlin.time.ExperimentalTime")
            optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")

            val forceCompilerWarningsAsErrors = false
            if (forceCompilerWarningsAsErrors) {
                extraWarnings.set(true)
                allWarningsAsErrors.set(true)
                verbose.set(true)

                if (true) {
                    // Temporarily disable to get past warnings`build/generated/ksp` code
                    freeCompilerArgs.add("-Xwarning-level=REDUNDANT_VISIBILITY_MODIFIER:disabled")
                    freeCompilerArgs.add("-Xwarning-level=CAN_BE_VAL:disabled")
                }

                // https://youtrack.jetbrains.com/issue/KT-78881 Fixed in Kotlin 2.3
                freeCompilerArgs.add("-Xwarning-level=ASSIGNED_VALUE_IS_NEVER_READ:disabled") // SettingsScreen.kt
            }

            // Our own overrides
            // https://youtrack.jetbrains.com/issue/KT-80399/ Fixed in Kotlin 2.3
            freeCompilerArgs.add("-Xwarning-level=OVERRIDE_DEPRECATION:disabled") // FooTextToSpeech.kt
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    //
    // Run with `./gradlew lint` after a successful build/assembleDebug/assembleRelease.
    //
    lint {
        val forceLintWarningsAsError = false
        if (forceLintWarningsAsError) {
            checkAllWarnings = true
            textReport = true
            explainIssues = true
            absolutePaths = false
            warningsAsErrors = true
        }

        // Suppress "Access to private method getAudioProfileDataStore of class {X} requires synthetic accessor"
        disable += "SyntheticAccessor"
    }

    /*
    testOptions {
        unitTests {
            // Used to help building out mocks.
            // See https://developer.android.com/training/testing/local-tests#error
            isReturnDefaultValues = true
        }
    }
     */
}

//
// Run with `./gradlew ktlintCheck` after a successful build.
// Fix with `./gradlew ktlintFormat`.
//
ktlint {
    filter {
        exclude("**/build/**") // avoid build/generated
    }
    verbose = true // display the corresponding rule
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.material)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.iconsExtended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Media/Notifications
    implementation(libs.androidx.media)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // JSON + time
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.material.kolor)

    // Test
    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
}
