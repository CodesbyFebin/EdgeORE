// Java-only Android library that wraps LiteRT-LM (com.google.ai.edge.litertlm, Apache-2.0), the on-device
// LLM runtime used by Google AI Edge Gallery. No Kotlin plugin here on purpose: litertlm 0.8.0 ships Kotlin 2.2
// metadata that the app's pinned Kotlin 2.0.21 compiler rejects. As an `implementation` dependency of this
// module, LiteRT-LM stays off the app's compile classpath; the app sees only LiteRtLmBridge (JDK types).
plugins {
    id("com.android.library")
}

android {
    namespace = "com.edgeore.litertlm"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Pinned to 0.8.0 on purpose (the gallery itself uses 0.18.0): 0.9.0+ pulls kotlin-reflect 2.2.21 and
    // 0.17.0+ also kotlinx-coroutines 1.11.0, which would bump the app's pinned Kotlin 2.0.21 / coroutines
    // 1.9.0 graph. 0.8.0 declares only gson 2.13.2 and coroutines 1.9.0. gson's error_prone_annotations 2.41.0
    // would lift the test classpath's 2.28.0; it is compile-time annotations only, so it is excluded.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.8.0") {
        exclude(group = "com.google.errorprone", module = "error_prone_annotations")
    }
    // litertlm 0.8.0 references kotlin.reflect.full without declaring it; supplied at the project's own Kotlin version.
    runtimeOnly("org.jetbrains.kotlin:kotlin-reflect:2.0.21")
}
