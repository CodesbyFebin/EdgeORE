# Tags, releases and packages

## Current review artifacts

The mint APK uses version `0.1.0-clockin`, code 12. Its validated delivery source is `ecb7edfac31647f8b1f618cfbb185c1007187d30` (PR #22; source-side CI passed). The created [review tag](https://github.com/CodesbyFebin/EdgeORE/tree/v0.1.0-clockin-review.1) is `v0.1.0-clockin-review.1`. It identifies that reviewed branch source; it does not assert that PR #22 has merged or that device gates have passed.

[APK + UI preview package](https://d2ol7oe51mr4n9.cloudfront.net/user_3Jn4WZsssIDmoyWlzqAT3djW3wG/cc6b3407-f20a-4e9b-9c06-4e314fc3b4ec.zip) · [Matching CI run](https://github.com/CodesbyFebin/EdgeORE/actions/runs/38249756791)

The package contains a debug APK, a labeled JVM UI preview, manifest and CI summary. The historical `v0.2.9-review` tag describes an earlier candidate. Compare source/versionCode/artifact identities rather than treating the largest tag string as proof of the current app version.

## Release preparation

The manual **Prepare Android review release** workflow accepts an existing review tag, checks out that exact tag, runs source-side qualification and visual verification, packages the debug APK with evidence and NOTICE, and prepares a **draft prerelease**. It uses no release keystore and does not publish the draft. Run it after this workflow is available on the repository's default branch.

Its qualification job has read-only repository permissions. A separate draft-creation job has `contents: write`, verifies that the tag still resolves to the tested source, and uploads the APK/package/checksums. Duplicate or failed release creation requires inspecting the existing draft before retrying; the workflow does not overwrite assets.

Production publication remains a separate owner action after license, signing and applicable runtime gates are resolved. Avoid production-readiness or earnings claims in release names and notes.

## Package format

For the Android app, APKs and download ZIPs are distributed as Actions artifacts and release assets. They are not a GitHub Packages registry format. A registry package needs a supported format and its own consumer contract, version, license/provenance and verification: for example a Maven Android library or a GHCR container for a separately reviewed service.

The owner selected a GHCR storage-gateway container. Its [container guide](../services/storage-gateway/CONTAINER.md) describes configuration and host-loopback exposure. The manual **Storage gateway review package** workflow tests the gateway, builds a nonroot image, exercises authentication/upload/retrieval/cancellation/restart persistence, then pushes that exact image as `ghcr.io/codesbyfebin/edgeore-storage-gateway:sha-<source SHA>` with repository-scoped `packages: write`. It runs only when dispatched, after becoming available on the default branch. No mutable `latest` or production tag is created.

Local image build and six container checks passed; the gateway suite passed 17 tests. Direct GHCR login returned `denied`; no registry artifact was published. The workflow has not run. Registry visibility remains whatever GitHub assigns until the owner configures package access. A published image would still require a private token, persistent volume, filesystem quota and owner-managed HTTPS; it is not a qualified deployed provider.

## Recorded preparation status

The annotated tag was pushed and verified against source `ecb7edfac316`. Direct draft-release creation returned `Post "https://api.github.com/graphql": Forbidden` in this environment and did not create a release. The manual workflow passes actionlint 1.7.7 and YAML validation, but has not run; it needs to be available on the default branch before manual dispatch. GHCR publication is blocked by registry authentication in this environment; the project license choice remains pending.
