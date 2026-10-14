// Functions below copied verbatim from regolith-labs/ore@48c203bd api/src/state/round.rs (rng, winning_square, did_hit_motherlode).
fn rng(slot_hash: [u8; 32]) -> Option<u64> {
    if slot_hash == [0; 32] || slot_hash == [u8::MAX; 32] { return None; }
    let r1 = u64::from_le_bytes(slot_hash[0..8].try_into().unwrap());
    let r2 = u64::from_le_bytes(slot_hash[8..16].try_into().unwrap());
    let r3 = u64::from_le_bytes(slot_hash[16..24].try_into().unwrap());
    let r4 = u64::from_le_bytes(slot_hash[24..32].try_into().unwrap());
    Some(r1 ^ r2 ^ r3 ^ r4)
}
fn winning_square(rng: u64) -> usize { (rng % 25) as usize }
fn did_hit_motherlode(rng: u64) -> bool { rng.reverse_bits() % 500 == 0 }
fn u64at(d: &[u8], o: usize) -> u64 { u64::from_le_bytes(d[o..o + 8].try_into().unwrap()) }
fn main() {
    for line in std::fs::read_to_string("rounds.hex").unwrap().lines() {
        let d: Vec<u8> = (0..line.len()).step_by(2).map(|i| u8::from_str_radix(&line[i..i + 2], 16).unwrap()).collect();
        let id = u64at(&d, 8);
        let sh_off = 8 + 8 + 3 * 25 * 8;
        let sh: [u8; 32] = d[sh_off..sh_off + 32].try_into().unwrap();
        let ml = u64at(&d, sh_off + 40);
        match rng(sh) {
            Some(r) => println!("{} {:016x} {} {} {}", id, r, winning_square(r) + 1, did_hit_motherlode(r), ml),
            None => println!("{} none", id),
        }
    }
}
