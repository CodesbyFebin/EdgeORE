# Logs from the patch author's environment (not reproduced here)

These files came with patch 1/3 of the unified series (upstream `a767f43`, "Add optional owned storage gateway and
repository adaptation plan", author `Codex <codex@localhost>`; here `68e392c`). They were produced in the author's
environment, not on EdgeORE's build host, and are unchanged except for being moved into this folder.

| File | What it claims | Status on EdgeORE's build host |
|---|---|---|
| `STATUS.json` | Backend 8/8; Android compile BLOCKED; Android tests NOT_RUN | Superseded. The gateway suite was re-run here after patch 3/3 and the review fixes, and the Android build and tests ran here; see `evidence/unified-upgrade/`. Its `source_sha256` values describe patch 1/3 only. |
| `backend-tests.log` | 8 gateway tests passed | Was true of the patch 1/3 suite; not the suite tested here. |
| `android-compile.log` | Gradle 8.9 download failed | The author's build was blocked; it is not an EdgeORE result. |
