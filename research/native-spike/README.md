# EdgeORE source review and corrected native spike

Built by CodesbyFebin. **NOT BUILT / NOT DEVICE QUALIFIED / NOT ORE EARNING.**

The uploaded `(1).zip` has exactly the same entry bytes as the previous web archive. It contains no Android project, Rust crate or Gradle files. Its ZIP-container SHA-256 differs, but it supplies no new application implementation.

The supplied extraction blueprint names files absent from `0xrsydn/ore-miner`. That repository is a Shell wrapper around an external CLI, not DrillX source. It has no detected repository license. Do not claim to extract a Rust core from it. Use the separately published, Apache-2.0 DrillX dependency and preserve its license obligations. This addon contains newly written glue, not copied miner scripts.

## Critical corrections

- `drillx::hash` returns a Result; errors/no solutions must be handled.
- Hash has `d:[u8;16]` and `h:[u8;32]`, not `h:[u8;16]` or `n`. Retain the attempted nonce yourself.
- The documented construction uses Equi-X and Keccak, not the blueprint’s asserted BLAKE3 path.
- A printed digest is not a golden-vector assertion; returning a digest is not protocol acceptance.
- Do not conflate legacy DrillX CPU proof-of-work with current ORE Deploy/Claim instructions. The inspected current Deploy handler deploys SOL across selected squares; it does not consume a DrillX solution. An app-local AI/storage multiplier cannot alter those reward rules.
- Actual RPC submission by an app is permitted by the existing sign-then-broadcast architecture if the signed bytes are exactly verified and submission explicitly authorized. MWA can also offer sign-and-send; choose deliberately, without claiming returned bytes were inspected if they were not returned.
- Run native work on a worker dispatcher, never directly inside a UI-thread LaunchedEffect. Cancellation takes effect between bounded chunks; the limit cannot interrupt an individual solver call mid-operation.
- Avoid panic/unwrap across JNI. Validate challenge length, budget, version, output length, attempt count and nonce overflow. Report missing library explicitly.
- CPU attempts/sec is not successful solutions/sec or ORE earnings. No NPU speedup is asserted.

## Files

`rust-core/`: original bounded compute/JNI glue with pinned direct crate versions. `android-reference/`: Kotlin bridge and strict 92-byte decoder. These are an independent spike, **not a complete Android app**, and are not wired into the React UI. No placeholder challenge is silently called live mining.

## Gates (all currently NOT_RUN)

1. Install a trusted Rust toolchain; run `cargo test --manifest-path rust-core/Cargo.toml`. Generate and review Cargo.lock, then use `--locked`. No lockfile or cargo tree was fabricated here.
2. Verify a deterministic golden vector against a second pinned implementation. Existing tests check rejection and solution integrity but are not a published golden vector.
3. Install Android NDK and reviewed cargo-ndk; run `cargo ndk -t arm64-v8a -p 26 build --release --manifest-path rust-core/Cargo.toml` after dependency/architecture review. Copy the produced library to a genuine Android project’s `jniLibs/arm64-v8a`.
4. Confirm JNI static method/symbol resolution, native resource use, pause behavior, foreground notification, real thermal/battery sensors and process-death recovery on a physical device. No service is claimed by the bridge alone.
5. Independently select and pin an actually deployed protocol accepting the intended work type. If using current ORE capital deployment, use that protocol’s exact accounts and spending risk model rather than a hash submission fiction.
6. Record authorized accepted operation, independent confirmation and actual reward claim before any “earns” claim. No mainnet action is performed by this package.

## Reject misleading pitch claims

Do not advertise fake 50 GB allocation as storage proof; inspect real capacity and bytes. A hash alone does not prove remote storage. Local AI cannot boost ORE rewards absent a real qualified protocol rule. Hardware keys do not make hacks impossible, on-chain logs do not prove physical truth, and no implementation is “unbeatable.” Keep research tracks and demonstrators labeled.

The previous working web/node package remains preserved separately. This addon does not replace its readiness report or qualify an APK.
