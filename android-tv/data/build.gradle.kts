import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Repositories, Firestore mappers and the media cache land here from M2 onwards.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "pro.joaosousa.queuedisplay.tv.data"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":domain"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
