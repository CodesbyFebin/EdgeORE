# EdgeORE build engineering adaptation

Candidate changes on top of storage-gateway adaptation a767f43; not build-qualified.
No upstream code was copied. Project names below credit design references, not authorship of this code.

## Implemented

- gradle/libs.versions.toml centralizes existing root plugin and Android module dependency coordinates.
  app and ondevice-llm now reference the catalog. No declaration versions or dependency scopes changed.
  Standalone receipt-checker remains its own Gradle build and retains its dependency declarations.
- scripts/build-policy.py compares catalog and configuration declarations with a reviewed baseline,
  rejects dynamic versions and checks the previously recorded Gradle 8.9 wrapper JAR SHA-256.
  It also rejects credential-like single-line BuildConfig fields in the scoped build scripts.
  This narrow policy is NOT a general secret scanner and NOT a resolved-classpath comparison.
- Six adversarial policy tests cover catalog drift, dependency-scope changes, wrapper mutation,
  dynamic versions and credential-like generated fields.
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

Offline declaration parity PASS, 6/6 policy tests PASS, storage gateway 8/8 PASS. Recorded logs live
in evidence/build-governance. These are distinct test suites and not Android results.
Gradle configuration/Android build is BLOCKED because the wrapper distribution cannot download in
this environment. No new APK, resolved graph, Detekt result, dependency-analysis report or remote CI
result exists. The migration must pass a clean network-enabled build before merge.

Requires Python 3.11+ for tomllib. CI pins Python 3.12. The workflow currently relies on the hosted
Ubuntu Android SDK and actions referenced by major tags; qualify the SDK and pin reviewed action
commits before production release. Do not enable configuration cache until Git source-stamp and
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
