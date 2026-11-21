import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Firestore mappers and repositories (M2), the RSS ticker feed (operator O4). Later: media cache and prefetch.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// The shared Firestore contract (C8): the mapper tests read its fixtures, and a drift test reads
// QUEUE_DEFAULTS and TEMPLATE_IDS from its schemas.
val contractDir = layout.projectDirectory.dir("../../admin-portal/packages/contract")
val tvContractDir = layout.projectDirectory.dir("../contract")

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

    // The contract fixture loader, shared with :operator's repository tests.
    sourceSets.getByName("test").kotlin.srcDir("src/sharedTest/kotlin")

    testOptions {
        unitTests.all { test ->
            test.systemProperty("contract.dir", contractDir.asFile.absolutePath)
            test.systemProperty("tv.contract.dir", tvContractDir.asFile.absolutePath)
            test.inputs.dir(contractDir.dir("fixtures")).withPathSensitivity(PathSensitivity.RELATIVE)
            test.inputs.dir(contractDir.dir("src/schemas")).withPathSensitivity(PathSensitivity.RELATIVE)
            test.inputs.dir(tvContractDir).withPathSensitivity(PathSensitivity.RELATIVE)
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":domain"))

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
}
