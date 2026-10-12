# Icon mapping (mint holographic design)

Every icon is a vector drawn in `app/src/main/java/com/edgeore/app/ui/components/Icons.kt` (`EdgeIcons`).
None are bitmaps, so they tint with the theme tokens in `ui/theme/Theme.kt`. Icons sit inside 48dp touch targets.

| Spec motif | EdgeIcons | Where |
|---|---|---|
| Mine tab / pickaxe | `Mine` | bottom bar, Mine header |
| AI tab | `Ai` | bottom bar, AI screen |
| Nodes tab | `Nodes` | bottom bar, Nodes screen |
| Receipts tab | `Receipts` | bottom bar, Receipts screen |
| Network / cluster selector | `Globe` | Mine network selector |
| Wallet / connect | `Wallet` | MWA Connect row, review |
| Copy address | `Copy` | wallet row |
| Switch account / refresh | `Refresh` | wallet row, balance refresh |
| Disconnect / close | `Cross` | wallet row, dialogs |
| Balances (SOL) | `Coins` | balance cards |
| ORE token | `Ore` | ORE card, grid, decorative particles |
| Daily check-in | `Calendar` | check-in card |
| Streak / points | `Gift` | check-in card (no token reward) |
| Reminders | `Bell` | ORE Bot reminders switch |
| ORE Bot tips | `Bot` | tips box (text only, cannot sign) |
| Clock / slot window | `Clock` | active-until line |
| CPU | `Cpu` | resource grid |
| Memory | `Memory` | resource grid |
| Storage | `Storage` | resource grid, Storage screen |
| Network activity | `Network` | resource grid (Idle / Active) |
| Wi-Fi gate | `Wifi` | contribution toggles |
| ORE 5x5 grid | `Grid` | ORE grid card |
| Mine start / Stop | `Play` / `Stop` / `Pause` | session panel |
| Deploy / send | `Send` / `Bolt` | ORE mining card |
| Transaction review / safety | `Shield` / `Lock` | review sheet |
| Settings | `Settings` | header / settings |
| Info | `Info` | header, help |
| Warning / error states | `Warning` | stale, error, mismatch states |
| Success | `Check` | confirmed states |
| Thermal / battery gates | `Thermo` / `Battery` | contribution toggles |
| Health pulse | `Pulse` | node status |
| Links / docs / export | `Link` / `Doc` / `Export` | receipts, storage |
| Navigation | `Chevron` / `Back` | lists, sheets |

The ORE-icon particles in `MiningAnimation.kt` are purely visual: they do not represent earned ORE.
