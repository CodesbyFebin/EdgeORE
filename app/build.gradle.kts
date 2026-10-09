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
val gitCommit: String = git("rev-parse", "--short=12", "HEAD").ifEmpty { "unknown" } +
    (if (git("status", "--porcelain", "--untracked-files=no").isNotEmpty()) "-dirty" else "")

android {
    namespace = "com.edgeore.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.edgeore.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.2.8-review"
        // Binds the APK to its source revision (shown in the app and in aapt badging via BuildConfig).
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val storePass = System.getenv("EDGEORE_STORE_PASS").orEmpty()
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("release.jks")
            storePassword = storePass
            keyAlias = "edgeore"
            keyPassword = storePass
        }
    }

    buildTypes {
        release {
            // Password stays in the environment. The keystore is not committed.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
        maxHeapSize = "1536m"
    } else {
        exclude("com/edgeore/app/screens/**")
    }
}


dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Solana Mobile Wallet Adapter: wallet keys stay in the wallet app.
    implementation("com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.0.3")
    // Ed25519 for node-agent pairing keys and for verifying wallet-returned signatures.
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    // On-device LLM runtime (LiteRT-LM, as used by Google AI Edge Gallery) behind a Java-only bridge module; see ondevice-llm/build.gradle.kts.
    implementation(project(":ondevice-llm"))

    testImplementation("junit:junit:4.13.2")
    // Real org.json on the JVM test classpath (android.jar only ships stubs).
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // Screenshot suite only (see -Pscreens above).
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.36.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.36.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.36.0")
    testImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test.ext:junit:1.1.5")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // MWA clientlib-ktx 2.0.3 publishes androidx.test.ext:junit-ktx 1.1.5 on its runtime path; test libs are aligned to it.
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:core-ktx:1.5.0")
}
