use anchor_lang::prelude::*;

pub const JOB_SEED: &[u8] = b"job";
pub const VAULT_SEED: &[u8] = b"vault";
pub const RECEIPT_SEED: &[u8] = b"receipt";
pub const SECTION_SEED: &[u8] = b"section";
pub const OUTPUT_SEED: &[u8] = b"output";

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy, PartialEq, Eq, Debug, InitSpace)]
pub enum JobStatus {
    Active,
    Cancelled,
}

/// PDA: ["job", creator, job_id.to_le_bytes()]
#[account]
#[derive(InitSpace)]
pub struct Job {
    pub creator: Pubkey,
    pub job_id: u64,
    /// Only this key's Ed25519 signature over a receipt digest is accepted.
    pub verifier: Pubkey,
    /// Lamports escrowed in the vault at creation (excludes vault rent).
    pub budget: u64,
    /// Sum of actual_charge over all accepted receipts (claimed or not).
    pub committed: u64,
    /// Sum of actual_charge over claimed receipts.
    pub paid: u64,
    pub section_count: u16,
    pub receipt_count: u32,
    /// Accepted receipts that have not been claimed yet.
    pub pending_claims: u32,
    pub status: JobStatus,
    pub bump: u8,
    pub vault_bump: u8,
}

/// Program-owned lamport escrow. PDA: ["vault", job]
#[account]
#[derive(InitSpace)]
pub struct Vault {
    pub job: Pubkey,
    pub bump: u8,
}

/// PDA: ["receipt", job, receipt_id]  (receipt ids are unique per job)
#[account]
#[derive(InitSpace)]
pub struct Receipt {
    pub job: Pubkey,
    pub worker: Pubkey,
    pub receipt_id: [u8; 32],
    pub section: u16,
    pub input_hash: [u8; 32],
    pub output_hash: [u8; 32],
    pub model_hash: [u8; 32],
    pub quoted_price: u64,
    pub actual_charge: u64,
    /// Canonical digest the verifier signed (see digest.rs).
    pub digest: [u8; 32],
    pub submitted_slot: u64,
    pub settled: bool,
    /// Slot in which the claim executed; 0 while unsettled. No transaction
    /// signature is stored: a program cannot observe its own tx signature.
    pub settled_slot: u64,
    pub bump: u8,
}

/// Marker making (job, section) unique. PDA: ["section", job, section.to_le_bytes()]
#[account]
#[derive(InitSpace)]
pub struct SectionMarker {
    pub receipt: Pubkey,
}

/// Marker rejecting a repeated output hash within one job.
/// PDA: ["output", job, output_hash]. A simple duplicate check only; it is NOT
/// collusion or plagiarism detection (a trivially different output passes).
#[account]
#[derive(InitSpace)]
pub struct OutputMarker {
    pub receipt: Pubkey,
}

#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy, Debug, PartialEq, Eq)]
pub struct ReceiptArgs {
    pub receipt_id: [u8; 32],
    pub section: u16,
    pub input_hash: [u8; 32],
    pub output_hash: [u8; 32],
    pub model_hash: [u8; 32],
    pub quoted_price: u64,
    pub actual_charge: u64,
}

#[event]
pub struct ReceiptSubmitted {
    pub job: Pubkey,
    pub receipt: Pubkey,
    pub worker: Pubkey,
    pub section: u16,
    pub actual_charge: u64,
}

#[event]
pub struct ReceiptClaimed {
    pub job: Pubkey,
    pub receipt: Pubkey,
    pub worker: Pubkey,
    pub amount: u64,
    pub slot: u64,
}

#[event]
pub struct JobCancelled {
    pub job: Pubkey,
    pub refunded_lamports: u64,
}
