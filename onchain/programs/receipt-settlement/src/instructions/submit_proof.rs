use anchor_lang::prelude::*;

use crate::{error::SettlementError, state::*};

#[derive(Accounts)]
#[instruction(section: u16, args: ProofArgs)]
pub struct SubmitProof<'info> {
    /// Must be the node recorded at accept_job; pays the output-marker rent.
    #[account(mut)]
    pub node: Signer<'info>,
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
        has_one = node
    )]
    pub assignment: Account<'info, Assignment>,
    /// `init` fails if this exact output hash was already submitted in this job.
    #[account(
        init,
        payer = node,
        space = 8 + OutputMarker::INIT_SPACE,
        seeds = [OUTPUT_SEED, job.key().as_ref(), &args.output_hash],
        bump
    )]
    pub output_marker: Account<'info, OutputMarker>,
    pub system_program: Program<'info, System>,
}

pub fn handle_submit_proof(ctx: Context<SubmitProof>, section: u16, args: ProofArgs) -> Result<()> {
    let job_key = ctx.accounts.job.key();
    let assignment_key = ctx.accounts.assignment.key();
    let node = ctx.accounts.node.key();
    let job = &mut ctx.accounts.job;
    let slot = Clock::get()?.slot;

    require!(job.status == JobStatus::Open, SettlementError::JobNotOpen);
    require!(slot <= job.deadline_slot, SettlementError::DeadlinePassed);
    let a = &mut ctx.accounts.assignment;
    require!(!a.proof_submitted, SettlementError::ProofAlreadySubmitted);
    require!(args.actual_charge > 0, SettlementError::ZeroCharge);
    // No tolerance: the charge may never exceed the quote.
    require!(
        args.actual_charge <= args.quoted_price,
        SettlementError::ChargeExceedsQuote
    );
    // Reserve the charge now, so a recorded proof is always fundable.
    let new_committed = job
        .committed
        .checked_add(args.actual_charge)
        .ok_or(SettlementError::MathOverflow)?;
    require!(new_committed <= job.budget, SettlementError::OverBudget);

    job.committed = new_committed;
    job.proof_count = job
        .proof_count
        .checked_add(1)
        .ok_or(SettlementError::MathOverflow)?;
    job.open_accounts = job
        .open_accounts
        .checked_add(1)
        .ok_or(SettlementError::MathOverflow)?;

    a.proof_submitted = true;
    a.receipt_id = args.receipt_id;
    a.input_hash = args.input_hash;
    a.output_hash = args.output_hash;
    a.model_hash = args.model_hash;
    a.quoted_price = args.quoted_price;
    a.actual_charge = args.actual_charge;
    a.proof_slot = slot;

    let m = &mut ctx.accounts.output_marker;
    m.job = job_key;
    m.assignment = assignment_key;
    m.node = node;
    m.output_hash = args.output_hash;

    emit!(ProofSubmitted {
        job: job_key,
        assignment: assignment_key,
        section,
        node,
        actual_charge: args.actual_charge,
        slot,
    });
    Ok(())
}
