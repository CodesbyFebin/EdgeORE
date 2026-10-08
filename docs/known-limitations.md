# Known limitations

- No phone was attached. Install, rotation, process death, TalkBack, and wallet return are NOT_RUN.
- No Solana wallet authorized this build. The transfer path is unit-tested only.
- No devnet transaction was broadcast by this work. No signature is recorded because none exists.
- AI cancellation stops waiting. It does not prove the host stopped computing.
- A private IP is not proof the host is yours.
- Model checksum matches a digest you type. That is not a trusted provenance pin.
- Airplane-mode control reports whether a network is up. It does not run a model.
- CPU and disk rates need two samples. The first reading is absent, not zero.
- Traffic since boot is device traffic, not bandwidth contributed by EdgeORE. Shared bytes stay 0.
- Vault export is plaintext. The Keystore key does not travel to another phone.
- Memory and CPU sliders are stored preferences. They do not cap a running model or the whole device.
- Resume on Mine changes a local flag. It does not start a miner.
- ORE rewards are not observed. SKR is not CPU-mined.
- The Gradle worker in this environment sometimes crashes after tests write XML. An assertion report plus a worker crash is not a clean PASS.
- The signed APK hash above was produced before this documentation commit.
