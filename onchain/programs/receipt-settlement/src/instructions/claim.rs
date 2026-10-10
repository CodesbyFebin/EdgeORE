use anchor_lang::prelude::*;

use crate::{error::SettlementError, state::*};

#[derive(Accounts)]
pub struct Claim<'info> {
    #[account(mut)]
    pub worker: Signer<'info>,
    #[account(
        mut,
        seeds = [JOB_SEED, job.creator.as_ref(), &job.job_id.to_le_bytes()],
        bump = job.bump
    )]
    pub job: Account<'info, Job>,
    #[account(
        mut,
        seeds = [VAULT_SEED, job.key().as_ref()],
        bump = job.vault_bump,
        has_one = job
    )]
    pub vault: Account<'info, Vault>,
    #[account(
        mut,
        seeds = [RECEIPT_SEED, job.key().as_ref(), &receipt.receipt_id],
        bump = receipt.bump,
        has_one = job,
        has_one = worker
    )]
    pub receipt: Account<'info, Receipt>,
}

pub fn handle_claim(ctx: Context<Claim>) -> Result<()> {
    let receipt = &mut ctx.accounts.receipt;
    require!(!receipt.settled, SettlementError::AlreadySettled);
    let amount = receipt.actual_charge;

    // Never dip into the vault's rent-exempt reserve.
    let vault_info = ctx.accounts.vault.to_account_info();
    let rent_min = Rent::get()?.minimum_balance(vault_info.data_len());
    let available = vault_info
        .lamports()
        .checked_sub(rent_min)
        .ok_or(SettlementError::InsufficientVault)?;
    require!(available >= amount, SettlementError::InsufficientVault);

    let slot = Clock::get()?.slot;
    receipt.settled = true;
    receipt.settled_slot = slot;

    let job = &mut ctx.accounts.job;
    job.paid = job
        .paid
        .checked_add(amount)
        .ok_or(SettlementError::MathOverflow)?;
    require!(job.paid <= job.committed, SettlementError::MathOverflow);
    job.pending_claims = job
        .pending_claims
        .checked_sub(1)
        .ok_or(SettlementError::MathOverflow)?;

    // The vault is owned by this program, so it can debit it directly.
    ctx.accounts.vault.sub_lamports(amount)?;
    ctx.accounts.worker.add_lamports(amount)?;

    emit!(ReceiptClaimed {
        job: ctx.accounts.job.key(),
        receipt: ctx.accounts.receipt.key(),
        worker: ctx.accounts.worker.key(),
        amount,
        slot,
    });
    Ok(())
}
