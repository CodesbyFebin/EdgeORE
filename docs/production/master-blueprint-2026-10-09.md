# EdgeORE master blueprint and Google AI Studio build prompt

**Builder:** Febin Francis / CodesbyFebin. **Prepared:** 9 October 2026. **Product:** EdgeORE. **Tagline:** Your Edge. Your AI. Your Proof. **Reference package:** EdgeORE-repo.zip, supplied by the owner. **Primary target:** native Android application, package `com.edgeore.app`.

## 1. How to use this document

Give the build agent the reference source, this blueprint, the audit report, and the two supplied screen reference images. Start with the short launcher prompt included in this kit. Then apply the phases in this document in order. This is an execution specification for creating and finishing a real application, not a certificate that a generated application has already passed tests. The requested ten-out-of-ten outcome means the agreed product scope is implemented, measured, recoverable, installable, and represented honestly.

Use Google AI Studio's Android workflow where it is available. Ask for Kotlin and Jetpack Compose explicitly so the agent does not create a React website. Export the project to Android Studio when its environment cannot support a required component. Do not assume an uploaded ZIP is parsed successfully: require a file inventory, known class names, and the inspected source identity before the agent claims it used the repository. If ZIP ingestion is unavailable, provide the source-context text file included in the kit and import the full source locally.

There are two valid development modes. Recovery mode improves the existing repository while preserving working logic and history. Clean-room mode creates a new working directory with the same application identity and design, selectively transferring reviewed code. Clean-room mode must not delete the original repository or discard its known security boundaries. Neither mode starts with a fabricated green dashboard. Both modes implement the same contracts and qualification gates.

The agent should work autonomously on code, tests, documentation, and reversible local changes. It should pause only for unavailable external prerequisites, private signing material, a physical wallet approval, or a publication decision that requires the owner. A blocked native runtime does not justify inserting a fake chat answer. A missing node does not justify creating sample health data in production. Complete independent work while preserving the blocked feature's actual status.

The final handoff is a source project, a tested candidate APK when the build environment can produce it, evidence, build instructions, and submission materials that match the installed version. An AI Studio preview URL is useful for iteration but is not the Android deliverable. A ZIP containing Kotlin files is source delivery; it is not automatically proof that the app compiles, installs, signs, or performs meaningful on-chain work.

## 2. Current platform constraints and correct tool responsibilities

Official Google documentation checked for this brief describes an Android mode that generates Kotlin/Compose projects, offers an emulator preview and physical-device installation, and supports ZIP export. Its Android-specific limitations include a single activity and module, no server runtime, and no NDK/native-code support. Use the Android-specific documentation when general Build-mode guidance differs. Reference: https://ai.google.dev/gemini-api/docs/aistudio-android. These capabilities can change; verify them in the active workspace before committing to a tool-dependent phase.

Therefore keep the AI Studio project in one `app` module with clear package boundaries. A modular domain design does not require many Gradle modules. Owned-host AI and node agents can be contacted by the Android client, but their server implementation must be built and deployed in a separate environment. Native mining or a runtime requiring custom native integration needs an Android Studio/NDK handoff if unsupported in the selected build surface.

Google AI Studio may build and run supported work in its own environment. The pasted claim that it can only emit files is no longer universally accurate. However, a build performed there proves only that environment's result. A physical-phone wallet approval, an owned-host response, and release-key continuity need their own evidence. Record which environment produced each observation and never copy one environment's success into another environment's gate.

A Kotlin APK does not install on iOS. Target compatible Android phones, including Solana Seeker, while designing interfaces that could later support a separate iOS client. Do not promise every Android model either: minimum API level, hardware capacity, wallet availability, native ABI coverage, and feature-specific requirements determine support. Tablets and foldables require layout testing; Wear OS and Android TV are separate product surfaces, not automatic compatibility wins.

## 3. Product thesis and the smallest complete user journey

EdgeORE is a mobile workspace for controlling user-owned edge resources, accessing private AI, storing encrypted files, reviewing Solana actions, and exporting records of what happened. The application should solve fragmentation: users otherwise need separate tools for wallet actions, model clients, host monitoring, file encryption, and record keeping. Its memorable differentiator is exact-message review connected to independently inspectable outcomes.

The submission-critical loop is intentionally concrete. The user launches the app, authorizes a wallet through Mobile Wallet Adapter, sees an actual devnet balance, prepares a small System Program transfer, reviews its exact decoded meaning, signs through the wallet, submits separately, observes confirmation, and exports a receipt. A second machine verifies the receipt and refuses a modified copy. That is meaningful interaction, not merely an RPC read.

Private AI adds a second useful loop when an owned host exists: discover actual models, choose one, send a prompt to the reviewed private endpoint, receive a real answer, and cancel an active request. The app records execution location and measured latency without publishing prompt content by default. On-device inference is a distinct capability requiring a runtime and weights on the phone. Do not hide a host client behind an on-device label.

Storage adds a local utility loop: import a selected file through Android's document picker, encrypt it with a device-protected key, display its saved identity and size, decrypt it through an explicit export, and delete it after confirmation. Nodes adds an authenticated infrastructure loop when a compatible agent exists: pair, inspect health, issue only qualified bounded operations, and revoke access.

ORE and SKR are conditional protocol integrations rather than assumed rewards. The product may express a long-term economic thesis, but AI use or storage occupancy cannot generate ORE by inventing a multiplier. An implemented protocol adapter must identify its accounting rules and observed result. Until then, the reward card remains unavailable or not observed with a clear explanation.

## 4. Reference repository identity and preservation rules

The supplied archive's main checkout is `ab009ff`. Its current source declares `0.2.6-review`, versionCode 8, package `com.edgeore.app`, min SDK 26, target/compile SDK 35, Java 17, Kotlin 2.0.21, AGP 8.7.3, and Gradle 8.9. Treat these as inspected baseline configuration, not a mandate to upgrade every dependency. Resolve compatibility before any version change.

The local `feature/kotlin-dapp` branch is older, at `7433eee`. The remote-tracking `origin/feature/kotlin-dapp` reaches `ab37f23` and contains later spend-reservation and claim-hardening work. Do not confuse those references. Review later patches individually and integrate only coherent fixes. The in-memory spend ledger is useful reference material but does not close durable recovery requirements.

`EdgeORE-extras` contains an old APK and preview files outside the repository. The binary manifest identifies the included APK as `0.2.0-hackathon`, versionCode 2. Its SHA-256 is `c4c1566f01b0b0f86b63f898a3742b7be21cc2e8d9c56867708f4cc1191f02b9`. It does not validate current main. Preserve it as historical material and build a new candidate from the selected source.

The source audit gave the archive a provisional 5.4/10. That is a review score, not an official judging result. A fresh audit build attempt stopped at Gradle bootstrap because network access failed before compilation. Historical evidence includes test and assembly records, but the current session did not reproduce a fresh successful suite or physical-device journey. The new agent must establish its own results.

Preserve exact-message validation, integer lamport handling, explicit unavailable states, separate signing and submission, private-host policy, cryptographic records, and device-bound vault protection. Repair weaknesses rather than replace these with superficial UI code. Do not overwrite user-restored files without first inspecting their diff. Keep original data and schemas until migration is specified and tested.

## 5. Authority, claims, and evidence vocabulary

Use six separate implementation statuses: absent, implemented, tested in isolation, integrated, observed on device, and release-qualified. A function can be implemented without having run against a wallet. A UI can be integrated without measuring hardware. A successful emulator launch does not close a physical-phone gate. Show these distinctions in development reports and simplify them into honest user-facing copy.

Use explicit observation states throughout the application: loading, available, unavailable, stale, refused, cancelled, and failed. Zero is a valid measurement only when a source returned zero. An unavailable balance is not zero SOL. A disconnected host is not a healthy idle node. A missing signature is not a rejected payment unless the app actually recorded that refusal.

Every operational observation should carry a source, timestamp, scope, and relevant freshness policy. A balance may include cluster and slot. A CPU rate includes sample interval and device/process scope. A host response includes endpoint identity and model identity. A record should state whether it comes from the app, node, chain RPC, or an external provider.

Never synthesize transaction signatures, balances, hashrates, model replies, provider acknowledgements, test counts, certificates, or PASSED badges. Test fixtures are permitted in test source sets and clearly isolated preview code. Production dependency wiring must never inject those fixtures. A deliberate concept preview needs a visible sample label and cannot be used as operational evidence.

A receipt signature proves that the corresponding key signed specified data. It does not establish physical truth, payment, ownership, or completeness unless those properties have separate evidence. The four dimensions in the reference artwork remain useful: local observation, node-signed record, independent verification, and provider acknowledgement. Payment stays a separate status.

Do not hardcode a submission deadline from contradictory conversation excerpts. Require the owner to supply the current official event page and verify it before scheduling submission. Submission eligibility and prize categories are external rules, not properties the application can infer. The project can be built correctly without inventing an event claim.

## 6. Visual system and image-to-code interpretation

Use the supplied images as layout references. They show five pages in order: Mine, Private AI, Storage, Nodes, Receipts. They use a near-black background, dark inset cards, mint status accents, copper primary actions, thin borders, rounded corners, restrained iconography, and CodesbyFebin branding. The images explicitly label sample UI and pending qualification; retain that integrity when transferring the design into real code.

Use design tokens rather than scattered hexadecimal values. Start with background `#101414`, surface `#1C2323`, inset `#121919`, border `#3D4745`, primary text `#EEF2EF`, muted text `#A6B2AC`, mint `#35E7C0`, copper `#FFA365`, deeper copper `#E86D35`, and danger `#FF8275`. Validate contrast for actual text sizes and control states instead of assuming a visually attractive palette passes accessibility.

The reference's mountains and glow belong mainly in banners, onboarding artwork, and marketing. Inside the app, keep backgrounds quiet so status and transaction values remain readable. The compute ring can use a modest gradient; it must not obscure its state label or animate fake progress. The wordmark is EdgeORE, with Edge in light text and ORE in copper.

Use a consistent spacing scale, practical touch targets, clear typography hierarchy, and flexible layouts. A reference screenshot is not a rigid canvas. Large text, narrow phones, landscape, split-screen, and keyboard visibility must not clip critical review details or hide confirmation controls. Provide scrollable content while keeping navigation and action semantics predictable.

Do not copy sample storage quantities, CPU percentages, wallet abbreviations, model names, timestamps, signatures, or pairing URLs into production. The model gallery may show a catalog only when labeled as downloadable/not installed and backed by a trusted manifest. A node address in the artwork is a visual placeholder, not the owner's live agent route.

## 7. Project structure for a single-module build

Keep one Android application module for AI Studio compatibility, but split packages by responsibility. Proposed packages are `core`, `data`, `wallet`, `solana`, `operations`, `receipts`, `vault`, `ai`, `nodes`, `resources`, `settings`, and `ui`. Existing code may be moved incrementally after tests protect its behavior. Avoid a large speculative rewrite of every file before the first build.

`core` owns typed observations, errors, identifiers, time abstractions, and exact amount parsing. `data` owns durable storage, schema migrations, and repository implementations. `wallet` wraps Mobile Wallet Adapter and exposes typed authorization/signing results. `solana` owns supported message construction, strict decoding, RPC, and cluster identity. `operations` coordinates reviewed transactions and recovery.

`receipts` owns record schemas, signing envelopes, verification, privacy exports, and trust context. `vault` owns encrypted objects, bounded imports, quotas, and explicit deletion. `ai` owns endpoint policy, model discovery, inference requests, and execution location. `nodes` owns pairing, protocol commands, pinning, scopes, and revocation. `resources` owns telemetry and actual workload coordination.

UI screens consume state flows and invoke actions; they do not implement cryptographic policy or write transaction bytes. ViewModels combine use cases but should not become the sole persistent store. Separate transient navigation state from durable financial state. A screen recreation must never be responsible for deciding whether a transaction was already sent.

Use dependency injection through a small explicit application container initially. Hilt is optional only when its cost and compatibility are justified. Add Room or another durable storage approach through a documented decision. Keep test-only fakes behind interfaces and outside production wiring. Do not add Firebase, wallet substitutes, analytics, or a cloud LLM because a generator suggests them automatically.

## 8. Domain model and persistence contract

Define stable identifiers for operations, receipts, vault objects, host identities, model installations, and key epochs. User-facing labels are editable metadata, not primary keys. An account's public key and cluster bind a financial operation. A host fingerprint binds a node relationship. A vault object's generated ID binds its ciphertext and metadata.

A typed observation contains value when valid, observation time, source, status, and reason when invalid. It may include sequence, slot, or interval depending on the domain. Do not represent all outcomes as nullable strings. A sealed result makes it harder for the UI to render unavailable data as success. Persist only the fields needed for recovery and evidence, with privacy-aware retention.

Use durable transactions for coupled changes. Reserving spend and creating a draft should be atomic. Publishing a vault object and consuming its storage reservation needs a recoverable protocol. Appending an operation outcome and its receipt should either commit together or have a repairable outbox relationship. A crash between independent writes must not lose the only record of a possible transfer.

Plan migration from the reference files instead of deleting them. Existing receipt IDs and signatures must retain their original byte representation and key context. Import old logs with explicit legacy schema classification. Do not re-sign old records as though the new app observed their original event. A migration can authenticate its conversion process separately without changing historical claims.

Settings persistence is appropriate for theme, resource preferences, notification choices, and endpoint configuration references. It is not appropriate as the only ledger for unknown transfers. Protect authentication tokens and private app keys using supported storage and cryptographic mechanisms. Avoid dumping endpoint credentials, prompt text, or file content into logs.

## 9. Wallet connection, identity, and multi-wallet behavior

Integrate the real Mobile Wallet Adapter library already present in the repository. Maintain a typed wrapper around authorization, account selection, signing, disconnect, and error handling. Keys stay in the wallet app; Seed Vault may protect a compatible wallet on supported hardware but is not a universal property of all Android wallets. Never request seed phrases, secret keys, or manual private-key import.

Multi-wallet means users can choose a compatible installed wallet and later disconnect or reconnect with another. It does not mean the app silently maintains signing authority across unrelated wallets. Bind each pending operation to its authorized public key, cluster, and exact message. An account change invalidates approval for a different signer and requires an explicit new review.

Display a shortened address for scanning and offer the complete address for review and copy. Wallet name and icon are presentation metadata; they are not proof of account ownership by themselves. If adding a backend login later, define a separate challenge-based sign-in protocol and verification. Ordinary MWA authorization should not be described as a server-authenticated login when no server session exists.

The app identity URI and icon path must be real owner-controlled assets before release. Test how the wallet presents EdgeORE. Do not assume `edgeore.ai` is live or owned just because it appears in source. If using a verified alternative domain, document the change and review any related trust or association behavior. Presentation should consistently identify CodesbyFebin as the builder without implying Solana sponsorship.

Handle missing wallet, declined association, expired token, cancelled signature, malformed return, network-independent wallet errors, and Activity lifecycle transitions. Reauthorization should be explicit and safe. Disconnect should invalidate the wallet authorization where supported and clear local session state, while retaining financial recovery records that still need observation.

## 10. Exact-message transaction review

Start with the supported System Program transfer. The decoder must reject unsupported message versions, unexpected programs, additional instructions, changed accounts, malformed lengths, extra signers, and unrecognized data. Avoid an apparently generic Approve button over arbitrary bytes. A narrowly supported instruction with exact validation is safer and easier to demonstrate than a wide decoder that guesses meaning.

Convert decimal SOL text directly to integer lamports. Reject negative values, unsupported precision, overflow, exponent forms if not deliberately supported, empty strings, and locale ambiguities. Never pass financial amounts through floating-point arithmetic. Display both the exact SOL amount and the integer amount where useful for detailed inspection.

Prepare a message using an observed cluster and recent blockhash. Decode the constructed message and compare it with requested sender, recipient, and amount before review. Store the exact serialized message bytes and their digest. Sign authorization applies to these bytes, not to a mutable form containing only a recipient and amount. Any relevant edit produces a new draft and invalidates prior approval.

Show the full recipient, sender, cluster, amount, fee observation or unknown state, blockhash validity, supported program, and message digest access. A clear title should say devnet transfer during qualification. Unknown blockhash validity blocks signing. Unknown fee requires a defined product policy; do not hide it behind a total that looks final. Use actual balance and fee observations with timestamps and fail safely when required inputs are unavailable.

After the wallet returns signed bytes, parse the transaction, verify the signer and Ed25519 signature, and compare the returned message byte-for-byte with the reviewed message. Refuse mutation before broadcasting. Do not switch to sign-and-send merely because a tutorial uses it; the reference product intentionally separates signing from submission so returned bytes can be checked first.

## 11. Durable submission and restart recovery

Create a durable operation before interacting with the wallet. Proposed states are draft, reviewed, signing, signed, submit-ready, submission-attempted, outcome-unknown, confirmed, finalized, failed, rejected, and expired. Persist enough information to distinguish these states after process death. The user-visible state should be derived from stored facts, not reconstructed from an optimistic button label.

Store a stable operation ID, signer, cluster, message digest, signed signature, appropriate protected bytes or references, blockhash, last valid block height, amount, review time, signing time, attempt IDs, and observations. Minimize retained raw data, but do not discard information essential to verifying wallet returns or recovering uncertainty. Define retention separately for completed and nonterminal operations.

Record an attempt durably before network submission. Use a single-flight guard or transaction state transition inside the method, not only a disabled UI button. Identical network bytes may have one signature, but repeated local invocations can still corrupt receipts and budget accounting. Prevent those races at the repository boundary.

When submission times out, mark outcome unknown. The network may have accepted the transaction before the response was lost. Query the already known signature after restart and show the latest observed status. Never automatically create and sign a replacement transaction. A new message after expiry requires fresh review and wallet approval; reconciliation determines whether such an action is safe to offer.

Blockhash expiry alone does not prove a transaction never landed. Reconcile status using the documented chain/RPC policy and retain uncertainty when evidence is insufficient. Distinguish recent-cache non-observation from stronger history lookup. A fresh transaction must not be offered as though a null status conclusively erased the prior attempt.

## 12. Spend limits and safety policy

Implement per-operation limits and a clearly defined daily or rolling-window budget. Bind reservations to signer and cluster. Define whether the budget constrains principal, fees, rent effects, or all transaction costs. The UI should not advertise total exposure if it only reserves the transfer amount. A simple, accurately described cap is better than a sophisticated-sounding rule with undefined accounting.

Reserve funds atomically with draft creation, enforce the rule again before signing, and reconcile after submission. Unknown operations retain conservative reservations. Cancelled drafts and wallet refusals may release principal when safe. Confirmed operations should move from pending to spent without double counting. On-chain failed transactions may still consume fees; model this separately if fees are in scope.

Handle abandoned drafts, edited reviews, duplicate preparation, account switching, and process restart. The later remote feature ledger has useful ideas but is in memory and needs durable integration. Avoid copying it as a finished crash-safe solution. Write tests for concurrent reservations and for each terminal outcome, using a clock abstraction to test window boundaries.

Resource safety and financial safety are separate policies. Charge-only, battery reserve, and thermal decisions should guard actual workers. A CPU budget requires an enforcement mechanism in the worker or agent, not just a stored number. If a reading required by policy is unavailable, use a documented fail-closed decision and explain the reason in the UI.

An immediate stop action should cancel local qualified work and request remote cancellation where possible. It does not revoke an already broadcast transaction or guarantee a host stopped without acknowledgement. Show what was stopped, what is pending, and what cannot be reversed. Safety copy must match these technical boundaries.

## 13. Receipt schema and independently checkable evidence

Build receipts as a versioned protocol. Each record has an ID, operation ID, event type, exact signed representation, source, timestamp, key identity, and assurance fields appropriate to its event. Review, wallet signing, network submission, confirmation, node observation, and vault deletion are different events. Do not describe all of them as immutable payment receipts.

For wallet events, retain the reviewed message representation, digest, signer public key, wallet signature, cluster, and relevant operation linkage. Independent verification should check the wallet signature over the actual message, not merely check that the app signed a JSON statement containing a signature-looking string. App signatures establish the integrity of app records; wallet signatures establish authorization of message bytes.

Use the repository's app-signing design as the starting point, preserving its exact signing representation and historical compatibility. If using P-256 through Android Keystore, do not casually replace it with a claimed hardware Ed25519 key. Software Ed25519 wrapped with a Keystore-protected key is a different assurance. Document algorithms and key protection without overstating hardware support.

Support key epochs. An installation must not silently generate a different ephemeral signing identity on restart and then export earlier records under the current public key. If Keystore initialization fails, apply an explicit policy: disable new signing while allowing read-only access, or use a deliberately persistent, accurately labeled fallback. Store the correct verification key context with each epoch.

A bundle needs an explicit export mode. Selected records may be valid without being a complete history. A complete-history claim requires a defined checkpoint or continuity mechanism. Verify sequence links and report missing segments. An unsigned bundle digest catches accidental edits but does not independently prevent an attacker from rebuilding a smaller bundle with a new digest.

Provide a portable CLI verifier and an in-app verifier. Both reject unsupported schema versions, malformed lengths, changed message digests, invalid app or wallet signatures, and contradictory claims. Report provenance separately from self-contained signature validity. A public key supplied inside a file is not automatically a trusted identity.

## 14. Receipt privacy, durability, and tamper demonstration

Default exports should omit raw prompts, document contents, endpoint credentials, private host addresses where unnecessary, precise location, and unrelated device identifiers. Explain that excluding a public key may prevent self-contained verification unless the verifier obtains it through another channel. Privacy controls must not silently change the mathematical claim made by the Verify button.

Replace fragile whole-log append behavior with a durable indexed record store or a carefully designed append protocol. Valid earlier records must remain accessible if the final line is truncated. Show corruption explicitly. Never substitute an empty list for a damaged log and imply that no operations occurred. Recovery should preserve original bytes rather than rewrite history optimistically.

Export through Android's supported chooser or share mechanisms with narrow file access. Temporary exports should have retention limits. Avoid world-readable storage or broad file permissions. The user should preview the categories included and whether the bundle is selected, complete, or redacted before exporting.

A real tamper demonstration begins with a receipt created by the actual device transfer. Verify the original on another machine, change an authenticated field, and demonstrate refusal with a nonzero verifier exit. Unit fixtures remain useful, but they do not substitute for the real exported record. Record the file digest and verifier revision for both the original and modified copy.

Separate offline verification from chain confirmation lookup. Offline verification can validate recorded cryptographic material. Online lookup can compare a signature with an observed chain outcome. A stale or unavailable RPC cannot invalidate a mathematically correct signature, but it does limit the current confirmation claim. The UI should describe the result without collapsing all dimensions into one green checkmark.

## 15. Encrypted local vault and safe file lifecycle

Implement AES-256-GCM with a device-protected key, generated nonces, authenticated metadata, and a versioned envelope. The vault stores app-private encrypted objects selected through the document picker. It does not promise unlimited free storage or a personal cloud merely because files are encrypted. Device filesystem capacity and the app's allocation policy are separate values.

Use generated object IDs independent of filenames. Preserve the original display name as metadata and permit user renaming. Two filenames that normalize to the same safe string must not overwrite each other. Replacement is an explicit operation. Validate object IDs and filesystem containment in every read, write, and delete boundary, even when current callers appear safe.

Stream input through a hard byte limit before allocating large buffers. The reference imports read the full stream before applying their size check; repair that pattern. Provider-reported size can support early feedback but is not trusted as the only bound. A provider with unknown length, cancellation, or a maliciously large stream must not cause unbounded memory growth.

Write encrypted output to a temporary object, flush and close according to the chosen durability design, and publish atomically with metadata. Reserve capacity before writing and release it on failure. After restart, detect unfinished objects and reservations. A partially written file must not become a completed vault entry or success receipt.

Bind schema version, object identity, and selected metadata through additional authenticated data. Reject malformed IV lengths, unsupported algorithms, truncated tags, changed headers, and authentication failure. Do not return partial plaintext on decryption failure. Error messages should identify a damaged object without exposing sensitive contents.

The current key is device-bound. A backup of ciphertext alone does not make a restore possible on another phone. For the first release, document this clearly. Portable recovery requires a separately designed key export or recovery mechanism with its own consent and threat model. Do not conflate plaintext Export with encrypted cross-device backup.

## 16. Capacity, quotas, and future cloud sync

Show total filesystem capacity, filesystem free space, vault allocation, encrypted bytes used, active reservations, and available vault allowance as separate measurements. If a value is not measured, label it unavailable. The donut chart should be generated from actual bytes. Do not reuse the reference's 86 GB or 256 GB values unless the tested device reports them.

Define quota arithmetic exactly: available vault allowance equals allocated bytes minus used bytes minus reserved bytes, bounded by actual filesystem free space and operational overhead. For unknown-size streams, either reserve a declared maximum or grow reservations under a strict cap. Concurrent imports must not each assume the same available bytes.

The Storage page should support import, encrypted-object inspection, explicit plaintext export, deletion confirmation, and capacity refresh. Restore controls appear only when there is a tested restore format. A disabled cloud-sync card may explain that no provider is configured. It must not show synced quantities or a green Connected label from a preference alone.

A future cloud adapter needs authenticated provider identity, encrypted uploads, object manifests, integrity verification, retry behavior, conflict handling, quota accounting, and restoration tests. The adapter should not receive plaintext or decryption keys unless the user deliberately chooses a mode that permits that exposure. Record where encryption occurs.

Deletion semantics require careful copy: local deletion, remote deletion request, and confirmed remote deletion are distinct. A disconnected provider cannot acknowledge deletion. Retain pending actions when safe and expose their state. Do not claim secure erasure of flash storage simply because a file was unlinked; define the actual protection supplied by encryption and key management.

## 17. Private AI execution modes and model gallery

Private AI has explicit execution locations: unavailable, owned host, and on-device runtime. The selected mode is displayed near the title and in request details. Owned-host prompts leave the phone and travel to the configured server. On-device prompts are processed by a phone runtime. Neither label establishes a universal no-leak guarantee without considering logs, exports, tools, and other app behavior.

For owned-host mode, discover real installed models through the compatible API and display their returned identities. Do not hardcode a model as installed. Allow a user to inspect endpoint identity before sending a prompt. Refuse public or otherwise disallowed destinations according to the implemented policy. Validate all destination resolutions and bind connection behavior to that policy.

For a downloadable gallery, use trusted manifests with expected digests, source, license, size, runtime format, quantization information where relevant, and tested compatibility. Downloading a file is not installation. Installation is complete only when verification succeeds and the runtime can load it. The gallery should distinguish catalog, downloading, verified, installed, load failed, and ready.

A user-supplied checksum mode is still useful, but label it as matching the user's reference. It does not authenticate a publisher. Avoid combining incompatible runtime and model formats from earlier prompts. Select the runtime and consult its exact model support before generating integration code or download instructions.

Do not automatically add Gemini API calls to the private chat because the app is being built in AI Studio. The build tool and the application inference backend are separate. Cloud fallback stays off by default and remains absent in the submission scope unless the owner explicitly expands the product. No cloud API secret may be embedded in the APK.

## 18. Owned-host inference, streaming, and cancellation

The owned-host adapter must enforce origin policy, transport authentication, request size, response size, connect/read deadlines, and redirect refusal. Endpoint credentials, if introduced, need protected storage and careful redaction. Private IP classification does not prove ownership, and TLS trust must not be disabled to make a LAN demo work.

Perform DNS and network work off the main thread. Cancellation must reach the underlying transport so the phone can stop waiting promptly. Distinguish local cancellation from a host's confirmed stop acknowledgement. If the backend lacks a termination API, say so. The reference's warning that the host may finish is appropriate and should not be replaced with a false Stopped everywhere label.

Streaming responses need bounded incremental parsing. Reject oversized chunks, malformed framing, excessive total output, and unsupported content. Update chat text through immutable state or controlled buffering so UI recomposition does not grow quadratically with output length. Preserve a partial response only when clearly labeled as interrupted.

Keep conversation retention configurable. Default to local data with explicit clearing controls. If attaching documents, bound the input before reading, report what will be sent, and avoid automatically uploading the entire vault. A document picker grant authorizes local access; it does not by itself authorize transmitting all content to a server.

Measure request latency and actual transport outcome. Host-side RAM or token rates require host observations; phone process memory is a different metric. A saved 4 GB preference is not a measured limit. If coordinating competing work, pause a real worker through its interface and record the result rather than merely changing a ring label.

## 19. On-device AI implementation handoff

Treat phone inference as a separately gated runtime integration. Evaluate a supported Android runtime such as a currently documented LiteRT-LM path or another deliberately selected engine. Check license, model format, packaging, supported APIs, native requirements, and ABI coverage. This blueprint does not prescribe a guessed dependency version or assert that any arbitrary GGUF file is compatible.

When custom native code is required beyond the build surface, export to Android Studio and provide a complete implementation handoff. Keep the same domain interface so UI behavior does not change merely because execution moved from host to phone. An unsupported runtime inside AI Studio remains unavailable; it must not be emulated with canned answers.

The first milestone is one compatible small model, bounded context, a single active generation, cancellation, and real measurements on a named device. Parallel model agents and tool use come afterward. Use model memory estimates only for preflight guidance and measure actual process pressure where supported. Reject loading when available memory or storage is insufficient under the defined policy.

Weights may be bundled or downloaded separately, but the user must know which. Verify expected digests before loading and retain a manifest. Download resumption must not bypass integrity verification. Keep an unverified model quarantined from runtime loading. Offer deletion and cleanly free runtime resources when unloading.

Qualification requires generation in airplane mode after weights are installed on the phone, a recorded response, runtime/model identity, device configuration, and measured resource observations. A screenshot of airplane mode without generation does not close the gate. A host response while Wi-Fi remains active is not phone-offline inference. Record exactly which networks were disabled and what ran.

## 20. Authenticated owned-node pairing

Pair only infrastructure the user owns or is authorized to control. The UI records host label, validated origin, agent identity, protocol version, certificate fingerprint, allowed scopes, and observation freshness. A typed hostname is not a pairing proof. A challenge-response relationship establishes a specific authenticated association under the chosen keys.

Pin agent source and binary identity when running integration tests. The supplied archive has historical JVM evidence but does not bundle the required external agent source/binary. The build agent must obtain a verified reference from the owner or implement a compatible agent in a separate server project with a documented protocol. Do not claim a server exists because the client compiles.

Challenges should bind client identity, agent identity, requested scopes, nonce, expiry, and protocol domain. Refuse reused or expired challenges. Commands should include stable IDs, scope, expiry, and signature. Replay protection and authorization state must survive agent restart. Avoid relying on an in-memory counter as the only durable security boundary.

HTTPS pinning must remain explicit and certificate validity checked. Refuse redirects and bind commands to the reviewed origin. Test a wrong pin, expired certificate, redirected response, malformed payload, oversized response, and offline host. A user-facing fingerprint comparison helps establish the initial trust relationship without introducing a trust-all transport.

Pairing and wallet authorization are separate identities. The node agent must never receive wallet keys. A wallet signature is not required for ordinary private host inspection unless a specific business rule justifies it. Keep the owner in control and avoid making an unavailable token integration a prerequisite for basic utility.

## 21. Bounded node work, revocation, and recovery

Expose only named qualified operations, such as inspect health, read logs, or a specific bounded task. Do not offer an unrestricted shell. Every runnable task has an input schema, allowed resources, maximum duration, destination rules, output limits, and cancellation contract. The server enforces these bounds independently of the phone UI.

Start returns an accepted job identifier and accepted limits, not merely a success toast. Stop reports requested cancellation and later observed termination. A phone disconnect cannot guarantee a host job stopped; the agent's own deadline must continue enforcing the budget. A restarted agent should reconcile running jobs and retain replay protection.

Health and logs have scope and freshness. A node may report host resources without proving a workload's contribution. Logs need redaction and bounded retrieval. Avoid exporting secrets, raw prompts, or private host paths. A signed observation authenticates the agent's record, not an external economic reward.

Revocation is remote authority removal. Forgetting is local cleanup. If the host is offline, record revocation pending and show the limitation. After a successful revoke, a signed old command must be refused. A restart must not resurrect the revoked client. Qualification should repeat this on the physical phone and through the agent integration suite.

If the agent's runtime isolation remains unqualified, disable compute start and ship authenticated read-only inspection. That is a finished constrained capability, not a failed attempt to fake a node. Future hosting requires isolation, quotas, and adversarial tests before marketing it as safe multi-tenant infrastructure.

## 22. Telemetry and workload attribution

Collect only meaningful supported metrics. Storage capacity, battery state, traffic counters, CPU samples, and disk rates have distinct sources and limitations. Label device-wide readings as device-wide. App traffic is not bandwidth sharing. A high CPU percentage while EdgeORE is paused may be another application, not a node contribution.

Rates use paired samples and monotonic elapsed time. Keep the timestamp of the exact valid counter. If an intermediate disk sample is unavailable, do not advance the timestamp while retaining the old counter; the reference has a continuity issue that can inflate rates. Test valid, missing, and valid sequences, resets, reboot, and concurrent reads.

Use unavailable and stale states rather than synthetic zeros. Graphs show gaps across missing measurements. Do not draw sample chart lines in production before a source exists. The ring displays worker state and observed uptime only when a real worker reports them. A persisted Edge Mode preference is user intent, not execution evidence.

Battery current is an instantaneous estimate and may reflect charging. Do not convert an absolute current into a precise hourly drain claim without a validated methodology. Phone process memory and host model memory must remain separate. A universal CPU/RAM/storage/network contribution score needs a transparent formula and qualified inputs before it influences rewards.

A workload coordinator arbitrates competing real tasks. It manages concurrency, priorities, thermal pause, battery reserve, and resume policy. Stop must terminate or cancel actual work. Recovery should not resume resource-intensive work automatically after a crash unless the user consent and safety policy explicitly permit it. Store intent, last runtime outcome, and current eligibility separately.

## 23. ORE, SKR, and economics without invented multipliers

The requested ecosystem vision can include ORE and SKR, but the build must use official pinned sources for every program address, mint, instruction layout, account order, decimal count, and reward rule. Earlier CLI mining concepts and later board-game mechanics must be separated. A DrillX benchmark does not establish eligibility for a different protocol's current rewards.

Begin with a read-only observer only if a correct parser and pinned account schema are available. Display source, cluster, slot or round identity, and age. An observer is not meaningful transaction participation by itself, and it must not replace the submission-critical wallet transfer. It can provide context without claiming mining or income.

A transactional ORE adapter needs strict supported instruction decoding, program/account validation, exact-message review, risk accounting, simulation policy, wallet signature validation, separate submission, and observed outcome. Include regression tests against pinned official fixtures. If the sources cannot be verified, leave the adapter unavailable with a specific prerequisite rather than guessing bytes.

SKR integration is a separate capability with its own verified mint and supported action. Do not use memory or concept artwork as mint verification. Token transfer, staking, rewards, and prize qualification are different claims. The deck should show ORE × SKR as roadmap when no real adapter has been qualified.

Do not implement utility multipliers unless a named protocol actually defines and recognizes them. Running AI, allocating storage, or enabling a toggle cannot create a token entitlement by itself. A reward tracker displays observed amounts from an authenticated accounting source and keeps claimable, claimed, pending, and unavailable distinct. No guaranteed income or partnership language belongs in the build.

## 24. Storage sharing, bandwidth, VPN, and browser boundaries

These capabilities are optional expansion, each requiring a real adapter. Bandwidth sharing needs informed opt-in, destination policy, quotas, measured adapter traffic, immediate stop, and a named provider. A device traffic counter is not evidence of sharing. Do not build an arbitrary public relay as a shortcut to a reward screen.

A VPN requires a real tunnel configuration, Android consent, route selection, failure handling, and verified traffic behavior. A kill-switch claim needs a test demonstrating what happens when the tunnel drops. Owned-host access and VPN routing may interact; test their combination. A saved region selection is not a connected tunnel.

An embedded browser can use a supported Android web surface with deliberate privacy choices, but do not promise no fingerprinting or absolute anonymity. Browser traffic, model prompts, node commands, and sharing adapters have different destinations and permissions. Avoid widening permissions merely to make every reference card active.

Cloud sync must authenticate a provider and verify encrypted upload and restore. A free tier or subscription-free local tool is not unlimited free hosting. Show user-provisioned capacity and provider quota distinctly. Provider errors remain errors, not optimistic synced totals.

For the first finalized submission scope, keep these cards unavailable or hide them from the core flow. Fully wired means every shipped active action has an implementation and state contract. It does not mean every roadmap action must be enabled. This boundary lets the application be useful and truthful while future adapters are built properly.

## 25. Ten concrete user pain points and finished behaviors

| User pain | Finished behavior | Proof required |
|---|---|---|
| Confusing wallet approval | Human-readable exact-message review | Mutated return refused |
| Uncertain network outcome | Persisted unknown state and reconciliation | Timeout/restart test |
| Accidental overspending | Durable signer/cluster budget reservation | Concurrent-draft test |
| Fragmented private AI tools | Real owned-host discovery and chat | Host reply and identity |
| Unexpected prompt upload | Execution location and explicit attachment consent | Destination-policy test |
| Encrypted files that disappear | Atomic object lifecycle and corruption reporting | Interrupted-write test |
| Fake capacity promises | Measured bytes and enforced allocation | Quota/import test |
| Untrusted remote control | Pinned pairing and scoped commands | Wrong-pin/replay refusal |
| Resource drain | Actual worker safety and immediate stop | Thermal/battery/stop test |
| Unverifiable activity history | Portable signatures and real-export verifier | Second-machine tamper refusal |

Each row should have a UI path, backend or local implementation, negative test, and qualification record. Do not turn the list into ten decorative cards with identical Coming soon buttons. Implement the highest-value vertical slices first and keep optional unavailable features separated from active controls. A user should know what the app can do today without reading the entire blueprint.

Measure usability through task completion rather than arbitrary five-star scores. Can a first-time user connect a wallet, review the recipient correctly, distinguish sign from submit, locate a pending outcome, and export a receipt? Can they tell whether chat runs on the phone or their host? Can they stop work and understand what remains pending? These are the questions the final walkthrough must answer.

## 26. Screen-by-screen implementation contracts

Mine opens with the wallet widget. Disconnected state offers Connect and explains the compatible-wallet requirement. Connected state shows the authorized account and actual cluster, with balance freshness and a disconnect route. The next card reports actual compute eligibility and state. Safety refusals are specific: charging required, battery below reserve, thermal limit reached, telemetry unavailable, or no qualified worker. The ring never invents hashrate.

Below Mine's state card, render device observations with source scope and freshness. CPU, memory, storage, and network tiles may independently be unavailable. A missing CPU reading should not disable vault browsing. ORE remains a separate card with not-observed status until qualification. The reviewed transfer action opens a dedicated flow with no implication that deploying SOL earns ORE.

Private AI contains Models, Chat, and configuration subviews or an equivalent accessible structure. Show execution location, host identity, model discovery status, and cloud-fallback policy. The composer is disabled until a compatible model is ready. Loading, streaming, cancelling, cancelled, and failed requests have distinct feedback. Clear history does not pretend to erase logs on an external host; state the local scope.

Storage begins with the local encrypted vault and actual capacity. File rows show display name, object identity access, encrypted size, creation time, and status. Import, export, and delete use real Android pickers and confirmation. An export progress state remains visible while decryption runs. Capacity reservations appear during writes. Unsupported restore/cloud features do not share the active import button.

Nodes begins with paired identity or a deliberate unpaired state. Pairing configuration includes validated origin and fingerprint, not a sample URL. Health is stale after its freshness interval or disconnect. Scope controls reflect agent-granted permissions. Start/stop appear only for qualified named tasks. Revoke has pending and acknowledged outcomes. Logs and inspection are bounded and redacted.

Receipts includes search, event/status/cluster filters, selected detail, evidence dimensions, privacy preview, export, and verify. Empty history is different from damaged history. A signature is copyable with its cluster. A refusal shows its recorded reason and null wallet signature where appropriate. Independent verification status is populated by a real verification result, never a default badge.

## 27. Settings, onboarding, and application customization

Place Settings behind the top menu. Provide appearance, resource safety, wallet/cluster information, host configuration, privacy retention, export choices, notifications, and diagnostics. Save ordinary preferences durably. Changing a setting must either immediately affect a qualified component or clearly state that it applies to a future run. Never label a stored CPU preference as an enforced limit.

Onboarding should be short and reversible. Explain what stays local, what goes to the user's host, and what is sent to Solana. Let users browse without connecting a wallet. Wallet authorization is required only for financial actions. Vault import should not require a token purchase. Node inspection should not force AI setup. This keeps the utility understandable and avoids unnecessary prerequisites.

Theme controls may offer dark, system, and an accessible light alternative if implemented. Keep the reference dark palette as the default. Font scaling should respect system settings, and reduced-motion preferences should affect ornamental animation. Haptics should confirm meaningful interactions, not fire on every telemetry refresh. Notification permission is requested only when a real notification use case exists.

Diagnostics should export redacted environment and error information. Do not include raw prompts, file names when privacy mode excludes them, credentials, private-key material, or full host routes unnecessarily. Let users preview the diagnostic fields. Support contact and privacy pages need real links before release; do not invent owner-controlled domains.

Customization must not weaken immutable security boundaries. Users may lower budgets or choose endpoint policy within supported rules, but cannot turn off wallet signature verification, exact-message matching, authenticated vault decryption, or schema checks. A setting named Advanced should not provide a generic bypass for refused transactions.

## 28. Step-by-step implementation phases

Phase zero inventories the input. The agent reports source identity, file tree, dependency pins, existing tests, APK identity, and known blockers. It reads the audit and compares actual files. It then chooses recovery or clean-room mode, records the reason, and creates an isolated working branch or directory. No code is discarded solely because it was generated earlier.

Phase one makes the native shell compile: application ID, one activity, navigation, design tokens, five screens, configuration, and production dependency container. Initial operational states are truthful and no sample data reaches production. Build as early as possible. Resolve only demonstrated failures before adding more features. A functional shell still does not close wallet or data gates.

Phase two implements durable operations, strict transaction construction/decoding, real RPC, MWA authorization/signing, safe separate submission, and reconciliation. This is the core vertical slice. Unit tests protect amount parsing, exact-message matching, signature validation, state transitions, and budget arithmetic. Integration tests cover the storage and RPC adapters.

Phase three adds versioned receipts and a portable verifier, then repairs the vault lifecycle. These functions provide utility and auditability even without rewards. Preserve legacy records with a migration path. Add corruption, truncation, size-bound, nonce, metadata-tamper, and restart tests. Confirm exports do not leak excluded fields.

Phase four integrates owned-host AI and authenticated node inspection when their external prerequisites exist. Run genuine discovery, chat, pairing, health, and revoke. If no host exists, complete the adapter code and tests but retain unavailable states. On-device runtime and bounded compute are separate exported-development phases when the build surface cannot support them.

Phase five qualifies the physical candidate, signs the release using owner-controlled material, records artifact identity, and creates matching demo/deck/README assets. Fix regressions from that installed build before calling the scope complete. Optional protocol adapters must not delay the demonstrated core journey unless the owner explicitly changes submission scope.

## 29. Executable build and test evidence

Create an evidence directory and unique log names. Capture the true Gradle exit, not the status of the logging command. The following Bash pattern is an example for a local supported environment; the agent should run it only when the repository and toolchain are actually available. AI Studio may expose different execution controls, which must be recorded honestly.

```bash
mkdir -p evidence/qualification
EDGEORE_STAMP=$(date -u +%Y%m%dT%H%M%SZ)
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon 2>&1 | tee "evidence/qualification/build-$EDGEORE_STAMP.log"
EDGEORE_BUILD_EXIT=${PIPESTATUS[0]}
printf 'gradle_exit=%s\n' "$EDGEORE_BUILD_EXIT" >> "evidence/qualification/build-$EDGEORE_STAMP.log"
```

Use an XML parser to aggregate test attributes by name. Record tests, failures, errors, and skips plus individual skipped case names. Attribute order is not guaranteed. Preserve XML before cleaning the build. A worker crash after assertions is a failed process outcome even when XML records zero assertion failures. Do not inherit the old 59-test count or a claimed 103-test count without reproducing it.

Tune heap and worker counts to actual machine RAM. A blanket six-gigabyte Gradle heap is unsuitable for a small container. Record memory and avoid treating every worker failure as a need for more RAM. Read the actual exception and resource pressure. Network, disk, Java compatibility, test process crashes, and code defects require different fixes.

Build debug first for iteration and then the intended signed release. Release signing requires the owner's existing keystore and credentials outside source control. Generate no substitute certificate for an upgrade without explicit owner intent. If signing cannot run, deliver the unsigned/source candidate and state that release qualification is blocked; do not invent an APK hash.

## 30. Test matrix and meaningful negative cases

Unit tests cover deterministic logic: decimal parsing, supported instruction encoding/decoding, message mutation refusal, wallet signature verification, blockhash policy, durable-state decision logic, spending windows, receipt schemas, endpoint policy, telemetry intervals, and quota arithmetic. Use independent expected results or pinned fixtures, not a duplicate copy of the implementation algorithm.

Android integration tests cover persistence, migrations, Keystore behavior, document-provider interaction, file grants, lifecycle restoration, export handling, and transport cancellation. Some tests need instrumentation because JVM Android stubs cannot prove device behavior. Isolate test doubles and use real platform components where the claim depends on them.

Adversarial input tests include oversized JSON, unknown schema, invalid base64, malformed transaction lengths, extra instructions, changed signer, unsupported message versions, forged signatures, truncated vault objects, modified AAD, unavailable metrics, expired challenges, repeated commands, wrong certificate pins, redirects, and lost network responses. Refusals should be typed and specific.

Process-death tests focus on financial and file boundaries. Kill after signature validation, before submission, after attempt persistence, after network acceptance, before receipt creation, during vault write, and during key initialization. Recovery must preserve uncertainty and valid prior data. It should never infer a fresh transfer is safe merely because a screen state disappeared.

Live-service tests use named owned infrastructure and recorded versions. A skipped live node test remains skipped until the service exists and the test runs. Do not satisfy it by pointing at a mock server while claiming live qualification. Test servers are valid for transport logic and must be labeled accordingly.

Physical-phone tests cover cold launch, all five tabs, rotation, large text, background/resume, no network, permission refusal, wallet return, export chooser, storage pressure, and the core real transfer. Device measurements must be compared with plausible platform readings where possible. An emulator video is useful debugging evidence but not physical-device evidence.

## 31. Qualification ledger and readiness scoring

Maintain a machine-readable ledger and a concise Markdown view. Each gate records requirement ID, source revision, artifact identity, environment, command or manual action, result, evidence path, timestamp, and blocker. Valid outcomes are PASS, FAIL, NOT_RUN, BLOCKED, and PARTIAL with a reason. A file being present does not automatically satisfy a gate.

Minimum core gates are source identity, clean build, strict transaction tests, durable recovery, physical installation, real MWA authorization, actual devnet balance, reviewed signed transfer, observed confirmation, real receipt export, independent verifier pass, tampered-copy refusal, release identity, and matching demo artifacts. AI, vault, and nodes have additional gates if advertised as active features.

A ten-out-of-ten target applies to the defined scope and evidence quality. It is not a promise of universal device compatibility, perfect security, guaranteed hackathon victory, or mainnet economic profitability. A product with unavailable optional adapters can be a strong finished core. A product claiming every adapter is running without proof cannot be considered complete.

Use scores to prioritize, not conceal blockers. Reassess the source-candidate score only after real changes and evidence. Track architecture, wallet correctness, durability/security, tests, real functionality, release qualification, and honesty consistently with the audit. Do not add points for extra pages or model names that cannot run.

The final readiness decision includes an explicit list of remaining unavailable features. If the candidate is not ready, deliver a specific blocker and the completed artifact set. The owner should be able to understand what to run next without reading logs scattered across unrelated directories. Keep the ledger tied to the candidate rather than the whole project history.

## 32. Submission package and truthful presentation

Produce a short demo recorded from the installed candidate. A suggested three-minute structure is problem, live wallet journey, confirmation, receipt export/tamper rejection, architecture, and limitations. Show owned-host AI only if it actually responds in that session. Show airplane-mode generation only when a phone runtime has passed its own gate.

Create a six-to-eight-slide deck with problem, product journey, architecture, exact-message differentiator, evidence, business/retention hypothesis, real-versus-roadmap status, and builder. The supplied copper-and-mint artwork is concept reference. Operational screenshots must come from the candidate. Stamp concept images appropriately instead of using them as proof that every module shipped.

Correct wallet language to keys stay in the wallet app, with Seed Vault where supported. Correct hosted AI language to prompts go to the user's configured host. Do not show ORE × SKR settled transactions unless those specific adapters exist and were observed. A devnet System Program transfer should be described exactly as that.

The README contains build/install instructions, minimum compatibility, source revision, release link, actual demo/deck links, observed on-chain action, status matrix, privacy boundaries, license, and roadmap. Do not add fabricated tracks, awards, sponsor logos, guaranteed rewards, or unverified mint addresses. Missing evidence links are omitted or explicitly marked unavailable, never rendered as fake play buttons.

Before submission, verify current official requirements and deadline from the event source. A store listing needs its own policy and release checks. The agent may prepare fields and files, but owner-controlled publication and submission must use the appropriate authorization. Saving a deck or APK is not submitting an entry. Preserve a real confirmation if submission occurs.

## 33. Dependency decisions and native expansion

Maintain a dependency decision file with each package's purpose, pinned version, source, license, compatibility, and update policy. Retain existing versions until a demonstrated need justifies change. New Room, network-client, serialization, background-work, or runtime dependencies require a compatible toolchain and tests, not automatic latest-version substitution.

Room is a reasonable durable-storage option, but select its release and compiler integration against the actual Kotlin/AGP environment. The current reference does not already provide the full durable operation database. Avoid claiming that adding annotations solves recovery; schema transactions, migrations, and lifecycle code still need implementation.

Use persistent background scheduling for safe observation and cleanup where appropriate. Wallet signing remains interactive; background workers must not request a signature silently. Expired messages require fresh review. Work scheduling is not a way to bypass Android resource restrictions or create passive mining behavior without user consent.

Native mining needs a separate, verified extraction plan. Pin the algorithm/library and protocol version, build supported ABIs, expose bounded chunk functions, enforce cancellation and safety, and measure actual performance. A benchmark is labeled benchmark. The economic adapter must separately prove whether its solutions are accepted by the intended protocol. Do not promise all mobile architectures from one ARM64 build.

Future iOS support means a separate native client and wallet integration with its own lifecycle, storage, signing, and tests. Share documented schemas and pure domain specifications where practical. Do not turn an Android APK into an iOS promise through a web wrapper. The first Android release should be stable before expanding platforms.

## 34. Master execution prompt for the build agent

The following block is the governing instruction. Use the rest of this document as the detailed specification, and the reference archive as reviewed input rather than a claim that every gate already passed.

```text
ROLE
You are the finishing Android engineer for EdgeORE, built by CodesbyFebin.
Produce a real Kotlin/Jetpack Compose application with package com.edgeore.app.
Use the attached repository, source context, audit, and two UI images.
Do not create a React website or a PWA in place of the Android deliverable.

FIRST ACTION
Inspect input availability and report known files, source revision, dependency
pins, and current build capability. If the ZIP cannot be read, use the supplied
source-context file and request only the specific missing source you need.
Choose an isolated recovery branch or clean-room directory; preserve originals.

BUILD CONTRACT
Implement the five screens Mine, Private AI, Storage, Nodes, Receipts plus
header-menu Settings. Match copper/mint tokens and reference layout while using
real source-backed data. Keep unsupported features visibly unavailable.
Preserve exact-message wallet verification and separate sign/submit.
Implement durable transaction recovery, reservations, receipt verification,
bounded authenticated vault storage, owned-host inference, and scoped nodes.

TRUTH CONTRACT
Never fabricate observations, signatures, balances, rewards, model responses,
APK hashes, test counts, or PASS results. Test fakes stay in test-only wiring.
Keys stay in the wallet; Seed Vault is conditional. Hosted AI is not phone AI.
No ORE/SKR reward claim until a verified adapter produces real evidence.
No cloud fallback, arbitrary public relay, or embedded API secret.

EXECUTION ORDER
Inventory -> compile shell -> durable operations -> real MWA/RPC transfer ->
receipts/verifier -> vault -> owned-host AI/node adapters -> device qualification
-> signed release -> matching demo/deck/README. Run each available gate.
Fix exact failures and rerun affected checks. Do not reset scope after errors.

ENVIRONMENT LIMITS
Use supported Google AI Studio Android capabilities. Export to Android Studio
for unsupported native/server work. External services and physical wallet
approval remain explicit prerequisites, never simulated success.

HANDOFF
Deliver source ZIP, candidate APK if actually built, evidence ledger, logs,
test XML, build/install guide, dependency decisions, privacy boundaries,
submission materials, and specific remaining blockers. State environment and
revision for every result. Completion requires observed acceptance gates.
```

## 35. Continuation prompts for iterative development

After inventory, use: “Implement only the native shell and production dependency wiring. Use unavailable states rather than samples. Build it in the available Android environment and return the actual outcome, changed files, and next blocker. Do not replace existing validated transaction logic.” This keeps the first iteration small enough to diagnose toolchain failures.

For operations, use: “Implement the durable operation repository and integrate preparation, review, signing, attempted submission, unknown outcome, and observation after restart. Add atomic spend reservations and single-flight guards. Preserve the exact wallet-return checks. Test process-death boundaries and demonstrate that no recovery path automatically sends a fresh transaction.”

For receipts and vault, use: “Implement versioned records, stable key epochs, wallet-aware independent verification, corruption-aware history, privacy export, bounded imports, atomic vault publication, object IDs, authenticated metadata, and explicit device-bound recovery limits. Add negative tests that exercise real serialized data rather than duplicating helper logic.”

For real services, use: “Wire model discovery and chat to the configured owned host, enforce destination policy and transport cancellation, then pair/inspect/revoke against the supplied pinned agent. Record actual service versions and results. If prerequisites are absent, complete independent adapter work and preserve NOT_RUN status. Do not generate demo replies or health values.”

For release, use: “Run the agreed build/test/lint gates from the selected commit, install the candidate on the attached physical phone, guide real wallet approval, observe confirmation, verify the exported receipt independently, and record artifact identity. Use owner signing material without exposing it. Rewrite presentation claims to match those exact results.”

## 36. Final completion criteria and operator handoff

The final product is complete for its stated scope when every active button has a real implementation, every external action has a typed outcome, every irreversible operation has safe review and recovery behavior, and every public claim maps to evidence. Unavailable roadmap features must not masquerade as active tools. The app should remain useful without rewards or a server session.

A clean fresh clone must build through documented steps. The installed candidate must survive the lifecycle matrix and perform the real wallet journey. Its receipt must verify independently and reject tampering. Its advertised vault, AI, and node actions must have actual qualification results or be explicitly limited. Signed updates must preserve the intended certificate and data compatibility.

The owner receives a final handoff manifest with commit, APK identity, certificate identity when verified, device details, wallet/cluster details, test outcomes, service outcomes, unavailable features, and submission links. Secrets, keys, and personal prompt data are excluded. If an artifact was not produced, its field remains absent with a reason instead of a placeholder that looks real.

This blueprint deliberately supports a finalized useful Android application while preserving the wider EdgeORE vision. It does not require pretending that every ecosystem component already exists. The winning technical story is a real user-controlled journey: understand the action, authorize only the reviewed bytes, observe the result, and carry evidence that another machine can inspect.

## 37. Official references and source-specific limits

Checked platform references are Google's Android AI Studio documentation, the Solana Mobile MWA usage guide, Android Keystore guidance, Android Room and background-work guidance, Solana transaction submission/status documentation, and Google AI Edge runtime documentation. Consult exact dependency sources before changing pinned APIs. General guides are not a replacement for compiling the actual selected release.

- https://ai.google.dev/gemini-api/docs/aistudio-android
- https://docs.solanamobile.com/android-native/using_mobile_wallet_adapter
- https://developer.android.com/privacy-and-security/keystore
- https://developer.android.com/training/data-storage/room
- https://developer.android.com/develop/background-work/background-tasks/persistent
- https://solana.com/docs/rpc/http/sendtransaction
- https://solana.com/docs/rpc/http/getsignaturestatuses
- https://developers.google.com/edge/litert-lm

Repository-specific findings come from the attached archive and its audit, not from these general pages. This deliverable generates a build specification and reference kit; it does not execute the complete Android build, install a phone candidate, approve a wallet signature, deploy an agent, publish a release, or submit a hackathon entry. Those actions are the build agent/operator's subsequent gated work.
