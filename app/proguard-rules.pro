# R8 keep rules for the release build. Runtime behaviour of the minified build is NOT verified on a device.
# org.json, BouncyCastle providers and MWA use reflection / service loading.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keep class com.solana.** { *; }
-keep class com.solanamobile.** { *; }
-dontwarn com.solana.**
-keep class com.google.ai.edge.** { *; }
-dontwarn com.google.ai.edge.**
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn com.google.errorprone.annotations.**
# Hardening 13 (R8 verified on for release: isMinifyEnabled + isShrinkResources in app/build.gradle.kts).
# JNI entry points (LiteRT-LM bridge) must keep their names.
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
# WorkManager creates workers by class name; keep their (Context, WorkerParameters) constructors.
-keep class * extends androidx.work.ListenableWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }
# Readable release stack traces with the mapping file; source file names are not shipped.
-keepattributes LineNumberTable
-renamesourcefileattribute SourceFile
