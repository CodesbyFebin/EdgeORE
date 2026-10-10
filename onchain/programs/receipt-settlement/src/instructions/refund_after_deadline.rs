use anchor_lang::prelude::*;

use crate::{error::SettlementError, state::*};

#[derive(Accounts)]
pub struct RefundAfterDeadline<'info> {
    /// Permissionless crank: any signer. It is the defined fee payer of the
    /// crank (transaction fee only; this instruction allocates nothing) and
    /// receives nothing. No creator signature is needed.
    pub cranker: Signer<'info>,
    /// CHECK: refund destination only; must equal job.creator.
    #[account(
        mut,
        constraint = creator.key() == job.creator @ SettlementError::CreatorMismatch
    )]
    pub creator: UncheckedAccount<'info>,
    #[account(
        mut,
        seeds = [JOB_SEED, job.creator.as_ref(), &job.job_id.to_le_bytes()],
        bump = job.bump
    )]
    pub job: Account<'info, Job>,
    /// Closed to the creator: unspent budget (incl. unsettled reservations)
    /// plus the vault's rent.
    #[account(
        mut,
        seeds = [VAULT_SEED, job.key().as_ref()],
        bump = job.vault_bump,
        has_one = job,
        close = creator
    )]
    pub vault: Account<'info, Vault>,
}

pub fn handle_refund_after_deadline(ctx: Context<RefundAfterDeadline>) -> Result<()> {
    let slot = Clock::get()?.slot;
    let job = &mut ctx.accounts.job;
    require!(job.status == JobStatus::Open, SettlementError::JobNotOpen);
    require!(
        slot > job.deadline_slot,
        SettlementError::DeadlineNotReached
    );

    job.status = JobStatus::Refunded;
    let unsettled_proofs = job
        .proof_count
        .checked_sub(job.settled_count)
        .ok_or(SettlementError::MathOverflow)?;
    emit!(JobRefunded {
        job: job.key(),
        creator: job.creator,
        cranker: ctx.accounts.cranker.key(),
        refunded_lamports: ctx.accounts.vault.to_account_info().lamports(),
        unsettled_proofs,
        slot,
    });
    Ok(())
}
