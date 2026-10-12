# Security Policy

## Supported versions
Only the latest `main` and the most recent tagged release receive security fixes.

## Reporting a vulnerability
Please **do not open a public issue**. Report privately via GitHub's
[private vulnerability reporting](https://github.com/CodesbyFebin/EdgeORE/security/advisories/new).
Include affected commit/tag, reproduction steps, and impact.

- Acknowledgement within 3 business days; triage assessment within 7 days.
- Coordinated disclosure: we aim to fix within 90 days and will credit reporters who wish it.
- Please avoid accessing others' data, degrading service, or moving real funds. Use devnet.

## Scope
In scope: the Android app (`app/`), receipt checker (`tools/receipt-checker`), storage gateway,
offline kit, on-chain programs (`onchain/`), and CI workflows.
Of special interest: anything that breaks the trust spine (review → digest pin → MWA sign →
verify-before-submit → ledger → receipt), e.g. signing bytes that differ from what was reviewed,
receipt forgery, or pinning bypass.

Out of scope: third-party wallets, Solana RPC providers, and the ORE protocol itself.
