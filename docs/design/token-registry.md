# Token registry (Mine balances)

Code: `app/src/main/java/com/edgeore/app/solana/Clusters.kt`. Evidence: `docs/design/mint-check.txt` (getAccountInfo per cluster, 2026-10-12 ~16:28 IST).

| Token | Mint | Source | mainnet-beta | devnet | testnet |
|---|---|---|---|---|---|
| ORE | `oreoU2P8bN6jkk3jbaiVxYnG1dCXcYxwhwyK9jSybcp` | regolith-labs/ore `api/src/consts.rs` MINT_ADDRESS (48c203bd) | SPL Token, 11 decimals, authority BiN8KJqt… | account exists, 11 dec, different authority Dh5ZkjGD… → shown as "Test ORE" (not official) | not found → "Not on testnet" |
| SKR | `SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3` | docs.solanamobile.com/solana-mobile-stack/skr; solanamobile.com/blog/skr-is-live | SPL Token, 6 decimals, authority FMNn5sor… | not found → "Not on devnet" | not found → "Not on testnet" |

SOL uses `getBalance`; tokens use `getTokenAccountsByOwner` (mint filter), summed only when owner program, mint and decimals match.
Genesis is checked before reads (mainnet 5eykt4Us…, testnet 4uhcVJyU…, devnet EtWTRABZ…); a mismatch is reported, not displayed as a balance.
Balances older than 2 minutes show as stale; a failed read keeps the last value marked with the error. No balance is hardcoded.
