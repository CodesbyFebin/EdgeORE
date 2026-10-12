# EdgeORE: pitch (CLOCK IN, 12 Oct 2026)

## One-liner
**EdgeORE is an Android ORE client for Solana Seeker. Every ORE deploy is reviewed, capped, signed in your own wallet, and receipted.**

## Problem
ORE on mobile means spending real SOL from a phone. Users need to know exactly what they sign, have a hard limit on what one tap can spend, never get a silent retry, and keep a record they can check later.

## Product
| Step | What EdgeORE does |
|---|---|
| See the board | Live ORE v3 mainnet board: 5×5 grid, per-square SOL, round, motherlode. |
| Choose the network | Devnet by default. Mainnet only through an explicit switch plus a risk tick. |
| Pick an amount | Lite (all 25 squares) or Pro (pick squares). Mainnet is capped at 0.01 SOL per deploy. |
| Review | The exact message is decoded on screen, with a copper mainnet panel (amount in SOL, round id, squares) and the message SHA-256. |
| Sign | Mobile Wallet Adapter. The keys stay in your wallet. Returned bytes must match the review. |
| Submit | One separate tap. The attempt is recorded first. Timeouts stay "unknown". No auto-retry. |
| Receipt | Android Keystore signed receipt. The JVM checker verifies the integrity of signed receipt contents. |
| Extras | Daily check-in streak and points (no token reward). Optional AI advisor, node and storage show *Not configured* until you set them up. |

## Evidence
- LiteSVM mainnet fork sim, two runs (rounds 437646 and 437756), against the live ORE v3 program (sha256 `b16a10029a35e709956cfe42c4dc51cf2c59522f477eba10816a40bcaa7dd5bd`, unchanged between runs). The app's exact Deploy bytes executed: +400,000 lamports on each of 25 squares for 0.01 SOL. A stale round was rejected.
- Account layout and instruction verified against [regolith-labs/ore](https://github.com/regolith-labs/ore) @ `48c203bd` (Deploy = ix 6, 8-byte amount + 4-byte mask, 12 accounts).
- Devnet: receipt-settlement program, checkpoint and verified receipt in [`evidence/devnet-session/`](../../evidence/devnet-session/README.md). These were sent from a laptop CLI.
- **UNVERIFIED:** mainnet MWA signing on a real device, until the owner records it.

## Tests (each suite separate, never summed)
| Suite | Result |
|---|---|
| Android unit tests | 386 run · 379 passed · 7 skipped |
| Receipt checker | 17 passed |
| LiteSVM receipt-settlement | 27 passed |
| ORE fork sim | 2 passed |

## Rules
One approval per transaction · no background spending · no reward, income or earnings claims (deployed SOL may be lost) · not production-ready.

## Next
Claim and checkpoint through the same reviewed path. More real-device testing.

## Research (no timelines, no partnerships)
ORE SDK · Reserve viewer · DePIN workloads on owned hardware · TEE/ZK receipt attestation.

## Q&A
- **Does it earn anything?** EdgeORE makes no income claims. ORE outcomes are decided by the ORE protocol, and SOL may be lost.
- **Can it spend without me?** No. Each deploy needs your review, your wallet signature and a separate Submit tap. There is no background spending.
- **Is it production-ready?** No.
- **What does the checker prove?** It verifies the integrity of signed receipt contents. It does not prove an on-chain outcome.

## Credits
Built by CodesbyFebin. Upstream: regolith-labs/ore (program layouts), Solana Mobile Wallet Adapter, Google AI Edge Gallery / LiteRT-LM, ZXing. See [`NOTICE`](../../NOTICE). Not affiliated with Regolith Labs or Solana Mobile.
