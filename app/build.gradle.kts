plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

fun git(vararg args: String): String = try {
    val p = ProcessBuilder(listOf("git") + args).directory(rootDir).redirectErrorStream(true).start()
    p.inputStream.bufferedReader().readText().trim().also { p.waitFor() }
} catch (_: Exception) { "" }
// Canonical source commit (scripts/commit-stamp.sh). "unstamped" when the file is missing.
val sourceStamp: String = rootProject.file("evidence/COMMIT-STAMP.json").takeIf { it.exists() }?.readText()
    ?.let { Regex("\"sourceCommit\"\\s*:\\s*\"([0-9a-f]{40})\"").find(it)?.groupValues?.get(1) } ?: "unstamped"
val gitCommit: String = git("rev-parse", "--short=12", "HEAD").ifEmpty { "unknown" } +
    (if (git("status", "--porcelain", "--untracked-files=no").isNotEmpty()) "-dirty" else "")

android {
    namespace = "com.edgeore.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.edgeore.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "0.1.0-clockin"
        // Binds the APK to its source revision (shown in the app and in aapt badging via BuildConfig).
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
        buildConfigField("String", "SOURCE_STAMP", "\"$sourceStamp\"")
        // ORE v3 deploy targets devnet only. Mainnet deploy stays off; no build type turns it on.
        buildConfigField("boolean", "ORE_MAINNET_DEPLOY", "true") // owner-approved 2026-10-12; capped at 0.01 SOL per deploy, explicit switch, owner signs each
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val storePass = System.getenv("EDGEORE_STORE_PASS").orEmpty()
    // Release signing only when the owner's keystore is present (never committed) and the password is set.
    // Otherwise assembleRelease produces an UNSIGNED, minified APK for R8/size checks only.
    // EDGEORE_KEYSTORE: path to a keystore outside the repo (scripts/make-release-keystore.sh); release.jks is the legacy default.
    val releaseKeystore = System.getenv("EDGEORE_KEYSTORE")?.takeIf { it.isNotBlank() }?.let { file(it) } ?: rootProject.file("release.jks")
    val keyPass = System.getenv("EDGEORE_KEY_PASS").orEmpty().ifEmpty { storePass }
    val canSignRelease = releaseKeystore.exists() && storePass.isNotEmpty()
    signingConfigs {
        if (canSignRelease) create("release") {
            storeFile = releaseKeystore
            storePassword = storePass
            keyAlias = "edgeore"
            keyPassword = keyPass
        }
    }

    buildTypes {
        debug {
            // Demo recordings: debug builds skip FLAG_SECURE so the screen can be recorded. Release keeps it.
            buildConfigField("boolean", "SCREEN_CAPTURE_ALLOWED", "true")
        }
        release {
            buildConfigField("boolean", "SCREEN_CAPTURE_ALLOWED", "false")
            // Password stays in the environment. The keystore is not committed.
            // Scan-only builds retain names for precise third-party finding attribution. Shipped release stays minified.
            isMinifyEnabled = !project.hasProperty("securityScan")
            isShrinkResources = !project.hasProperty("securityScan")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (canSignRelease) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { compose = true; buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources.excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/DEPENDENCIES")
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        // Version-update checks need network metadata and are not correctness issues.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }

    testOptions { unitTests.isReturnDefaultValues = false; unitTests.isIncludeAndroidResources = true }
}

// Screenshot suite (Robolectric + Roborazzi, API 28, Pixel 7). It is slow, so it only runs when asked:
//   ./gradlew :app:recordRoborazziDebug -Pscreens   (writes docs/screenshots/)
//   ./gradlew :app:verifyRoborazziDebug -Pscreens   (fails if a screen changed)
val screens = project.hasProperty("screens")
tasks.withType<Test>().configureEach {
    if (screens) {
        filter { includeTestsMatching("com.edgeore.app.screens.*") }
        systemProperty("screens.out", rootProject.file("docs/screenshots").absolutePath)
        System.getenv("STORE_OUT")?.let { systemProperty("store.out", it) }
        maxHeapSize = "1536m"
    } else {
        exclude("com/edgeore/app/screens/**")
    }
}


dependencies {
    implementation(platform(libs.androidx.compose.compose.bom.lib))
    implementation(libs.androidx.activity.activity.compose.lib)
    implementation(libs.androidx.compose.material3.material3.lib)
    implementation(libs.androidx.compose.ui.ui.lib)
    implementation(libs.androidx.compose.ui.ui.tooling.preview.lib)
    debugImplementation(libs.androidx.compose.ui.ui.tooling.lib)
    implementation(libs.androidx.core.core.ktx.lib)
    implementation(libs.androidx.lifecycle.lifecycle.runtime.ktx.lib)
    implementation(libs.androidx.lifecycle.lifecycle.viewmodel.compose.lib)
    implementation(libs.androidx.lifecycle.lifecycle.runtime.compose.lib)
    implementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.android.lib)
    // Opt-in contribution scheduler (Wi-Fi + charging + battery-not-low constraints). 2.10.0 is the newest line that
    // adds only new modules here: every existing resolved version stays the same (evidence/contribution-scheduler/*/deps).
    implementation(libs.androidx.work.work.runtime.ktx.lib)

    // Solana Mobile Wallet Adapter: wallet keys stay in the wallet app.
    implementation(libs.com.solanamobile.mobile.wallet.adapter.clientlib.ktx.lib)
    // User-presence gate (biometric or device credential) before each MWA signing dispatch (hardening 15).
    implementation(libs.androidx.biometric.biometric.lib)
    // biometric 1.1.0 brings fragment 1.2.5; activity-result APIs in a FragmentActivity need fragment >= 1.3.0.
    implementation(libs.androidx.fragment.fragment.lib)
    // Ed25519 for node-agent pairing keys and for verifying wallet-returned signatures.
    implementation(libs.org.bouncycastle.bcprov.jdk18on.lib)
    // On-device LLM runtime (LiteRT-LM, as used by Google AI Edge Gallery) behind a Java-only bridge module; see ondevice-llm/build.gradle.kts.
    implementation(project(":ondevice-llm"))
    // QR decoding for the review destination field. zxing-core is pure Java with no runtime dependencies, so it adds
    // one jar and changes no resolved version (evidence/ux-from-clearance/*/deps). Chosen over ML Kit (Play Services or a
    // bundled model) and CameraX (several extra artifacts for one screen). The camera is driven with platform Camera2.
    implementation(libs.com.google.zxing.core.lib)

    testImplementation(libs.junit.junit.lib)
    // Real org.json on the JVM test classpath (android.jar only ships stubs).
    testImplementation(libs.org.json.json.lib)
    testImplementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.test.lib)
    // WorkManager test driver (constraints met/unmet) for the scheduler tests; same version as the runtime.
    testImplementation(libs.androidx.work.work.testing.lib)
    // Screenshot suite only (see -Pscreens above).
    testImplementation(libs.org.robolectric.robolectric.lib)
    testImplementation(libs.io.github.takahirom.roborazzi.roborazzi.lib)
    testImplementation(libs.io.github.takahirom.roborazzi.roborazzi.compose.lib)
    testImplementation(libs.io.github.takahirom.roborazzi.roborazzi.junit.rule.lib)
    testImplementation(platform(libs.androidx.compose.compose.bom.lib))
    testImplementation(libs.androidx.compose.ui.ui.test.junit4.lib)
    testImplementation(libs.androidx.test.ext.junit.lib)

    androidTestImplementation(platform(libs.androidx.compose.compose.bom.lib))
    androidTestImplementation(libs.androidx.compose.ui.ui.test.junit4.lib)
    debugImplementation(libs.androidx.compose.ui.ui.test.manifest.lib)
    // MWA clientlib-ktx 2.0.3 publishes androidx.test.ext:junit-ktx 1.1.5 on its runtime path; test libs are aligned to it.
    androidTestImplementation(libs.androidx.test.ext.junit.lib)
    androidTestImplementation(libs.androidx.test.runner.lib)
    androidTestImplementation(libs.androidx.test.core.ktx.lib)
}

