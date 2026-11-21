import java.util.Base64
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The native operator app (design docs/design/2026-11-21-native-operator-app.md, Option B): sign-in,
// session, queue setup, advancing and (later) the display zones. It replaces the Flutter app
// on the same boxes, so it keeps its applicationId and Firebase registration.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Screenshot goldens: `recordRoborazziDebug` writes them to src/test/screenshots, `verifyRoborazziDebug` checks them.
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

// Debug builds talk to the Firebase emulators when this is set, e.g.
// `./gradlew :operator:installDebug -PfirebaseEmulatorHost=10.0.2.2` (see README, "Operator app").
val firebaseEmulatorHost = providers.gradleProperty("firebaseEmulatorHost").orElse("").get()

// Release signing and versioning (O6, docs/operator/DEPLOY.md). Each value comes from an environment
// variable or, failing that, a Gradle property: the release workflow sets the environment from secrets, and a
// local build can pass `-PoperatorKeystoreBase64=...` and so on. Without them `assembleRelease` builds an
// unsigned APK; it never falls back to the debug key, which would lock a box to a key we don't control.
fun releaseInput(env: String, property: String): String? =
    providers.environmentVariable(env).orElse(providers.gradleProperty(property)).orNull?.takeIf { it.isNotBlank() }

val signingInputs = mapOf(
    "OPERATOR_KEYSTORE_BASE64" to releaseInput("OPERATOR_KEYSTORE_BASE64", "operatorKeystoreBase64"),
    "OPERATOR_KEYSTORE_PASSWORD" to releaseInput("OPERATOR_KEYSTORE_PASSWORD", "operatorKeystorePassword"),
    "OPERATOR_KEY_ALIAS" to releaseInput("OPERATOR_KEY_ALIAS", "operatorKeyAlias"),
    "OPERATOR_KEY_PASSWORD" to releaseInput("OPERATOR_KEY_PASSWORD", "operatorKeyPassword"),
)
val missingSigningInputs = signingInputs.filterValues { it == null }.keys
if (missingSigningInputs.isNotEmpty() && missingSigningInputs.size < signingInputs.size) {
    // Half a configuration is a mistake, not a request for an unsigned build.
    throw GradleException("Operator release signing is partly configured; missing: ${missingSigningInputs.joinToString()}")
}

// The keystore is decoded into the build directory. Reading it back through providers.fileContents makes it a
// configuration input, so a `clean` invalidates the configuration cache and the next build decodes it again.
val releaseKeystore: File? = signingInputs["OPERATOR_KEYSTORE_BASE64"]?.let { encoded ->
    val file = layout.buildDirectory.file("signing/operator-release.jks").get().asFile
    val bytes = Base64.getMimeDecoder().decode(encoded)
    if (!file.exists() || !file.readBytes().contentEquals(bytes)) {
        file.parentFile.mkdirs()
        file.writeBytes(bytes)
    }
    providers.fileContents(layout.buildDirectory.file("signing/operator-release.jks")).asBytes.get()
    file
}

// CI passes `1000 + github.run_number` and the version from the `operator-v*` tag. 1000 is above anything the
// Flutter app shipped (1.0.0+1), so a box never sees a downgrade.
val operatorVersionCode = providers.gradleProperty("operatorVersionCode").orElse("1000").get().toInt()
val operatorVersionName = providers.gradleProperty("operatorVersionName").orElse("2.0.0").get()

// The shared Firestore contract (C8): the repository tests read its queue fixtures with :data's loader.
val contractDir = layout.projectDirectory.dir("../../admin-portal/packages/contract")

android {
    namespace = "pro.joaosousa.queuedisplay.operator"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // The Flutter app's id: the native app replaces it on a box (design, "Distribution and upgrade").
        applicationId = "pro.joaosousa.queue_management_system"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = operatorVersionCode
        versionName = operatorVersionName
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = signingInputs.getValue("OPERATOR_KEYSTORE_PASSWORD")
                keyAlias = signingInputs.getValue("OPERATOR_KEY_ALIAS")
                keyPassword = signingInputs.getValue("OPERATOR_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"$firebaseEmulatorHost\"")
        }
        release {
            // Release builds always use the real project.
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"\"")
            // Unsigned without the release key (never the debug key).
            signingConfig = signingConfigs.findByName("release")
            // No R8: the design doesn't call for it, the APK is sideloaded (size matters little), and it would
            // need keep rules for Firebase, Hilt and Media3 that only a device test could prove.
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

    // :data's contract fixture loader (data/src/sharedTest), for the repository tests.
    sourceSets.getByName("test").kotlin.srcDir("../data/src/sharedTest/kotlin")

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            test.systemProperty("contract.dir", contractDir.asFile.absolutePath)
            test.inputs.dir(contractDir.dir("fixtures")).withPathSensitivity(PathSensitivity.RELATIVE)
        }
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
    implementation(project(":data"))
    implementation(project(":ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    debugImplementation(libs.compose.ui.test.manifest)
}
