# ORE rules reference (Rust)

`rng`, `winning_square` and `did_hit_motherlode` copied verbatim from regolith-labs/ore@48c203bd `api/src/state/round.rs`. Reads `rounds.hex` (one Round account per line, hex) and prints `id rng winner(1-based) motherlode_hit motherlode`. It produced `app/src/test/resources/ore/settled-rounds-rust-reference.txt`. No dependencies: `cargo run`.
