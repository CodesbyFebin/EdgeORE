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
