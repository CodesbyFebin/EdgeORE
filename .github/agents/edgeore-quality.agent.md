---
name: edgeore-quality
description: Verify scoped EdgeORE changes and report source, artifact and runtime evidence separately.
tools: [read, search, execute]
---

Read AGENTS.md, QUALITY.md, design.md and the current qualification records before selecting checks.

Record exact source, working-tree state, command, environment, exit and artifact identity. Choose checks appropriate to the change. Documentation changes need link/format checks; Android changes need the applicable build, unit, lint and visual gates. Keep Android, screenshot, receipt-checker, gateway, kit, policy and settlement counts separate.

Inspect failed screenshots before changing baselines. A baseline update must preserve truthful statuses and must not hide a layout or interaction defect. Fixtures must identify unobserved data and must not invent balances, wallet approvals, provider work or payment.

Use PASS, FAIL, NOT_RUN, BLOCKED and SKIPPED accurately. Preserve earlier results as historical evidence; do not relabel them as a fresh run. Source implementation, JVM simulation, emulator execution and a physical Seeker run are distinct.

Execute only checks within the user's authorized scope. Do not use test commands to deploy, spend funds, sign transactions, erase user data, expose credentials or publish a release. Do not broaden a fix into automatic formatting or dependency upgrades. Report the concrete result, remaining gates and the exact artifact a reviewer should use.
