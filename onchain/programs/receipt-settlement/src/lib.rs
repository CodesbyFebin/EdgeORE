//! EdgeORE receipt settlement — DEVNET-ONLY PROTOTYPE.
//!
//! Unaudited. Not deployed anywhere. Not integrated into the EdgeORE Android
//! app. There is no token, no reward and no mining here: a customer escrows
//! SOL lamports for a job with fixed terms, a deadline and a verifier; nodes
//! accept sections; a node submits a proof; the verifier's Ed25519 signature
//! over the canonical digest settles it to the node exactly once; after the
//! deadline anyone can crank the refund of the remainder to the customer.
//!
//! docs/SETTLEMENT-SPEC.md (v2) is the canonical reference.

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

    /// Customer escrows `budget` lamports; binds terms_hash, verifier and
    /// deadline_slot = Clock::slot + deadline_slots.
    pub fn create_job(
        ctx: Context<CreateJob>,
        job_id: u64,
        terms_hash: [u8; 32],
        budget: u64,
        section_count: u16,
        verifier: Pubkey,
        deadline_slots: u64,
    ) -> Result<()> {
        instructions::create_job::handle_create_job(
            ctx,
            job_id,
            terms_hash,
            budget,
            section_count,
            verifier,
            deadline_slots,
        )
    }

    /// A node accepts one section under the job's terms, recording its
    /// pubkey (the payee), consent_hash and limits_hash.
    pub fn accept_job(
        ctx: Context<AcceptJob>,
        section: u16,
        terms_hash: [u8; 32],
        consent_hash: [u8; 32],
        limits_hash: [u8; 32],
    ) -> Result<()> {
        instructions::accept_job::handle_accept_job(
            ctx,
            section,
            terms_hash,
            consent_hash,
            limits_hash,
        )
    }

    /// The accepted node records its proof (hashes, quote, charge) and
    /// reserves the charge against the budget.
    pub fn submit_proof(ctx: Context<SubmitProof>, section: u16, args: ProofArgs) -> Result<()> {
        instructions::submit_proof::handle_submit_proof(ctx, section, args)
    }

    /// Anyone submits the verifier's Ed25519 signature over the canonical
    /// digest (preceding instruction); pays the recorded node once.
    pub fn verify_and_settle(ctx: Context<VerifyAndSettle>, section: u16) -> Result<()> {
        instructions::verify_and_settle::handle_verify_and_settle(ctx, section)
    }

    /// Permissionless crank after the deadline: closes the vault to the
    /// creator. The cranker signs and pays the fee; no creator signature.
    pub fn refund_after_deadline(ctx: Context<RefundAfterDeadline>) -> Result<()> {
        instructions::refund_after_deadline::handle_refund_after_deadline(ctx)
    }

    /// Creator replaces the verifier; only while no node has accepted.
    pub fn rotate_verifier(ctx: Context<RotateVerifier>, new_verifier: Pubkey) -> Result<()> {
        instructions::rotate_verifier::handle_rotate_verifier(ctx, new_verifier)
    }

    /// After refund: close an assignment (+ its output marker); rent to the
    /// node. Signer: the node or the creator.
    pub fn close_assignment(ctx: Context<CloseAssignment>, section: u16) -> Result<()> {
        instructions::close_assignment::handle_close_assignment(ctx, section)
    }

    /// After refund and once no assignment/marker is open: close the job;
    /// rent to the creator.
    pub fn close_job(ctx: Context<CloseJob>) -> Result<()> {
        instructions::close_job::handle_close_job(ctx)
    }
}
