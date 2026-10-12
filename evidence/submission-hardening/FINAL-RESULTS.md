# Final consolidated submission results

All six original PRs (#20, #22–#26) and integration PR #27 are merged. Integration merge: `48669070181837ef32f90cc6efeb0e0bece02020`. Canonical source: `76cb0e8d53a7c6d51e18e36ad459c98b568396e7`; tested PR head: `6a45680d6dd4057022b10a518addbb922e92be4d`; CI synthetic merge: `45952302b790ab19d008f75b2ec8a322f0aec539`.

All four final workflows passed. Android: 391 run, 382 passed, 9 skipped, zero failures. Screenshots: 29/29. Receipt checker: 17/17. Gateway: 17; offline kit: 22; policy tests: 12; manifest tests: 9; scanner triage tests: 5. Counts stay separate. Static analysis and minified R8 release build passed. Android lint passed with warnings; this is not a zero-warning claim.

MobSF retained one raw high finding: the reviewed loopback-only network exception. No unreviewed high findings remained. The scanner APK uses a disposable CI signature and is not the owner-signed production artifact.

Debug APK SHA-256: `3ef9ba1477855ceb538275133eb7e78f000e165ecd95764e22fc14d824ca7365`; 59,474,099 bytes; version 0.1.0-clockin / code 12. The downloaded APK was hashed independently and matched the CI summary.

Final runs:
- [CodeQL](https://github.com/CodesbyFebin/EdgeORE/actions/runs/38296685230)
- [MobSF static scan (local container)](https://github.com/CodesbyFebin/EdgeORE/actions/runs/38296685224)
- [Quality](https://github.com/CodesbyFebin/EdgeORE/actions/runs/38296685468)
- [Build governance and qualification](https://github.com/CodesbyFebin/EdgeORE/actions/runs/38296685205)

Device signing, real mainnet landing and interrupted-submit device recovery remain NOT_RUN. No funds were spent, no transaction signed/submitted and no store/release/registry publication performed. ORE/SKR prize eligibility is not claimed. The deploy cap excludes rent and network fees. The generated presentation is not a device-recorded demo.
