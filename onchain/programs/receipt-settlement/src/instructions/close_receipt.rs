use anchor_lang::prelude::*;

use crate::{error::SettlementError, state::*};

#[derive(Accounts)]
pub struct CloseReceipt<'info> {
    pub closer: Signer<'info>,
    /// CHECK: rent destination only; constrained to receipt.worker by has_one.
    #[account(mut)]
    pub worker: UncheckedAccount<'info>,
    #[account(
        mut,
        seeds = [JOB_SEED, job.creator.as_ref(), &job.job_id.to_le_bytes()],
        bump = job.bump
    )]
    pub job: Account<'info, Job>,
    #[account(
        mut,
        seeds = [RECEIPT_SEED, job.key().as_ref(), &receipt.receipt_id],
        bump = receipt.bump,
        has_one = job,
        has_one = worker,
        close = worker
    )]
    pub receipt: Account<'info, Receipt>,
}

pub fn handle_close_receipt(ctx: Context<CloseReceipt>) -> Result<()> {
    let job = &mut ctx.accounts.job;
    let receipt = &ctx.accounts.receipt;
    let closer = ctx.accounts.closer.key();

    // Only a receipt that can no longer move money may be closed.
    let slot = Clock::get()?.slot;
    require!(
        receipt.settled || slot > receipt.claim_deadline_slot,
        SettlementError::ReceiptStillClaimable
    );
    // The worker may close any time; the creator only after finalization
    // (needed so the creator can clean up and then close the job).
    require!(
        closer == receipt.worker || (closer == job.creator && job.status != JobStatus::Active),
        SettlementError::Unauthorized
    );
    // Replay safety: while the job is active the section and output markers
    // stay, so re-submitting this signed receipt fails on the section marker.

    job.open_accounts = job
        .open_accounts
        .checked_sub(1)
        .ok_or(SettlementError::MathOverflow)?;

    emit!(AccountsClosed {
        job: job.key(),
        receipt: receipt.key(),
        rent_to: receipt.worker,
        lamports: receipt.to_account_info().lamports(),
    });
    Ok(())
}
