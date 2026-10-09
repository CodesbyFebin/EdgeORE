plugins {
    kotlin("jvm") version "2.0.21"
    application
}
repositories { mavenCentral() }
kotlin { jvmToolchain(17) }
application { mainClass.set("ReceiptCheckerKt") }
// Compile the actual verifier and its helpers, rather than a second implementation.
sourceSets.main {
    kotlin.srcDir("../../app/src/main/java")
    kotlin.include("ReceiptChecker.kt", "com/edgeore/app/receipts/Receipts.kt",
        "com/edgeore/app/crypto/Bytes.kt", "com/edgeore/app/crypto/Ed25519.kt",
        "com/edgeore/app/io/SafeFiles.kt")
}
dependencies {
    implementation("org.json:json:20240303")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    testImplementation("junit:junit:4.13.2")
}
tasks.test { testLogging { events("passed", "failed", "skipped") } }
