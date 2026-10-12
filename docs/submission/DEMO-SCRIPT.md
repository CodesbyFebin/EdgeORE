# EdgeORE: 3-minute demo shot list (real phone, single take)

Rules: one continuous take on the physical phone. No edited balances or signatures. A failure stays in the video and is narrated as a failure. No emulator footage presented as the phone. No income or reward claims.

## Before recording (not filmed)
1. Install the demo APK `EdgeORE-76eea59-demo-debug.apk` (debug build, screen capture allowed). If Android refuses the update, uninstall the old build first.
2. A Mobile Wallet Adapter wallet (Phantom / Solflare / Seed Vault wallet) set to **mainnet**, holding **at least about 0.02 SOL**. A first deploy costs about 0.0145 SOL: 0.01 deploy + about 0.0045 miner account rent + fee.
3. Do Not Disturb on, battery above 50%, Android screen recorder with mic.
4. Optional: AI host / node / storage. If not set up, show *Not configured*. Don't fake it.

## Shot list

| Time | On screen / taps | Voiceover |
|---|---|---|
| 0:00–0:15 | Launch EdgeORE on the phone | "EdgeORE is an Android ORE client for Solana Seeker. Every ORE deploy is reviewed, capped, signed in your own wallet, and receipted." |
| 0:15–0:35 | Connect wallet → MWA sheet → approve | "Mobile Wallet Adapter. The keys stay in my wallet. EdgeORE never holds them." |
| 0:35–0:55 | Mine → live ORE board grid (round, per-square SOL, motherlode) | "This is the live ORE v3 board on mainnet, read straight from the program accounts." |
| 0:55–1:15 | ORE Mining card → tap **Mainnet** → read the copper disclaimer → tick the risk box → **Check mainnet again** → "ORE v3 found · round N" | "Deploy starts on devnet. Mainnet is opt-in, with a risk tick. Deploying risks this SOL." |
| 1:15–1:30 | Lite, tap +0.01 (try +1 to show it stops at 0.01) | "Each mainnet deploy is capped at 0.01 SOL. The cap is enforced in code before any wallet request exists." |
| 1:30–1:55 | **Deploy** → review screen: copper panel (amount, round, squares), program, per-square, fee, message SHA-256 | "This is the exact message: ORE v3 Deploy, this round, these squares, this amount. My wallet must return these exact bytes." |
| 1:55–2:10 | **Approve & sign in wallet** → approve in the wallet (move quickly: rounds last about a minute) | "My wallet signs. EdgeORE checks the returned bytes match the review." |
| 2:10–2:30 | Tap **Submit** once → status → check confirmation; show the signature | "Submit is one separate tap. No auto-retry. If it times out, it stays 'unknown' rather than resending." |
| 2:30–2:45 | Receipts → open the deploy receipt → export | "Every outcome becomes a receipt signed with an Android Keystore key." |
| 2:45–2:55 | (Optional) Daily check-in; AI / Nodes / Storage showing *Not configured* | "A check-in streak and points, no token. Optional AI, node and storage need my own setup." |
| 2:55–3:05 | End card | "Reviewed, capped, signed by you, receipted. Not production-ready, and no income claims." |

**If MWA or the deploy fails:** keep recording, show the error, and say what failed. If the round ended before submit, say so and review again for the new round. Never resend the old bytes.

**Optional laptop cut (labelled LAPTOP):** `bash scripts/verify-receipt.sh receipt.json` → accepted; edit one lamport → rejected. "The checker verifies the integrity of signed receipt contents."
