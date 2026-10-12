# Mine screen art: rock and pickaxe (feature/mine-pickaxe-animation)

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

`ui/components/MiningAnimation.kt`. The pose is derived only from the existing evaluated `EdgeState`
(`EdgePolicy.evaluate(settings, device)`); no new app state is stored.

| EdgeState | Pose |
|---|---|
| `IDLE` (Edge Mode resumed and every gate passes), `STARTING`, `ACTIVE` | `SWINGING`: one swing per second (wind-up 0–45%, strike 45–60%, rebound, carry). At impact (60%) the rock shakes ±3 dp, the veins pulse mint and seven copper/mint sparks fly. |
| same, with system **Remove animations** (animator duration scale 0) | `STILL_ON`: pickaxe held against the rock, faint vein glow, no motion |
| `PAUSED` (by you or by policy), `PAUSING`, `UNAVAILABLE`, `FAILED` | `RESTING`: pickaxe raised 24° at a tilt, no motion |

Tapping the art (or the **MINE / PAUSE** label under it) is the same `edgeModeResumed` toggle the ring was.
The label under it still shows the evaluated state (`PAUSED`, `IDLE`), the policy headline is unchanged
("Paused by you", "Paused by policy: …", "Idle · no qualified workload in this build"), and a caption says
"Animation only: the pickaxe swings while Edge Mode is on. No workload runs and nothing is earned."
The ORE rewards card still reads "Not observed". TalkBack reads the art's description, for example
"Pickaxe striking the rock: Edge Mode is on. Animation only; no workload runs and nothing is earned."

### Departure from design.md (owner decision)

`design.md` §5 says "Do not animate a running ring without measured activity" and its motion rules say "no looping
ornament". This build has no qualified workload, so `ACTIVE` is never reached: binding the swing to `ACTIVE` only
would mean the pickaxe never moves. At the owner's request the swing also runs in `IDLE` (resumed, gates pass),
with the caption and description above saying it is an animation only. If that should not ship, change
`MiningMotion.pose` so only `ACTIVE` swings; the tests in `MiningMotionTest` name the mapping.

## Tests

- `MiningMotionTest` (JVM): pose for every `EdgeState` with and without reduced motion; fresh install and
  paused-by-policy rest; swing continuity and strike at impact; impact effects only after the strike; descriptions
  carry no ORE/SKR/reward/income wording.
- `MiningAnimationComposeTest` (Robolectric): swinging changes the drawn angle over time; paused holds 24°;
  reduced motion holds 0°; the real `Settings.Global.ANIMATOR_DURATION_SCALE = 0` selects the static pose.
  Its `frames` test writes preview PNGs only when `MINE_FRAMES_OUT` is set (skipped in the gate).

Screenshot baselines show the idle (resting) frame; see `docs/screenshots/README.md`.
These are JVM renders, not a phone run.
