package com.edgeore.app

/** Build identity, generated from Gradle so the screen, receipts and APK badging always agree. */
object BuildConfigInfo {
    val VERSION: String get() = BuildConfig.VERSION_NAME
    val VERSION_CODE: Int get() = BuildConfig.VERSION_CODE
    /** Short git commit the APK was built from, with "-dirty" when the tree had uncommitted changes. */
    val COMMIT: String get() = BuildConfig.GIT_COMMIT
}
