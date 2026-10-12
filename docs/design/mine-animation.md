# Mine screen art: rock and pickaxe

The Edge Mode ring on the Mine screen is replaced by the owner's own art: a rock with mint veins and copper streaks,
and a pickaxe that swings into it. Requested by the repository owner on 2026-10-12.

## Assets (user-supplied art)

| Drawable (`app/src/main/res/drawable-nodpi/`) | Size | Bytes | From |
|---|---|---|---|
| `mine_rock.webp` | 640×455, alpha | 78,802 | rock image, background removed, soil mound kept and faded at its edges |
| `mine_rock_veins.webp` | 320×227, alpha mask | 2,350 | the rock's mint veins only (white on transparent), tinted mint and pulsed at impact |
| `mine_pickaxe.webp` | 420×422, alpha | 17,290 | pickaxe image, flat background removed, mirrored so the head points at the rock |

Source images, supplied by the owner in chat (not committed; 1408×1408 JPEG):
- rock: SHA-256 `e79e5f1632647929f85d9dfcdd2b5373f6924a41863a606f4a8ba64de3315d38`
- pickaxe: SHA-256 `c1a5746cfe8dd4bb651574ad0b7cae6fd8fff55c6170d9c683347e176e0895a6`

Reproduce (Pillow, numpy, scipy; the output is byte-identical to the committed drawables):

```
python3 tools/art/mine_cutout.py ROCK.jpg PICKAXE.jpg /tmp/mine
python3 tools/art/mine_export.py /tmp/mine app/src/main/res/drawable-nodpi
```

The pickaxe source had a small mark in its bottom-right corner; the cut-out keeps only the pickaxe itself.
No third-party art, font or library is added. See `NOTICE`.

## Behaviour

`ui/components/MiningAnimation.kt`. The pose is derived only from the contribution session's phase
(`contribution/ContributionSession.kt`, see `docs/contribution/session.md`); no separate animation state exists.

| Session phase | Pose |
|---|---|
| `ACTIVE` (a session is running its parts on your node and gateway) | `SWINGING`: one swing per second (wind-up 0–45%, strike 45–60%, rebound, carry). At impact (60%) the rock shakes ±3 dp, the veins pulse mint and seven copper/mint sparks fly. |
| `ACTIVE`, with system **Remove animations** (animator duration scale 0) | `STILL_ON`: pickaxe held against the rock, faint vein glow, no motion |
| `IDLE`, `STARTING`, `STOPPING`, `FAILED` | `RESTING`: pickaxe raised 24° at a tilt, no motion |

Under the art: the session phase, then **Start Mining** (idle or failed) or **Stop** (starting or active;
"Stopping…" while it winds down), then the copy that says what a session runs and that nothing is earned, then
each part with its status ("Not configured: …", running, waiting, done, cancelled, failed) and only the measurements
that part reported. **Pause Edge Mode / Resume Edge Mode** is a separate button; pausing stops a running session.
The art itself is no longer a toggle.

The 2026-10-12 owner exception (swinging while Edge Mode was merely on, with an "Animation only" caption) is
reverted: a real workload now exists, so the art follows it and the caption is gone.

## Tests

- `MiningMotionTest` (JVM): pose for every session phase with and without reduced motion (only `ACTIVE` swings);
  swing continuity and strike at impact; impact effects only after the strike; descriptions
  carry no ORE/SKR/reward/income wording.
- `MiningAnimationComposeTest` (Robolectric): swinging changes the drawn angle over time; paused holds 24°;
  reduced motion holds 0°; the real `Settings.Global.ANIMATOR_DURATION_SCALE = 0` selects the static pose.
  Its `frames` test writes preview PNGs of the session panel only when `MINE_FRAMES_OUT` and
  `MINE_FRAMES_SESSION` are set (skipped in the gate); the active frames use the measurements recorded by the
  real end-to-end session in `evidence/contribution-session/`.
- `MiningSessionPanelTest` (Robolectric): Start Mining is visible and clickable while idle, Stop while active; only
  `ACTIVE` swings; not-configured parts show what to set up and no numbers; only reported measurements are shown.

Screenshot baselines show the idle (resting) frame; see `docs/screenshots/README.md`.
These are JVM renders, not a phone run.

## 2026-10-12: frame-based art from the user's GIF (feature/home-ore-upgrade)

The drawn rock + rotating pickaxe (mine_rock / mine_rock_veins / mine_pickaxe) was replaced by 25 frames cut from the
user's rock-and-pickaxe GIF (560×420, 25 frames at 40 ms, sha256 b5bb0f6fb8405796bd09ec7f4470a75d54be6133a2e396c728f02073baeed8f4).
Each frame is cropped to the art's bounding box, its background keyed toward the brand background #101414, scaled to
400×275 and saved as WebP q80 (`res/drawable-nodpi/mine_frame_00..24.webp`, 224,960 bytes total). Behaviour is unchanged:
frames play (one strike per 1000 ms cycle) only while the contribution session is ACTIVE; idle, starting, stopping and
failed hold frame 0; "Remove animations" holds the impact frame 15 (MiningMotion.IMPACT = 0.60).

The Mine home also gains an ORE strip (ui/screens/OreHomeStrip.kt): deployed SOL, motherlode and round time from the
read-only OreObserver, Lite / Pro shortcuts into the ORE board, and ORE / SOL prices shown as "—" because no price
source is configured. Reads older than 60 s are labelled stale; loading and errors are labelled. No deploy controls.
