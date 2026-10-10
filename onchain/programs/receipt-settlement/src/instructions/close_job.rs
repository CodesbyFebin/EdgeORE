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
    // Refunded jobs only; their vault was already closed by the refund crank.
    require!(
        job.status == JobStatus::Refunded,
        SettlementError::JobStillOpen
    );
    // No assignment/marker PDA derived from this job address may outlive it,
    // so a job re-created at the same address starts from a clean slate.
    require!(job.open_accounts == 0, SettlementError::OpenAccountsRemain);
    // A re-created job must get a strictly larger created_slot (part of the
    // signed digest). Already implied: Refunded needs slot > deadline_slot >=
    // created_slot + 1. Kept as an explicit guard.
    require!(
        Clock::get()?.slot > job.created_slot,
        SettlementError::CloseTooEarly
    );
    emit!(JobClosed {
        job: job.key(),
        creator: job.creator,
        lamports: job.to_account_info().lamports(),
    });
    Ok(())
}
