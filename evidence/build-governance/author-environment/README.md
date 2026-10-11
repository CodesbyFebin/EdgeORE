# Logs from the patch author's environment (not reproduced here)

These five files came with patch 2/3 of the unified series (upstream `b59b9d14`, "Centralize pinned dependencies and
add build governance checks", author `Codex <codex@localhost>`). They were produced in the author's environment, not
on EdgeORE's build host, and are kept unchanged except for being moved into this folder. They are not EdgeORE results.

| File | What it claims | Status on EdgeORE's build host |
|---|---|---|
| `STATUS.json` | Base `a767f43`; policy 6/6; gateway 8/8; Gradle BLOCKED | `a767f43` is the author's commit for patch 1/3; here it is `68e392c`. |
| `gateway-tests.log` | 8 storage-gateway tests passed (the suite before patch 3/3) | Superseded: the gateway suite was re-run here after patch 3/3 and the review fixes; see `evidence/unified-upgrade/`. |
| `gradle-configuration.log` | Gradle 8.9 wrapper download failed (no network) | The author's build was blocked. Gradle ran here; see `evidence/unified-upgrade/`. |
| `policy-tests.log` | 6 policy tests passed | Re-run here; the suite now has 12 tests. |
| `policy.log` | Policy PASS | Re-run here. |
