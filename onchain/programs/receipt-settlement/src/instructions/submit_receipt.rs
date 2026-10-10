use anchor_lang::prelude::*;

use crate::{digest, error::SettlementError, state::*};

#[derive(Accounts)]
#[instruction(args: ReceiptArgs)]
pub struct SubmitReceipt<'info> {
    /// The worker named in (and bound by) the signed digest; pays receipt rent.
    #[account(mut)]
    pub worker: Signer<'info>,
    #[account(
        mut,
        seeds = [JOB_SEED, job.creator.as_ref(), &job.job_id.to_le_bytes()],
        bump = job.bump
    )]
    pub job: Account<'info, Job>,
    /// `init` fails if this receipt id was already used in this job.
    #[account(
        init,
        payer = worker,
        space = 8 + Receipt::INIT_SPACE,
        seeds = [RECEIPT_SEED, job.key().as_ref(), &args.receipt_id],
        bump
    )]
    pub receipt: Account<'info, Receipt>,
    /// `init` fails if this section of the job already has a receipt.
    #[account(
        init,
        payer = worker,
        space = 8 + SectionMarker::INIT_SPACE,
        seeds = [SECTION_SEED, job.key().as_ref(), &args.section.to_le_bytes()],
        bump
    )]
    pub section_marker: Account<'info, SectionMarker>,
    /// `init` fails if this exact output hash was already accepted in this job.
    #[account(
        init,
        payer = worker,
        space = 8 + OutputMarker::INIT_SPACE,
        seeds = [OUTPUT_SEED, job.key().as_ref(), &args.output_hash],
        bump
    )]
    pub output_marker: Account<'info, OutputMarker>,
    /// CHECK: address-constrained to the instructions sysvar; read via the
    /// checked loaders in digest.rs.
    #[account(address = solana_sdk_ids::sysvar::instructions::ID)]
    pub instructions_sysvar: UncheckedAccount<'info>,
    pub system_program: Program<'info, System>,
}

pub fn handle_submit_receipt(ctx: Context<SubmitReceipt>, args: ReceiptArgs) -> Result<()> {
    let job_key = ctx.accounts.job.key();
    let receipt_key = ctx.accounts.receipt.key();
    let worker_key = ctx.accounts.worker.key();
    let job = &mut ctx.accounts.job;

    require!(
        job.status == JobStatus::Active,
        SettlementError::JobNotActive
    );
    require!(
        args.section < job.section_count,
        SettlementError::SectionOutOfRange
    );
    require!(args.actual_charge > 0, SettlementError::ZeroCharge);
    // No tolerance: the verifier-signed charge may never exceed the quote.
    require!(
        args.actual_charge <= args.quoted_price,
        SettlementError::ChargeExceedsQuote
    );
    let new_committed = job
        .committed
        .checked_add(args.actual_charge)
        .ok_or(SettlementError::MathOverflow)?;
    require!(new_committed <= job.budget, SettlementError::OverBudget);

    let expected = digest::receipt_digest(ctx.program_id, &job_key, job.job_id, &worker_key, &args);
    digest::verify_preceding_ed25519(
        &ctx.accounts.instructions_sysvar.to_account_info(),
        &job.verifier,
        &expected,
    )?;

    job.committed = new_committed;
    job.receipt_count = job
        .receipt_count
        .checked_add(1)
        .ok_or(SettlementError::MathOverflow)?;
    job.pending_claims = job
        .pending_claims
        .checked_add(1)
        .ok_or(SettlementError::MathOverflow)?;

    let slot = Clock::get()?.slot;
    let claim_deadline_slot = slot
        .checked_add(job.claim_window_slots)
        .ok_or(SettlementError::MathOverflow)?;
    job.claim_deadline = job.claim_deadline.max(claim_deadline_slot);

    let receipt = &mut ctx.accounts.receipt;
    receipt.job = job_key;
    receipt.worker = worker_key;
    receipt.receipt_id = args.receipt_id;
    receipt.section = args.section;
    receipt.input_hash = args.input_hash;
    receipt.output_hash = args.output_hash;
    receipt.model_hash = args.model_hash;
    receipt.quoted_price = args.quoted_price;
    receipt.actual_charge = args.actual_charge;
    receipt.digest = expected;
    receipt.submitted_slot = slot;
    receipt.claim_deadline_slot = claim_deadline_slot;
    receipt.settled = false;
    receipt.settled_slot = 0;
    receipt.bump = ctx.bumps.receipt;

    ctx.accounts.section_marker.receipt = receipt_key;
    ctx.accounts.output_marker.receipt = receipt_key;

    emit!(ReceiptSubmitted {
        job: job_key,
        receipt: receipt_key,
        worker: worker_key,
        section: args.section,
        actual_charge: args.actual_charge,
    });
    Ok(())
}
