# EdgeORE build engineering adaptation

Imported as patch 2/3 of the unified series (upstream `b59b9d14`, author `Codex <codex@localhost>`), applied on top
of patch 1/3, the storage-gateway adaptation (upstream `a767f43`), on `main` `268fd74`. Qualification on EdgeORE's
build host is recorded in `evidence/unified-upgrade/`.
No upstream code was copied. Project names below credit design references, not authorship of this code.

## Implemented

- gradle/libs.versions.toml centralizes existing root plugin and Android module dependency coordinates.
  app and ondevice-llm now reference the catalog. No declaration versions or dependency scopes changed.
  Standalone receipt-checker remains its own Gradle build and retains its dependency declarations.
- scripts/build-policy.py compares catalog and configuration declarations with a reviewed baseline,
  rejects dynamic versions and checks the previously recorded Gradle 8.9 wrapper JAR SHA-256. After review it also
  covers every dependency configuration name (e.g. testRuntimeOnly, kapt, ksp), project() dependencies, dependency
  exclusions, the settings.gradle.kts repository set and API_KEY/CREDENTIAL-style BuildConfig field names.
  It also rejects credential-like single-line BuildConfig fields in the scoped build scripts.
  This narrow policy is NOT a general secret scanner and NOT a resolved-classpath comparison.
- Twelve policy tests (six original, six added in review) cover catalog drift, dependency-scope changes, wrapper
  mutation, dynamic versions, credential-like generated fields, unlisted configurations, project-dependency removal,
  exclusion removal and added repositories.
- GitHub Actions separates offline checks, Android qualification, screenshot verification and the
  receipt checker, with read-only repository permissions and artifacts saved even on failure.
  No automatic publishing, deployments or wallet actions. Workflow has not run on GitHub yet.
- scripts/clean-plan.py lists only known local build output directories. It never deletes anything,
  reads global caches or touches evidence or private keys.

## Repository decisions

| Reference | Decision |
|---|---|
| [LouisCAD/kotlin-libraries-playground](https://github.com/LouisCAD/kotlin-libraries-playground) | Adopt isolated experimentation before adding libraries. Do not import samples or auto-update dependencies with refreshVersions. |
| [moallemi/gradle-advanced-build-version](https://github.com/moallemi/gradle-advanced-build-version) | Preserve explicit versionCode/versionName and separate Git source stamp. Do not derive store versionCode from branch commit count. Current documented 4.0.0 requires AGP 9; EdgeORE pins 8.7.3. |
| [icerockdev/mobile-multiplatform-gradle-plugin](https://github.com/icerockdev/mobile-multiplatform-gradle-plugin) | Defer KMP until an actual second platform is required; retain Android Keystore/MWA boundaries. No iOS targets added. |
| [igorwojda/android-showcase](https://github.com/igorwojda/android-showcase) | Adopt catalog centralization and separate CI checks. Keep existing controllers; feature-module extraction requires separate behavior-preservation qualification. No wholesale architecture replacement. |
| [GradleUp/shadow](https://github.com/GradleUp/shadow) | Fat JAR packaging is optional for the standalone checker, not Android. Current 9.x lines require newer Gradle; keep existing installDist instead. |
| [autonomousapps/dependency-analysis-gradle-plugin](https://github.com/autonomousapps/dependency-analysis-gradle-plugin) | Adopt declaration governance now. Actual buildHealth plugin remains deferred until compatible version and resolved graphs can be tested. Never auto-remove reflective/JNI dependencies based only on static usage advice. |
| [xbdcc/GrabRedEnvelope](https://github.com/xbdcc/GrabRedEnvelope) | Exclude. README restricts commercial use and describes accessibility-driven third-party automation. No automatic wallet approval, accessibility interception or notification monitoring. |
| [google/secrets-gradle-plugin](https://github.com/google/secrets-gradle-plugin) | Adopt separating local configuration from version control; do not embed provider tokens. README explicitly says compiled keys can be recovered from APKs. |
| [rock3r/deep-clean](https://github.com/rock3r/deep-clean) | Adapt dry-run diagnostics into bounded cleanup planning. No global cache/IDE deletion or recursive nuke script. |
| [gmazzo/gradle-buildconfig-plugin](https://github.com/gmazzo/gradle-buildconfig-plugin) | Retain native AGP BuildConfig for non-secret provenance. No second generator needed; generated constants do not protect secrets. |

Additional prior references: cortinico/kotlin-android-template for CI discipline; gradle/kotlin-dsl-samples
explicitly marks itself obsolete. Use official Gradle Kotlin DSL documentation for future build logic.

## Qualification and next checks

The author reported offline declaration parity PASS, 6/6 policy tests PASS and storage gateway 8/8 PASS, with Gradle
BLOCKED because the wrapper distribution could not download. Those logs are kept, labelled, in
`evidence/build-governance/author-environment/`. Results from EdgeORE's network-enabled build host, including the
resolved-graph comparison and APK hash, are in `evidence/unified-upgrade/` (see its `README.md`). The GitHub
workflow itself has not run yet.

Requires Python 3.11+ for tomllib. CI pins Python 3.12. The workflow currently relies on the hosted
Ubuntu Android SDK and actions pinned to the commits their major tags pointed at on 2026-10-10;
qualify the SDK and re-review the pins before production release. Do not enable configuration cache until Git source-stamp and
configuration behavior are tested. Adding convention plugins now would expand the unqualified
build surface; extract them after the catalog migration is green.

On a build host:

    python3 scripts/build-policy.py
    python3 scripts/test_build_policy.py -v
    bash scripts/qualify.sh
    bash gradlew :app:verifyRoborazziDebug -Pscreens
    bash gradlew -p tools/receipt-checker test installDist

Compare before/after resolved dependency reports, including ondevice-llm exclusions and the Java-only
LiteRT-LM isolation boundary. The offline check covers declarations, not transitive resolution.
