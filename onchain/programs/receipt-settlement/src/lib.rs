//! EdgeORE receipt settlement — DEVNET-ONLY PROTOTYPE.
//!
//! Unaudited. Not deployed anywhere. Not integrated into the EdgeORE Android
//! app. There is no token, no reward and no mining here: a job creator escrows
//! SOL lamports for a fixed number of work sections, an authorized verifier
//! signs (Ed25519) a canonical digest of each accepted receipt, and the worker
//! named in that receipt can claim exactly the signed charge once.
//!
//! See README.md for the full rule set and its limitations.

pub mod digest;
pub mod error;
pub mod instructions;
pub mod state;

use anchor_lang::prelude::*;

pub use instructions::*;
pub use state::*;

declare_id!("8JMkxGfiEfXJ3xJgt9Dh2bzdp6ozL6WQ5E534WfLwHfT");

#[program]
pub mod receipt_settlement {
    use super::*;

    /// Create a job and escrow `budget` lamports in the job's vault PDA.
    pub fn create_job(
        ctx: Context<CreateJob>,
        job_id: u64,
        budget: u64,
        section_count: u16,
        verifier: Pubkey,
    ) -> Result<()> {
        instructions::create_job::handle_create_job(ctx, job_id, budget, section_count, verifier)
    }

    /// Record a verifier-signed receipt. The transaction must contain an
    /// Ed25519 native-program instruction immediately before this one, signed
    /// by `job.verifier` over the canonical receipt digest.
    pub fn submit_receipt(ctx: Context<SubmitReceipt>, args: ReceiptArgs) -> Result<()> {
        instructions::submit_receipt::handle_submit_receipt(ctx, args)
    }

    /// Pay the receipt's worker its signed charge from the vault, exactly once.
    pub fn claim(ctx: Context<Claim>) -> Result<()> {
        instructions::claim::handle_claim(ctx)
    }

    /// Creator cancels the job and reclaims the vault (unspent budget + rent).
    /// Only allowed when no submitted receipt is still unclaimed.
    pub fn cancel_job(ctx: Context<CancelJob>) -> Result<()> {
        instructions::cancel_job::handle_cancel_job(ctx)
    }
}
