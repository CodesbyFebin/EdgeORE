# EdgeORE master source kit

This ZIP contains the actual tracked source at `6f7b60050f3651227ddc9d7dd261f6d31f7324b2`, plus a production design/prompt kit. It is not a newly built APK or a production-qualified release.

1. Read `design.md` for exact native UI/UX.
2. Open `docs/production/ui-preview.html` for the interactive static visual reference (illustrative, no wallet/network/backend).
3. Give your build agent `MASTER-BUILD-PROMPT.md`.
4. Use `docs/production/GATES.json` and `EVIDENCE-TEMPLATE.md` to preserve measured outcomes.
5. Retain existing `docs/HANDOFF-GOOGLE-AI-STUDIO.md` and evidence as historical baseline.

Clone the upstream baseline:

```bash
git clone https://github.com/CodesbyFebin/EdgeORE.git
cd EdgeORE
git checkout 6f7b60050f3651227ddc9d7dd261f6d31f7324b2
git switch -c feature/production-readiness
```

The additional kit files are delivered in this ZIP and are not pushed upstream. Copy them into the checkout, inspect the diff, then commit them before building if a clean source stamp is required. JDK 17, Android SDK 35 and the existing Gradle wrapper are required. See the original handoff for separate Android/checker/Anchor gates. Never label a source ZIP as an installable APK.
