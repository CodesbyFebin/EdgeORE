# Tile insight: crowding and expected-value estimate

Information only. Selecting a square shows numbers. The one action, "Review a deploy on square N", opens the usual
exact-message review. Nothing is deployed until you review and sign in your wallet. On screen every number is labelled
**estimate, not a promise**.

## Inputs (read-only)

- `Round.deployed[25]` and `Round.count[25]` from the live Round account (validated by `OreObserver`)
- `Treasury.motherlode`
- your stake `x` (the amount in the ORE card, by default the 0.01 SOL mainnet cap)

## Rules used (regolith-labs/ore @ 48c203bd)

- `round.rs` `winning_square = rng % 25`. EdgeORE treats every square as having a 1/25 chance. That assumes the
  entropy is uniform, which EdgeORE does not verify.
- `checkpoint.rs`: the winning square gets back `deployed − max(deployed/100, 1)`, so 99% is kept. A losing square gets
  back `deployed − admin − max((deployed − admin)/10, 1)`, about 89.1%.
- `round.rs` `did_hit_motherlode`: `reverse_bits(rng) % 500 == 0`, a 1/500 chance.
- `reset.rs`: the winning square receives `rewards[0] = min(ONE_ORE, MAX_SUPPLY − supply)`. `Round.rewards[0]` was 1 ORE on all five
  live settled rounds checked on 13 Oct 2026, so the estimate uses R = 1 ORE. If the supply cap is reached, R becomes 0.

## Formulas

Let `D` be the SOL already on the square and `x` your stake.

- Crowding ratio = `D / mean(deployed)`. Below 0.75 is *light*, up to 1.5 is *average*, above 1.5 is *crowded*, and 0 is *empty*.
- Expected SOL kept = `x · (1/25 · 0.99 + 24/25 · 0.891)` ≈ `0.895 · x`. This is the same for every square: the
  expected fee cost is about **10.5%** of the stake whichever square you pick.
- Expected ORE = `1/25 · x/(D + x) · (R + M/500)`, where `R` is the round reward and `M` is the motherlode.

## Limits

These are averages over many rounds, not a prediction for the next one. Later deploys into the same round dilute `x/(D+x)`.
Fees are rounded per square on-chain. SOL lost on losing squares is not recovered. Nothing here is income, yield or advice.
