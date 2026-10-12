# ORE v3 Deploy (devnet only, user-signed)

**Status (2026-10-12):** Deploy is **off**. ORE v3 is not deployed on devnet. A `getMultipleAccounts` read of the program, Board, Treasury and Config returned `null` for all four, on a devnet RPC whose genesis hash is `EtWTRABZaYq6iMfeYKouRu166VU2xqa1wcaWoxPkrZBG`. See `devnet-check.txt`. The app repeats this check on the phone and keeps Deploy disabled with the reason. It never falls back to mainnet: `ORE_MAINNET_DEPLOY` is `false` in `app/build.gradle.kts`.

## Instruction (regolith-labs/ore @ 48c203bd75db3cc45105ec29d8f8db719e5a2263)
- **Data:** `api/src/instruction.rs` `OreInstruction::Deploy = 6`, then `Deploy { amount: [u8;8] LE, squares: [u8;4] LE }`. That is 13 bytes, with the steel discriminator first.
- **Amount:** in `program/src/deploy.rs`, `amount` is lamports per selected square, and `squares` is a 25-bit mask.
- **Accounts:** `api/src/sdk.rs deploy()` takes signer (writable, signer), then authority (= signer), automation_pda, board, config, miner_pda, round_pda(round_id), treasury, system program, ORE program, entropy `var_pda(board, 0)` and the entropy program.
- **Entropy:** entropy-api 0.1.4 (crate sha256 `bfdd094b…`, which matches Cargo.lock) has program `3jSkUuYBoJzQPMEzTvkDFXCZUBksPamrVhrnHR9igu2X` and seeds `["var", authority, id LE]`.
- **Fixtures:** `tools/ore-fixtures/gen_deploy_fixture.py` derives the PDAs and message bytes independently. Its PDA code reproduces the published Board, Treasury and Config addresses. Output is in `app/src/test/resources/ore/deploy-fixture.json`, and `OreDeployMessageTest` checks the Kotlin encoder byte for byte.

## Path of one deploy
1. You tap Deploy on Mine.
2. `OreDeployReview.prepare` builds the message and re-decodes it.
3. The limits are checked: 0.1 SOL per deploy; MAX is the balance minus a 0.01 SOL reserve; the daily budget applies.
4. The deploy is reserved in the operations store.
5. You see it on the exact-message review screen and approve it.
6. Your wallet signs it through MWA.
7. `TransferReview.verifyWalletReturn` checks the returned bytes.
8. You submit it separately. It is never resent automatically.
9. A signed receipt is written.

`OreReadOnlyArchitectureTest` allowlists `solana/OreDeployMessage.kt` as the only ORE instruction builder, and `EdgeOreViewModel.prepareOreDeploy` as its only caller (through the review).

## Limits of this build
- **One round per deploy.** Multi-round automation is not supported.
- **No claim path.** There is no checkpoint or claim instruction.
- **Not financial advice.** Deployed SOL may be lost, and EdgeORE claims no income.
