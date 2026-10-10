# EdgeORE unified storage adaptation

Implementation candidate; not a production qualification. Base: origin/main 268fd74.

## One app, distinct responsibilities

Keep Kotlin/Compose as the user interface, LocalVault as the device-bound cryptographic boundary,
BackupCoordinator as the durable operation controller, and signed storage receipts as records of
observations. The optional OwnedGatewayBackupProvider plugs into the existing BackupProvider
interface. services/storage-gateway provides a real owned-host ciphertext backend using SQLite
transactions and synchronous=FULL. No upstream source was copied and no provider account was created.

The shipped app still selects NotConfiguredBackupProvider. The optional adapter is not selectable
in the UI yet. Do not advertise working remote backup based on this candidate.

## Strategy taken from each repository

| Source | Useful strategy | EdgeORE decision |
|---|---|---|
| Titannet-dao/titan-storage-js-sdk | File operation abstraction and explicit provider acknowledgements | Adapt provider operations through BackupProvider. Titan transport remains pending API/authentication review; no guessed endpoints. JS browser SDK is not assumed to run on JVM or Node. |
| Titannet-dao/titan-node | Separate resource-provider processes from user UI | Host gateway remains a separate process. No Titan node is installed or operationally qualified. |
| TitanNet/Fil-Titan-Agent | Separate execution agent, provider identity and settlement | Keep agent observations distinct from payments. Filecoin FIL/USDFC rewards are not EdgeORE SOL rewards. |
| uprockcom/solana-program-library | Separate on-chain programs from storage transport | Retain existing EdgeORE settlement prototype. No SPL fork or new payment program needed for uploading ciphertext. |
| uprockcom/fuel-engine | Capability-limited, signed workloads in a bounded execution engine | Future optional executor boundary; never execute uploaded files in this gateway. No JNI engine included. |
| peaqnetwork/peaq-network-node | Separate network/node lifecycle from application lifecycle | Apply separation only; do not add another blockchain to the Solana app. |
| pi-apps/carrierwave-google-storage | Replaceable object-store adapter, server-side credentials | Adopt backend adapter separation. No Rails dependency or public GCS bucket. |

Sources are the respective public README files reviewed this session. Reuse is architectural,
not relicensing. Titan SDK LICENSE.txt has placeholder attribution and incomplete MIT text;
clarify before copying implementation. Preserve upstream attribution for any future vendored code.

## Implemented protocol

All routes require a bearer credential supplied outside source control.
The gateway binds only to 127.0.0.1:19844. Android requires an HTTPS origin with owner-managed TLS.
No IPFS administrative API or arbitrary remote URL is exposed.

- PUT /v1/jobs/{id}: octet-stream ciphertext, Content-Length, X-Ciphertext-SHA256.
  Repeating the same job/content is idempotent; changing its digest or reusing a canceled job fails.
- GET /v1/jobs/{id}: acknowledged/canceled observation with ciphertext digest and observedAt;
  404 means no gateway record, not proof an upload never occurred elsewhere.
- GET /v1/objects/{sha256}: authenticated retrieval, bounded by the client.
- POST /v1/jobs/{id}/cancel: gateway cancellation acknowledgement. Prevents retrieval through
  that canceled job; does not delete bytes or prove erasure. Another active job with identical
  ciphertext can retain access to its own copy.

No operation returns PINNED. SQLite acknowledgement is not IPFS pinning or durability across
host failures. Remote references are ciphertext digests; filenames and plaintext digests are
absent from the upload contract. Existing BackupCoordinator checks the downloaded digest,
authenticates/decrypts and publishes a new object after successful verification.

## Run the development gateway

Use Python 3 on an owned host. Set EDGEORE_STORAGE_TOKEN through a secret manager or private shell
configuration to at least 32 printable random characters. Never paste it into logs or an APK.
Set EDGEORE_STORAGE_DB to an absolute path on private, durable storage.

    python3 services/storage-gateway/server.py
    python3 -m unittest discover -s services/storage-gateway -v

Place a hardened HTTPS reverse proxy in front of loopback before Android integration. Configure
request-body limits, connection/concurrency limits, timeouts and a host disk quota. Do not expose
Python's development HTTP server directly. This prototype has one operator credential and one
namespace: it is not a multi-user production service. Production needs scoped expiring auth,
tenant isolation, quotas, key rotation, monitoring, retention/delete policy and disaster recovery.
Cancellation cannot interrupt a blocking network call immediately; client read/connect timeouts
bound the wait. Streams check coroutine cancellation between reads/writes.

## Remaining implementation

1. Qualify Kotlin adapter compilation and malformed-response/redirect/timeout tests.
2. Add explicit provider settings and consent; store credentials with Android Keystore protection.
   Inject OwnedGatewayBackupProvider into the coordinator only after configuration. Configured
   means a route was supplied, not that the server was reached.
3. Add a gateway-side Titan or owned-IPFS provider after verifying its documented protocol,
   authentication, retention, quotas and failure semantics. Provider tokens stay on the host.
4. Implement a separately reviewed portable backup envelope. Existing EOV2 backup uses the same
   device-bound key; it cannot recover on a fresh installation or after loss of that key.
5. Demonstrate ciphertext upload, interrupted upload + observe, download, wrong-key refusal and
   authenticated restore on the exact tested APK. Do not claim second-device restore until the
   portable format has actually passed that test.
6. Keep SOL settlement separate and unqualified until approved deployment and actual payout
   observation. Storage receipts verify signed contents, not useful work or payment.

## Evidence

Backend: 8 tests passed, including real loopback HTTP authentication and upload/retrieve/cancel.
Android compile: BLOCKED, Gradle 8.9 distribution download failed with Network is unreachable.
No new APK, Titan request, deployed gateway, device restore or payment was observed.
Logs are in evidence/storage-gateway-adaptation. Historical Android counts do not qualify this diff.
