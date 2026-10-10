use anchor_lang::prelude::*;

use crate::{error::SettlementError, state::*};

#[derive(Accounts)]
#[instruction(job_id: u64)]
pub struct CreateJob<'info> {
    /// Customer: signs, pays job + vault rent and the escrowed budget.
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
    terms_hash: [u8; 32],
    budget: u64,
    section_count: u16,
    verifier: Pubkey,
    deadline_slots: u64,
) -> Result<()> {
    require!(terms_hash != [0u8; 32], SettlementError::InvalidHash);
    require!(budget > 0, SettlementError::InvalidBudget);
    require!(section_count > 0, SettlementError::InvalidSectionCount);
    require!(
        verifier != Pubkey::default(),
        SettlementError::InvalidVerifier
    );
    require!(
        deadline_slots > 0 && deadline_slots <= MAX_DEADLINE_SLOTS,
        SettlementError::InvalidDeadline
    );

    // The deadline is a duration; the absolute slot comes from the on-chain
    // Clock of this transaction, never from the client.
    let created_slot = Clock::get()?.slot;
    let deadline_slot = created_slot
        .checked_add(deadline_slots)
        .ok_or(SettlementError::MathOverflow)?;

    let job_key = ctx.accounts.job.key();
    let job = &mut ctx.accounts.job;
    job.creator = ctx.accounts.creator.key();
    job.job_id = job_id;
    job.terms_hash = terms_hash;
    job.verifier = verifier;
    job.budget = budget;
    job.committed = 0;
    job.paid = 0;
    job.section_count = section_count;
    job.accepted_count = 0;
    job.proof_count = 0;
    job.settled_count = 0;
    job.created_slot = created_slot;
    job.deadline_slot = deadline_slot;
    job.open_accounts = 0;
    job.status = JobStatus::Open;
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

    emit!(JobCreated {
        job: job_key,
        creator: ctx.accounts.creator.key(),
        terms_hash,
        verifier,
        budget,
        section_count,
        created_slot,
        deadline_slot,
    });
    Ok(())
}
