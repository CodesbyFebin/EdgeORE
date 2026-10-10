use anchor_lang::prelude::*;

use crate::{error::SettlementError, state::*};

#[derive(Accounts)]
pub struct CancelJob<'info> {
    #[account(mut)]
    pub creator: Signer<'info>,
    #[account(
        mut,
        seeds = [JOB_SEED, creator.key().as_ref(), &job.job_id.to_le_bytes()],
        bump = job.bump,
        has_one = creator
    )]
    pub job: Account<'info, Job>,
    /// Closed to the creator: returns the unspent budget plus vault rent.
    #[account(
        mut,
        seeds = [VAULT_SEED, job.key().as_ref()],
        bump = job.vault_bump,
        has_one = job,
        close = creator
    )]
    pub vault: Account<'info, Vault>,
}

pub fn handle_cancel_job(ctx: Context<CancelJob>) -> Result<()> {
    let job = &mut ctx.accounts.job;
    require!(
        job.status == JobStatus::Active,
        SettlementError::JobNotActive
    );
    // Rule: every accepted receipt must be claimed before the creator can
    // withdraw, so a worker holding a signed, accepted receipt is never left
    // unpaid by a cancellation.
    require!(job.pending_claims == 0, SettlementError::PendingClaims);

    // The job account stays as a Cancelled tombstone so the same
    // (creator, job_id) cannot be re-created over old receipt/section PDAs.
    job.status = JobStatus::Cancelled;

    let refunded = ctx.accounts.vault.to_account_info().lamports();
    emit!(JobCancelled {
        job: job.key(),
        refunded_lamports: refunded,
    });
    Ok(())
}
