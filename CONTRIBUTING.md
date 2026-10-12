# Contributing to EdgeORE

Read FORK-GUIDE.md and the current qualification ledger. Keep changes focused and preserve wallet
exact-message binding, durable state before side effects and observation-only recovery.

For new providers/workloads document: input, destination, credentials, consent, byte/time limits,
cancel/restart behavior, receipt contents, test environment and upstream licenses. Never add
implicit cloud fallback, fabricated metrics, automatic wallet approval or payment claims.

Run applicable suites separately and attach logs naming the exact tested source/artifact. A source
review is not a runtime test. The offline artifact manifest checker does not prove authorship.

Open a focused issue or pull request with the template. Do not paste keys, tokens, passphrases,
private documents or unredacted wallet/browser logs. Respect NOTICE and the pending project-license
decision before redistributing code. No broad auto-format or dependency upgrades in functional fixes.
