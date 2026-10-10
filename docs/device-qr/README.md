# QR test images for the device runbook

Generated with ZXing core 3.5.3 (`QRCodeWriter`, error correction M, 600×600 px, 4-module margin) and decoded back with ZXing to confirm each payload. They are copied into the device kit as `qr/`.

| File | Payload | Expected EdgeORE behaviour |
|---|---|---|
| `qr-recipient-address.png` | `73Cc84sjGtk3RNSVoEres4PLMcZZtg4n46kQUzJnR93n` | Fills the destination only |
| `qr-solana-pay-0.01.png` | `solana:73Cc84sjGtk3RNSVoEres4PLMcZZtg4n46kQUzJnR93n?amount=0.01` | Fills destination and amount `0.01` |
| `qr-refused-spl-token.png` | `solana:73Cc84sjGtk3RNSVoEres4PLMcZZtg4n46kQUzJnR93n?amount=0.01&spl-token=EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v` | Refused: "The link asks for an SPL token transfer…" |

The recipient is the devnet address approved for the single Gate 6 transfer. These codes only fill the review form; nothing is prepared or signed from a scan.
