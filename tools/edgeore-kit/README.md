# EdgeORE offline extension kit

Python 3.11+, standard library only. Does not connect wallets, upload data, or run AI models.

    python3 -m unittest discover -s tools/edgeore-kit -v
    python3 tools/edgeore-kit/edgeore_kit.py hash-consent your-consent.json
    python3 tools/edgeore-kit/edgeore_kit.py verify-evidence your-evidence-directory

`consent_bytes` produces a versioned deterministic byte representation for the limited v1 format.
`plan` refuses absent capability qualification, expired/revoked consent, exceeded budgets and an
unapproved destination. Inputs `qualified` and `available` are caller-supplied assertions; the planner
is NOT an attestation verifier, executor, scheduler, runtime quota enforcer or wallet approval.
Retention is bound into the consent bytes, but enforcement needs a runtime/backend integration.
Revocation state likewise needs an authoritative durable registry. A successful preflight returns
ELIGIBLE_FOR_REVIEW and leaves execution NOT_RUN and settlement NOT_OBSERVED.

`verify_bundle` streams SHA-256 over explicitly listed files. Manifest paths must be relative,
unique and non-symlinked. Each artifact is capped at 256 MB, manifest at 1 MB and count at 256.
Use a private stable directory during verification; concurrent file replacement is outside this
prototype's trust boundary. The verifier checks content only. An attacker able to rewrite the
unsigned manifest can rewrite hashes too. Source commit/environment are labels, not verified
provenance. This tool supplements rather than replaces the existing signed receipt checker.

Never export tokens, private keys, passphrases, raw documents or entire browser logs to an evidence
bundle. This verifier does not detect credentials or redact files. Explicitly review selected files.

`schemas/evidence-manifest-example.json` shows the shape only: its file list is empty and its commit is all zeros,
so `verify-evidence` refuses it as-is (at least one listed file is required). `schemas/consent-example.json` expired
at `expiresAt: 1`, so `plan` always refuses it; `hash-consent` still hashes it.
