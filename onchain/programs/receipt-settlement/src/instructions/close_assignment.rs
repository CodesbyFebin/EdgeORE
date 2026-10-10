use anchor_lang::prelude::*;

use crate::{error::SettlementError, state::*};

#[derive(Accounts)]
#[instruction(section: u16)]
pub struct CloseAssignment<'info> {
    /// The node or the job creator.
    pub closer: Signer<'info>,
    /// CHECK: rent destination only; must be the node that paid the rent.
    #[account(mut, address = assignment.node @ SettlementError::PayoutMismatch)]
    pub node: UncheckedAccount<'info>,
    #[account(
        mut,
        seeds = [JOB_SEED, job.creator.as_ref(), &job.job_id.to_le_bytes()],
        bump = job.bump
    )]
    pub job: Account<'info, Job>,
    #[account(
        mut,
        seeds = [ASSIGNMENT_SEED, job.key().as_ref(), &section.to_le_bytes()],
        bump = assignment.bump,
        has_one = job,
        close = node
    )]
    pub assignment: Account<'info, Assignment>,
    /// Required exactly when the assignment has a proof.
    #[account(
        mut,
        seeds = [OUTPUT_SEED, job.key().as_ref(), &assignment.output_hash],
        bump,
        has_one = job,
        has_one = assignment,
        close = node
    )]
    pub output_marker: Option<Account<'info, OutputMarker>>,
}

pub fn handle_close_assignment(ctx: Context<CloseAssignment>, _section: u16) -> Result<()> {
    let job = &mut ctx.accounts.job;
    // The assignment carries the settle-once flag and makes (job, section)
    // unique, so it must outlive every possible settlement: Refunded only.
    require!(
        job.status == JobStatus::Refunded,
        SettlementError::JobStillOpen
    );
    let closer = ctx.accounts.closer.key();
    require!(
        closer == ctx.accounts.assignment.node || closer == job.creator,
        SettlementError::Unauthorized
    );
    require!(
        ctx.accounts.assignment.proof_submitted == ctx.accounts.output_marker.is_some(),
        SettlementError::OutputMarkerMismatch
    );

    let mut lamports = ctx.accounts.assignment.to_account_info().lamports();
    let mut closed: u32 = 1;
    if let Some(m) = &ctx.accounts.output_marker {
        lamports = lamports
            .checked_add(m.to_account_info().lamports())
            .ok_or(SettlementError::MathOverflow)?;
        closed = 2;
    }
    job.open_accounts = job
        .open_accounts
        .checked_sub(closed)
        .ok_or(SettlementError::MathOverflow)?;

    emit!(AccountsClosed {
        job: job.key(),
        assignment: ctx.accounts.assignment.key(),
        rent_to: ctx.accounts.node.key(),
        lamports,
    });
    Ok(())
}
