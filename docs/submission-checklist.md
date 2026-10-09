# Submission checklist

Frozen device candidate, unchanged by later source edits:

- Commit `3929edbbd89cfebc8f309b090fd268e532bd684a`
- APK SHA-256 `d64d5b92f28ac8856a9904528f464125a3b6333a9b52e1f7b0e622e46196dd0b`
- Certificate SHA-256 `5baab0630958af7a3a5faabc3f3525f8fd5e2bb6a1b98e19bd6e16b1f6e834a1`
- `com.edgeore.app` `0.2.6-review` versionCode 8

The report commit `ab009ff` only recorded that rebuild. It is not the build commit.

Outer hash `6f1f73a9…` differs because `META-INF/version-control-info.textproto` embeds `7433eee` instead of `3929edb`. `classes.dex` matched.

- [ ] Install that exact APK on a phone
- [ ] Cold launch and five tabs
- [ ] Rotation, large text, process death, no-network, permission denial
- [ ] Wallet authorization and devnet balance
- [ ] Reviewed transfer, separate submit, confirmed or finalized
- [ ] Receipt export verified on a second machine, then rejected after one changed byte
- [ ] Owned-host model reply and a refused public host
- [ ] Node pair and revoke
- [ ] Vault encrypt, export, delete
- [ ] On-device airplane-mode inference (blocked: no runtime in the APK)
- [ ] ORE, SKR, VPN, cloud, bandwidth earning (leave unavailable)

Decision: **NO-GO** for a full-scope release.
