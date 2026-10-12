# Contribution session (Mine → Start Mining)

"Start Mining" is the owner's chosen button text. What it starts is a **contribution session on the owner's own
equipment**: bounded, cancellable, measured work on a node and a storage gateway the owner runs. It mines nothing,
earns nothing and involves no ORE, SKR, points, tokens, payment or wallet signature. EdgeORE is not a token sink,
points or gambling product.

Code: `app/src/main/java/com/edgeore/app/contribution/ContributionSession.kt` (state machine) and `SessionParts.kt`
(the two parts). UI: `MiningSessionPanel` in `ui/screens/MineScreen.kt`. Setup: Nodes (pairing scope
`RUN_PROOF_JOB`) and Storage (*Your storage gateway*).

## Inventory: what is real in this build

| Component | State before this change | Used by a session |
|---|---|---|
| Owned-host node pairing + DeProof `deproof-node` agent (pinned `7431f08`, binary SHA-256 in `scripts/node-agent.pin`) | Real: pinned-certificate TLS, Ed25519 pairing, `observe`/`revoke`. The agent also implements `RUN_PROOF_JOB` (`discoverProofJobs`, `proof`, `cancelProof`), `SHARE_BANDWIDTH` (`consent`, `transfer`, `stop`) and adapters, none called by the app | **Yes**: `RUN_PROOF_JOB`. The app can now pair with that scope and run the node's owner-pinned proof job |
| Proof backend on the node | Real when the owner starts the agent with `-proof-tool/-proof-job/-proof-owner`: the DeProof `tools/proof_jobs.py` runs gnark v0.14.0 Groth16/BN254 on a fixed sample circuit (x³+x+5=35), 60 s limit, separate verifier process, node-signed result | **Yes**, one job per round. Without it the node answers `PROOF_BACKEND_UNAVAILABLE` and the part shows *Not configured on the node* |
| Contribution scheduler (WorkManager, unmetered + charging + battery not low) | Real scheduling; it runs **checks only**, no workload | **No.** A session is foreground and user-started. The scheduler is unchanged and still runs no workload |
| Storage vault (AES-256-GCM EOV2, Keystore key) | Real, local | **Yes**: seals the session's chunk (`LocalVault.sealDetached`) and decrypts it after download |
| Owned storage gateway (`services/storage-gateway`, PR #15) + `OwnedGatewayBackupProvider` | Real server and client, provider **unwired**, TLS trust was platform CAs only | **Yes**, now with **SPKI pins entered at setup** (`SpkiPinning`, required: no pin, no gateway part). The vault's *Remote backup* is still *Not configured*: the gateway is used by sessions only |
| Bandwidth | No real measured transfer: the Storage *Bandwidth sharing* consent sends nothing ("Shared by EdgeORE: 0 B"); "since boot" figures are device-wide counters. The agent's `SHARE_BANDWIDTH` transfer is real but not called by the app | **Yes, to your own gateway only**: bytes this phone writes to and reads from the gateway's TLS stream each round. Not bandwidth sharing with anyone else |
| AI jobs (owned-host Ollama chat; on-device LiteRT-LM) | Real clients, user-initiated chat only; on-device path not run on a phone | **No.** A session does not run AI. It would need a model and prompt the owner chose; not part of this change |

## What one session does

`IDLE → STARTING → ACTIVE → STOPPING → IDLE`, or `FAILED` with the reason. Only `ACTIVE` swings the pickaxe.

1. **Start Mining** turns Edge Mode on if it was paused and starts a session. `STARTING` checks, in order:
   Edge Mode on; every Edge Mode gate passes (charge-only, thermal guard, battery reserve, each as you set them);
   an unmetered network (Wi-Fi) is observed. If any fails: `FAILED` "Not started: …", nothing runs, nothing is
   recorded. If no part is configured: `FAILED` "Nothing to run. Not configured: pair a node … set up your storage
   gateway …".
2. `ACTIVE` runs rounds back to back. A round is each configured part once:
   - **Proof job on your paired node** (`NodeProofPart`): `discoverProofJobs` once, then a signed `proof` command
     with `explicitConsent: true`. The phone checks the node's Ed25519 signature over the result against the paired
     node key, that the job and operation ids match and that the node's verifier said `VALID`. Measured: jobs
     verified, current and total round-trip time (phone monotonic clock). The agent reports no CPU time, so none
     is shown.
   - **Encrypted chunk to, on and back from your gateway** (`GatewayRoundTripPart`): one 256 KiB chunk of random
     bytes sealed with the vault key per session (262,205 bytes as EOV2). Each round uploads it over SPKI-pinned TLS
     (the gateway re-checks the digest and stores it once), requires the gateway's acknowledgement of its SHA-256,
     downloads it, checks the SHA-256 and decrypts it. Measured: bytes sent and read back (counted on the TLS
     stream by the phone), bytes stored (acknowledged), round trips verified, transfer time.
   The gates are re-checked every 2 s; a failing gate stops the session as `FAILED` "Stopped by policy: …".
3. The session ends at **Stop** (→ `IDLE` "Stopped by you."), at a gate failure, when every part has failed, or at
   the session limits: **100 rounds or 10 minutes** (at most about 50 MiB through the gateway). Pausing Edge Mode
   stops it too.
4. **Stop** cancels immediately: the phase leaves `ACTIVE` (the pickaxe rests), the running part's coroutine is
   cancelled, and the node gets `cancelProof` (the agent kills the prover process group). A first upload that was
   never acknowledged is cancelled on the gateway; an acknowledged chunk stays stored.
5. Receipts (signed by the app's receipt key, chained, verifiable by the JVM verifier): one per part that completed
   at least one round (`NODE PROOF_JOBS_VERIFIED`, `STORAGE GATEWAY_ROUND_TRIPS_VERIFIED`) with its measured totals
   and digests, then a `POLICY` summary (`CONTRIBUTION_SESSION_COMPLETED` / `_STOPPED` / `_FAILED`). Payment is
   always `NOT_OBSERVED`. A part that is not configured is shown as such, is not run and is not counted.

## Nothing is invented

`Measurement` cannot be built without a named source and a non-negative value. Parts report measurements only from
their own callbacks (TLS stream byte counters, the phone's monotonic clock, node-signed results). Before a part
reports, the UI shows no number for it; a not-configured part shows what to set up instead.

## Evidence (build machine, loopback)

`scripts/session-e2e.sh` builds the pinned agent and prover, creates an owner-pinned sample job, starts the agent
with its proof backend, starts the gateway behind the pinned Caddy with a throwaway self-signed certificate, and
runs `ContributionSessionE2ETest`: pair with `READ_NODE,RUN_PROOF_JOB`, one full 5-round session, then a session
stopped during round 2's proof job. Output: `evidence/contribution-session/e2e/`. Phones: **NOT_RUN** (no phone
was used; the phone-side gates are covered by unit tests only).

## Setup on your hosts

- Node: `deproof-node -pair-scopes READ_NODE,RUN_PROOF_JOB -proof-tool /abs/tools/proof_jobs.py -proof-job /abs/job/job.json -proof-owner /abs/job/owner-public.key`
  (create the job with `python3 tools/proof_jobs.py create DIR` from the pinned DeProof source; prover built from
  its `prover-worker`), then pair in Nodes with the `RUN_PROOF_JOB` box ticked.
- Gateway: `services/storage-gateway/server.py` behind an HTTPS proxy (`Caddyfile.example`); in Storage enter the
  origin, the bearer token and the certificate's SPKI pin, e.g.
  `openssl x509 -in cert.pem -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64`
  prefixed with `sha256/`. The token is encrypted with an Android Keystore key and never shown again.
