# EdgeORE Privacy Policy

Effective 13 October 2026 · applies to EdgeORE for Android (`com.edgeore.app`), hackathon build 0.1.0-clockin.

## Summary
- EdgeORE has no account system and no EdgeORE backend server. We do not collect, sell, rent or share your personal data.
- Your private keys stay in your own wallet app. EdgeORE talks to it through Solana Mobile Wallet Adapter (MWA) and only ever receives your public address and the signed transactions you approve.
- Receipts, the operation ledger, settings, check-in streak and points are stored on your phone only.
- The app contains no analytics, advertising or crash-reporting SDKs.

## What stays on your phone
- **Receipts and operation ledger:** a record of each reviewed transaction and its outcome, signed with a key held in Android Keystore. They leave the phone only if you export or share one yourself.
- **Settings:** network choice, theme, safety preferences, reminders, daily check-in streak and points.
- **Optional features you set up yourself:** node pairing details, storage gateway settings and the encrypted vault, and an optional on-device AI model. Secrets for these are kept in app-private storage protected by Android Keystore.

## What is sent over the network, and to whom
EdgeORE reads public blockchain data and submits the transactions you sign. These requests go directly from your phone to the following public Solana RPC endpoints, which can see your IP address and the public addresses or transactions in each request:
- `https://api.devnet.solana.com` (devnet: transfers and default reads)
- `https://api.testnet.solana.com` (testnet reads, if you select it)
- `https://api.mainnet-beta.solana.com` (mainnet: ORE board reads, balances, and an ORE deploy you approve)

Those endpoints are operated by third parties under their own policies. Signed transactions are public on the Solana blockchain once submitted.

Other connections happen only if you configure them:
- **Your own node or storage gateway:** sent to the host you enter.
- **Optional AI:** prompts go to an AI host you own and configure. Or, if you download an on-device model, it is fetched from an allowlisted `https://huggingface.co/` URL at a pinned commit, and inference then runs on the phone.
- **Your wallet app:** receives the transactions you ask it to sign.

## Permissions
- **Internet:** for the RPC endpoints above.
- **Camera:** only when you tap Scan QR. Frames are decoded on the phone and never saved or sent.
- **Notifications:** only if you turn on reminders. Reminders are local and contain no balances or addresses.

## Children
EdgeORE is not directed at children under 13 (or the minimum age in your country) and should not be used by them.

## Deleting your data
Uninstalling the app, or clearing its storage, removes everything EdgeORE stored on the phone. Data already on the blockchain cannot be deleted by anyone.

## Changes and contact
We will update this page and its effective date if anything changes.
Contact: **privacy@REPLACE-WITH-CONTACT-EMAIL** (placeholder).
