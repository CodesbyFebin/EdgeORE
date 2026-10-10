use anchor_lang::prelude::*;

use crate::{error::SettlementError, state::*};

#[derive(Accounts)]
#[instruction(section: u16)]
pub struct AcceptJob<'info> {
    /// The node: signs, pays the assignment rent, and becomes the only payee.
    #[account(mut)]
    pub node: Signer<'info>,
    #[account(
        mut,
        seeds = [JOB_SEED, job.creator.as_ref(), &job.job_id.to_le_bytes()],
        bump = job.bump
    )]
    pub job: Account<'info, Job>,
    /// `init` fails if this section was already accepted.
    #[account(
        init,
        payer = node,
        space = 8 + Assignment::INIT_SPACE,
        seeds = [ASSIGNMENT_SEED, job.key().as_ref(), &section.to_le_bytes()],
        bump
    )]
    pub assignment: Account<'info, Assignment>,
    pub system_program: Program<'info, System>,
}

pub fn handle_accept_job(
    ctx: Context<AcceptJob>,
    section: u16,
    terms_hash: [u8; 32],
    consent_hash: [u8; 32],
    limits_hash: [u8; 32],
) -> Result<()> {
    let job_key = ctx.accounts.job.key();
    let assignment_key = ctx.accounts.assignment.key();
    let node = ctx.accounts.node.key();
    let job = &mut ctx.accounts.job;
    let slot = Clock::get()?.slot;

    require!(job.status == JobStatus::Open, SettlementError::JobNotOpen);
    require!(slot <= job.deadline_slot, SettlementError::DeadlinePassed);
    require!(
        section < job.section_count,
        SettlementError::SectionOutOfRange
    );
    // The node states which terms it accepts; they must be the job's.
    require!(terms_hash == job.terms_hash, SettlementError::TermsMismatch);
    require!(
        consent_hash != [0u8; 32] && limits_hash != [0u8; 32],
        SettlementError::InvalidHash
    );

    job.accepted_count = job
        .accepted_count
        .checked_add(1)
        .ok_or(SettlementError::MathOverflow)?;
    job.open_accounts = job
        .open_accounts
        .checked_add(1)
        .ok_or(SettlementError::MathOverflow)?;

    let a = &mut ctx.accounts.assignment;
    a.job = job_key;
    a.section = section;
    a.node = node;
    a.consent_hash = consent_hash;
    a.limits_hash = limits_hash;
    a.accepted_slot = slot;
    a.proof_submitted = false;
    a.settled = false;
    a.bump = ctx.bumps.assignment;

    emit!(JobAccepted {
        job: job_key,
        assignment: assignment_key,
        section,
        node,
        consent_hash,
        limits_hash,
        slot,
    });
    Ok(())
}
