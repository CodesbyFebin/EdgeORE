# Receipt checker demo (synthetic)

Inputs here are a **generated test export**, not from a real wallet transfer or
devnet session. They only exercise `scripts/verify-receipt.sh` on the build box.

- `receipt.json` — synthetic export (copy of workspace test fixture)
- `receipt-tampered.json` — JSON-aware copy: one signed receipt body's integer
  `lamports` field incremented by 1; digest and signature left unchanged
- `original.out` / `tampered.out` — verifier stdout/stderr plus exit codes

Claim: the checker verifies the integrity of **signed receipt contents**, not
every field in the export envelope.
