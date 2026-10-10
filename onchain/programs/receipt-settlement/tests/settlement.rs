//! LiteSVM tests for the receipt-settlement program.
//!
//! Signatures are real Ed25519 signatures produced by test keypairs and are
//! verified by the Ed25519 native program loaded into LiteSVM (`precompiles`
//! feature). The receipt digest is recomputed here independently with `sha2`
//! rather than by calling the program's own digest function.

use {
    anchor_lang::{
        error::ERROR_CODE_OFFSET,
        prelude::Pubkey,
        solana_program::{instruction::Instruction, system_program},
        AccountDeserialize, InstructionData, Space, ToAccountMetas,
    },
    litesvm::{types::TransactionResult, LiteSVM},
    receipt_settlement::{error::SettlementError, state::*},
    sha2::{Digest, Sha256},
    solana_keypair::Keypair,
    solana_message::{Message, VersionedMessage},
    solana_signer::Signer,
    solana_transaction::{versioned::VersionedTransaction, InstructionError, TransactionError},
};

const SOL: u64 = 1_000_000_000;
const START_SLOT: u64 = 1_000;
const ED25519_ID: Pubkey = solana_sdk_ids::ed25519_program::ID;
const IX_SYSVAR: Pubkey = solana_sdk_ids::sysvar::instructions::ID;
// Anchor framework error codes used below.
const CONSTRAINT_HAS_ONE: u32 = 2001;
const CONSTRAINT_SEEDS: u32 = 2006;

fn code(e: SettlementError) -> u32 {
    ERROR_CODE_OFFSET + e as u32
}

struct Env {
    svm: LiteSVM,
    creator: Keypair,
    verifier: Keypair,
    worker: Keypair,
    job_id: u64,
    job: Pubkey,
    vault: Pubkey,
    budget: u64,
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
    svm: &mut LiteSVM,
    ixs: &[Instruction],
    payer: &Keypair,
    signers: &[&Keypair],
) -> TransactionResult {
    // Fresh blockhash so a retried identical transaction is executed again
    // instead of being rejected as AlreadyProcessed.
    svm.expire_blockhash();
    let bh = svm.latest_blockhash();
    let msg = Message::new_with_blockhash(ixs, Some(&payer.pubkey()), &bh);
    let tx = VersionedTransaction::try_new(VersionedMessage::Legacy(msg), signers).unwrap();
    svm.send_transaction(tx)
}

fn expect_custom(res: TransactionResult, ix_index: u8, expected: u32) -> Vec<String> {
    let failed = res.expect_err("transaction unexpectedly succeeded");
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

fn setup_with(budget: u64, sections: u16) -> Env {
    let mut svm = LiteSVM::new();
    let bytes = include_bytes!(concat!(
        env!("CARGO_TARGET_TMPDIR"),
        "/../deploy/receipt_settlement.so"
    ));
    svm.add_program(pid(), bytes).unwrap();
    // Non-zero slot so submitted_slot / settled_slot assertions are meaningful.
    svm.warp_to_slot(START_SLOT);
    let creator = Keypair::new();
    let verifier = Keypair::new();
    let worker = Keypair::new();
    svm.airdrop(&creator.pubkey(), 100 * SOL).unwrap();
    svm.airdrop(&worker.pubkey(), 10 * SOL).unwrap();
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
    let res = send(&mut svm, &[ix], &creator, &[&creator]);
    assert!(
        res.is_ok(),
        "create_job failed: {:?}",
        res.err().map(|e| e.meta.pretty_logs())
    );
    Env {
        svm,
        creator,
        verifier,
        worker,
        job_id,
        job,
        vault,
        budget,
    }
}

fn setup() -> Env {
    setup_with(10 * SOL, 4)
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
fn digest(job: &Pubkey, job_id: u64, worker: &Pubkey, a: &ReceiptArgs) -> [u8; 32] {
    let mut h = Sha256::new();
    h.update(b"EdgeORE/receipt-settlement/v1");
    h.update(pid().as_ref());
    h.update(job.as_ref());
    h.update(job_id.to_le_bytes());
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
    let d = digest(&env.job, env.job_id, &env.worker.pubkey(), a);
    let ixs = [
        ed25519_ix(&env.verifier, &d),
        submit_ix(env, &env.worker.pubkey(), a),
    ];
    let w = env.worker.insecure_clone();
    send(&mut env.svm, &ixs, &w, &[&w])
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
    send(&mut env.svm, &[ix], &w, &[&w])
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
    send(&mut env.svm, &[ix], &c, &[&c])
}

fn read<T: AccountDeserialize>(svm: &LiteSVM, key: &Pubkey) -> T {
    let acc = svm.get_account(key).expect("account missing");
    T::try_deserialize(&mut acc.data.as_slice()).unwrap()
}

fn exists(svm: &LiteSVM, key: &Pubkey) -> bool {
    svm.get_account(key)
        .map(|a| a.lamports > 0)
        .unwrap_or(false)
}

fn vault_rent(svm: &LiteSVM) -> u64 {
    svm.minimum_balance_for_rent_exemption(8 + Vault::INIT_SPACE)
}

// ---------------------------------------------------------------- tests

#[test]
fn happy_path_submit_claim_cancel() {
    let mut env = setup();
    let rent = vault_rent(&env.svm);
    assert_eq!(env.svm.get_balance(&env.vault).unwrap(), env.budget + rent);

    let a = args(1, 0, 2 * SOL, SOL + 500);
    let res = submit(&mut env, &a);
    assert!(res.is_ok(), "{}", res.err().unwrap().meta.pretty_logs());

    let r: Receipt = read(&env.svm, &receipt_pda(&env.job, &a.receipt_id));
    assert_eq!(r.worker, env.worker.pubkey());
    assert_eq!(r.actual_charge, SOL + 500);
    assert_eq!(
        r.digest,
        digest(&env.job, env.job_id, &env.worker.pubkey(), &a)
    );
    assert!(!r.settled);
    assert_eq!(r.settled_slot, 0);
    assert!(r.submitted_slot >= START_SLOT);
    let j: Job = read(&env.svm, &env.job);
    assert_eq!(
        (j.committed, j.paid, j.pending_claims, j.receipt_count),
        (SOL + 500, 0, 1, 1)
    );

    let before = env.svm.get_balance(&env.worker.pubkey()).unwrap();
    let res = claim(&mut env, &a.receipt_id);
    assert!(res.is_ok(), "{}", res.err().unwrap().meta.pretty_logs());
    let fee = res.unwrap().fee;
    let after = env.svm.get_balance(&env.worker.pubkey()).unwrap();
    assert_eq!(
        after + fee,
        before + SOL + 500,
        "worker must receive exactly the signed charge"
    );
    assert_eq!(
        env.svm.get_balance(&env.vault).unwrap(),
        env.budget - (SOL + 500) + rent
    );

    let r: Receipt = read(&env.svm, &receipt_pda(&env.job, &a.receipt_id));
    assert!(r.settled);
    assert!(r.settled_slot >= START_SLOT);
    let j: Job = read(&env.svm, &env.job);
    assert_eq!((j.paid, j.pending_claims), (SOL + 500, 0));

    let c_before = env.svm.get_balance(&env.creator.pubkey()).unwrap();
    let res = cancel(&mut env);
    assert!(res.is_ok(), "{}", res.err().unwrap().meta.pretty_logs());
    let fee = res.unwrap().fee;
    let c_after = env.svm.get_balance(&env.creator.pubkey()).unwrap();
    assert_eq!(
        c_after + fee,
        c_before + env.budget - (SOL + 500) + rent,
        "creator gets unspent budget + vault rent"
    );
    assert!(!exists(&env.svm, &env.vault), "vault closed");
    let j: Job = read(&env.svm, &env.job);
    assert_eq!(j.status, JobStatus::Cancelled);

    // Submitting to a cancelled job is rejected.
    let b = args(2, 1, SOL, SOL);
    expect_custom(submit(&mut env, &b), 1, code(SettlementError::JobNotActive));
}

#[test]
fn bad_signature_is_rejected_by_ed25519_program() {
    let mut env = setup();
    let a = args(1, 0, SOL, SOL);
    let d = digest(&env.job, env.job_id, &env.worker.pubkey(), &a);
    let mut sig: [u8; 64] = env.verifier.sign_message(&d).as_ref().try_into().unwrap();
    sig[10] ^= 0x01;
    let ixs = [
        ed25519_ix_raw(env.verifier.pubkey().as_ref(), &sig, &d, u16::MAX),
        submit_ix(&env, &env.worker.pubkey(), &a),
    ];
    let w = env.worker.insecure_clone();
    let failed = send(&mut env.svm, &ixs, &w, &[&w]).expect_err("bad signature accepted");
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
    assert!(!exists(&env.svm, &receipt_pda(&env.job, &a.receipt_id)));
}

#[test]
fn missing_ed25519_instruction_is_rejected() {
    let mut env = setup();
    let a = args(1, 0, SOL, SOL);
    let ix = submit_ix(&env, &env.worker.pubkey(), &a);
    let w = env.worker.insecure_clone();
    expect_custom(
        send(&mut env.svm, &[ix], &w, &[&w]),
        0,
        code(SettlementError::MissingEd25519Instruction),
    );
}

#[test]
fn ed25519_offsets_into_other_instruction_index_are_rejected() {
    // Explicit index 0 resolves to the same bytes for the native program, but
    // the program only accepts the self-referencing u16::MAX form.
    let mut env = setup();
    let a = args(1, 0, SOL, SOL);
    let d = digest(&env.job, env.job_id, &env.worker.pubkey(), &a);
    let sig = env.verifier.sign_message(&d);
    let ixs = [
        ed25519_ix_raw(env.verifier.pubkey().as_ref(), sig.as_ref(), &d, 0),
        submit_ix(&env, &env.worker.pubkey(), &a),
    ];
    let w = env.worker.insecure_clone();
    expect_custom(
        send(&mut env.svm, &ixs, &w, &[&w]),
        1,
        code(SettlementError::InvalidEd25519Instruction),
    );
}

#[test]
fn wrong_verifier_is_rejected() {
    let mut env = setup();
    let a = args(1, 0, SOL, SOL);
    let d = digest(&env.job, env.job_id, &env.worker.pubkey(), &a);
    let impostor = Keypair::new();
    let ixs = [
        ed25519_ix(&impostor, &d),
        submit_ix(&env, &env.worker.pubkey(), &a),
    ];
    let w = env.worker.insecure_clone();
    expect_custom(
        send(&mut env.svm, &ixs, &w, &[&w]),
        1,
        code(SettlementError::VerifierMismatch),
    );
    assert!(!exists(&env.svm, &receipt_pda(&env.job, &a.receipt_id)));
}

#[test]
fn tampered_receipt_fields_after_signing_are_rejected() {
    let mut env = setup();
    let signed = args(1, 0, 2 * SOL, SOL);
    let d = digest(&env.job, env.job_id, &env.worker.pubkey(), &signed);
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
        let res = send(&mut env.svm, &ixs, &w, &[&w]);
        assert!(res.is_err(), "tampered {name} accepted");
        expect_custom(res, 1, code(SettlementError::DigestMismatch));
    }
    // A different worker cannot submit a receipt signed for env.worker.
    let thief = Keypair::new();
    env.svm.airdrop(&thief.pubkey(), SOL).unwrap();
    let ixs = [
        ed25519_ix(&env.verifier, &d),
        submit_ix(&env, &thief.pubkey(), &signed),
    ];
    expect_custom(
        send(&mut env.svm, &ixs, &thief, &[&thief]),
        1,
        code(SettlementError::DigestMismatch),
    );
    // The untampered receipt still goes through.
    assert!(submit(&mut env, &signed).is_ok());
}

#[test]
fn over_budget_is_rejected() {
    let mut env = setup_with(1_000_000, 4);
    assert!(submit(&mut env, &args(1, 0, 600_000, 600_000)).is_ok());
    expect_custom(
        submit(&mut env, &args(2, 1, 500_000, 400_001)),
        1,
        code(SettlementError::OverBudget),
    );
    // Exactly reaching the budget is allowed.
    assert!(submit(&mut env, &args(3, 2, 400_000, 400_000)).is_ok());
    let j: Job = read(&env.svm, &env.job);
    assert_eq!(j.committed, 1_000_000);
}

#[test]
fn charge_above_quote_is_rejected() {
    let mut env = setup();
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

#[test]
fn section_out_of_range_is_rejected() {
    let mut env = setup_with(SOL, 2);
    expect_custom(
        submit(&mut env, &args(1, 2, 1_000, 1_000)),
        1,
        code(SettlementError::SectionOutOfRange),
    );
}

#[test]
fn duplicate_receipt_id_is_rejected() {
    let mut env = setup();
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
    let j: Job = read(&env.svm, &env.job);
    assert_eq!(j.receipt_count, 1);
}

#[test]
fn duplicate_section_is_rejected() {
    let mut env = setup();
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

#[test]
fn duplicate_output_hash_in_same_job_is_rejected() {
    let mut env = setup();
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

#[test]
fn double_claim_is_rejected() {
    let mut env = setup();
    let a = args(1, 0, SOL, SOL);
    assert!(submit(&mut env, &a).is_ok());
    assert!(claim(&mut env, &a.receipt_id).is_ok());
    let vault_before = env.svm.get_balance(&env.vault).unwrap();
    expect_custom(
        claim(&mut env, &a.receipt_id),
        0,
        code(SettlementError::AlreadySettled),
    );
    assert_eq!(env.svm.get_balance(&env.vault).unwrap(), vault_before);
    let j: Job = read(&env.svm, &env.job);
    assert_eq!((j.paid, j.pending_claims), (SOL, 0));
}

#[test]
fn claim_by_wrong_worker_is_rejected() {
    let mut env = setup();
    let a = args(1, 0, SOL, SOL);
    assert!(submit(&mut env, &a).is_ok());
    let thief = Keypair::new();
    env.svm.airdrop(&thief.pubkey(), SOL).unwrap();
    let ix = claim_ix(&env, &thief.pubkey(), &a.receipt_id);
    expect_custom(
        send(&mut env.svm, &[ix], &thief, &[&thief]),
        0,
        CONSTRAINT_HAS_ONE,
    );
    let r: Receipt = read(&env.svm, &receipt_pda(&env.job, &a.receipt_id));
    assert!(!r.settled);
    // The rightful worker can still claim.
    assert!(claim(&mut env, &a.receipt_id).is_ok());
}

#[test]
fn cancel_with_pending_claims_is_rejected_until_claimed() {
    let mut env = setup();
    let a = args(1, 0, SOL, SOL);
    assert!(submit(&mut env, &a).is_ok());
    expect_custom(cancel(&mut env), 0, code(SettlementError::PendingClaims));
    assert!(exists(&env.svm, &env.vault));
    assert!(claim(&mut env, &a.receipt_id).is_ok());
    assert!(cancel(&mut env).is_ok());
    // Second cancel: vault is gone, so account validation fails before the handler.
    assert!(cancel(&mut env).is_err());
}

#[test]
fn cancel_by_non_creator_is_rejected() {
    let mut env = setup();
    let other = Keypair::new();
    env.svm.airdrop(&other.pubkey(), SOL).unwrap();
    let ix = cancel_ix(&env, &other.pubkey());
    // Job PDA seeds include the creator, so a different signer fails the seeds check.
    expect_custom(
        send(&mut env.svm, &[ix], &other, &[&other]),
        0,
        CONSTRAINT_SEEDS,
    );
    assert!(exists(&env.svm, &env.vault));
}

#[test]
fn create_job_rejects_invalid_parameters() {
    let mut svm = LiteSVM::new();
    let bytes = include_bytes!(concat!(
        env!("CARGO_TARGET_TMPDIR"),
        "/../deploy/receipt_settlement.so"
    ));
    svm.add_program(pid(), bytes).unwrap();
    let creator = Keypair::new();
    svm.airdrop(&creator.pubkey(), 10 * SOL).unwrap();
    let verifier = Keypair::new().pubkey();
    for (i, (budget, sections, ver, err)) in [
        (0u64, 1u16, verifier, SettlementError::InvalidBudget),
        (SOL, 0u16, verifier, SettlementError::InvalidSectionCount),
        (
            SOL,
            1u16,
            Pubkey::default(),
            SettlementError::InvalidVerifier,
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
        expect_custom(send(&mut svm, &[ix], &creator, &[&creator]), 0, code(err));
    }
}
