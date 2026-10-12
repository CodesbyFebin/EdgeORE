//! Mainnet-fork simulation (LiteSVM) of the exact ORE v3 Deploy message EdgeORE builds.
//! Inputs: fixtures/ from fetch.py (read-only mainnet snapshot + dumped program ELFs) and fixtures/kotlin-deploy.json
//! (written by the app's OreMainnetCapTest). Nothing is sent to any real cluster.
use base64::Engine;
use litesvm::LiteSVM;
use solana_account::Account;
use solana_address::Address;
use solana_clock::Clock;
use solana_hash::Hash;
use solana_instruction::{AccountMeta, Instruction};
use solana_message::{compiled_instruction::CompiledInstruction, Message, MessageHeader};
use solana_signature::Signature;
use solana_transaction::Transaction;
use std::str::FromStr;

const ORE: &str = "oreV3EG1i9BEgiAJ8b177Z2S2rMarzak4NMv1kULvWv";
const ENTROPY: &str = "3jSkUuYBoJzQPMEzTvkDFXCZUBksPamrVhrnHR9igu2X";
const BOARD: &str = "BrcSxdp1nXFzou1YyDnQJcPNBNHgoypZmTsyKBSLLXzi";
const TREASURY: &str = "45db2FSR4mcXdSVVZbKbwojU6uYDpMyhpEi7cC8nHaWG";
const CONFIG: &str = "9c9X7aDRAF41faiDs94ELjT19UrGnn72wBW9hPsS4Awy";
const VAR: &str = "BWCaDY96Xe4WkFq1M7UiCCRcChsJ3p51L5KrGzhxgm2E";
const SYSTEM: &str = "11111111111111111111111111111111";

fn a(s: &str) -> Address { Address::from_str(s).unwrap() }
fn json(p: &str) -> serde_json::Value { serde_json::from_str(&std::fs::read_to_string(p).unwrap_or_else(|_| panic!("missing {p}: run fetch.py and the app unit test first"))).unwrap() }
fn hex(s: &str) -> Vec<u8> { (0..s.len()).step_by(2).map(|i| u8::from_str_radix(&s[i..i + 2], 16).unwrap()).collect() }

/// Parses the app's legacy message bytes strictly (one instruction, nothing trailing).
fn parse(m: &[u8]) -> Message {
    let header = MessageHeader { num_required_signatures: m[0], num_readonly_signed_accounts: m[1], num_readonly_unsigned_accounts: m[2] };
    let n = m[3] as usize;
    let keys: Vec<Address> = (0..n).map(|i| Address::try_from(&m[4 + 32 * i..36 + 32 * i]).unwrap()).collect();
    let mut p = 4 + 32 * n;
    let bh = Hash::new_from_array(m[p..p + 32].try_into().unwrap()); p += 32;
    assert_eq!(m[p], 1, "exactly one instruction"); p += 1;
    let program_id_index = m[p]; p += 1;
    let na = m[p] as usize; p += 1;
    let accounts = m[p..p + na].to_vec(); p += na;
    let nd = m[p] as usize; p += 1;
    let data = m[p..p + nd].to_vec(); p += nd;
    assert_eq!(p, m.len(), "no trailing bytes");
    Message { header, account_keys: keys, recent_blockhash: bh, instructions: vec![CompiledInstruction { program_id_index, accounts, data }] }
}

/// Independent derivation from regolith-labs/ore @48c203bd api/src/sdk.rs deploy() and state/mod.rs seeds.
fn source_deploy(signer: Address, round_id: u64, amount: u64, mask: u32) -> Instruction {
    let ore = a(ORE);
    let pda = |seeds: &[&[u8]]| Address::find_program_address(seeds, &ore).0;
    let mut data = vec![6u8]; data.extend_from_slice(&amount.to_le_bytes()); data.extend_from_slice(&mask.to_le_bytes());
    Instruction { program_id: ore, data, accounts: vec![
        AccountMeta::new(signer, true), AccountMeta::new(signer, false),
        AccountMeta::new(pda(&[b"automation", signer.as_ref()]), false), AccountMeta::new(a(BOARD), false),
        AccountMeta::new(a(CONFIG), false), AccountMeta::new(pda(&[b"miner", signer.as_ref()]), false),
        AccountMeta::new(pda(&[b"round", &round_id.to_le_bytes()]), false), AccountMeta::new(a(TREASURY), false),
        AccountMeta::new_readonly(a(SYSTEM), false), AccountMeta::new_readonly(ore, false),
        AccountMeta::new(Address::find_program_address(&[b"var", a(BOARD).as_ref(), &0u64.to_le_bytes()], &a(ENTROPY)).0, false),
        AccountMeta::new_readonly(a(ENTROPY), false),
    ]}
}

/// The instruction as the runtime sees it: program, then (key, signer, writable) per account in order, then data.
fn resolve(m: &Message) -> (Address, Vec<(Address, bool, bool)>, Vec<u8>) {
    let h = m.header; let n = m.account_keys.len();
    let signed = h.num_required_signatures as usize;
    let writable = |i: usize| if i < signed { i < signed - h.num_readonly_signed_accounts as usize } else { i < n - h.num_readonly_unsigned_accounts as usize };
    let ix = &m.instructions[0];
    (m.account_keys[ix.program_id_index as usize],
     ix.accounts.iter().map(|&i| { let i = i as usize; (m.account_keys[i], i < signed, writable(i)) }).collect(),
     ix.data.clone())
}

fn fork() -> (LiteSVM, serde_json::Value) {
    let snap = json("fixtures/snapshot.json");
    let mut svm = LiteSVM::new().with_sigverify(false).with_blockhash_check(false);
    svm.add_program_from_file(a(ORE), "fixtures/ore.so").unwrap();
    svm.add_program_from_file(a(ENTROPY), "fixtures/entropy.so").unwrap();
    for (k, v) in snap["accounts"].as_object().unwrap() {
        let data = base64::engine::general_purpose::STANDARD.decode(v["data"].as_str().unwrap()).unwrap();
        svm.set_account(a(k), Account { lamports: v["lamports"].as_u64().unwrap(), data, owner: a(v["owner"].as_str().unwrap()), executable: false, rent_epoch: 0 }).unwrap();
    }
    let mut clock: Clock = svm.get_sysvar();
    clock.slot = snap["startSlot"].as_u64().unwrap() + 1; // inside the snapshot round's deploy window
    svm.set_sysvar(&clock);
    (svm, snap)
}

fn round_deployed(svm: &LiteSVM, round: &Address) -> Vec<u64> {
    let d = svm.get_account(round).unwrap().data;
    (0..25).map(|i| u64::from_le_bytes(d[16 + 8 * i..24 + 8 * i].try_into().unwrap())).collect()
}

#[test]
fn app_bytes_equal_source_derivation_and_execute_on_the_mainnet_fork() {
    let k = json("fixtures/kotlin-deploy.json");
    let (mut svm, snap) = fork();
    let round_id = snap["roundId"].as_u64().unwrap();
    assert_eq!(k["roundId"].as_u64().unwrap(), round_id);
    let bytes = hex(k["messageHex"].as_str().unwrap());
    let msg = parse(&bytes);
    let signer = a(k["signer"].as_str().unwrap());
    let total = k["totalLamports"].as_u64().unwrap();
    assert!(total <= 10_000_000, "0.01 SOL cap");
    let ix = source_deploy(signer, round_id, total / 25, (1 << 25) - 1);
    let expected = Message::new_with_blockhash(&[ix], Some(&signer), &msg.recent_blockhash);
    // Key ORDER in the message table may differ (the Rust compiler sorts keys); what the program sees must not.
    assert_eq!(resolve(&msg), resolve(&expected), "app instruction == independent Rust derivation from the ORE source");
    assert_eq!(msg.header, expected.header);
    let round = a(snap["roundAddress"].as_str().unwrap());
    assert_eq!(msg.account_keys[5], round, "round PDA matches the Solana CLI derivation");
    assert_eq!(msg.account_keys[7], a(VAR));

    svm.airdrop(&signer, 1_000_000_000).unwrap();
    let before = round_deployed(&svm, &round);
    let bal0 = svm.get_account(&signer).unwrap().lamports;
    let tx = Transaction { signatures: vec![Signature::default()], message: msg };
    let res = svm.send_transaction(tx);
    match &res { Ok(m) => println!("logs:\n{}", m.logs.join("\n")), Err(e) => panic!("deploy failed: {:?}\n{}", e.err, e.meta.logs.join("\n")) }
    let after = round_deployed(&svm, &round);
    for i in 0..25 { assert_eq!(after[i] - before[i], total / 25, "square {i}"); }
    let spent = bal0 - svm.get_account(&signer).unwrap().lamports;
    println!("round {round_id}: +{} lamports on each of 25 squares; signer spent {spent} lamports (deploy + miner rent + fee)", total / 25);
    assert!(spent >= total);
}

#[test]
fn a_stale_round_is_rejected_by_the_program() {
    let (mut svm, snap) = fork();
    let signer = Address::new_from_array([1; 32]);
    let round_id = snap["roundId"].as_u64().unwrap();
    svm.airdrop(&signer, 1_000_000_000).unwrap();
    let ix = source_deploy(signer, round_id + 1, 400_000, (1 << 25) - 1);
    let msg = Message::new_with_blockhash(&[ix], Some(&signer), &svm.latest_blockhash());
    assert!(svm.send_transaction(Transaction { signatures: vec![Signature::default()], message: msg }).is_err());
}
