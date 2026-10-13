plugins {
    kotlin("jvm") version "2.0.21"
    application
    `maven-publish`
}
group = "io.github.codesbyfebin.edgeore"
version = (findProperty("receiptCheckerVersion") as String?) ?: "0.2.9-review"
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
    implementation("org.json:json:20260814")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    testImplementation("junit:junit:4.13.2")
}
tasks.test { testLogging { events("passed", "failed", "skipped") } }

java { withSourcesJar() }
// GitHub Packages. Credentials come only from the environment (GITHUB_ACTOR / GITHUB_TOKEN); nothing is committed.
// Publish: GITHUB_ACTOR=... GITHUB_TOKEN=... ./gradlew -p tools/receipt-checker publish  (token needs write:packages)
publishing {
    publications { create<MavenPublication>("receiptChecker") { artifactId = "receipt-checker"; from(components["java"]) } }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/CodesbyFebin/EdgeORE")
            credentials { username = System.getenv("GITHUB_ACTOR"); password = System.getenv("GITHUB_TOKEN") }
        }
    }
}
