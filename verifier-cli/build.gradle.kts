import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Second-machine receipt verifier (JVM, no Android device).
// It compiles the app's EXISTING receipt verifier sources by reference; nothing is copied or changed in app/src/main.
// Only pure-Kotlin/JDK files are included: receipts/Receipts.kt and the crypto/ and io/ helpers it imports.
plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

val appSources = rootProject.file("app/src/main/java")

sourceSets {
    main {
        kotlin {
            srcDir(appSources)
            include(
                "com/edgeore/app/receipts/Receipts.kt",
                "com/edgeore/app/crypto/Bytes.kt",
                "com/edgeore/app/crypto/Ed25519.kt",
                "com/edgeore/app/io/SafeFiles.kt",
                "com/edgeore/verifier/**",
            )
        }
    }
    test {
        kotlin {
            // Test-only: the app's real transfer-message builder, to put a genuine wallet signature in fixtures.
            srcDir(appSources)
            include("com/edgeore/app/solana/SolanaMessage.kt", "com/edgeore/verifier/**")
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    // Same versions as the app. On a phone org.json comes from Android; here it is the reference library
    // (the same one the app's JVM unit tests use).
    implementation("org.json:json:20240303")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    testImplementation("junit:junit:4.13.2")
}

application {
    mainClass.set("com.edgeore.verifier.VerifyReceiptKt")
    applicationName = "verifier-cli"
}

// Writes a generated sample export (test key, not a real transfer) for the end-to-end check in qualify.sh:
//   ./gradlew :verifier-cli:demoReceipt   -> verifier-cli/build/demo/receipt-export.json
tasks.register<JavaExec>("demoReceipt") {
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.edgeore.verifier.DemoReceiptKt")
    val out = layout.buildDirectory.file("demo/receipt-export.json")
    args(out.get().asFile.absolutePath)
    outputs.file(out)
}
