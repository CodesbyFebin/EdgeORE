# Devnet session — Oct 12, 2026 (~17:47–17:58 IST). DEVNET ONLY.

RPC: `https://solana-devnet.api.onfinality.io/public` (api.devnet.solana.com was unreachable from the build box).
Genesis hash checked: `EtWTRABZaYq6iMfeYKouRu166VU2xqa1wcaWoxPkrZBG` (devnet). Each transaction was submitted once; none was resent.

Wallet: throwaway devnet test key `2GUk3Jm4GADwe5K27UDDXCqQT6ANSjDtRKGrnbjg7Rxk` (mock MWA wallet key; never committed).
Balance 5 SOL before, 3.4685574 SOL after (program rent ≈1.53 SOL, checkpoint rent, fees, 0.001 transfer).

| What | Value |
|---|---|
| Program ID (`declare_id!`, unchanged) | `8JMkxGfiEfXJ3xJgt9Dh2bzdp6ozL6WQ5E534WfLwHfT` — [Explorer](https://explorer.solana.com/address/8JMkxGfiEfXJ3xJgt9Dh2bzdp6ozL6WQ5E534WfLwHfT?cluster=devnet) |
| Deployed from commit | `1e6bf237b9aaf874bbed73ab4bf78f704df46008` (clean clone) |
| Deploy signature | `5DKbCx8NwYw1EBM1sEWD8K6UgrrCUs8Rw3x8eYBNSRQNc6eQMn3gDo6qjcvxZoUpsXYVSabrhWzphsc6o3TUgQdU` — [Explorer](https://explorer.solana.com/tx/5DKbCx8NwYw1EBM1sEWD8K6UgrrCUs8Rw3x8eYBNSRQNc6eQMn3gDo6qjcvxZoUpsXYVSabrhWzphsc6o3TUgQdU?cluster=devnet) |
| Built .so sha256 = on-chain dump prefix sha256 | `a452bdfe37450c52981f99337a3c81e2b2d5e7cdc451ec92d5a7a85be7526234` (PASS, trailing bytes zero) |
| Upgrade authority | `2GUk3Jm4…7Rxk` (UPGRADEABLE; slot 510261718) |
| Sample transfer, 0.001 SOL → `73Cc84sjGtk3RNSVoEres4PLMcZZtg4n46kQUzJnR93n` | `4AYeQ2cUszdHgJZpsH5HRi2MsNR1k1tx6L4M7KmcNyLDFjMB6N2s2XPUsKaXFqXi2sqyY3ki6tZr4uPpoQXvo1zV` — [Explorer](https://explorer.solana.com/tx/4AYeQ2cUszdHgJZpsH5HRi2MsNR1k1tx6L4M7KmcNyLDFjMB6N2s2XPUsKaXFqXi2sqyY3ki6tZr4uPpoQXvo1zV?cluster=devnet) (Finalized) |
| Receipt checkpoint (init + record, one tx) | `5jgyBsA84TC1ajKv2GdVdmWx1QFXMM9Hd1V6vW79AL1gtbw3UJgcXgRSUAD7URoTwsHjrY27Kj9XXi2rNGa9h82q` — [Explorer](https://explorer.solana.com/tx/5jgyBsA84TC1ajKv2GdVdmWx1QFXMM9Hd1V6vW79AL1gtbw3UJgcXgRSUAD7URoTwsHjrY27Kj9XXi2rNGa9h82q?cluster=devnet) (Finalized) |
| Checkpoint account | `Adwt61vGKfuX4x2ZS8mDDNG39JKj8PES3amjYD8aK8Rs` |

Receipt: `receipt/receipt-export.json` (schema v3, 1 receipt) written by the app's receipt code on the build box with an
in-memory software key. `scripts/verify-receipt.sh receipt/receipt-export.json --trusted-key receipt/device-key.der`
→ `VERIFIED: PASS` (`receipt/checker-output.txt`). The checkpoint records that export's lastSequence 1, lastSha256
`07ac40f5…8b70`, bundleSha256 `071f62eb…850b` under device key id `710a1d6f…55ed`.

Limits: the transfer is a CLI transfer, not an in-app MWA transfer through EdgeORE on a phone (still NOT_RUN). The
receipt is a build-box fixture, not a phone receipt. The program is unaudited and upgradeable. No mainnet activity.
The deploy log's temporary buffer recovery phrase was removed (the buffer was consumed and closed by the deploy).
