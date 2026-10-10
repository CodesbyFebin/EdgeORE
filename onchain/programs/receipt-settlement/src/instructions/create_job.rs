use anchor_lang::prelude::*;

use crate::{error::SettlementError, state::*};

#[derive(Accounts)]
#[instruction(job_id: u64)]
pub struct CreateJob<'info> {
    #[account(mut)]
    pub creator: Signer<'info>,
    #[account(
        init,
        payer = creator,
        space = 8 + Job::INIT_SPACE,
        seeds = [JOB_SEED, creator.key().as_ref(), &job_id.to_le_bytes()],
        bump
    )]
    pub job: Account<'info, Job>,
    #[account(
        init,
        payer = creator,
        space = 8 + Vault::INIT_SPACE,
        seeds = [VAULT_SEED, job.key().as_ref()],
        bump
    )]
    pub vault: Account<'info, Vault>,
    pub system_program: Program<'info, System>,
}

pub fn handle_create_job(
    ctx: Context<CreateJob>,
    job_id: u64,
    budget: u64,
    section_count: u16,
    verifier: Pubkey,
    claim_window_slots: u64,
) -> Result<()> {
    require!(budget > 0, SettlementError::InvalidBudget);
    require!(section_count > 0, SettlementError::InvalidSectionCount);
    require!(
        verifier != Pubkey::default(),
        SettlementError::InvalidVerifier
    );
    require!(claim_window_slots > 0, SettlementError::InvalidClaimWindow);

    let job_key = ctx.accounts.job.key();
    let job = &mut ctx.accounts.job;
    job.creator = ctx.accounts.creator.key();
    job.job_id = job_id;
    job.verifier = verifier;
    job.budget = budget;
    job.committed = 0;
    job.paid = 0;
    job.section_count = section_count;
    job.claim_window_slots = claim_window_slots;
    job.claim_deadline = 0;
    job.receipt_count = 0;
    job.pending_claims = 0;
    job.status = JobStatus::Active;
    job.bump = ctx.bumps.job;
    job.vault_bump = ctx.bumps.vault;

    let vault = &mut ctx.accounts.vault;
    vault.job = job_key;
    vault.bump = ctx.bumps.vault;

    // Escrow the budget on top of the vault's rent-exempt minimum.
    anchor_lang::system_program::transfer(
        CpiContext::new(
            anchor_lang::system_program::ID,
            anchor_lang::system_program::Transfer {
                from: ctx.accounts.creator.to_account_info(),
                to: ctx.accounts.vault.to_account_info(),
            },
        ),
        budget,
    )?;
    Ok(())
}
