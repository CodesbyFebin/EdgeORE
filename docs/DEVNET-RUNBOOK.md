# Devnet runbook — program deploy and sample transfer (hardening 1)

> **Status: PREPARED, NOT RUN.** Requires the owner's explicit approval and a funded devnet wallet.
> No agent or CI job runs this. Nothing here touches mainnet.

## 0. Before you start
- Approval in writing naming the commit to deploy (`DEPLOY_COMMIT`, 40-hex, pushed).
- Keypairs **outside any repository**: a devnet deployer (fee payer + upgrade authority, ≥ 4 devnet SOL) and the
  program-id keypair whose pubkey equals `declare_id!` (`8JMkxGfiEfXJ3xJgt9Dh2bzdp6ozL6WQ5E534WfLwHfT`).
- Anchor 1.2.1 and solana CLI 4.3.0 on `PATH`. Read [DEPLOY-DEVNET.md](DEPLOY-DEVNET.md): the program is **upgradeable**.

## 1. Fund the deployer (devnet faucet)
```bash
solana airdrop 2 "$(solana-keygen pubkey $HOME/keys/devnet-deployer.json)" --url https://api.devnet.solana.com
```

## 2. Deploy receipt-settlement
```bash
EDGEORE_DEPLOY_CONFIRM=devnet DEPLOY_COMMIT=<sha> \
DEPLOYER_KEYPAIR=$HOME/keys/devnet-deployer.json PROGRAM_KEYPAIR=$HOME/keys/receipt_settlement-program.json \
scripts/devnet-deploy.sh deploy
```
Records program id, deploy signature, upgrade authority and a built-vs-dumped sha256 comparison.

## 3. Sample CLI transfer (≤ 0.01 SOL, capped by the script)
```bash
EDGEORE_TRANSFER_CONFIRM=devnet DEPLOYER_KEYPAIR=$HOME/keys/devnet-deployer.json \
TRANSFER_TO=<your Seeker wallet devnet address> TRANSFER_SOL=0.001 scripts/devnet-deploy.sh transfer
```
Writes `transfer-record.txt` with the signature and a devnet Explorer link. This proves the wallet is funded; it is
**not** the in-app evidence.

## 4. In-app transfer through EdgeORE (the evidence that matters)
On the Seeker (DEVICE-RUNBOOK.md gates): connect wallet (MWA) → Review → enter destination and amount → biometric /
PIN gate → wallet signs → verify-before-submit → submit → receipt. Export the receipt and check it with
`scripts/verify-receipt.sh`. Record the screen.

## 5. File the evidence
Copy `deploy-evidence/...` to `evidence/anchor-receipt-settlement/<commit>/devnet-deploy/` and the transfer record
and receipt export to `evidence/devnet-transfer/<commit>/` in a reviewed commit. Never commit a keypair.
