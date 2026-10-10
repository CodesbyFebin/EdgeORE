# Browser judge entry point

`index.html` is a buildless, five-tab explanatory walkthrough. It is not an Android runtime and performs no wallet, RPC, inference or verification operations. Runtime gates stay NOT_RUN.

Open this file locally in a browser, or include it in the existing GitHub Pages deployment. If Pages publishes the repository `docs/` directory, the path is `/EdgeORE/judges/`; if it publishes another directory or custom artifact, copy `judges/` into that artifact. Do not replace the existing deployment blindly. A deployment URL is not claimed until the workflow and public path are confirmed.

For interactive access to the actual APK, provision a separate Android runtime with ADB and screen streaming (for example noVNC on a KVM-capable owned Linux host). Install the verified d675002bd701 APK. Give each judge an isolated temporary session, secure the stream, and never use a mainnet wallet or shared persistent credentials. Confirm wallet compatibility before advertising the session. GitHub Pages cannot run that Android runtime.

Verification: static content checks passed. Browser QA and deployed URL were not verified by this change. No app code, dependencies or Android artifact changed.

**Staleness note (added 2026-10-10):** this walkthrough describes the `0.2.8-review` candidate (`d675002bd701`) and its counts at that time (Android 164 tests, checker 7/7). Since then, `main` gained the 0.2.9 integration candidate (PR #8); its current gate results and runtime ledger are in `evidence/integration-0.2.9/gate-250be029b35c/README.md` and `docs/HANDOFF-GOOGLE-AI-STUDIO.md`. Wallet Connect and Disconnect have since passed in one hosted-emulator session on build `3676094ad487`; every other runtime gate remains NOT_RUN.
