import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM: no Android dependency, so domain logic runs as fast JVM tests.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Flow in the repository interfaces. Pure Kotlin, no Android.
    api(libs.kotlinx.coroutines.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// Lets `./gradlew testDebugUnitTest` cover this module alongside the Android ones.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Runs the JVM unit tests (alias of `test`)."
    dependsOn(tasks.test)
}
