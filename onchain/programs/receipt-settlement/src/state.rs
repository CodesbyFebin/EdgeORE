//! Account layouts, seeds, instruction arguments and events.
//! docs/SETTLEMENT-SPEC.md (v2) is the canonical description of all of these.

use anchor_lang::prelude::*;

pub const JOB_SEED: &[u8] = b"job";
pub const VAULT_SEED: &[u8] = b"vault";
pub const ASSIGNMENT_SEED: &[u8] = b"assignment";
pub const OUTPUT_SEED: &[u8] = b"output";

/// Upper bound for `create_job`'s `deadline_slots` (~46 days at 400 ms/slot),
/// so a typo cannot lock a budget practically forever.
pub const MAX_DEADLINE_SLOTS: u64 = 10_000_000;

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy, PartialEq, Eq, Debug, InitSpace)]
pub enum JobStatus {
    /// Accepting, proving and settling allowed while `slot <= deadline_slot`.
    Open,
    /// `refund_after_deadline` ran: vault closed to the creator. Terminal
    /// (apart from rent cleanup and `close_job`).
    Refunded,
}

/// PDA: ["job", creator, job_id u64 LE]. Payer: creator.
#[account]
#[derive(InitSpace)]
pub struct Job {
    pub creator: Pubkey,
    pub job_id: u64,
    /// Hash of the off-chain job terms. Bound at create_job; never changes.
    pub terms_hash: [u8; 32],
    /// Only this key's Ed25519 signature settles a proof. Bound at create_job;
    /// `rotate_verifier` may change it only while no node has accepted.
    pub verifier: Pubkey,
    /// Lamports escrowed in the vault at creation (excludes vault rent).
    pub budget: u64,
    /// Sum of actual_charge over submitted proofs (reserved, settled or not).
    pub committed: u64,
    /// Sum of actual_charge over settled proofs.
    pub paid: u64,
    pub section_count: u16,
    pub accepted_count: u16,
    pub proof_count: u16,
    pub settled_count: u16,
    /// `Clock::slot` of the create_job transaction.
    pub created_slot: u64,
    /// created_slot + deadline_slots, computed on-chain at create_job.
    pub deadline_slot: u64,
    /// Assignment + output-marker accounts not closed yet; close_job needs 0.
    pub open_accounts: u32,
    pub status: JobStatus,
    pub bump: u8,
    pub vault_bump: u8,
}

/// Program-owned lamport escrow. PDA: ["vault", job]. Payer: creator.
#[account]
#[derive(InitSpace)]
pub struct Vault {
    pub job: Pubkey,
    pub bump: u8,
}

/// One node's acceptance of one section, its proof, and its settlement.
/// PDA: ["assignment", job, section u16 LE]. Payer: the node (accept_job).
/// `init` makes (job, section) acceptable exactly once.
#[account]
#[derive(InitSpace)]
pub struct Assignment {
    pub job: Pubkey,
    pub section: u16,
    /// The accepting signer. The only key that may submit the proof and the
    /// only account verify_and_settle pays.
    pub node: Pubkey,
    pub consent_hash: [u8; 32],
    pub limits_hash: [u8; 32],
    pub accepted_slot: u64,
    // ---- proof (zero until submit_proof) ----
    pub proof_submitted: bool,
    pub receipt_id: [u8; 32],
    pub input_hash: [u8; 32],
    pub output_hash: [u8; 32],
    pub model_hash: [u8; 32],
    pub quoted_price: u64,
    pub actual_charge: u64,
    pub proof_slot: u64,
    // ---- settlement ----
    /// Settle-once flag. Set by the single successful verify_and_settle and
    /// never cleared; the account cannot be closed while the job is Open, so
    /// the flag lives as long as settlement is possible.
    pub settled: bool,
    pub settled_slot: u64,
    pub settled_amount: u64,
    pub bump: u8,
}

/// Rejects a repeated output hash within one job.
/// PDA: ["output", job, output_hash]. Payer: the node (submit_proof).
/// A simple exact-duplicate check only; NOT collusion or plagiarism detection.
#[account]
#[derive(InitSpace)]
pub struct OutputMarker {
    pub job: Pubkey,
    pub assignment: Pubkey,
    /// Paid the rent; receives it back on close.
    pub node: Pubkey,
    pub output_hash: [u8; 32],
}

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy, Debug, PartialEq, Eq)]
pub struct ProofArgs {
    pub receipt_id: [u8; 32],
    pub input_hash: [u8; 32],
    pub output_hash: [u8; 32],
    pub model_hash: [u8; 32],
    pub quoted_price: u64,
    pub actual_charge: u64,
}

#[event]
pub struct JobCreated {
    pub job: Pubkey,
    pub creator: Pubkey,
    pub terms_hash: [u8; 32],
    pub verifier: Pubkey,
    pub budget: u64,
    pub section_count: u16,
    pub created_slot: u64,
    pub deadline_slot: u64,
}

#[event]
pub struct JobAccepted {
    pub job: Pubkey,
    pub assignment: Pubkey,
    pub section: u16,
    pub node: Pubkey,
    pub consent_hash: [u8; 32],
    pub limits_hash: [u8; 32],
    pub slot: u64,
}

#[event]
pub struct ProofSubmitted {
    pub job: Pubkey,
    pub assignment: Pubkey,
    pub section: u16,
    pub node: Pubkey,
    pub actual_charge: u64,
    pub slot: u64,
}

#[event]
pub struct Settled {
    pub job: Pubkey,
    pub assignment: Pubkey,
    pub section: u16,
    pub node: Pubkey,
    pub amount: u64,
    pub settler: Pubkey,
    pub slot: u64,
}

#[event]
pub struct JobRefunded {
    pub job: Pubkey,
    pub creator: Pubkey,
    /// Signer (and fee payer) of the crank; receives nothing.
    pub cranker: Pubkey,
    /// Vault lamports returned to the creator (unspent budget + vault rent).
    pub refunded_lamports: u64,
    /// Proofs submitted but not settled before the deadline.
    pub unsettled_proofs: u16,
    pub slot: u64,
}

#[event]
pub struct VerifierRotated {
    pub job: Pubkey,
    pub old_verifier: Pubkey,
    pub new_verifier: Pubkey,
}

#[event]
pub struct AccountsClosed {
    pub job: Pubkey,
    pub assignment: Pubkey,
    pub rent_to: Pubkey,
    pub lamports: u64,
}

#[event]
pub struct JobClosed {
    pub job: Pubkey,
    pub creator: Pubkey,
    pub lamports: u64,
}
