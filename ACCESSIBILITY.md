# Accessibility statement

EdgeORE aims to let people understand wallet actions, resource controls and evidence states without relying on color, animation or a particular input method. Accessibility is part of the native interface design, not a claim that all assistive-technology gates have passed.

## Supported environment and design commitments

The Android app declares Android 8.0 / API 26 as its minimum platform. Wallet interactions require a compatible Mobile Wallet Adapter wallet; that wallet supplies its own interface. On-device AI has separate model/runtime and hardware requirements and is not available on every device that can display the app.

The [design specification](design.md) calls for scalable text, readable contrast, descriptive control labels and headings, explicit unavailable/error states, reachable actions at 200% font scale, keyboard and switch navigation, TalkBack reading order, and reduced-motion behavior. Essential wallet decisions must have a text explanation rather than being conveyed by an icon, animation or color alone.

## Verified coverage and known limitations

- Repository screenshot tests use API 28 Robolectric renders, including an onboarding render at 150% font scale. They are JVM layout evidence, not proof of TalkBack or physical-device usability.
- Complete 200% text, narrow-screen and landscape coverage, keyboard focus, Switch Access and TalkBack behavior still require recorded runtime checks against the exact shipped APK.
- Animated mining artwork, changing status announcements and reduced-motion/power-save behavior need assistive-technology and device qualification. Artwork must not be the sole indicator of session state.
- Wallet prompts and configured provider/model interfaces introduce external accessibility dependencies. Report which interface presents the barrier so the appropriate maintainer can investigate.
- Unsupported hardware and unread observations should appear explicitly as unavailable or unobserved. They must not be replaced with invented progress, balances or successful outcomes.

Consult the [device runbook](docs/DEVICE-RUNBOOK.md) and the evidence for the commit under review. A green build or a newly recorded screenshot does not close an unrun accessibility gate. No WCAG-conformance or accessibility-certification claim is made here.

## Report a barrier

Open an [accessibility issue](https://github.com/CodesbyFebin/EdgeORE/issues/new) with a title beginning `[Accessibility]`. Include the affected screen/action, APK version or commit, Android version, relevant font/display settings and assistive technology, the steps to reproduce, and what would make the task usable. A redacted screenshot or recording is helpful but optional; do not include wallet secrets or private documents.

For a report containing sensitive information, request private contact from [CodesbyFebin](https://github.com/CodesbyFebin). You do not need to disclose a diagnosis or disability to report a barrier. Contributors should follow the [code of conduct](CODE_OF_CONDUCT.md).
