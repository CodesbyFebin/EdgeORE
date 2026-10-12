# Security and quality gates

EdgeORE records evidence for a specific source and artifact. A successful build, tag, uploaded package or release draft does not establish a production-qualified app.

## Agent profiles

- [Security reviewer](.github/agents/edgeore-security.agent.md): read/search review of wallet, receipt, storage and provider boundaries.
- [Quality verifier](.github/agents/edgeore-quality.agent.md): scoped checks and evidence reporting.

These are repository agent profiles for compatible coding tools. Adding a profile does not run an agent, install a scanner or prove that a review happened.

## Applicable checks

| Change or gate | Entry point | Evidence boundary |
|---|---|---|
| Documentation | Review relative links and `git diff --check` | Documentation validity |
| Android source | `bash scripts/qualify.sh` | Unit, lint, debug/test assembly and manifest policy; no device result |
| Native visuals | `bash gradlew :app:verifyRoborazziDebug -Pscreens` | JVM render comparison; inspect differences before re-recording |
| Receipt checker | `bash gradlew -p tools/receipt-checker test` | Signed-content integrity; not proof of work/payment |
| Storage gateway | `python3 -m unittest discover -s services/storage-gateway -v` | Backend unit coverage; not a qualified deployed provider |
| Offline kit | `python3 -m unittest discover -s tools/edgeore-kit -v` | Advisory planning and manifest integrity |
| Dependency declarations | `python3 scripts/build-policy.py` | Offline declaration policy, not a resolved-graph audit or credential scanner |
| Device and wallet | [Device runbook](docs/DEVICE-RUNBOOK.md) | Exact installed APK and actual actions; preserve NOT_RUN until executed |
| Production phases | [Gate definitions](docs/production/GATES.json) | Each phase requires its own recorded evidence |

Run applicable suites separately and retain their logs and counts. A skipped test has not passed. Record both the PR head and any CI synthetic merge commit. Hash the actual downloadable APK/package rather than assuming it matches a local build or ZIP hash.

## Security and release criteria

Preserve exact reviewed/signed bytes, verified signatures, durable state before network effects and observation-only recovery. Unavailable metrics, providers and outcomes remain explicit. Follow [SECURITY.md](SECURITY.md) for private reporting and [NOTICE](NOTICE) for upstream obligations.

Review tags bind source identity. Draft releases contain review artifacts and qualification limits. A production release additionally needs the owner's license decision, private release signing, relevant device/wallet/provider gates and explicit publication authorization. See [release and package guide](docs/releases-and-packages.md).
