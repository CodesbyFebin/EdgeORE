//! Shared test harness and scenarios for the receipt-settlement program.
//!
//! Every scenario runs against a `Backend`:
//! - `Mode::Svm`: in-process LiteSVM with the Ed25519 native program loaded
//!   (`precompiles` feature). Used by `tests/settlement.rs`.
//! - `Mode::Validator`: a running local `solana-test-validator` over RPC
//!   (`ANCHOR_PROVIDER_URL`, default http://127.0.0.1:8899), with the program
//!   loaded at genesis by `anchor test --validator legacy`. Used by
//!   `tests/validator.rs`. Airdrops come from the local test faucet only.
//!
//! Signatures are real Ed25519 signatures produced by test keypairs. The
//! receipt digest is recomputed here independently with `sha2` rather than by
//! calling the program's own digest function.
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
    solana_rpc_client_api::config::RpcSimulateTransactionConfig,
    solana_signer::Signer,
    solana_transaction::{versioned::VersionedTransaction, InstructionError, TransactionError},
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
/// Claim window used for create_job parameter checks.
const WINDOW: u64 = 100;
/// Claim window per backend: LiteSVM warps instantly; on a validator every
/// slot is real time (~400 ms), so a shorter window keeps the run short.
const WINDOW_SVM: u64 = 100;
const WINDOW_VALIDATOR: u64 = 40;
const ED25519_ID: Pubkey = solana_sdk_ids::ed25519_program::ID;
const IX_SYSVAR: Pubkey = solana_sdk_ids::sysvar::instructions::ID;
// Anchor framework error codes used below.
const CONSTRAINT_HAS_ONE: u32 = 2001;
const CONSTRAINT_SEEDS: u32 = 2006;

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
}

pub struct Meta {
    pub logs: Vec<String>,
}
impl Meta {
    pub fn pretty_logs(&self) -> String {
        self.logs.join("\n")
    }
}

pub struct Failed {
    pub err: TransactionError,
    pub meta: Meta,
}
impl std::fmt::Debug for Failed {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{:?}\n{}", self.err, self.meta.pretty_logs())
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

    pub fn window(&self) -> u64 {
        if self.is_svm() {
            WINDOW_SVM
        } else {
            WINDOW_VALIDATOR
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

    pub fn get_balance(&self, key: &Pubkey) -> Option<u64> {
        self.get_account(key).map(|a| a.lamports)
    }

    pub fn minimum_balance_for_rent_exemption(&self, len: usize) -> u64 {
        match self {
            Backend::Svm(svm) => svm.minimum_balance_for_rent_exemption(len),
            Backend::Rpc(rpc) => rpc.get_minimum_balance_for_rent_exemption(len).unwrap(),
        }
    }

    pub fn airdrop(&mut self, key: &Pubkey, lamports: u64) -> Result<(), String> {
        match self {
            Backend::Svm(svm) => svm
                .airdrop(key, lamports)
                .map(|_| ())
                .map_err(|e| format!("{e:?}")),
            Backend::Rpc(rpc) => {
                // Local test-validator faucet only (asserted in Backend::new).
                let sig = rpc
                    .request_airdrop(key, lamports)
                    .map_err(|e| e.to_string())?;
                let t = Instant::now();
                while t.elapsed() < Duration::from_secs(60) {
                    if rpc
                        .confirm_transaction_with_commitment(&sig, CommitmentConfig::confirmed())
                        .map(|r| r.value)
                        .unwrap_or(false)
                    {
                        return Ok(());
                    }
                    std::thread::sleep(Duration::from_millis(200));
                }
                Err("airdrop not confirmed".into())
            }
        }
    }

    /// LiteSVM: set the clock. Validator: wait until the confirmed slot
    /// reaches `slot` (so both simulation and execution see slot >= target).
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
                let msg = Message::new_with_blockhash(ixs, Some(&payer.pubkey()), &bh);
                let tx =
                    VersionedTransaction::try_new(VersionedMessage::Legacy(msg), signers).unwrap();
                match svm.send_transaction(tx) {
                    Ok(meta) => Ok(TxOk { fee: meta.fee }),
                    Err(f) => Err(Failed {
                        err: f.err,
                        meta: Meta { logs: f.meta.logs },
                    }),
                }
            }
            Backend::Rpc(rpc) => {
                let bh = rpc.get_latest_blockhash().unwrap();
                let msg = Message::new_with_blockhash(ixs, Some(&payer.pubkey()), &bh);
                let fee = rpc.get_fee_for_message(&msg).unwrap();
                let tx =
                    VersionedTransaction::try_new(VersionedMessage::Legacy(msg), signers).unwrap();
                // Simulate first (with signature verification) to get the
                // error and program logs of a failing transaction.
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
                if let Some(err) = sim.err {
                    return Err(Failed {
                        err: err.into(),
                        meta: Meta {
                            logs: sim.logs.unwrap_or_default(),
                        },
                    });
                }
                match rpc.send_and_confirm_transaction(&tx) {
                    Ok(_) => Ok(TxOk { fee }),
                    Err(e) => Err(Failed {
                        err: e
                            .get_transaction_error()
                            .unwrap_or_else(|| panic!("rpc send failed: {e}")),
                        meta: Meta {
                            logs: vec![format!("send failed after successful simulation: {e}")],
                        },
                    }),
                }
            }
        }
    }
}

pub struct Env {
    pub b: Backend,
    pub creator: Keypair,
    pub verifier: Keypair,
    pub worker: Keypair,
    pub job_id: u64,
    pub created_slot: u64,
    pub window: u64,
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
fn receipt_pda(job: &Pubkey, id: &[u8; 32]) -> Pubkey {
    Pubkey::find_program_address(&[RECEIPT_SEED, job.as_ref(), id], &pid()).0
}
fn section_pda(job: &Pubkey, section: u16) -> Pubkey {
    Pubkey::find_program_address(
        &[SECTION_SEED, job.as_ref(), &section.to_le_bytes()],
        &pid(),
    )
    .0
}
fn output_pda(job: &Pubkey, out: &[u8; 32]) -> Pubkey {
    Pubkey::find_program_address(&[OUTPUT_SEED, job.as_ref(), out], &pid()).0
}

fn send(
    b: &mut Backend,
    ixs: &[Instruction],
    payer: &Keypair,
    signers: &[&Keypair],
) -> TransactionResult {
    b.send(ixs, payer, signers)
}

fn expect_custom(res: TransactionResult, ix_index: u8, expected: u32) -> Vec<String> {
    let failed = res.err().expect("transaction unexpectedly succeeded");
    match &failed.err {
        TransactionError::InstructionError(i, InstructionError::Custom(c)) => {
            assert_eq!(
                *i,
                ix_index,
                "wrong failing instruction; logs:\n{}",
                failed.meta.pretty_logs()
            );
            assert_eq!(
                *c,
                expected,
                "wrong error code; logs:\n{}",
                failed.meta.pretty_logs()
            );
        }
        other => panic!(
            "unexpected error {other:?}; logs:\n{}",
            failed.meta.pretty_logs()
        ),
    }
    failed.meta.logs
}

fn setup_with(mode: Mode, budget: u64, sections: u16) -> Env {
    let mut b = Backend::new(mode);
    let window = b.window();
    let creator = Keypair::new();
    let verifier = Keypair::new();
    let worker = Keypair::new();
    b.airdrop(&creator.pubkey(), 100 * SOL).unwrap();
    b.airdrop(&worker.pubkey(), 10 * SOL).unwrap();
    let job_id = 42u64;
    let job = job_pda(&creator.pubkey(), job_id);
    let vault = vault_pda(&job);
    let ix = Instruction::new_with_bytes(
        pid(),
        &receipt_settlement::instruction::CreateJob {
            job_id,
            budget,
            section_count: sections,
            verifier: verifier.pubkey(),
            claim_window_slots: window,
        }
        .data(),
        receipt_settlement::accounts::CreateJob {
            creator: creator.pubkey(),
            job,
            vault,
            system_program: system_program::ID,
        }
        .to_account_metas(None),
    );
    let res = send(&mut b, &[ix], &creator, &[&creator]);
    assert!(res.is_ok(), "create_job failed: {:?}", res.err());
    let created_slot = read::<Job>(&b, &job).created_slot;
    Env {
        b,
        creator,
        verifier,
        worker,
        job_id,
        created_slot,
        window,
        job,
        vault,
        budget,
    }
}

fn setup(mode: Mode) -> Env {
    setup_with(mode, 10 * SOL, 4)
}

fn read<T: AccountDeserialize>(b: &Backend, key: &Pubkey) -> T {
    let acc = b.get_account(key).expect("account missing");
    T::try_deserialize(&mut acc.data.as_slice()).unwrap()
}

fn exists(b: &Backend, key: &Pubkey) -> bool {
    b.get_account(key).map(|a| a.lamports > 0).unwrap_or(false)
}

fn vault_rent(b: &Backend) -> u64 {
    b.minimum_balance_for_rent_exemption(8 + Vault::INIT_SPACE)
}

fn warp(env: &mut Env, slot: u64) {
    env.b.warp_to_slot(slot);
}

fn args(n: u8, section: u16, quote: u64, charge: u64) -> ReceiptArgs {
    ReceiptArgs {
        receipt_id: [n; 32],
        section,
        input_hash: [n.wrapping_add(100); 32],
        output_hash: [n.wrapping_add(150); 32],
        model_hash: [7; 32],
        quoted_price: quote,
        actual_charge: charge,
    }
}

/// Independent re-implementation of the canonical digest (see digest.rs).
fn digest(
    job: &Pubkey,
    job_id: u64,
    created_slot: u64,
    worker: &Pubkey,
    a: &ReceiptArgs,
) -> [u8; 32] {
    let mut h = Sha256::new();
    h.update(b"EdgeORE/receipt-settlement/v2");
    h.update(pid().as_ref());
    h.update(job.as_ref());
    h.update(job_id.to_le_bytes());
    h.update(created_slot.to_le_bytes());
    h.update(a.receipt_id);
    h.update(a.section.to_le_bytes());
    h.update(worker.as_ref());
    h.update(a.input_hash);
    h.update(a.output_hash);
    h.update(a.model_hash);
    h.update(a.quoted_price.to_le_bytes());
    h.update(a.actual_charge.to_le_bytes());
    h.finalize().into()
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

fn submit_ix(env: &Env, worker: &Pubkey, a: &ReceiptArgs) -> Instruction {
    Instruction::new_with_bytes(
        pid(),
        &receipt_settlement::instruction::SubmitReceipt { args: *a }.data(),
        receipt_settlement::accounts::SubmitReceipt {
            worker: *worker,
            job: env.job,
            receipt: receipt_pda(&env.job, &a.receipt_id),
            section_marker: section_pda(&env.job, a.section),
            output_marker: output_pda(&env.job, &a.output_hash),
            instructions_sysvar: IX_SYSVAR,
            system_program: system_program::ID,
        }
        .to_account_metas(None),
    )
}

/// Verifier signs the digest of `a` for env.worker; env.worker submits `a`.
fn submit(env: &mut Env, a: &ReceiptArgs) -> TransactionResult {
    let d = digest(
        &env.job,
        env.job_id,
        env.created_slot,
        &env.worker.pubkey(),
        a,
    );
    let ixs = [
        ed25519_ix(&env.verifier, &d),
        submit_ix(env, &env.worker.pubkey(), a),
    ];
    let w = env.worker.insecure_clone();
    send(&mut env.b, &ixs, &w, &[&w])
}

fn claim_ix(env: &Env, worker: &Pubkey, receipt_id: &[u8; 32]) -> Instruction {
    Instruction::new_with_bytes(
        pid(),
        &receipt_settlement::instruction::Claim {}.data(),
        receipt_settlement::accounts::Claim {
            worker: *worker,
            job: env.job,
            vault: env.vault,
            receipt: receipt_pda(&env.job, receipt_id),
        }
        .to_account_metas(None),
    )
}

fn claim(env: &mut Env, receipt_id: &[u8; 32]) -> TransactionResult {
    let ix = claim_ix(env, &env.worker.pubkey(), receipt_id);
    let w = env.worker.insecure_clone();
    send(&mut env.b, &[ix], &w, &[&w])
}

fn cancel_ix(env: &Env, creator: &Pubkey) -> Instruction {
    Instruction::new_with_bytes(
        pid(),
        &receipt_settlement::instruction::CancelJob {}.data(),
        receipt_settlement::accounts::CancelJob {
            creator: *creator,
            job: env.job,
            vault: env.vault,
        }
        .to_account_metas(None),
    )
}

fn cancel(env: &mut Env) -> TransactionResult {
    let ix = cancel_ix(env, &env.creator.pubkey());
    let c = env.creator.insecure_clone();
    send(&mut env.b, &[ix], &c, &[&c])
}

// ---------------------------------------------------------------- tests

pub fn happy_path_submit_claim_cancel(mode: Mode) {
    let mut env = setup(mode);
    let rent = vault_rent(&env.b);
    assert_eq!(env.b.get_balance(&env.vault).unwrap(), env.budget + rent);

    let a = args(1, 0, 2 * SOL, SOL + 500);
    let res = submit(&mut env, &a);
    assert!(res.is_ok(), "{}", res.err().unwrap().meta.pretty_logs());

    let r: Receipt = read(&env.b, &receipt_pda(&env.job, &a.receipt_id));
    assert_eq!(r.worker, env.worker.pubkey());
    assert_eq!(r.actual_charge, SOL + 500);
    assert_eq!(
        r.digest,
        digest(
            &env.job,
            env.job_id,
            env.created_slot,
            &env.worker.pubkey(),
            &a
        )
    );
    assert!(!r.settled);
    assert_eq!(r.settled_slot, 0);
    assert!(r.submitted_slot >= env.created_slot);
    let j: Job = read(&env.b, &env.job);
    assert_eq!(
        (j.committed, j.paid, j.pending_claims, j.receipt_count),
        (SOL + 500, 0, 1, 1)
    );

    let before = env.b.get_balance(&env.worker.pubkey()).unwrap();
    let res = claim(&mut env, &a.receipt_id);
    assert!(res.is_ok(), "{}", res.err().unwrap().meta.pretty_logs());
    let fee = res.unwrap().fee;
    let after = env.b.get_balance(&env.worker.pubkey()).unwrap();
    assert_eq!(
        after + fee,
        before + SOL + 500,
        "worker must receive exactly the signed charge"
    );
    assert_eq!(
        env.b.get_balance(&env.vault).unwrap(),
        env.budget - (SOL + 500) + rent
    );

    let r: Receipt = read(&env.b, &receipt_pda(&env.job, &a.receipt_id));
    assert!(r.settled);
    assert!(r.settled_slot >= env.created_slot);
    let j: Job = read(&env.b, &env.job);
    assert_eq!((j.paid, j.pending_claims), (SOL + 500, 0));

    let c_before = env.b.get_balance(&env.creator.pubkey()).unwrap();
    let res = cancel(&mut env);
    assert!(res.is_ok(), "{}", res.err().unwrap().meta.pretty_logs());
    let fee = res.unwrap().fee;
    let c_after = env.b.get_balance(&env.creator.pubkey()).unwrap();
    assert_eq!(
        c_after + fee,
        c_before + env.budget - (SOL + 500) + rent,
        "creator gets unspent budget + vault rent"
    );
    assert!(!exists(&env.b, &env.vault), "vault closed");
    let j: Job = read(&env.b, &env.job);
    assert_eq!(j.status, JobStatus::Cancelled);

    // Submitting to a cancelled job is rejected.
    let b = args(2, 1, SOL, SOL);
    expect_custom(submit(&mut env, &b), 1, code(SettlementError::JobNotActive));
}

pub fn bad_signature_is_rejected_by_ed25519_program(mode: Mode) {
    let mut env = setup(mode);
    let a = args(1, 0, SOL, SOL);
    let d = digest(
        &env.job,
        env.job_id,
        env.created_slot,
        &env.worker.pubkey(),
        &a,
    );
    let mut sig: [u8; 64] = env.verifier.sign_message(&d).as_ref().try_into().unwrap();
    sig[10] ^= 0x01;
    let ixs = [
        ed25519_ix_raw(env.verifier.pubkey().as_ref(), &sig, &d, u16::MAX),
        submit_ix(&env, &env.worker.pubkey(), &a),
    ];
    let w = env.worker.insecure_clone();
    let failed = send(&mut env.b, &ixs, &w, &[&w]).expect_err("bad signature accepted");
    println!("bad signature rejected with: {:?}", failed.err);
    match failed.err {
        TransactionError::InstructionError(0, _) => {}
        other => panic!("expected failure in Ed25519 instruction 0, got {other:?}"),
    }
    // Our program never ran.
    assert!(
        !failed
            .meta
            .logs
            .iter()
            .any(|l| l.contains(&pid().to_string())),
        "{:#?}",
        failed.meta.logs
    );
    assert!(!exists(&env.b, &receipt_pda(&env.job, &a.receipt_id)));
}

pub fn missing_ed25519_instruction_is_rejected(mode: Mode) {
    let mut env = setup(mode);
    let a = args(1, 0, SOL, SOL);
    let ix = submit_ix(&env, &env.worker.pubkey(), &a);
    let w = env.worker.insecure_clone();
    expect_custom(
        send(&mut env.b, &[ix], &w, &[&w]),
        0,
        code(SettlementError::MissingEd25519Instruction),
    );
}

pub fn ed25519_offsets_into_other_instruction_index_are_rejected(mode: Mode) {
    // Explicit index 0 resolves to the same bytes for the native program, but
    // the program only accepts the self-referencing u16::MAX form.
    let mut env = setup(mode);
    let a = args(1, 0, SOL, SOL);
    let d = digest(
        &env.job,
        env.job_id,
        env.created_slot,
        &env.worker.pubkey(),
        &a,
    );
    let sig = env.verifier.sign_message(&d);
    let ixs = [
        ed25519_ix_raw(env.verifier.pubkey().as_ref(), sig.as_ref(), &d, 0),
        submit_ix(&env, &env.worker.pubkey(), &a),
    ];
    let w = env.worker.insecure_clone();
    expect_custom(
        send(&mut env.b, &ixs, &w, &[&w]),
        1,
        code(SettlementError::InvalidEd25519Instruction),
    );
}

pub fn wrong_verifier_is_rejected(mode: Mode) {
    let mut env = setup(mode);
    let a = args(1, 0, SOL, SOL);
    let d = digest(
        &env.job,
        env.job_id,
        env.created_slot,
        &env.worker.pubkey(),
        &a,
    );
    let impostor = Keypair::new();
    let ixs = [
        ed25519_ix(&impostor, &d),
        submit_ix(&env, &env.worker.pubkey(), &a),
    ];
    let w = env.worker.insecure_clone();
    expect_custom(
        send(&mut env.b, &ixs, &w, &[&w]),
        1,
        code(SettlementError::VerifierMismatch),
    );
    assert!(!exists(&env.b, &receipt_pda(&env.job, &a.receipt_id)));
}

pub fn tampered_receipt_fields_after_signing_are_rejected(mode: Mode) {
    let mut env = setup(mode);
    let signed = args(1, 0, 2 * SOL, SOL);
    let d = digest(
        &env.job,
        env.job_id,
        env.created_slot,
        &env.worker.pubkey(),
        &signed,
    );
    let mut variants: Vec<(&str, ReceiptArgs)> = Vec::new();
    let mut t = signed;
    t.actual_charge = SOL - 1;
    variants.push(("actual_charge", t));
    let mut t = signed;
    t.quoted_price = 3 * SOL;
    variants.push(("quoted_price", t));
    let mut t = signed;
    t.section = 1;
    variants.push(("section", t));
    let mut t = signed;
    t.receipt_id[0] ^= 1;
    variants.push(("receipt_id", t));
    let mut t = signed;
    t.input_hash[31] ^= 1;
    variants.push(("input_hash", t));
    let mut t = signed;
    t.output_hash[0] ^= 1;
    variants.push(("output_hash", t));
    let mut t = signed;
    t.model_hash[5] ^= 1;
    variants.push(("model_hash", t));
    for (name, tampered) in variants {
        let ixs = [
            ed25519_ix(&env.verifier, &d),
            submit_ix(&env, &env.worker.pubkey(), &tampered),
        ];
        let w = env.worker.insecure_clone();
        let res = send(&mut env.b, &ixs, &w, &[&w]);
        assert!(res.is_err(), "tampered {name} accepted");
        expect_custom(res, 1, code(SettlementError::DigestMismatch));
    }
    // A different worker cannot submit a receipt signed for env.worker.
    let thief = Keypair::new();
    env.b.airdrop(&thief.pubkey(), SOL).unwrap();
    let ixs = [
        ed25519_ix(&env.verifier, &d),
        submit_ix(&env, &thief.pubkey(), &signed),
    ];
    expect_custom(
        send(&mut env.b, &ixs, &thief, &[&thief]),
        1,
        code(SettlementError::DigestMismatch),
    );
    // The untampered receipt still goes through.
    assert!(submit(&mut env, &signed).is_ok());
}

pub fn over_budget_is_rejected(mode: Mode) {
    let mut env = setup_with(mode, 1_000_000, 4);
    assert!(submit(&mut env, &args(1, 0, 600_000, 600_000)).is_ok());
    expect_custom(
        submit(&mut env, &args(2, 1, 500_000, 400_001)),
        1,
        code(SettlementError::OverBudget),
    );
    // Exactly reaching the budget is allowed.
    assert!(submit(&mut env, &args(3, 2, 400_000, 400_000)).is_ok());
    let j: Job = read(&env.b, &env.job);
    assert_eq!(j.committed, 1_000_000);
}

pub fn charge_above_quote_is_rejected(mode: Mode) {
    let mut env = setup(mode);
    expect_custom(
        submit(&mut env, &args(1, 0, 1_000, 1_001)),
        1,
        code(SettlementError::ChargeExceedsQuote),
    );
    expect_custom(
        submit(&mut env, &args(2, 0, 1_000, 0)),
        1,
        code(SettlementError::ZeroCharge),
    );
    assert!(submit(&mut env, &args(3, 0, 1_000, 1_000)).is_ok());
}

pub fn section_out_of_range_is_rejected(mode: Mode) {
    let mut env = setup_with(mode, SOL, 2);
    expect_custom(
        submit(&mut env, &args(1, 2, 1_000, 1_000)),
        1,
        code(SettlementError::SectionOutOfRange),
    );
}

pub fn duplicate_receipt_id_is_rejected(mode: Mode) {
    let mut env = setup(mode);
    let a = args(1, 0, 1_000, 1_000);
    assert!(submit(&mut env, &a).is_ok());
    let mut dup = args(9, 1, 1_000, 1_000);
    dup.receipt_id = a.receipt_id;
    let receipt = receipt_pda(&env.job, &dup.receipt_id);
    let logs = expect_custom(submit(&mut env, &dup), 1, 0); // system program: AccountAlreadyInUse
    assert!(
        logs.iter()
            .any(|l| l.contains("already in use") && l.contains(&receipt.to_string())),
        "{logs:#?}"
    );
    let j: Job = read(&env.b, &env.job);
    assert_eq!(j.receipt_count, 1);
}

pub fn duplicate_section_is_rejected(mode: Mode) {
    let mut env = setup(mode);
    assert!(submit(&mut env, &args(1, 0, 1_000, 1_000)).is_ok());
    let dup = args(2, 0, 1_000, 1_000);
    let section = section_pda(&env.job, 0);
    let logs = expect_custom(submit(&mut env, &dup), 1, 0);
    assert!(
        logs.iter()
            .any(|l| l.contains("already in use") && l.contains(&section.to_string())),
        "{logs:#?}"
    );
}

pub fn duplicate_output_hash_in_same_job_is_rejected(mode: Mode) {
    let mut env = setup(mode);
    let a = args(1, 0, 1_000, 1_000);
    assert!(submit(&mut env, &a).is_ok());
    let mut dup = args(2, 1, 1_000, 1_000);
    dup.output_hash = a.output_hash;
    let out = output_pda(&env.job, &dup.output_hash);
    let logs = expect_custom(submit(&mut env, &dup), 1, 0);
    assert!(
        logs.iter()
            .any(|l| l.contains("already in use") && l.contains(&out.to_string())),
        "{logs:#?}"
    );
}

pub fn double_claim_is_rejected(mode: Mode) {
    let mut env = setup(mode);
    let a = args(1, 0, SOL, SOL);
    assert!(submit(&mut env, &a).is_ok());
    assert!(claim(&mut env, &a.receipt_id).is_ok());
    let vault_before = env.b.get_balance(&env.vault).unwrap();
    expect_custom(
        claim(&mut env, &a.receipt_id),
        0,
        code(SettlementError::AlreadySettled),
    );
    assert_eq!(env.b.get_balance(&env.vault).unwrap(), vault_before);
    let j: Job = read(&env.b, &env.job);
    assert_eq!((j.paid, j.pending_claims), (SOL, 0));
}

pub fn claim_by_wrong_worker_is_rejected(mode: Mode) {
    let mut env = setup(mode);
    let a = args(1, 0, SOL, SOL);
    assert!(submit(&mut env, &a).is_ok());
    let thief = Keypair::new();
    env.b.airdrop(&thief.pubkey(), SOL).unwrap();
    let ix = claim_ix(&env, &thief.pubkey(), &a.receipt_id);
    expect_custom(
        send(&mut env.b, &[ix], &thief, &[&thief]),
        0,
        CONSTRAINT_HAS_ONE,
    );
    let r: Receipt = read(&env.b, &receipt_pda(&env.job, &a.receipt_id));
    assert!(!r.settled);
    // The rightful worker can still claim.
    assert!(claim(&mut env, &a.receipt_id).is_ok());
}

pub fn cancel_with_pending_claims_is_rejected_until_claimed(mode: Mode) {
    let mut env = setup(mode);
    let a = args(1, 0, SOL, SOL);
    assert!(submit(&mut env, &a).is_ok());
    expect_custom(cancel(&mut env), 0, code(SettlementError::PendingClaims));
    assert!(exists(&env.b, &env.vault));
    assert!(claim(&mut env, &a.receipt_id).is_ok());
    assert!(cancel(&mut env).is_ok());
    // Second cancel: vault is gone, so account validation fails before the handler.
    assert!(cancel(&mut env).is_err());
}

pub fn cancel_by_non_creator_is_rejected(mode: Mode) {
    let mut env = setup(mode);
    let other = Keypair::new();
    env.b.airdrop(&other.pubkey(), SOL).unwrap();
    let ix = cancel_ix(&env, &other.pubkey());
    // Job PDA seeds include the creator, so a different signer fails the seeds check.
    expect_custom(
        send(&mut env.b, &[ix], &other, &[&other]),
        0,
        CONSTRAINT_SEEDS,
    );
    assert!(exists(&env.b, &env.vault));
}

pub fn create_job_rejects_invalid_parameters(mode: Mode) {
    let mut b = Backend::new(mode);
    let creator = Keypair::new();
    b.airdrop(&creator.pubkey(), 10 * SOL).unwrap();
    let verifier = Keypair::new().pubkey();
    for (i, (budget, sections, ver, window, err)) in [
        (0u64, 1u16, verifier, WINDOW, SettlementError::InvalidBudget),
        (
            SOL,
            0u16,
            verifier,
            WINDOW,
            SettlementError::InvalidSectionCount,
        ),
        (
            SOL,
            1u16,
            Pubkey::default(),
            WINDOW,
            SettlementError::InvalidVerifier,
        ),
        (
            SOL,
            1u16,
            verifier,
            0u64,
            SettlementError::InvalidClaimWindow,
        ),
    ]
    .into_iter()
    .enumerate()
    {
        let job_id = i as u64;
        let job = job_pda(&creator.pubkey(), job_id);
        let ix = Instruction::new_with_bytes(
            pid(),
            &receipt_settlement::instruction::CreateJob {
                job_id,
                budget,
                section_count: sections,
                verifier: ver,
                claim_window_slots: window,
            }
            .data(),
            receipt_settlement::accounts::CreateJob {
                creator: creator.pubkey(),
                job,
                vault: vault_pda(&job),
                system_program: system_program::ID,
            }
            .to_account_metas(None),
        );
        expect_custom(send(&mut b, &[ix], &creator, &[&creator]), 0, code(err));
    }
}

// ------------------------------------------------- claim window (deadline)

pub fn claim_window_recorded_on_receipt_and_job(mode: Mode) {
    let mut env = setup(mode);
    let a = args(1, 0, SOL, SOL);
    assert!(submit(&mut env, &a).is_ok());
    let r: Receipt = read(&env.b, &receipt_pda(&env.job, &a.receipt_id));
    assert_eq!(r.claim_deadline_slot, r.submitted_slot + env.window);
    let j: Job = read(&env.b, &env.job);
    assert_eq!(j.claim_window_slots, env.window);
    assert_eq!(j.claim_deadline, r.claim_deadline_slot);
}

pub fn early_cancel_with_unclaimed_receipt_is_rejected_until_deadline_passes(mode: Mode) {
    let mut env = setup(mode);
    let a = args(1, 0, SOL, SOL);
    assert!(submit(&mut env, &a).is_ok());
    let r: Receipt = read(&env.b, &receipt_pda(&env.job, &a.receipt_id));
    // At the deadline slot itself the window is still open. (Exact slots can
    // only be targeted in LiteSVM; on a validator the cancel runs well inside
    // the window instead.)
    if env.b.is_svm() {
        warp(&mut env, r.claim_deadline_slot);
    }
    expect_custom(cancel(&mut env), 0, code(SettlementError::PendingClaims));
    assert!(exists(&env.b, &env.vault));
}

pub fn cancel_after_deadline_reclaims_unclaimed_funds(mode: Mode) {
    let mut env = setup(mode);
    let rent = vault_rent(&env.b);
    let a = args(1, 0, SOL, SOL);
    assert!(submit(&mut env, &a).is_ok());
    let r: Receipt = read(&env.b, &receipt_pda(&env.job, &a.receipt_id));
    warp(&mut env, r.claim_deadline_slot + 1);
    let before = env.b.get_balance(&env.creator.pubkey()).unwrap();
    let res = cancel(&mut env);
    assert!(res.is_ok(), "{}", res.err().unwrap().meta.pretty_logs());
    let fee = res.unwrap().fee;
    let after = env.b.get_balance(&env.creator.pubkey()).unwrap();
    // The whole budget (including the unclaimed charge) plus vault rent returns.
    assert_eq!(after + fee, before + env.budget + rent);
    assert!(!exists(&env.b, &env.vault));
    let j: Job = read(&env.b, &env.job);
    assert_eq!(j.status, JobStatus::Cancelled);
    assert_eq!(j.pending_claims, 1, "expired receipt stays unclaimed");
    let r: Receipt = read(&env.b, &receipt_pda(&env.job, &a.receipt_id));
    assert!(!r.settled);
}

pub fn claim_at_deadline_allowed_after_deadline_rejected(mode: Mode) {
    let mut env = setup(mode);
    let a = args(1, 0, SOL, SOL);
    let b = args(2, 1, SOL, SOL);
    assert!(submit(&mut env, &a).is_ok());
    assert!(submit(&mut env, &b).is_ok());
    let ra: Receipt = read(&env.b, &receipt_pda(&env.job, &a.receipt_id));
    // Last slot of the window: claim succeeds (exact slot only in LiteSVM; on a
    // validator the claim simply runs inside the window).
    if env.b.is_svm() {
        warp(&mut env, ra.claim_deadline_slot);
    }
    assert!(claim(&mut env, &a.receipt_id).is_ok());
    // One slot later: claim rejected, nothing paid.
    warp(&mut env, ra.claim_deadline_slot + 1);
    let vault_before = env.b.get_balance(&env.vault).unwrap();
    expect_custom(
        claim(&mut env, &b.receipt_id),
        0,
        code(SettlementError::ClaimWindowExpired),
    );
    assert_eq!(env.b.get_balance(&env.vault).unwrap(), vault_before);
    let rb: Receipt = read(&env.b, &receipt_pda(&env.job, &b.receipt_id));
    assert!(!rb.settled);
}

pub fn later_receipt_extends_job_deadline(mode: Mode) {
    let mut env = setup(mode);
    let a = args(1, 0, SOL, SOL);
    assert!(submit(&mut env, &a).is_ok());
    let mid = env.created_slot + env.window / 2;
    warp(&mut env, mid);
    let b = args(2, 1, SOL, SOL);
    assert!(submit(&mut env, &b).is_ok());
    let rb: Receipt = read(&env.b, &receipt_pda(&env.job, &b.receipt_id));
    // a's window has passed but b's has not: cancel still rejected, b claimable.
    let ra: Receipt = read(&env.b, &receipt_pda(&env.job, &a.receipt_id));
    assert!(rb.claim_deadline_slot > ra.claim_deadline_slot + 1);
    warp(&mut env, ra.claim_deadline_slot + 1);
    expect_custom(cancel(&mut env), 0, code(SettlementError::PendingClaims));
    expect_custom(
        claim(&mut env, &a.receipt_id),
        0,
        code(SettlementError::ClaimWindowExpired),
    );
    assert!(claim(&mut env, &b.receipt_id).is_ok());
    // a is still unclaimed, so cancel waits for the job-level deadline (b's).
    expect_custom(cancel(&mut env), 0, code(SettlementError::PendingClaims));
    warp(&mut env, rb.claim_deadline_slot + 1);
    assert!(cancel(&mut env).is_ok());
}

// ------------------------------------------------------ verifier rotation

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

fn rotate(env: &mut Env, new_verifier: Pubkey) -> TransactionResult {
    let ix = rotate_ix(env, &env.creator.pubkey(), new_verifier);
    let c = env.creator.insecure_clone();
    send(&mut env.b, &[ix], &c, &[&c])
}

/// `signer` signs the digest of `a` for env.worker; env.worker submits.
fn submit_signed_by(env: &mut Env, signer: &Keypair, a: &ReceiptArgs) -> TransactionResult {
    let d = digest(
        &env.job,
        env.job_id,
        env.created_slot,
        &env.worker.pubkey(),
        a,
    );
    let ixs = [
        ed25519_ix(signer, &d),
        submit_ix(env, &env.worker.pubkey(), a),
    ];
    let w = env.worker.insecure_clone();
    send(&mut env.b, &ixs, &w, &[&w])
}

pub fn rotated_verifier_old_key_rejected_new_key_accepted(mode: Mode) {
    let mut env = setup(mode);
    let old = env.verifier.insecure_clone();
    let new = Keypair::new();
    // Accepted under the old key before rotation.
    let a = args(1, 0, SOL, SOL);
    assert!(submit_signed_by(&mut env, &old, &a).is_ok());

    assert!(rotate(&mut env, new.pubkey()).is_ok());
    let j: Job = read(&env.b, &env.job);
    assert_eq!(j.verifier, new.pubkey());

    // A receipt signed by the old key (even one signed before the rotation) is rejected.
    let b = args(2, 1, SOL, SOL);
    expect_custom(
        submit_signed_by(&mut env, &old, &b),
        1,
        code(SettlementError::VerifierMismatch),
    );
    assert!(!exists(&env.b, &receipt_pda(&env.job, &b.receipt_id)));
    // The same receipt signed by the new key is accepted.
    assert!(submit_signed_by(&mut env, &new, &b).is_ok());
    // The receipt accepted before rotation is still claimable.
    assert!(claim(&mut env, &a.receipt_id).is_ok());
}

pub fn rotate_verifier_rejects_non_creator_default_same_and_inactive(mode: Mode) {
    let mut env = setup(mode);
    let other = Keypair::new();
    env.b.airdrop(&other.pubkey(), SOL).unwrap();
    let ix = rotate_ix(&env, &other.pubkey(), other.pubkey());
    expect_custom(
        send(&mut env.b, &[ix], &other, &[&other]),
        0,
        CONSTRAINT_SEEDS,
    );
    expect_custom(
        rotate(&mut env, Pubkey::default()),
        0,
        code(SettlementError::InvalidVerifier),
    );
    let same = env.verifier.pubkey();
    expect_custom(
        rotate(&mut env, same),
        0,
        code(SettlementError::SameVerifier),
    );
    let j: Job = read(&env.b, &env.job);
    assert_eq!(j.verifier, same);
    assert!(cancel(&mut env).is_ok());
    expect_custom(
        rotate(&mut env, Keypair::new().pubkey()),
        0,
        code(SettlementError::JobNotActive),
    );
}

// ------------------------------------------------------------ rent reclaim

fn close_receipt_ix(env: &Env, closer: &Pubkey, receipt_id: &[u8; 32]) -> Instruction {
    Instruction::new_with_bytes(
        pid(),
        &receipt_settlement::instruction::CloseReceipt {}.data(),
        receipt_settlement::accounts::CloseReceipt {
            closer: *closer,
            worker: env.worker.pubkey(),
            job: env.job,
            receipt: receipt_pda(&env.job, receipt_id),
        }
        .to_account_metas(None),
    )
}

fn close_markers_ix(env: &Env, closer: &Pubkey, a: &ReceiptArgs) -> Instruction {
    Instruction::new_with_bytes(
        pid(),
        &receipt_settlement::instruction::CloseMarkers {
            section: a.section,
            output_hash: a.output_hash,
        }
        .data(),
        receipt_settlement::accounts::CloseMarkers {
            closer: *closer,
            worker: env.worker.pubkey(),
            job: env.job,
            section_marker: section_pda(&env.job, a.section),
            output_marker: output_pda(&env.job, &a.output_hash),
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
    send(&mut env.b, &[ix], who, &[who])
}

fn lamports(env: &Env, key: &Pubkey) -> u64 {
    env.b.get_balance(key).unwrap_or(0)
}

pub fn worker_closes_settled_receipt_while_job_active_and_replay_still_blocked(mode: Mode) {
    let mut env = setup(mode);
    let a = args(1, 0, SOL, SOL);
    assert!(submit(&mut env, &a).is_ok());
    let w = env.worker.insecure_clone();
    let rpda = receipt_pda(&env.job, &a.receipt_id);

    // Unsettled and inside its window: cannot be closed.
    let ix = close_receipt_ix(&env, &w.pubkey(), &a.receipt_id);
    expect_custom(
        as_signer(&mut env, &w, ix),
        0,
        code(SettlementError::ReceiptStillClaimable),
    );

    assert!(claim(&mut env, &a.receipt_id).is_ok());
    let receipt_rent = lamports(&env, &rpda);
    let before = lamports(&env, &w.pubkey());
    let ix = close_receipt_ix(&env, &w.pubkey(), &a.receipt_id);
    let res = as_signer(&mut env, &w, ix);
    assert!(res.is_ok(), "{}", res.err().unwrap().meta.pretty_logs());
    let fee = res.unwrap().fee;
    assert_eq!(lamports(&env, &w.pubkey()) + fee, before + receipt_rent);
    assert!(!exists(&env.b, &rpda));
    let j: Job = read(&env.b, &env.job);
    assert_eq!(j.open_accounts, 2, "markers remain open");

    // Re-submitting the same signed receipt fails on the surviving section marker.
    let logs = expect_custom(submit(&mut env, &a), 1, 0);
    let section = section_pda(&env.job, a.section);
    assert!(
        logs.iter()
            .any(|l| l.contains("already in use") && l.contains(&section.to_string())),
        "{logs:#?}"
    );

    // Markers cannot be closed while the job is active.
    let ix = close_markers_ix(&env, &w.pubkey(), &a);
    expect_custom(
        as_signer(&mut env, &w, ix),
        0,
        code(SettlementError::MarkersStillNeeded),
    );
}

pub fn completed_job_full_rent_reclaim_lifecycle(mode: Mode) {
    let mut env = setup_with(mode, SOL, 2);
    let a = args(1, 0, SOL / 2, SOL / 2);
    let b = args(2, 1, SOL / 2, SOL / 2);
    assert!(submit(&mut env, &a).is_ok());
    assert!(submit(&mut env, &b).is_ok());
    assert!(claim(&mut env, &a.receipt_id).is_ok());
    assert!(claim(&mut env, &b.receipt_id).is_ok());

    // close_job before finalization is rejected.
    let c = env.creator.insecure_clone();
    let ix = close_job_ix(&env, &c.pubkey());
    expect_custom(
        as_signer(&mut env, &c, ix),
        0,
        code(SettlementError::JobStillActive),
    );

    assert!(cancel(&mut env).is_ok());
    let j: Job = read(&env.b, &env.job);
    assert_eq!(j.status, JobStatus::Completed);
    assert_eq!(j.open_accounts, 6);

    // Open receipt/marker accounts block close_job.
    let ix = close_job_ix(&env, &c.pubkey());
    expect_custom(
        as_signer(&mut env, &c, ix),
        0,
        code(SettlementError::OpenAccountsRemain),
    );

    // Worker reclaims rent for both receipts and their markers, exactly.
    let w = env.worker.insecure_clone();
    for r in [a, b] {
        let keys = [
            receipt_pda(&env.job, &r.receipt_id),
            section_pda(&env.job, r.section),
            output_pda(&env.job, &r.output_hash),
        ];
        let rent: u64 = keys.iter().map(|k| lamports(&env, k)).sum();
        let before = lamports(&env, &w.pubkey());
        let ix1 = close_receipt_ix(&env, &w.pubkey(), &r.receipt_id);
        let ix2 = close_markers_ix(&env, &w.pubkey(), &r);
        let res = send(&mut env.b, &[ix1, ix2], &w, &[&w]);
        assert!(res.is_ok(), "{}", res.err().unwrap().meta.pretty_logs());
        let fee = res.unwrap().fee;
        assert_eq!(lamports(&env, &w.pubkey()) + fee, before + rent);
        assert!(keys.iter().all(|k| !exists(&env.b, k)));
    }
    let j: Job = read(&env.b, &env.job);
    assert_eq!(j.open_accounts, 0);

    // A non-creator cannot close the job.
    let ix = close_job_ix(&env, &w.pubkey());
    expect_custom(as_signer(&mut env, &w, ix), 0, CONSTRAINT_SEEDS);

    // Creator closes the job (a later slot than creation) and gets its rent.
    env.b.warp_to_slot(env.created_slot + 1);
    let job_rent = lamports(&env, &env.job);
    let before = lamports(&env, &c.pubkey());
    let ix = close_job_ix(&env, &c.pubkey());
    let res = as_signer(&mut env, &c, ix);
    assert!(res.is_ok(), "{}", res.err().unwrap().meta.pretty_logs());
    let fee = res.unwrap().fee;
    assert_eq!(lamports(&env, &c.pubkey()) + fee, before + job_rent);
    assert!(!exists(&env.b, &env.job));
}

pub fn creator_cleans_up_expired_receipt_rent_goes_to_worker(mode: Mode) {
    let mut env = setup(mode);
    let a = args(1, 0, SOL, SOL);
    assert!(submit(&mut env, &a).is_ok());
    let c = env.creator.insecure_clone();
    let stranger = Keypair::new();
    env.b.airdrop(&stranger.pubkey(), SOL).unwrap();

    // Settled-or-expired rule applies to the creator too; and the creator may
    // not close receipts of an active job.
    let r: Receipt = read(&env.b, &receipt_pda(&env.job, &a.receipt_id));
    env.b.warp_to_slot(r.claim_deadline_slot + 1);
    let ix = close_receipt_ix(&env, &c.pubkey(), &a.receipt_id);
    expect_custom(
        as_signer(&mut env, &c, ix),
        0,
        code(SettlementError::Unauthorized),
    );

    assert!(cancel(&mut env).is_ok());
    let j: Job = read(&env.b, &env.job);
    assert_eq!(j.status, JobStatus::Cancelled);

    // A stranger can close nothing.
    let ix = close_receipt_ix(&env, &stranger.pubkey(), &a.receipt_id);
    expect_custom(
        as_signer(&mut env, &stranger, ix),
        0,
        code(SettlementError::Unauthorized),
    );
    let ix = close_markers_ix(&env, &stranger.pubkey(), &a);
    expect_custom(
        as_signer(&mut env, &stranger, ix),
        0,
        code(SettlementError::Unauthorized),
    );

    // Creator closes the expired receipt + markers; the worker gets the rent.
    let keys = [
        receipt_pda(&env.job, &a.receipt_id),
        section_pda(&env.job, a.section),
        output_pda(&env.job, &a.output_hash),
    ];
    let rent: u64 = keys.iter().map(|k| lamports(&env, k)).sum();
    let worker_before = lamports(&env, &env.worker.pubkey());
    let ix1 = close_receipt_ix(&env, &c.pubkey(), &a.receipt_id);
    let ix2 = close_markers_ix(&env, &c.pubkey(), &a);
    let res = send(&mut env.b, &[ix1, ix2], &c, &[&c]);
    assert!(res.is_ok(), "{}", res.err().unwrap().meta.pretty_logs());
    assert_eq!(lamports(&env, &env.worker.pubkey()), worker_before + rent);

    let ix = close_job_ix(&env, &c.pubkey());
    assert!(as_signer(&mut env, &c, ix).is_ok());
    assert!(!exists(&env.b, &env.job));
}

pub fn close_job_rejected_in_creation_slot(mode: Mode) {
    let mut env = setup(mode);
    assert!(cancel(&mut env).is_ok());
    let c = env.creator.insecure_clone();
    let ix = close_job_ix(&env, &c.pubkey());
    expect_custom(
        as_signer(&mut env, &c, ix),
        0,
        code(SettlementError::CloseTooEarly),
    );
    env.b.warp_to_slot(env.created_slot + 1);
    let ix = close_job_ix(&env, &c.pubkey());
    assert!(as_signer(&mut env, &c, ix).is_ok());
}

pub fn recreated_job_rejects_replay_of_old_signed_receipt(mode: Mode) {
    let mut env = setup(mode);
    let a = args(1, 0, SOL, SOL);
    // Verifier-signed receipt for the first generation of this job.
    let old_digest = digest(
        &env.job,
        env.job_id,
        env.created_slot,
        &env.worker.pubkey(),
        &a,
    );
    assert!(submit(&mut env, &a).is_ok());
    assert!(claim(&mut env, &a.receipt_id).is_ok());
    assert!(cancel(&mut env).is_ok());
    let w = env.worker.insecure_clone();
    let ix1 = close_receipt_ix(&env, &w.pubkey(), &a.receipt_id);
    let ix2 = close_markers_ix(&env, &w.pubkey(), &a);
    assert!(send(&mut env.b, &[ix1, ix2], &w, &[&w]).is_ok());
    env.b.warp_to_slot(env.created_slot + 5);
    let c = env.creator.insecure_clone();
    let ix = close_job_ix(&env, &c.pubkey());
    assert!(as_signer(&mut env, &c, ix).is_ok());

    // Re-create the same (creator, job_id) with the same verifier.
    let ix = Instruction::new_with_bytes(
        pid(),
        &receipt_settlement::instruction::CreateJob {
            job_id: env.job_id,
            budget: env.budget,
            section_count: 4,
            verifier: env.verifier.pubkey(),
            claim_window_slots: env.window,
        }
        .data(),
        receipt_settlement::accounts::CreateJob {
            creator: c.pubkey(),
            job: env.job,
            vault: env.vault,
            system_program: system_program::ID,
        }
        .to_account_metas(None),
    );
    assert!(as_signer(&mut env, &c, ix).is_ok());
    let j: Job = read(&env.b, &env.job);
    assert!(j.created_slot > env.created_slot);

    // Replaying the old signature/digest is rejected (digest binds created_slot).
    let ixs = [
        ed25519_ix(&env.verifier, &old_digest),
        submit_ix(&env, &w.pubkey(), &a),
    ];
    expect_custom(
        send(&mut env.b, &ixs, &w, &[&w]),
        1,
        code(SettlementError::DigestMismatch),
    );
    // A fresh signature for the new generation is accepted.
    env.created_slot = j.created_slot;
    assert!(submit(&mut env, &a).is_ok());
}
