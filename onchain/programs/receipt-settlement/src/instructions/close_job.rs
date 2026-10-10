use anchor_lang::prelude::*;

use crate::{error::SettlementError, state::*};

#[derive(Accounts)]
pub struct CloseJob<'info> {
    #[account(mut)]
    pub creator: Signer<'info>,
    #[account(
        mut,
        seeds = [JOB_SEED, creator.key().as_ref(), &job.job_id.to_le_bytes()],
        bump = job.bump,
        has_one = creator,
        close = creator
    )]
    pub job: Account<'info, Job>,
}

pub fn handle_close_job(ctx: Context<CloseJob>) -> Result<()> {
    let job = &ctx.accounts.job;
    // Finalized jobs only; their vault was already closed by cancel_job.
    require!(
        job.status != JobStatus::Active,
        SettlementError::JobStillActive
    );
    // No receipt/marker PDA derived from this job address may outlive it, so a
    // job later re-created at the same address starts from a clean slate.
    require!(job.open_accounts == 0, SettlementError::OpenAccountsRemain);
    // Guarantees a re-created job gets a strictly larger created_slot, which is
    // part of the receipt digest, so old signed receipts cannot be replayed.
    require!(
        Clock::get()?.slot > job.created_slot,
        SettlementError::CloseTooEarly
    );
    Ok(())
}
