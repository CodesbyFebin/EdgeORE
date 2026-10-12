# Release preparation evidence

Date: 2026-10-12. These are repository/document/workflow preparation results, not a new Android or device run.

- Annotated tag: `v0.1.0-clockin-review.1`, object `65899258ca830a90ed0128aab7e3f441cf78aba0`; peeled source `ecb7edfac31647f8b1f618cfbb185c1007187d30`. Pushed and checked with `git ls-remote --tags`.
- Source-side CI for the tagged candidate passed: run `38249756791`. This result belongs to that source, not to these new policy files.
- Security and quality agent profiles: authored; not executed as reviews.
- Community/agent Markdown relative-link checks and `git diff --check`: PASS.
- YAML parsing for agent frontmatter and the review workflow: PASS.
- `actionlint` 1.7.7 on `.github/workflows/review-release.yml`: exit 0. The downloaded tool matched its published checksum file.
- Offline `python3 scripts/build-policy.py`: PASS.
- Direct `gh release create` with `--verify-tag --draft --prerelease`: exit 1; GitHub GraphQL access was forbidden. See `create-draft-release.txt`. No draft release was created by that command.
- Review-release workflow execution: NOT_RUN. It is manual, separates read-only qualification from draft creation, and does not publish the draft or use a release keystore.
- APK/download ZIP: the previously confirmed review package is linked in `docs/releases-and-packages.md`; no GitHub Packages registry artifact is claimed.
- Registry format: owner selected GHCR storage-gateway container.
- Gateway tests: 17/17 PASS, fresh local run; see gateway-tests.txt.
- Container smoke checks: 6 PASS; see container-smoke.txt. Throwaway credential/volume removed after testing. Authentication, upload/retrieval, cancellation and restart persistence tested locally, not Android/HTTPS.
- Digest-pinned base image: python:3.12-slim-bookworm@sha256:34386ef0cb081344d7ec1c103ba398e6e9f64e9ab3a1509accc92a4e24a07258.
- Direct GHCR login: exit 1, `Get "https://ghcr.io/v2/": denied: denied`; no push attempted after refused login. No package published.
- storage-gateway-package.yml: actionlint PASS; execution NOT_RUN. Manual publishing uses repository-scoped packages:write.
- Owner project-license decision: pending.
