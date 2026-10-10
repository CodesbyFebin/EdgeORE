use anchor_lang::prelude::*;

use crate::{digest, error::SettlementError, state::*};

#[derive(Accounts)]
#[instruction(section: u16)]
pub struct VerifyAndSettle<'info> {
    /// Anyone (node, verifier, a relayer). Pays only the transaction fee and
    /// receives nothing; authorization comes from the verifier's signature.
    pub settler: Signer<'info>,
    /// CHECK: payout destination only; must equal the node recorded at accept.
    #[account(
        mut,
        constraint = node.key() == assignment.node @ SettlementError::PayoutMismatch
    )]
    pub node: UncheckedAccount<'info>,
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
        seeds = [ASSIGNMENT_SEED, job.key().as_ref(), &section.to_le_bytes()],
        bump = assignment.bump,
        has_one = job
    )]
    pub assignment: Account<'info, Assignment>,
    /// CHECK: address-constrained to the instructions sysvar; read via the
    /// checked loaders in digest.rs.
    #[account(address = solana_sdk_ids::sysvar::instructions::ID)]
    pub instructions_sysvar: UncheckedAccount<'info>,
}

pub fn handle_verify_and_settle(ctx: Context<VerifyAndSettle>, section: u16) -> Result<()> {
    let job_key = ctx.accounts.job.key();
    let slot = Clock::get()?.slot;
    {
        let job = &ctx.accounts.job;
        let a = &ctx.accounts.assignment;
        require!(job.status == JobStatus::Open, SettlementError::JobNotOpen);
        require!(slot <= job.deadline_slot, SettlementError::DeadlinePassed);
        require!(a.proof_submitted, SettlementError::NoProof);
        // Settle-once: the structural flag on the assignment account.
        require!(!a.settled, SettlementError::AlreadySettled);

        let expected = digest::settlement_digest(ctx.program_id, &job_key, job, a);
        digest::verify_preceding_ed25519(
            &ctx.accounts.instructions_sysvar.to_account_info(),
            &job.verifier,
            &expected,
        )?;
    }

    let amount = ctx.accounts.assignment.actual_charge;
    let job = &mut ctx.accounts.job;
    let new_paid = job
        .paid
        .checked_add(amount)
        .ok_or(SettlementError::MathOverflow)?;
    // Implied by the reservation in submit_proof; kept as defense in depth.
    require!(
        new_paid <= job.committed && new_paid <= job.budget,
        SettlementError::OverBudget
    );
    // Never dip into the vault's rent-exempt reserve.
    let vault_info = ctx.accounts.vault.to_account_info();
    let rent_min = Rent::get()?.minimum_balance(vault_info.data_len());
    let available = vault_info
        .lamports()
        .checked_sub(rent_min)
        .ok_or(SettlementError::InsufficientVault)?;
    require!(available >= amount, SettlementError::InsufficientVault);

    job.paid = new_paid;
    job.settled_count = job
        .settled_count
        .checked_add(1)
        .ok_or(SettlementError::MathOverflow)?;
    let a = &mut ctx.accounts.assignment;
    a.settled = true;
    a.settled_slot = slot;
    a.settled_amount = amount;

    // The vault is owned by this program, so it can debit it directly.
    ctx.accounts.vault.sub_lamports(amount)?;
    ctx.accounts.node.add_lamports(amount)?;

    emit!(Settled {
        job: job_key,
        assignment: ctx.accounts.assignment.key(),
        section,
        node: ctx.accounts.node.key(),
        amount,
        settler: ctx.accounts.settler.key(),
        slot,
    });
    Ok(())
}
