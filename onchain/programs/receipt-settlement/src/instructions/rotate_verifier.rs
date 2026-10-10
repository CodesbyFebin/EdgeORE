use anchor_lang::prelude::*;

use crate::{error::SettlementError, state::*};

#[derive(Accounts)]
pub struct RotateVerifier<'info> {
    pub creator: Signer<'info>,
    #[account(
        mut,
        seeds = [JOB_SEED, creator.key().as_ref(), &job.job_id.to_le_bytes()],
        bump = job.bump,
        has_one = creator
    )]
    pub job: Account<'info, Job>,
}

pub fn handle_rotate_verifier(ctx: Context<RotateVerifier>, new_verifier: Pubkey) -> Result<()> {
    let job = &mut ctx.accounts.job;
    require!(
        job.status == JobStatus::Active,
        SettlementError::JobNotActive
    );
    require!(
        new_verifier != Pubkey::default(),
        SettlementError::InvalidVerifier
    );
    require!(new_verifier != job.verifier, SettlementError::SameVerifier);

    let old = job.verifier;
    // submit_receipt compares the Ed25519 signer against job.verifier at
    // submission time, so receipts signed by the old key are rejected from
    // now on. Receipts already accepted stay valid and claimable.
    job.verifier = new_verifier;
    emit!(VerifierRotated {
        job: job.key(),
        old_verifier: old,
        new_verifier,
    });
    Ok(())
}
