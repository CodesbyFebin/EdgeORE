use anchor_lang::prelude::*;

use crate::{error::SettlementError, state::*};

#[derive(Accounts)]
#[instruction(section: u16, output_hash: [u8; 32])]
pub struct CloseMarkers<'info> {
    pub closer: Signer<'info>,
    /// CHECK: rent destination only; constrained to the markers' worker by has_one.
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
        seeds = [SECTION_SEED, job.key().as_ref(), &section.to_le_bytes()],
        bump,
        has_one = job,
        has_one = worker,
        close = worker
    )]
    pub section_marker: Account<'info, SectionMarker>,
    #[account(
        mut,
        seeds = [OUTPUT_SEED, job.key().as_ref(), &output_hash],
        bump,
        has_one = job,
        has_one = worker,
        close = worker
    )]
    pub output_marker: Account<'info, OutputMarker>,
}

pub fn handle_close_markers(
    ctx: Context<CloseMarkers>,
    _section: u16,
    _output_hash: [u8; 32],
) -> Result<()> {
    let job = &mut ctx.accounts.job;
    // Markers are what make (job, section) and (job, output_hash) unique, so
    // they must outlive every possible submission: only after finalization.
    require!(
        job.status != JobStatus::Active,
        SettlementError::MarkersStillNeeded
    );
    require!(
        ctx.accounts.section_marker.receipt == ctx.accounts.output_marker.receipt,
        SettlementError::MarkerMismatch
    );
    let closer = ctx.accounts.closer.key();
    require!(
        closer == ctx.accounts.section_marker.worker || closer == job.creator,
        SettlementError::Unauthorized
    );

    job.open_accounts = job
        .open_accounts
        .checked_sub(2)
        .ok_or(SettlementError::MathOverflow)?;

    let lamports = ctx
        .accounts
        .section_marker
        .to_account_info()
        .lamports()
        .checked_add(ctx.accounts.output_marker.to_account_info().lamports())
        .ok_or(SettlementError::MathOverflow)?;
    emit!(AccountsClosed {
        job: job.key(),
        receipt: ctx.accounts.section_marker.receipt,
        rent_to: ctx.accounts.section_marker.worker,
        lamports,
    });
    Ok(())
}
