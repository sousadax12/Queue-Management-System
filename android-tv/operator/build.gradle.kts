import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The native operator app (design docs/design/2026-11-21-native-operator-app.md, Option B): sign-in,
// session and (later) queue setup, advancing and the display zones. It replaces the Flutter app
// on the same boxes, so it keeps its applicationId and Firebase registration.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

// Debug builds talk to the Firebase emulators when this is set, e.g.
// `./gradlew :operator:installDebug -PfirebaseEmulatorHost=10.0.2.2` (see README, "Operator app").
val firebaseEmulatorHost = providers.gradleProperty("firebaseEmulatorHost").orElse("").get()

android {
    namespace = "pro.joaosousa.queuedisplay.operator"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // The Flutter app's id: the native app replaces it on a box (design, "Distribution and upgrade").
        applicationId = "pro.joaosousa.queue_management_system"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // Above anything the Flutter app shipped (1.0.0+1), so a box never sees a downgrade.
        versionCode = 1000
        versionName = "2.0.0"
    }

    buildTypes {
        debug {
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"$firebaseEmulatorHost\"")
        }
        release {
            // Release builds always use the real project.
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"\"")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        // targetSdk 35 is deliberate, as in :app (TV design, D1).
        disable += "OldTargetApi"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    debugImplementation(libs.compose.ui.test.manifest)
}
