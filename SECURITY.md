# Security policy

EdgeORE handles wallet review, signed receipts and device-bound encrypted storage. Report suspected flaws in those boundaries privately.

## Versions and scope

The current Android app is a review candidate. Debug APKs, devnet prototypes and source-side test results do not constitute a qualified production release or a guaranteed supported release line. Include the exact commit, APK version and build stamp when reporting an issue; where possible, reproduce it against current `main`.

Relevant reports include mismatched reviewed/signed messages, unauthorized or repeated submission, incorrect recovery after an unknown outcome, receipt-verification bypass, damaged-store handling, credential exposure, vault confidentiality/integrity, and node or storage-provider authentication and consent boundaries. A receipt signature alone is not proof of work or payment.

## Private reporting

Use this repository's **Security → Report a vulnerability** option when GitHub private vulnerability reporting is available. This policy does not assert that the repository setting is enabled.

If that option is unavailable, contact [CodesbyFebin](https://github.com/CodesbyFebin) through an available private channel. If no private channel is listed, open a minimal issue titled `Request for private security contact`, without exploit details, secrets or affected-user information. The maintainer can arrange a private reporting channel before you send the report.

Include, privately:

- Affected component, commit/build stamp, Android version and relevant wallet/provider version.
- Expected behavior, observed behavior and a minimal reproduction using resources you control.
- Impact, prerequisites, and whether an actual action occurred or its outcome remains unknown.
- Redacted logs or a minimal test case. Never include seed phrases, private keys, passphrases, access tokens or private documents.

Do not publish exploit details in a normal issue or pull request before coordinating disclosure. An unknown transaction outcome is not permission to resend it.

## Handling and disclosure

The maintainer will assess reproducibility and impact, coordinate follow-up with the reporter and record affected source/artifact identities when preparing a fix. A patch, release build and device-qualified result must be reported separately. Disclosure timing and any advisory should be coordinated with the reporter and affected maintainers; this document does not announce a bounty or promise a response deadline.

See [CONTRIBUTING.md](CONTRIBUTING.md) for contribution boundaries and [NOTICE](NOTICE) for upstream attribution.
