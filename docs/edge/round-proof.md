# Round Proof

After a round you deployed in settles, EdgeORE does the following, read-only, while the app is open:

1. Reads the Round account (PDA `["round", id]`) and your Miner account (PDA `["miner", wallet]`) once.
2. Re-derives the result with `app/src/main/java/com/edgeore/app/edge/RoundRules.kt`, a port of regolith-labs/ore @
   `48c203bd75db3cc45105ec29d8f8db719e5a2263` (`api/src/state/round.rs`, `program/src/checkpoint.rs`).
3. Stores the proof JSON. That JSON holds the raw account bytes in base64, the read slot, the RPC and the derived
   results, and lists what was not verified.
4. Appends a Keystore-signed receipt (`kind POLICY`, title `Round Proof #id`) with `digests.roundProofSha256` and
   `relatesTo` set to the deploy receipt.

Export makes a bundle `edgeore.roundproof-bundle.v1` with `{receiptExport, proofJson}`. Check it offline:

```bash
tools/receipt-checker/build/install/edgeore-receipt-checker/bin/edgeore-receipt-checker --round-proof bundle.json
```

The checker verifies the integrity of signed receipt contents. It then requires the proof digest to be in a verified
receipt, and re-derives every result from the bytes.

## Verified (re-derived from the bytes)

- The Round layout, discriminator 109 and size, and that the PDA matches the round id.
- `rng` = XOR of the four u64 words of `slot_hash`, and the winning square = `rng % 25`.
- Motherlode hit = `reverse_bits(rng) % 500 == 0`, checked against the `Round.motherlode` field.
- Split vs top-miner from `Round.top_miner == SPLIT_ADDRESS`.
- Whether the winning tile was split or solo: `distribution_mask(round id)` (keccak256 Fisher-Yates, 10 solo tiles) must
  agree with `Round.top_miner == SPLIT_ADDRESS`.
- Losing-square fee accounting: `Round.total_vaulted` (the SOL sent to the buyback vault) and `total_returned_sol` must equal
  `calculate_fees()`.
- Your SOL return and ORE share under the `checkpoint.rs` rules, when your Miner account still held that round.
- Test evidence: five real settled mainnet rounds (439108–439115, read-only capture). The Kotlin results match a Rust
  program that uses `rng`, `winning_square` and `did_hit_motherlode` copied verbatim from the pinned `round.rs`.

## Not verified

- That the RPC returned what the chain held. It is a single read, so compare with another RPC or an explorer.
- How `slot_hash` was produced (the entropy accounts in `reset.rs`). Only the steps after `slot_hash` are re-derived.
- What the buyback does with the vaulted SOL afterwards. The burn/staker split happens in other instructions that are not read.
- Any fairness property beyond the rules above.
- Whether you have checkpointed or claimed, and your on-chain balances.
- A cross-check against the program's own `Winning square:` log line. It was tried and hit the public RPC's rate limit.

## Rules check, 13 Oct 2026 (claims relayed from the user, tested against source and live accounts)

- regolith-labs/ore `master` is still `48c203bd`, so the program source has not changed since the pin. EdgeORE's Deploy
  (instruction 6, 12 accounts, 13-byte data) is unchanged.
- **"SOL on losing squares is returned": false for the live program.** On five live settled rounds (439108–439115),
  `total_vaulted` equals exactly the 10% protocol fee on losing squares (after the 1% admin fee) from `calculate_fees()`.
- **15 split + 10 solo tiles known before the round ends: true.** `distribution_mask` depends only on the round id. The
  split marker matched it on all five live rounds. The board shows ★ on solo tiles.
- **Per-round reward:** `Round.rewards[0]` = 1 ORE on all five live rounds, so minting continued on those rounds.
- **Not verified:** the 90/10 buyback-burn/stakers split, the "v4 after round 364885" date, the 5M cap and DrillX. EdgeORE
  does not use any of them.

## `.oreproof` file (format `edgeore.roundproof/v0`)

Export writes `edgeore-round-<id>.oreproof`. The JSON is canonical (RFC 8785-style subset): keys sorted, no whitespace,
and only integers, strings, booleans and null. All amounts are integers: SOL in lamports, ORE in base units
(10^11 per ORE, from `TOKEN_DECIMALS = 11` in `api/src/consts.rs`). The `protocol_version` is `ore-v3@48c203bd…`.

- `receipt`: receipt_id, intent_digest (the SHA-256 of the reviewed message), wallet, round_id, deployments
  (square, lamports), message_base64, tx_signature and wallet_signature.
- `chain`: rpc, read_slot, round_expires_at_slot, winner_square with its winner_source, tile_mode (split/solo),
  fee_params, round fee totals, the user's SOL deployed and returned, ORE credited, and the signed round_proof_json.
- `fee_params`: the Config account (`9c9X7aDR…S4Awy`) is read and its `fee_rate` values are recorded. At 48c203bd those
  values are **not** what the program applies: `calculate_fees()` and `checkpoint.rs` hardcode 1% and 10%, and
  `update_protocol_config.rs` has the rate update commented out. Live Config on 13 Oct 2026 read admin 100 bps and
  protocol 1000 bps, which agree. If Config ever differs from the applied rates, the checker reports P4 FAIL.
- `attestation` (optional) is ignored by the checker.

`edgeore-receipt-checker file.oreproof` prints one line per claim:

| Claim | PASS means | UNVERIFIED when |
|---|---|---|
| P1 intent | the deploy receipt is in a verified export, the digest equals SHA-256 of the message, the message is the exact ORE v3 Deploy for round_id, the deployments match it, and the wallet Ed25519 signature verifies | the wallet signature or message is missing |
| P2 winner | the square recomputed from `slot_hash` equals winner_square. ORE v3's Round has no separate winner field, so the source is always "recomputed from slot_hash" | slot_hash is empty |
| P3 split/solo | `distribution_mask(id)` and `Round.top_miner` agree with tile_mode | there is no winner |
| P4 SOL accounting | round totals equal `calculate_fees()`, Config rates equal the applied rates, and the user's return matches `checkpoint.rs` | the Miner or Config account was not captured |
| P5 ORE share | the ORE credited matches `checkpoint.rs` | the Miner account was not captured |

Exit 0 requires P1 and P2 to PASS and no claim to FAIL.
