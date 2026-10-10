//! Shared test harness and scenarios for the receipt-settlement program (spec v2).
//!
//! Every scenario runs against a `Backend`:
//! - `Mode::Svm`: in-process LiteSVM with the Ed25519 native program loaded
//!   (`precompiles` feature). Used by `tests/settlement.rs`.
//! - `Mode::Validator`: a running local `solana-test-validator` over RPC
//!   (`ANCHOR_PROVIDER_URL`, default http://127.0.0.1:8899), with the program
//!   loaded at genesis by `anchor test --validator legacy`. Used by
//!   `tests/validator.rs`. Airdrops come from the local test faucet only.
//!
//! Validator mode SENDS every transaction (skip_preflight) and reads the
//! landed result back with getTransaction, so an expected failure is asserted
//! on the slot it actually executed in, not on a simulation against an older
//! bank (see docs/SETTLEMENT-SPEC.md §9, J-1).
//!
//! Signatures are real Ed25519 signatures by test keypairs. The settlement
//! digest is recomputed here independently with `sha2`.
#![allow(dead_code)]

use {
    anchor_lang::{
        error::ERROR_CODE_OFFSET,
        prelude::Pubkey,
        solana_program::{instruction::Instruction, system_program},
        AccountDeserialize, InstructionData, Space, ToAccountMetas,
    },
    litesvm::LiteSVM,
    receipt_settlement::{error::SettlementError, state::*},
    sha2::{Digest, Sha256},
    solana_commitment_config::CommitmentConfig,
    solana_keypair::Keypair,
    solana_message::{Message, VersionedMessage},
    solana_rpc_client::rpc_client::RpcClient,
    solana_rpc_client_api::config::{
        RpcSendTransactionConfig, RpcSimulateTransactionConfig, RpcTransactionConfig,
    },
    solana_signer::Signer,
    solana_transaction::{versioned::VersionedTransaction, InstructionError, TransactionError},
    solana_transaction_status_client_types::{
        option_serializer::OptionSerializer, UiTransactionEncoding,
    },
    std::time::{Duration, Instant},
};

/// Generate one `#[test]` per scenario for a backend mode.
macro_rules! scenario_tests {
    (svm; $($name:ident),* $(,)?) => {
        $(
            #[test]
            fn $name() {
                common::$name(common::Mode::Svm)
            }
        )*
    };
    (validator; $($name:ident),* $(,)?) => {
        $(
            #[test]
            #[ignore = "needs a local solana-test-validator; see tests/validator.rs"]
            fn $name() {
                common::$name(common::Mode::Validator)
            }
        )*
    };
}

pub const SOL: u64 = 1_000_000_000;
/// LiteSVM runs start at this slot so slot assertions are meaningful.
const START_SLOT: u64 = 1_000;
/// Deadlines (in slots after creation). LiteSVM warps instantly; on a
/// validator each slot is ~400 ms of real time, so "short" jobs that must
/// expire during a test are kept small, and "long" jobs must not expire.
const LONG_SVM: u64 = 1_000;
const LONG_VALIDATOR: u64 = 400;
const SHORT_SVM: u64 = 100;
const SHORT_VALIDATOR: u64 = 60;
const ED25519_ID: Pubkey = solana_sdk_ids::ed25519_program::ID;
const IX_SYSVAR: Pubkey = solana_sdk_ids::sysvar::instructions::ID;
// Anchor framework error codes used below.
const CONSTRAINT_HAS_ONE: u32 = 2001;
const ACCOUNT_NOT_INITIALIZED: u32 = 3012;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Mode {
    Svm,
    Validator,
}

fn code(e: SettlementError) -> u32 {
    ERROR_CODE_OFFSET + e as u32
}

// ------------------------------------------------------------------ backend

#[derive(Debug)]
pub struct TxOk {
    pub fee: u64,
    pub slot: u64,
}

pub struct Failed {
    pub err: TransactionError,
    pub logs: Vec<String>,
    /// Fee debited from the fee payer by the failed transaction (0 if the
    /// transaction never landed).
    pub fee_charged: u64,
    /// Validator: whether the transaction landed in a block. LiteSVM: true.
    pub landed: bool,
    pub slot: u64,
}
impl std::fmt::Debug for Failed {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(
            f,
            "{:?} (landed={}, slot={}, fee={})\n{}",
            self.err,
            self.landed,
            self.slot,
            self.fee_charged,
            self.logs.join("\n")
        )
    }
}

pub type TransactionResult = Result<TxOk, Failed>;

pub struct Acc {
    pub lamports: u64,
    pub data: Vec<u8>,
}

pub enum Backend {
    Svm(LiteSVM),
    Rpc(RpcClient),
}

impl Backend {
    pub fn new(mode: Mode) -> Self {
        match mode {
            Mode::Svm => {
                let mut svm = LiteSVM::new();
                let bytes = include_bytes!(concat!(
                    env!("CARGO_TARGET_TMPDIR"),
                    "/../deploy/receipt_settlement.so"
                ));
                svm.add_program(pid(), bytes).unwrap();
                svm.warp_to_slot(START_SLOT);
                Backend::Svm(svm)
            }
            Mode::Validator => {
                let url = std::env::var("ANCHOR_PROVIDER_URL")
                    .unwrap_or_else(|_| "http://127.0.0.1:8899".to_string());
                assert!(
                    url.contains("127.0.0.1") || url.contains("localhost"),
                    "validator tests only run against a local validator, got {url}"
                );
                let rpc = RpcClient::new_with_commitment(url, CommitmentConfig::confirmed());
                let prog = rpc
                    .get_account(&pid())
                    .expect("program not loaded on the local validator");
                assert!(prog.executable, "program account is not executable");
                Backend::Rpc(rpc)
            }
        }
    }

    pub fn is_svm(&self) -> bool {
        matches!(self, Backend::Svm(_))
    }

    pub fn long(&self) -> u64 {
        if self.is_svm() {
            LONG_SVM
        } else {
            LONG_VALIDATOR
        }
    }

    pub fn short(&self) -> u64 {
        if self.is_svm() {
            SHORT_SVM
        } else {
            SHORT_VALIDATOR
        }
    }

    /// LiteSVM: the clock's slot. Validator: the confirmed slot.
    pub fn slot(&self) -> u64 {
        match self {
            Backend::Svm(svm) => {
                svm.get_sysvar::<anchor_lang::solana_program::clock::Clock>()
                    .slot
            }
            Backend::Rpc(rpc) => rpc
                .get_slot_with_commitment(CommitmentConfig::confirmed())
                .unwrap(),
        }
    }

    pub fn get_account(&self, key: &Pubkey) -> Option<Acc> {
        match self {
            Backend::Svm(svm) => svm.get_account(key).map(|a| Acc {
                lamports: a.lamports,
                data: a.data,
            }),
            Backend::Rpc(rpc) => rpc
                .get_account_with_commitment(key, CommitmentConfig::confirmed())
                .expect("rpc get_account")
                .value
                .map(|a| Acc {
                    lamports: a.lamports,
                    data: a.data,
                }),
        }
    }

    pub fn minimum_balance_for_rent_exemption(&self, len: usize) -> u64 {
        match self {
            Backend::Svm(svm) => svm.minimum_balance_for_rent_exemption(len),
            Backend::Rpc(rpc) => rpc.get_minimum_balance_for_rent_exemption(len).unwrap(),
        }
    }

    pub fn airdrop(&mut self, key: &Pubkey, lamports: u64) {
        match self {
            Backend::Svm(svm) => {
                svm.airdrop(key, lamports).unwrap();
            }
            Backend::Rpc(rpc) => {
                // Local test-validator faucet only (asserted in Backend::new).
                let sig = rpc.request_airdrop(key, lamports).expect("airdrop");
                let t = Instant::now();
                while t.elapsed() < Duration::from_secs(60) {
                    if rpc
                        .confirm_transaction_with_commitment(&sig, CommitmentConfig::confirmed())
                        .map(|r| r.value)
                        .unwrap_or(false)
                    {
                        return;
                    }
                    std::thread::sleep(Duration::from_millis(200));
                }
                panic!("airdrop not confirmed");
            }
        }
    }

    /// LiteSVM: set the clock to `slot`. Validator: wait until the confirmed
    /// slot is >= `slot`. A transaction sent afterwards executes in a bank
    /// whose slot is >= the confirmed slot, so "after the deadline" checks are
    /// deterministic on both backends. Landing in an exact slot is only
    /// possible in LiteSVM.
    pub fn warp_to_slot(&mut self, slot: u64) {
        match self {
            Backend::Svm(svm) => svm.warp_to_slot(slot),
            Backend::Rpc(rpc) => {
                let t = Instant::now();
                while rpc
                    .get_slot_with_commitment(CommitmentConfig::confirmed())
                    .unwrap()
                    < slot
                {
                    assert!(
                        t.elapsed() < Duration::from_secs(300),
                        "slot wait timed out"
                    );
                    std::thread::sleep(Duration::from_millis(100));
                }
            }
        }
    }

    pub fn send(
        &mut self,
        ixs: &[Instruction],
        payer: &Keypair,
        signers: &[&Keypair],
    ) -> TransactionResult {
        match self {
            Backend::Svm(svm) => {
                // Fresh blockhash so a retried identical transaction is executed
                // again instead of being rejected as AlreadyProcessed.
                svm.expire_blockhash();
                let bh = svm.latest_blockhash();
                let slot = svm
                    .get_sysvar::<anchor_lang::solana_program::clock::Clock>()
                    .slot;
                let msg = Message::new_with_blockhash(ixs, Some(&payer.pubkey()), &bh);
                let tx =
                    VersionedTransaction::try_new(VersionedMessage::Legacy(msg), signers).unwrap();
                match svm.send_transaction(tx) {
                    Ok(meta) => Ok(TxOk {
                        fee: meta.fee,
                        slot,
                    }),
                    Err(f) => Err(Failed {
                        err: f.err,
                        logs: f.meta.logs,
                        fee_charged: f.meta.fee,
                        landed: true,
                        slot,
                    }),
                }
            }
            Backend::Rpc(rpc) => send_rpc(rpc, ixs, payer, signers),
        }
    }
}

/// Append one line per validator transaction to
/// $CARGO_TARGET_TMPDIR/validator-transactions.log (evidence of which
/// outcomes were observed on-chain vs. recovered from simulation).
fn record(sig: &impl std::fmt::Display, landed: bool, slot: u64, outcome: &str) {
    use std::io::Write;
    let path = concat!(env!("CARGO_TARGET_TMPDIR"), "/validator-transactions.log");
    let test = std::thread::current().name().unwrap_or("?").to_string();
    // One write_all per line: O_APPEND keeps concurrent test threads' lines whole.
    let line = format!("{test}\t{sig}\tlanded={landed}\tslot={slot}\t{outcome}\n");
    if let Ok(mut f) = std::fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(path)
    {
        let _ = f.write_all(line.as_bytes());
    }
}

/// Send without preflight, wait for the confirmed status, then read the
/// landed transaction's error, logs, fee and slot.
fn send_rpc(
    rpc: &RpcClient,
    ixs: &[Instruction],
    payer: &Keypair,
    signers: &[&Keypair],
) -> TransactionResult {
    let bh = rpc.get_latest_blockhash().unwrap();
    let msg = Message::new_with_blockhash(ixs, Some(&payer.pubkey()), &bh);
    let tx = VersionedTransaction::try_new(VersionedMessage::Legacy(msg), signers).unwrap();
    let sig = rpc
        .send_transaction_with_config(
            &tx,
            RpcSendTransactionConfig {
                skip_preflight: true,
                ..Default::default()
            },
        )
        .expect("sendTransaction");
    let t = Instant::now();
    let mut landed = false;
    while t.elapsed() < Duration::from_secs(90) {
        if let Some(Some(st)) = rpc
            .get_signature_statuses(&[sig])
            .ok()
            .map(|r| r.value.into_iter().next().flatten())
            .map(|s| s.filter(|s| s.satisfies_commitment(CommitmentConfig::confirmed())))
        {
            let _ = st;
            landed = true;
            break;
        }
        // Stop once the blockhash can no longer land.
        if !rpc
            .is_blockhash_valid(&bh, CommitmentConfig::processed())
            .unwrap_or(true)
        {
            break;
        }
        std::thread::sleep(Duration::from_millis(150));
    }
    if !landed {
        // Rejected before execution (never in a block): no fee, no state
        // change. Simulate only to recover the reason for the assertion.
        let sim = rpc
            .simulate_transaction_with_config(
                &tx,
                RpcSimulateTransactionConfig {
                    sig_verify: true,
                    commitment: Some(CommitmentConfig::confirmed()),
                    ..Default::default()
                },
            )
            .expect("simulate")
            .value;
        let err = sim
            .err
            .map(Into::into)
            .unwrap_or_else(|| panic!("transaction {sig} neither landed nor fails in simulation"));
        record(&sig, false, 0, &format!("err(simulated)={err:?}"));
        let mut logs = vec![format!("[{sig} did not land; reason from simulation]")];
        logs.extend(sim.logs.unwrap_or_default());
        return Err(Failed {
            err,
            logs,
            fee_charged: 0,
            landed: false,
            slot: 0,
        });
    }
    let cfg = RpcTransactionConfig {
        encoding: Some(UiTransactionEncoding::Json),
        commitment: Some(CommitmentConfig::confirmed()),
        max_supported_transaction_version: Some(0),
    };
    let t = Instant::now();
    let landed_tx = loop {
        match rpc.get_transaction_with_config(&sig, cfg) {
            Ok(x) => break x,
            Err(e) if t.elapsed() < Duration::from_secs(30) => {
                let _ = e;
                std::thread::sleep(Duration::from_millis(200));
            }
            Err(e) => panic!("getTransaction {sig}: {e}"),
        }
    };
    let meta = landed_tx.transaction.meta.expect("transaction meta");
    let logs = match meta.log_messages {
        OptionSerializer::Some(l) => l,
        _ => vec![],
    };
    match &meta.err {
        None => record(&sig, true, landed_tx.slot, "ok"),
        Some(e) => record(&sig, true, landed_tx.slot, &format!("err={e:?}")),
    }
    match meta.err {
        None => Ok(TxOk {
            fee: meta.fee,
            slot: landed_tx.slot,
        }),
        Some(e) => Err(Failed {
            err: e.into(),
            logs,
            fee_charged: meta.fee,
            landed: true,
            slot: landed_tx.slot,
        }),
    }
}

// ------------------------------------------------------------ environment

pub struct Env {
    pub b: Backend,
    pub creator: Keypair,
    pub verifier: Keypair,
    pub node: Keypair,
    pub job_id: u64,
    pub terms: [u8; 32],
    pub created_slot: u64,
    pub deadline_slot: u64,
    pub job: Pubkey,
    pub vault: Pubkey,
    pub budget: u64,
}

fn pid() -> Pubkey {
    receipt_settlement::id()
}

fn job_pda(creator: &Pubkey, job_id: u64) -> Pubkey {
    Pubkey::find_program_address(&[JOB_SEED, creator.as_ref(), &job_id.to_le_bytes()], &pid()).0
}
fn vault_pda(job: &Pubkey) -> Pubkey {
    Pubkey::find_program_address(&[VAULT_SEED, job.as_ref()], &pid()).0
}
fn assignment_pda(job: &Pubkey, section: u16) -> Pubkey {
    Pubkey::find_program_address(
        &[ASSIGNMENT_SEED, job.as_ref(), &section.to_le_bytes()],
        &pid(),
    )
    .0
}
fn output_pda(job: &Pubkey, out: &[u8; 32]) -> Pubkey {
    Pubkey::find_program_address(&[OUTPUT_SEED, job.as_ref(), out], &pid()).0
}

fn expect_custom(res: TransactionResult, ix_index: u8, expected: u32) -> Failed {
    let failed = res.expect_err("transaction unexpectedly succeeded");
    match &failed.err {
        TransactionError::InstructionError(i, InstructionError::Custom(c))
            if *i == ix_index && *c == expected => {}
        other => panic!(
            "expected InstructionError({ix_index}, Custom({expected})), got {other:?}\n{failed:?}"
        ),
    }
    failed
}

fn expect_ok(res: TransactionResult, what: &str) -> TxOk {
    match res {
        Ok(ok) => ok,
        Err(f) => panic!("{what} failed: {f:?}"),
    }
}

fn read<T: AccountDeserialize>(b: &Backend, key: &Pubkey) -> T {
    let acc = b.get_account(key).expect("account missing");
    T::try_deserialize(&mut acc.data.as_slice()).unwrap()
}

fn exists(b: &Backend, key: &Pubkey) -> bool {
    b.get_account(key).map(|a| a.lamports > 0).unwrap_or(false)
}

fn lamports(env: &Env, key: &Pubkey) -> u64 {
    env.b.get_account(key).map(|a| a.lamports).unwrap_or(0)
}

fn rent(env: &Env, space: usize) -> u64 {
    env.b.minimum_balance_for_rent_exemption(8 + space)
}

fn funded(env: &mut Env, sol: u64) -> Keypair {
    let k = Keypair::new();
    env.b.airdrop(&k.pubkey(), sol * SOL);
    k
}

fn create_job_ix(
    creator: &Pubkey,
    job_id: u64,
    terms_hash: [u8; 32],
    budget: u64,
    section_count: u16,
    verifier: Pubkey,
    deadline_slots: u64,
) -> Instruction {
    let job = job_pda(creator, job_id);
    Instruction::new_with_bytes(
        pid(),
        &receipt_settlement::instruction::CreateJob {
            job_id,
            terms_hash,
            budget,
            section_count,
            verifier,
            deadline_slots,
        }
        .data(),
        receipt_settlement::accounts::CreateJob {
            creator: *creator,
            job,
            vault: vault_pda(&job),
            system_program: system_program::ID,
        }
        .to_account_metas(None),
    )
}

const TERMS: [u8; 32] = [0x7E; 32];

fn setup_with(mode: Mode, budget: u64, sections: u16, deadline: fn(&Backend) -> u64) -> Env {
    let mut b = Backend::new(mode);
    let creator = Keypair::new();
    let verifier = Keypair::new();
    let node = Keypair::new();
    b.airdrop(&creator.pubkey(), 100 * SOL);
    b.airdrop(&node.pubkey(), 10 * SOL);
    let job_id = 42u64;
    let job = job_pda(&creator.pubkey(), job_id);
    let ix = create_job_ix(
        &creator.pubkey(),
        job_id,
        TERMS,
        budget,
        sections,
        verifier.pubkey(),
        deadline(&b),
    );
    expect_ok(b.send(&[ix], &creator, &[&creator]), "create_job");
    let j: Job = read(&b, &job);
    Env {
        b,
        creator,
        verifier,
        node,
        job_id,
        terms: TERMS,
        created_slot: j.created_slot,
        deadline_slot: j.deadline_slot,
        job,
        vault: vault_pda(&job),
        budget,
    }
}

fn setup(mode: Mode) -> Env {
    setup_with(mode, 10 * SOL, 4, Backend::long)
}

fn setup_short(mode: Mode) -> Env {
    setup_with(mode, 10 * SOL, 4, Backend::short)
}

fn warp(env: &mut Env, slot: u64) {
    env.b.warp_to_slot(slot);
}

fn past_deadline(env: &mut Env) {
    let s = env.deadline_slot + 1;
    warp(env, s);
}

// ------------------------------------------------------- instruction helpers

fn consent_of(node: &Pubkey) -> [u8; 32] {
    let mut c = [0xC0u8; 32];
    c[..8].copy_from_slice(&node.to_bytes()[..8]);
    c
}
const LIMITS: [u8; 32] = [0x11; 32];

fn accept_ix(
    env: &Env,
    node: &Pubkey,
    section: u16,
    terms_hash: [u8; 32],
    consent_hash: [u8; 32],
    limits_hash: [u8; 32],
) -> Instruction {
    Instruction::new_with_bytes(
        pid(),
        &receipt_settlement::instruction::AcceptJob {
            section,
            terms_hash,
            consent_hash,
            limits_hash,
        }
        .data(),
        receipt_settlement::accounts::AcceptJob {
            node: *node,
            job: env.job,
            assignment: assignment_pda(&env.job, section),
            system_program: system_program::ID,
        }
        .to_account_metas(None),
    )
}

fn accept_as(env: &mut Env, node: &Keypair, section: u16) -> TransactionResult {
    let ix = accept_ix(
        env,
        &node.pubkey(),
        section,
        env.terms,
        consent_of(&node.pubkey()),
        LIMITS,
    );
    env.b.send(&[ix], node, &[node])
}

fn accept(env: &mut Env, section: u16) -> TransactionResult {
    let n = env.node.insecure_clone();
    accept_as(env, &n, section)
}

fn proof(n: u8, quote: u64, charge: u64) -> ProofArgs {
    ProofArgs {
        receipt_id: [n; 32],
        input_hash: [n.wrapping_add(100); 32],
        output_hash: [n.wrapping_add(150); 32],
        model_hash: [7; 32],
        quoted_price: quote,
        actual_charge: charge,
    }
}

fn submit_ix(env: &Env, node: &Pubkey, section: u16, a: &ProofArgs) -> Instruction {
    Instruction::new_with_bytes(
        pid(),
        &receipt_settlement::instruction::SubmitProof { section, args: *a }.data(),
        receipt_settlement::accounts::SubmitProof {
            node: *node,
            job: env.job,
            assignment: assignment_pda(&env.job, section),
            output_marker: output_pda(&env.job, &a.output_hash),
            system_program: system_program::ID,
        }
        .to_account_metas(None),
    )
}

fn submit_as(env: &mut Env, node: &Keypair, section: u16, a: &ProofArgs) -> TransactionResult {
    let ix = submit_ix(env, &node.pubkey(), section, a);
    env.b.send(&[ix], node, &[node])
}

fn submit(env: &mut Env, section: u16, a: &ProofArgs) -> TransactionResult {
    let n = env.node.insecure_clone();
    submit_as(env, &n, section, a)
}

/// Independent re-implementation of the canonical digest (spec §5).
fn digest(
    job: &Pubkey,
    job_id: u64,
    created_slot: u64,
    terms: &[u8; 32],
    section: u16,
    node: &Pubkey,
    consent: &[u8; 32],
    limits: &[u8; 32],
    a: &ProofArgs,
) -> [u8; 32] {
    let mut h = Sha256::new();
    h.update(b"EdgeORE/receipt-settlement/v3");
    h.update(pid().as_ref());
    h.update(job.as_ref());
    h.update(job_id.to_le_bytes());
    h.update(created_slot.to_le_bytes());
    h.update(terms);
    h.update(section.to_le_bytes());
    h.update(node.as_ref());
    h.update(consent);
    h.update(limits);
    h.update(a.receipt_id);
    h.update(a.input_hash);
    h.update(a.output_hash);
    h.update(a.model_hash);
    h.update(a.quoted_price.to_le_bytes());
    h.update(a.actual_charge.to_le_bytes());
    h.finalize().into()
}

fn digest_for(env: &Env, section: u16, node: &Pubkey, a: &ProofArgs) -> [u8; 32] {
    digest(
        &env.job,
        env.job_id,
        env.created_slot,
        &env.terms,
        section,
        node,
        &consent_of(node),
        &LIMITS,
        a,
    )
}

/// Standard single-signature Ed25519 native-program instruction with all
/// offsets pointing into its own data (instruction index u16::MAX).
fn ed25519_ix_raw(pubkey: &[u8], sig: &[u8], msg: &[u8], ix_index: u16) -> Instruction {
    let pk_off: u16 = 16;
    let sig_off: u16 = pk_off + 32;
    let msg_off: u16 = sig_off + 64;
    let mut d = vec![1u8, 0u8];
    for v in [
        sig_off,
        ix_index,
        pk_off,
        ix_index,
        msg_off,
        msg.len() as u16,
        ix_index,
    ] {
        d.extend_from_slice(&v.to_le_bytes());
    }
    d.extend_from_slice(pubkey);
    d.extend_from_slice(sig);
    d.extend_from_slice(msg);
    Instruction {
        program_id: ED25519_ID,
        accounts: vec![],
        data: d,
    }
}

fn ed25519_ix(signer: &Keypair, msg: &[u8]) -> Instruction {
    let sig = signer.sign_message(msg);
    ed25519_ix_raw(signer.pubkey().as_ref(), sig.as_ref(), msg, u16::MAX)
}

fn settle_ix(env: &Env, settler: &Pubkey, payout: &Pubkey, section: u16) -> Instruction {
    Instruction::new_with_bytes(
        pid(),
        &receipt_settlement::instruction::VerifyAndSettle { section }.data(),
        receipt_settlement::accounts::VerifyAndSettle {
            settler: *settler,
            node: *payout,
            job: env.job,
            vault: env.vault,
            assignment: assignment_pda(&env.job, section),
            instructions_sysvar: IX_SYSVAR,
        }
        .to_account_metas(None),
    )
}

/// [Ed25519(signer over digest), verify_and_settle] sent and paid by `settler`.
fn settle_full(
    env: &mut Env,
    settler: &Keypair,
    signer: &Keypair,
    signed_digest: &[u8; 32],
    payout: &Pubkey,
    section: u16,
) -> TransactionResult {
    let ixs = [
        ed25519_ix(signer, signed_digest),
        settle_ix(env, &settler.pubkey(), payout, section),
    ];
    env.b.send(&ixs, settler, &[settler])
}

/// The normal case: the verifier signs the correct digest for env.node; the
/// node itself submits and is paid.
fn settle(env: &mut Env, section: u16, a: &ProofArgs) -> TransactionResult {
    let node = env.node.insecure_clone();
    let verifier = env.verifier.insecure_clone();
    let d = digest_for(env, section, &node.pubkey(), a);
    settle_full(env, &node, &verifier, &d, &node.pubkey(), section)
}

fn refund_ix(env: &Env, cranker: &Pubkey, creator_dest: &Pubkey) -> Instruction {
    Instruction::new_with_bytes(
        pid(),
        &receipt_settlement::instruction::RefundAfterDeadline {}.data(),
        receipt_settlement::accounts::RefundAfterDeadline {
            cranker: *cranker,
            creator: *creator_dest,
            job: env.job,
            vault: env.vault,
        }
        .to_account_metas(None),
    )
}

fn refund_by(env: &mut Env, cranker: &Keypair) -> TransactionResult {
    let ix = refund_ix(env, &cranker.pubkey(), &env.creator.pubkey());
    env.b.send(&[ix], cranker, &[cranker])
}

fn rotate_ix(env: &Env, signer: &Pubkey, new_verifier: Pubkey) -> Instruction {
    Instruction::new_with_bytes(
        pid(),
        &receipt_settlement::instruction::RotateVerifier { new_verifier }.data(),
        receipt_settlement::accounts::RotateVerifier {
            creator: *signer,
            job: env.job,
        }
        .to_account_metas(None),
    )
}

fn close_assignment_ix(
    env: &Env,
    closer: &Pubkey,
    node: &Pubkey,
    section: u16,
    output_hash: Option<[u8; 32]>,
) -> Instruction {
    Instruction::new_with_bytes(
        pid(),
        &receipt_settlement::instruction::CloseAssignment { section }.data(),
        receipt_settlement::accounts::CloseAssignment {
            closer: *closer,
            node: *node,
            job: env.job,
            assignment: assignment_pda(&env.job, section),
            output_marker: output_hash.map(|h| output_pda(&env.job, &h)),
        }
        .to_account_metas(None),
    )
}

fn close_job_ix(env: &Env, creator: &Pubkey) -> Instruction {
    Instruction::new_with_bytes(
        pid(),
        &receipt_settlement::instruction::CloseJob {}.data(),
        receipt_settlement::accounts::CloseJob {
            creator: *creator,
            job: env.job,
        }
        .to_account_metas(None),
    )
}

fn as_signer(env: &mut Env, who: &Keypair, ix: Instruction) -> TransactionResult {
    env.b.send(&[ix], who, &[who])
}

/// Snapshot (lamports, data) of a set of accounts.
fn snapshot(env: &Env, keys: &[Pubkey]) -> Vec<(u64, Vec<u8>)> {
    keys.iter()
        .map(|k| {
            env.b
                .get_account(k)
                .map(|a| (a.lamports, a.data))
                .unwrap_or((0, vec![]))
        })
        .collect()
}

/// Assert a failed transaction moved zero lamports (and changed no data) on
/// every listed account, and that the fee payer lost exactly the network fee
/// the runtime charged for the failed transaction (not a program transfer).
/// Only used for [Ed25519, verify_and_settle] transactions.
fn assert_no_movement(
    env: &Env,
    keys: &[Pubkey],
    before: &[(u64, Vec<u8>)],
    fee_payer: &Pubkey,
    payer_before: u64,
    failed: &Failed,
) {
    let after = snapshot(env, keys);
    for ((k, b), a) in keys.iter().zip(before).zip(&after) {
        assert_eq!(b.0, a.0, "lamports moved on {k}");
        assert_eq!(b.1, a.1, "data changed on {k}");
    }
    let payer_after = lamports(env, fee_payer);
    assert_eq!(
        payer_before - payer_after,
        failed.fee_charged,
        "fee payer moved by more than the network fee"
    );
    if failed.landed {
        // 5_000 lamports per signature: the transaction signature plus the
        // one signature verified by the Ed25519 precompile instruction.
        assert_eq!(failed.fee_charged, 10_000, "network fee (2 signatures)");
    }
}

// ================================================================= scenarios

/// create_job -> accept_job -> submit_proof -> verify_and_settle (by a
/// third-party relayer) -> refund_after_deadline (by a stranger) ->
/// close_assignment -> close_job, with exact lamport accounting.
pub fn happy_path_create_accept_prove_settle_refund_close(mode: Mode) {
    let mut env = setup_short(mode);
    let relayer = funded(&mut env, 1);
    let stranger = funded(&mut env, 1);
    let a = proof(1, 2 * SOL, 3 * SOL / 2);

    expect_ok(accept(&mut env, 0), "accept");
    expect_ok(submit(&mut env, 0, &a), "submit");

    let node_before = lamports(&env, &env.node.pubkey());
    let vault_before = lamports(&env, &env.vault);
    let relayer_before = lamports(&env, &relayer.pubkey());
    let verifier = env.verifier.insecure_clone();
    let node = env.node.pubkey();
    let d = digest_for(&env, 0, &node, &a);
    let ok = expect_ok(
        settle_full(&mut env, &relayer, &verifier, &d, &node, 0),
        "settle",
    );
    assert_eq!(lamports(&env, &node) - node_before, a.actual_charge);
    assert_eq!(vault_before - lamports(&env, &env.vault), a.actual_charge);
    assert_eq!(relayer_before - lamports(&env, &relayer.pubkey()), ok.fee);

    let asg: Assignment = read(&env.b, &assignment_pda(&env.job, 0));
    assert!(asg.settled);
    assert_eq!(asg.settled_amount, a.actual_charge);
    assert!(asg.settled_slot >= asg.proof_slot && asg.settled_slot <= env.deadline_slot);
    let job: Job = read(&env.b, &env.job);
    assert_eq!(
        (job.paid, job.committed, job.settled_count, job.proof_count),
        (a.actual_charge, a.actual_charge, 1, 1)
    );

    // Refund after the deadline: anyone cranks; creator gets the vault.
    past_deadline(&mut env);
    let creator_before = lamports(&env, &env.creator.pubkey());
    let vault_now = lamports(&env, &env.vault);
    assert_eq!(
        vault_now,
        env.budget - a.actual_charge + rent(&env, Vault::INIT_SPACE)
    );
    let stranger_before = lamports(&env, &stranger.pubkey());
    let ok = expect_ok(refund_by(&mut env, &stranger), "refund");
    assert_eq!(
        lamports(&env, &env.creator.pubkey()) - creator_before,
        vault_now
    );
    assert_eq!(stranger_before - lamports(&env, &stranger.pubkey()), ok.fee);
    assert!(!exists(&env.b, &env.vault));
    assert_eq!(read::<Job>(&env.b, &env.job).status, JobStatus::Refunded);

    // Rent cleanup.
    let node_kp = env.node.insecure_clone();
    let node_before = lamports(&env, &node);
    let ix = close_assignment_ix(&env, &node, &node, 0, Some(a.output_hash));
    let ok = expect_ok(as_signer(&mut env, &node_kp, ix), "close_assignment");
    assert_eq!(
        lamports(&env, &node) + ok.fee - node_before,
        rent(&env, Assignment::INIT_SPACE) + rent(&env, OutputMarker::INIT_SPACE)
    );
    let creator = env.creator.insecure_clone();
    let creator_before = lamports(&env, &creator.pubkey());
    let ix = close_job_ix(&env, &creator.pubkey());
    let ok = expect_ok(as_signer(&mut env, &creator, ix), "close_job");
    assert_eq!(
        lamports(&env, &creator.pubkey()) + ok.fee - creator_before,
        rent(&env, Job::INIT_SPACE)
    );
    assert!(!exists(&env.b, &env.job));
}

/// terms_hash, verifier and deadline are bound at create_job, and the
/// deadline slot is computed from the on-chain Clock of that transaction.
pub fn create_job_binds_terms_verifier_and_clock_deadline(mode: Mode) {
    let mut b = Backend::new(mode);
    let creator = Keypair::new();
    let verifier = Keypair::new();
    b.airdrop(&creator.pubkey(), 20 * SOL);
    if b.is_svm() {
        b.warp_to_slot(5_000);
    }
    let before = b.slot();
    let terms = [0x5A; 32];
    let n = b.long();
    let ix = create_job_ix(&creator.pubkey(), 9, terms, SOL, 3, verifier.pubkey(), n);
    let ok = expect_ok(b.send(&[ix], &creator, &[&creator]), "create_job");
    let job: Job = read(&b, &job_pda(&creator.pubkey(), 9));
    assert_eq!(job.terms_hash, terms);
    assert_eq!(job.verifier, verifier.pubkey());
    assert_eq!(job.creator, creator.pubkey());
    assert_eq!((job.budget, job.section_count), (SOL, 3));
    assert_eq!(job.status, JobStatus::Open);
    if b.is_svm() {
        assert_eq!(job.created_slot, 5_000);
    } else {
        // Executed in the bank of the slot the transaction landed in.
        assert!(job.created_slot >= before);
        assert_eq!(job.created_slot, ok.slot);
    }
    assert_eq!(job.deadline_slot, job.created_slot + n);
}

pub fn create_job_rejects_invalid_parameters(mode: Mode) {
    let mut b = Backend::new(mode);
    let creator = Keypair::new();
    let v = Keypair::new().pubkey();
    b.airdrop(&creator.pubkey(), 20 * SOL);
    let cases: [([u8; 32], u64, u16, Pubkey, u64, SettlementError); 6] = [
        ([0; 32], SOL, 1, v, 10, SettlementError::InvalidHash),
        (TERMS, 0, 1, v, 10, SettlementError::InvalidBudget),
        (TERMS, SOL, 0, v, 10, SettlementError::InvalidSectionCount),
        (
            TERMS,
            SOL,
            1,
            Pubkey::default(),
            10,
            SettlementError::InvalidVerifier,
        ),
        (TERMS, SOL, 1, v, 0, SettlementError::InvalidDeadline),
        (
            TERMS,
            SOL,
            1,
            v,
            MAX_DEADLINE_SLOTS + 1,
            SettlementError::InvalidDeadline,
        ),
    ];
    for (i, (t, budget, sections, ver, dl, e)) in cases.into_iter().enumerate() {
        let ix = create_job_ix(&creator.pubkey(), i as u64, t, budget, sections, ver, dl);
        expect_custom(b.send(&[ix], &creator, &[&creator]), 0, code(e));
        assert!(!exists(&b, &job_pda(&creator.pubkey(), i as u64)));
    }
}

/// J-1: the old two-transaction "same slot" check, expressed as ONE
/// transaction. All instructions of a transaction execute in the same bank,
/// so they read the same Clock slot on LiteSVM and on a real validator.
/// The refund in the creation slot must fail, and atomicity rolls back the
/// job creation too.
pub fn create_and_refund_in_one_transaction_is_rejected(mode: Mode) {
    let mut b = Backend::new(mode);
    let creator = Keypair::new();
    let v = Keypair::new().pubkey();
    b.airdrop(&creator.pubkey(), 20 * SOL);
    let job = job_pda(&creator.pubkey(), 1);
    let create = create_job_ix(&creator.pubkey(), 1, TERMS, SOL, 1, v, 1);
    let refund = Instruction::new_with_bytes(
        pid(),
        &receipt_settlement::instruction::RefundAfterDeadline {}.data(),
        receipt_settlement::accounts::RefundAfterDeadline {
            cranker: creator.pubkey(),
            creator: creator.pubkey(),
            job,
            vault: vault_pda(&job),
        }
        .to_account_metas(None),
    );
    let close = Instruction::new_with_bytes(
        pid(),
        &receipt_settlement::instruction::CloseJob {}.data(),
        receipt_settlement::accounts::CloseJob {
            creator: creator.pubkey(),
            job,
        }
        .to_account_metas(None),
    );
    let before = b.get_account(&creator.pubkey()).unwrap().lamports;
    let f = expect_custom(
        b.send(&[create, refund, close], &creator, &[&creator]),
        1,
        code(SettlementError::DeadlineNotReached),
    );
    assert!(!exists(&b, &job));
    assert!(!exists(&b, &vault_pda(&job)));
    assert_eq!(
        before - b.get_account(&creator.pubkey()).unwrap().lamports,
        f.fee_charged
    );
}

/// accept_job records the node pubkey, consent_hash and limits_hash.
pub fn accept_records_node_consent_and_limits(mode: Mode) {
    let mut env = setup(mode);
    let ok = expect_ok(accept(&mut env, 2), "accept");
    let a: Assignment = read(&env.b, &assignment_pda(&env.job, 2));
    assert_eq!(a.job, env.job);
    assert_eq!(a.section, 2);
    assert_eq!(a.node, env.node.pubkey());
    assert_eq!(a.consent_hash, consent_of(&env.node.pubkey()));
    assert_eq!(a.limits_hash, LIMITS);
    assert_eq!(a.accepted_slot, ok.slot);
    assert!(!a.proof_submitted && !a.settled);
    let j: Job = read(&env.b, &env.job);
    assert_eq!((j.accepted_count, j.open_accounts), (1, 1));
}

pub fn accept_rejects_wrong_terms_zero_hashes_range_and_duplicate(mode: Mode) {
    let mut env = setup(mode);
    let node = env.node.insecure_clone();
    let n = node.pubkey();
    let c = consent_of(&n);
    let cases = [
        (0u16, [0x99; 32], c, LIMITS, SettlementError::TermsMismatch),
        (0, TERMS, [0; 32], LIMITS, SettlementError::InvalidHash),
        (0, TERMS, c, [0; 32], SettlementError::InvalidHash),
        (4, TERMS, c, LIMITS, SettlementError::SectionOutOfRange),
    ];
    for (section, t, cons, lim, e) in cases {
        let ix = accept_ix(&env, &n, section, t, cons, lim);
        expect_custom(as_signer(&mut env, &node, ix), 0, code(e));
        assert!(!exists(&env.b, &assignment_pda(&env.job, section)));
    }
    expect_ok(accept(&mut env, 0), "accept");
    // A second node cannot take an already-accepted section (init fails).
    let other = funded(&mut env, 1);
    let f = accept_as(&mut env, &other, 0).expect_err("duplicate accept");
    assert!(
        matches!(
            f.err,
            TransactionError::InstructionError(0, InstructionError::Custom(0))
        ),
        "{f:?}"
    );
    let a: Assignment = read(&env.b, &assignment_pda(&env.job, 0));
    assert_eq!(a.node, env.node.pubkey());
}

/// Deadline is inclusive: actions at deadline_slot succeed (LiteSVM only can
/// land on that exact slot), actions after it fail on both backends.
pub fn deadline_from_clock_is_inclusive_then_closes(mode: Mode) {
    let mut env = setup_short(mode);
    if env.b.is_svm() {
        let d = env.deadline_slot;
        warp(&mut env, d);
        expect_ok(accept(&mut env, 0), "accept at deadline");
    } else {
        expect_ok(accept(&mut env, 0), "accept before deadline");
    }
    let a = proof(1, SOL, SOL);
    past_deadline(&mut env);
    expect_custom(
        accept(&mut env, 1),
        0,
        code(SettlementError::DeadlinePassed),
    );
    expect_custom(
        submit(&mut env, 0, &a),
        0,
        code(SettlementError::DeadlinePassed),
    );
    let f = expect_custom(
        settle(&mut env, 0, &a),
        1,
        code(SettlementError::DeadlinePassed),
    );
    assert!(f.slot == 0 || f.slot > env.deadline_slot);
}

pub fn submit_proof_only_by_accepted_node(mode: Mode) {
    let mut env = setup(mode);
    expect_ok(accept(&mut env, 0), "accept");
    let intruder = funded(&mut env, 1);
    let a = proof(1, SOL, SOL);
    expect_custom(submit_as(&mut env, &intruder, 0, &a), 0, CONSTRAINT_HAS_ONE);
    // Unaccepted section: no assignment account exists.
    expect_custom(submit(&mut env, 1, &a), 0, ACCOUNT_NOT_INITIALIZED);
    expect_ok(submit(&mut env, 0, &a), "submit by accepted node");
}

pub fn submit_proof_rejects_bad_charge_second_proof_and_duplicate_output(mode: Mode) {
    let mut env = setup(mode);
    expect_ok(accept(&mut env, 0), "accept 0");
    expect_ok(accept(&mut env, 1), "accept 1");
    expect_custom(
        submit(&mut env, 0, &proof(1, SOL, SOL + 1)),
        0,
        code(SettlementError::ChargeExceedsQuote),
    );
    expect_custom(
        submit(&mut env, 0, &proof(1, SOL, 0)),
        0,
        code(SettlementError::ZeroCharge),
    );
    let a = proof(1, SOL, SOL);
    expect_ok(submit(&mut env, 0, &a), "submit");
    let mut again = proof(2, SOL, SOL);
    again.output_hash = [0xEE; 32];
    expect_custom(
        submit(&mut env, 0, &again),
        0,
        code(SettlementError::ProofAlreadySubmitted),
    );
    // Same output hash on another section: output marker init fails.
    let mut dup = proof(3, SOL, SOL);
    dup.output_hash = a.output_hash;
    let f = submit(&mut env, 1, &dup).expect_err("duplicate output");
    assert!(
        matches!(
            f.err,
            TransactionError::InstructionError(0, InstructionError::Custom(0))
        ),
        "{f:?}"
    );
    let j: Job = read(&env.b, &env.job);
    assert_eq!((j.proof_count, j.committed), (1, SOL));
}

pub fn submit_proof_over_budget_is_rejected(mode: Mode) {
    let mut env = setup_with(mode, 3 * SOL, 2, Backend::long);
    expect_ok(accept(&mut env, 0), "accept 0");
    expect_ok(accept(&mut env, 1), "accept 1");
    expect_ok(submit(&mut env, 0, &proof(1, 2 * SOL, 2 * SOL)), "submit 0");
    expect_custom(
        submit(&mut env, 1, &proof(2, 2 * SOL, 2 * SOL)),
        0,
        code(SettlementError::OverBudget),
    );
    expect_ok(
        submit(&mut env, 1, &proof(2, 2 * SOL, SOL)),
        "submit 1 within budget",
    );
    assert_eq!(read::<Job>(&env.b, &env.job).committed, 3 * SOL);
}

/// Settlement pays only the node recorded at accept_job. A substituted payout
/// account fails, even with a valid verifier signature, and moves nothing.
pub fn settle_pays_recorded_node_and_substituted_payout_fails(mode: Mode) {
    let mut env = setup(mode);
    let a = proof(1, SOL, SOL);
    expect_ok(accept(&mut env, 0), "accept");
    expect_ok(submit(&mut env, 0, &a), "submit");
    let thief = funded(&mut env, 1);
    let verifier = env.verifier.insecure_clone();
    let node = env.node.pubkey();
    let d = digest_for(&env, 0, &node, &a);

    let keys = [
        thief.pubkey(),
        node,
        env.vault,
        env.job,
        assignment_pda(&env.job, 0),
    ];
    let before = snapshot(&env, &keys);
    let thief_before = lamports(&env, &thief.pubkey());
    // The thief signs, pays the fee, and names itself as payout.
    let f = expect_custom(
        settle_full(&mut env, &thief, &verifier, &d, &thief.pubkey(), 0),
        1,
        code(SettlementError::PayoutMismatch),
    );
    // Thief is the fee payer: only the network fee left its balance.
    assert_eq!(
        thief_before - lamports(&env, &thief.pubkey()),
        f.fee_charged
    );
    assert_no_movement(
        &env,
        &keys[1..],
        &before[1..],
        &thief.pubkey(),
        thief_before,
        &f,
    );

    // Signing a digest for the thief as node does not help either: the
    // payout constraint fails first, and the digest would not match.
    let d_thief = digest_for(&env, 0, &thief.pubkey(), &a);
    expect_custom(
        settle_full(&mut env, &thief, &verifier, &d_thief, &thief.pubkey(), 0),
        1,
        code(SettlementError::PayoutMismatch),
    );

    // The real node, paid via a third-party settler (the thief, even).
    let node_before = lamports(&env, &node);
    expect_ok(
        settle_full(&mut env, &thief, &verifier, &d, &node, 0),
        "settle to recorded node",
    );
    assert_eq!(lamports(&env, &node) - node_before, a.actual_charge);
}

/// A second verify_and_settle for the same assignment fails on the settle-once
/// flag and moves zero lamports on every account in the transaction.
pub fn duplicate_settle_moves_zero_lamports_on_every_account(mode: Mode) {
    let mut env = setup(mode);
    let a = proof(1, SOL, SOL);
    expect_ok(accept(&mut env, 0), "accept");
    expect_ok(submit(&mut env, 0, &a), "submit");
    expect_ok(settle(&mut env, 0, &a), "first settle");
    assert!(read::<Assignment>(&env.b, &assignment_pda(&env.job, 0)).settled);

    // Separate fee payer so the node's balance must be exactly unchanged.
    let settler = funded(&mut env, 1);
    let verifier = env.verifier.insecure_clone();
    let node = env.node.pubkey();
    let d = digest_for(&env, 0, &node, &a);
    let keys = [
        node,
        env.job,
        env.vault,
        assignment_pda(&env.job, 0),
        output_pda(&env.job, &a.output_hash),
        env.creator.pubkey(),
        env.verifier.pubkey(),
        IX_SYSVAR,
        ED25519_ID,
    ];
    let before = snapshot(&env, &keys);
    let payer_before = lamports(&env, &settler.pubkey());
    let f = expect_custom(
        settle_full(&mut env, &settler, &verifier, &d, &node, 0),
        1,
        code(SettlementError::AlreadySettled),
    );
    assert_no_movement(&env, &keys, &before, &settler.pubkey(), payer_before, &f);
    let j: Job = read(&env.b, &env.job);
    assert_eq!((j.paid, j.settled_count), (SOL, 1));
}

pub fn settle_without_proof_is_rejected(mode: Mode) {
    let mut env = setup(mode);
    expect_ok(accept(&mut env, 0), "accept");
    let a = proof(1, SOL, SOL);
    expect_custom(settle(&mut env, 0, &a), 1, code(SettlementError::NoProof));
}

pub fn settle_bad_signature_is_rejected_by_ed25519_program(mode: Mode) {
    let mut env = setup(mode);
    let a = proof(1, SOL, SOL);
    expect_ok(accept(&mut env, 0), "accept");
    expect_ok(submit(&mut env, 0, &a), "submit");
    let node = env.node.insecure_clone();
    let d = digest_for(&env, 0, &node.pubkey(), &a);
    let mut sig = env.verifier.sign_message(&d).as_ref().to_vec();
    sig[0] ^= 0xFF;
    let ixs = [
        ed25519_ix_raw(env.verifier.pubkey().as_ref(), &sig, &d, u16::MAX),
        settle_ix(&env, &node.pubkey(), &node.pubkey(), 0),
    ];
    let f = env
        .b
        .send(&ixs, &node, &[&node])
        .expect_err("bad signature");
    // Ed25519 program error: custom 2 = InvalidSignature, at instruction 0.
    assert!(
        matches!(
            f.err,
            TransactionError::InstructionError(0, InstructionError::Custom(2))
        ),
        "{f:?}"
    );
    assert!(!read::<Assignment>(&env.b, &assignment_pda(&env.job, 0)).settled);
}

pub fn settle_missing_ed25519_instruction_is_rejected(mode: Mode) {
    let mut env = setup(mode);
    let a = proof(1, SOL, SOL);
    expect_ok(accept(&mut env, 0), "accept");
    expect_ok(submit(&mut env, 0, &a), "submit");
    let node = env.node.insecure_clone();
    let ix = settle_ix(&env, &node.pubkey(), &node.pubkey(), 0);
    expect_custom(
        as_signer(&mut env, &node, ix),
        0,
        code(SettlementError::MissingEd25519Instruction),
    );
}

/// Offsets pointing at another instruction's data (explicit index 1 = the
/// settle instruction itself) are rejected.
pub fn settle_ed25519_offsets_into_other_instruction_are_rejected(mode: Mode) {
    let mut env = setup(mode);
    let a = proof(1, SOL, SOL);
    expect_ok(accept(&mut env, 0), "accept");
    expect_ok(submit(&mut env, 0, &a), "submit");
    let node = env.node.insecure_clone();
    let d = digest_for(&env, 0, &node.pubkey(), &a);
    let sig = env.verifier.sign_message(&d);
    // Index 0 (= this Ed25519 instruction, by explicit index) still verifies
    // in the Ed25519 program, but the settlement program requires u16::MAX.
    let ixs = [
        ed25519_ix_raw(env.verifier.pubkey().as_ref(), sig.as_ref(), &d, 0),
        settle_ix(&env, &node.pubkey(), &node.pubkey(), 0),
    ];
    expect_custom(
        env.b.send(&ixs, &node, &[&node]),
        1,
        code(SettlementError::InvalidEd25519Instruction),
    );
}

pub fn settle_wrong_verifier_is_rejected(mode: Mode) {
    let mut env = setup(mode);
    let a = proof(1, SOL, SOL);
    expect_ok(accept(&mut env, 0), "accept");
    expect_ok(submit(&mut env, 0, &a), "submit");
    let node = env.node.insecure_clone();
    let impostor = Keypair::new();
    let d = digest_for(&env, 0, &node.pubkey(), &a);
    expect_custom(
        settle_full(&mut env, &node, &impostor, &d, &node.pubkey(), 0),
        1,
        code(SettlementError::VerifierMismatch),
    );
}

/// The verifier's signature must cover exactly what is stored on-chain: a
/// signature over any other value of any bound field is rejected.
pub fn settle_signature_over_different_values_is_rejected(mode: Mode) {
    let mut env = setup(mode);
    let a = proof(1, 2 * SOL, SOL);
    expect_ok(accept(&mut env, 0), "accept");
    expect_ok(submit(&mut env, 0, &a), "submit");
    let node = env.node.insecure_clone();
    let n = node.pubkey();
    let verifier = env.verifier.insecure_clone();
    let c = consent_of(&n);
    let mut variants: Vec<[u8; 32]> = vec![];
    let base =
        |job: &Pubkey, id, cs, t: &[u8; 32], s, c: &[u8; 32], l: &[u8; 32], p: &ProofArgs| {
            digest(job, id, cs, t, s, &n, c, l, p)
        };
    let e = &env;
    variants.push(base(
        &Keypair::new().pubkey(),
        e.job_id,
        e.created_slot,
        &e.terms,
        0,
        &c,
        &LIMITS,
        &a,
    ));
    variants.push(base(
        &e.job,
        e.job_id + 1,
        e.created_slot,
        &e.terms,
        0,
        &c,
        &LIMITS,
        &a,
    ));
    variants.push(base(
        &e.job,
        e.job_id,
        e.created_slot + 1,
        &e.terms,
        0,
        &c,
        &LIMITS,
        &a,
    ));
    variants.push(base(
        &e.job,
        e.job_id,
        e.created_slot,
        &[0x01; 32],
        0,
        &c,
        &LIMITS,
        &a,
    ));
    variants.push(base(
        &e.job,
        e.job_id,
        e.created_slot,
        &e.terms,
        1,
        &c,
        &LIMITS,
        &a,
    ));
    variants.push(base(
        &e.job,
        e.job_id,
        e.created_slot,
        &e.terms,
        0,
        &[0x02; 32],
        &LIMITS,
        &a,
    ));
    variants.push(base(
        &e.job,
        e.job_id,
        e.created_slot,
        &e.terms,
        0,
        &c,
        &[0x03; 32],
        &a,
    ));
    for f in 0..6 {
        let mut p = a;
        match f {
            0 => p.receipt_id = [0xAB; 32],
            1 => p.input_hash = [0xAB; 32],
            2 => p.output_hash = [0xAB; 32],
            3 => p.model_hash = [0xAB; 32],
            4 => p.quoted_price += 1,
            _ => p.actual_charge = 2 * SOL,
        }
        variants.push(base(
            &e.job,
            e.job_id,
            e.created_slot,
            &e.terms,
            0,
            &c,
            &LIMITS,
            &p,
        ));
    }
    // Wrong node in the digest (payout stays the recorded node).
    variants.push(digest(
        &e.job,
        e.job_id,
        e.created_slot,
        &e.terms,
        0,
        &Keypair::new().pubkey(),
        &c,
        &LIMITS,
        &a,
    ));
    let vault_before = lamports(&env, &env.vault);
    for d in &variants {
        expect_custom(
            settle_full(&mut env, &node, &verifier, d, &n, 0),
            1,
            code(SettlementError::DigestMismatch),
        );
    }
    assert_eq!(lamports(&env, &env.vault), vault_before);
    expect_ok(settle(&mut env, 0, &a), "correct digest settles");
}

/// refund_after_deadline: rejected before the deadline; afterwards any signer
/// can crank it without the creator's signature, the cranker pays only the
/// fee, the vault goes to the creator, and a substituted destination fails.
pub fn refund_crank_is_permissionless_after_deadline_only(mode: Mode) {
    let mut env = setup_short(mode);
    let cranker = funded(&mut env, 1);
    expect_custom(
        refund_by(&mut env, &cranker),
        0,
        code(SettlementError::DeadlineNotReached),
    );
    past_deadline(&mut env);
    // Substituted refund destination.
    let ix = refund_ix(&env, &cranker.pubkey(), &cranker.pubkey());
    let vault_before = lamports(&env, &env.vault);
    expect_custom(
        as_signer(&mut env, &cranker, ix),
        0,
        code(SettlementError::CreatorMismatch),
    );
    assert_eq!(lamports(&env, &env.vault), vault_before);

    let creator_before = lamports(&env, &env.creator.pubkey());
    let cranker_before = lamports(&env, &cranker.pubkey());
    let ok = expect_ok(refund_by(&mut env, &cranker), "refund crank");
    assert!(ok.slot == 0 || ok.slot > env.deadline_slot || env.b.is_svm());
    assert_eq!(
        lamports(&env, &env.creator.pubkey()) - creator_before,
        env.budget + rent(&env, Vault::INIT_SPACE)
    );
    assert_eq!(cranker_before - lamports(&env, &cranker.pubkey()), ok.fee);
    assert!(!exists(&env.b, &env.vault));
    // Second crank: the vault is gone, the job is no longer Open.
    let f = refund_by(&mut env, &cranker).expect_err("second refund");
    assert!(
        matches!(f.err, TransactionError::InstructionError(0, _)),
        "{f:?}"
    );
}

/// A submitted but unsettled proof is refunded to the creator at the deadline
/// and can no longer be settled.
pub fn refund_returns_unsettled_reservations(mode: Mode) {
    let mut env = setup_short(mode);
    let a = proof(1, SOL, SOL);
    let b = proof(2, SOL, SOL / 2);
    expect_ok(accept(&mut env, 0), "accept 0");
    expect_ok(accept(&mut env, 1), "accept 1");
    expect_ok(submit(&mut env, 0, &a), "submit 0");
    expect_ok(submit(&mut env, 1, &b), "submit 1");
    expect_ok(settle(&mut env, 0, &a), "settle 0");
    past_deadline(&mut env);
    let creator_before = lamports(&env, &env.creator.pubkey());
    let cranker = funded(&mut env, 1);
    expect_ok(refund_by(&mut env, &cranker), "refund");
    assert_eq!(
        lamports(&env, &env.creator.pubkey()) - creator_before,
        env.budget - a.actual_charge + rent(&env, Vault::INIT_SPACE)
    );
    let f = settle(&mut env, 1, &b).expect_err("settle after refund");
    assert!(
        matches!(f.err, TransactionError::InstructionError(1, _)),
        "{f:?}"
    );
    assert!(!read::<Assignment>(&env.b, &assignment_pda(&env.job, 1)).settled);
}

pub fn rotate_verifier_allowed_before_accept_and_locked_after(mode: Mode) {
    let mut env = setup(mode);
    let creator = env.creator.insecure_clone();
    let new_v = Keypair::new();
    let ix = rotate_ix(&env, &creator.pubkey(), new_v.pubkey());
    expect_ok(as_signer(&mut env, &creator, ix), "rotate before accept");
    assert_eq!(read::<Job>(&env.b, &env.job).verifier, new_v.pubkey());
    let old_v = std::mem::replace(&mut env.verifier, new_v);

    expect_ok(accept(&mut env, 0), "accept");
    let ix = rotate_ix(&env, &creator.pubkey(), Keypair::new().pubkey());
    expect_custom(
        as_signer(&mut env, &creator, ix),
        0,
        code(SettlementError::VerifierLocked),
    );
    let a = proof(1, SOL, SOL);
    expect_ok(submit(&mut env, 0, &a), "submit");
    let node = env.node.insecure_clone();
    let d = digest_for(&env, 0, &node.pubkey(), &a);
    expect_custom(
        settle_full(&mut env, &node, &old_v, &d, &node.pubkey(), 0),
        1,
        code(SettlementError::VerifierMismatch),
    );
    expect_ok(settle(&mut env, 0, &a), "settle with new verifier");
}

pub fn rotate_verifier_rejects_non_creator_default_and_same(mode: Mode) {
    let mut env = setup(mode);
    let creator = env.creator.insecure_clone();
    let stranger = funded(&mut env, 1);
    // has_one = creator plus creator-derived seeds: a stranger fails on seeds.
    let ix = rotate_ix(&env, &stranger.pubkey(), Keypair::new().pubkey());
    let f = as_signer(&mut env, &stranger, ix).expect_err("stranger rotate");
    assert!(
        matches!(
            f.err,
            TransactionError::InstructionError(0, InstructionError::Custom(_))
        ),
        "{f:?}"
    );
    let ix = rotate_ix(&env, &creator.pubkey(), Pubkey::default());
    expect_custom(
        as_signer(&mut env, &creator, ix),
        0,
        code(SettlementError::InvalidVerifier),
    );
    let ix = rotate_ix(&env, &creator.pubkey(), env.verifier.pubkey());
    expect_custom(
        as_signer(&mut env, &creator, ix),
        0,
        code(SettlementError::SameVerifier),
    );
}

/// Assignment and output-marker rent goes back to the node, only after the
/// refund; the creator may clean up on the node's behalf; strangers may not.
pub fn close_assignment_only_after_refund_rent_to_node(mode: Mode) {
    let mut env = setup_short(mode);
    let a = proof(1, SOL, SOL);
    expect_ok(accept(&mut env, 0), "accept 0");
    expect_ok(accept(&mut env, 1), "accept 1");
    expect_ok(submit(&mut env, 0, &a), "submit 0");
    let node = env.node.insecure_clone();
    let n = node.pubkey();
    let creator = env.creator.insecure_clone();
    let ix = close_assignment_ix(&env, &n, &n, 0, Some(a.output_hash));
    expect_custom(
        as_signer(&mut env, &node, ix),
        0,
        code(SettlementError::JobStillOpen),
    );

    past_deadline(&mut env);
    let cranker = funded(&mut env, 1);
    expect_ok(refund_by(&mut env, &cranker), "refund");

    let stranger = funded(&mut env, 1);
    let ix = close_assignment_ix(&env, &stranger.pubkey(), &n, 0, Some(a.output_hash));
    expect_custom(
        as_signer(&mut env, &stranger, ix),
        0,
        code(SettlementError::Unauthorized),
    );
    // Rent must go to the recorded node.
    let ix = close_assignment_ix(
        &env,
        &creator.pubkey(),
        &stranger.pubkey(),
        0,
        Some(a.output_hash),
    );
    expect_custom(
        as_signer(&mut env, &creator, ix),
        0,
        code(SettlementError::PayoutMismatch),
    );
    // Proof submitted: the output marker must be passed.
    let ix = close_assignment_ix(&env, &n, &n, 0, None);
    expect_custom(
        as_signer(&mut env, &node, ix),
        0,
        code(SettlementError::OutputMarkerMismatch),
    );

    // Creator cleans up section 0 for the node.
    let node_before = lamports(&env, &n);
    let ix = close_assignment_ix(&env, &creator.pubkey(), &n, 0, Some(a.output_hash));
    expect_ok(as_signer(&mut env, &creator, ix), "creator closes for node");
    assert_eq!(
        lamports(&env, &n) - node_before,
        rent(&env, Assignment::INIT_SPACE) + rent(&env, OutputMarker::INIT_SPACE)
    );
    assert!(!exists(&env.b, &output_pda(&env.job, &a.output_hash)));

    // Node closes section 1 (no proof, no marker).
    let node_before = lamports(&env, &n);
    let ix = close_assignment_ix(&env, &n, &n, 1, None);
    let ok = expect_ok(as_signer(&mut env, &node, ix), "node closes own");
    assert_eq!(
        lamports(&env, &n) + ok.fee - node_before,
        rent(&env, Assignment::INIT_SPACE)
    );
    assert_eq!(read::<Job>(&env.b, &env.job).open_accounts, 0);
}

pub fn close_job_requires_refund_and_no_open_accounts(mode: Mode) {
    let mut env = setup_short(mode);
    let creator = env.creator.insecure_clone();
    expect_ok(accept(&mut env, 0), "accept");
    let ix = close_job_ix(&env, &creator.pubkey());
    expect_custom(
        as_signer(&mut env, &creator, ix),
        0,
        code(SettlementError::JobStillOpen),
    );
    past_deadline(&mut env);
    let cranker = funded(&mut env, 1);
    expect_ok(refund_by(&mut env, &cranker), "refund");
    let ix = close_job_ix(&env, &creator.pubkey());
    expect_custom(
        as_signer(&mut env, &creator, ix),
        0,
        code(SettlementError::OpenAccountsRemain),
    );
    let n = env.node.pubkey();
    let node = env.node.insecure_clone();
    let ix = close_assignment_ix(&env, &n, &n, 0, None);
    expect_ok(as_signer(&mut env, &node, ix), "close assignment");
    let ix = close_job_ix(&env, &creator.pubkey());
    expect_ok(as_signer(&mut env, &creator, ix), "close job");
    assert!(!exists(&env.b, &env.job));
}

/// A job re-created at the same address after close_job gets a later
/// created_slot, so a signature over the old job's digest is rejected.
pub fn recreated_job_rejects_replay_of_old_signature(mode: Mode) {
    let mut env = setup_short(mode);
    let a = proof(1, SOL, SOL);
    expect_ok(accept(&mut env, 0), "accept");
    expect_ok(submit(&mut env, 0, &a), "submit");
    let node = env.node.insecure_clone();
    let n = node.pubkey();
    let old_digest = digest_for(&env, 0, &n, &a);
    let old_created = env.created_slot;
    past_deadline(&mut env);
    let creator = env.creator.insecure_clone();
    expect_ok(refund_by(&mut env, &creator), "refund");
    let ix = close_assignment_ix(&env, &n, &n, 0, Some(a.output_hash));
    expect_ok(as_signer(&mut env, &node, ix), "close assignment");
    let ix = close_job_ix(&env, &creator.pubkey());
    expect_ok(as_signer(&mut env, &creator, ix), "close job");

    // Same creator + job_id => same job PDA.
    let n_slots = env.b.long();
    let ix = create_job_ix(
        &creator.pubkey(),
        env.job_id,
        env.terms,
        env.budget,
        4,
        env.verifier.pubkey(),
        n_slots,
    );
    expect_ok(as_signer(&mut env, &creator, ix), "re-create");
    let j: Job = read(&env.b, &env.job);
    assert!(j.created_slot > old_created);
    env.created_slot = j.created_slot;
    env.deadline_slot = j.deadline_slot;
    expect_ok(accept(&mut env, 0), "accept again");
    expect_ok(submit(&mut env, 0, &a), "submit again");
    let verifier = env.verifier.insecure_clone();
    expect_custom(
        settle_full(&mut env, &node, &verifier, &old_digest, &n, 0),
        1,
        code(SettlementError::DigestMismatch),
    );
    expect_ok(settle(&mut env, 0, &a), "fresh signature settles");
}
