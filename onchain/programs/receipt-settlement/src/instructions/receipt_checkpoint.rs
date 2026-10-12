//! On-chain anchor for an exported receipt chain (hardening 17). Records the latest
//! (sequence, last receipt digest, bundle digest) for one device receipt key, signed
//! by the wallet that owns the checkpoint. It does not verify receipts on-chain: it
//! makes a later rewrite of an already-checkpointed history detectable, because the
//! off-chain checker can compare an export against the recorded digests.
//! Unaudited prototype; not deployed.

use anchor_lang::prelude::*;

use crate::{error::SettlementError, state::*};

#[derive(Accounts)]
#[instruction(device_key_id: [u8; 32])]
pub struct InitReceiptCheckpoint<'info> {
    #[account(mut)]
    pub authority: Signer<'info>,
    #[account(
        init,
        payer = authority,
        space = 8 + ReceiptCheckpoint::INIT_SPACE,
        seeds = [RECEIPT_CHECKPOINT_SEED, authority.key().as_ref(), device_key_id.as_ref()],
        bump
    )]
    pub checkpoint: Account<'info, ReceiptCheckpoint>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct RecordReceiptCheckpoint<'info> {
    pub authority: Signer<'info>,
    #[account(
        mut,
        seeds = [RECEIPT_CHECKPOINT_SEED, authority.key().as_ref(), checkpoint.device_key_id.as_ref()],
        bump = checkpoint.bump,
        has_one = authority
    )]
    pub checkpoint: Account<'info, ReceiptCheckpoint>,
}

pub fn handle_init_receipt_checkpoint(ctx: Context<InitReceiptCheckpoint>, device_key_id: [u8; 32]) -> Result<()> {
    require!(device_key_id != [0u8; 32], SettlementError::InvalidHash);
    let cp = &mut ctx.accounts.checkpoint;
    cp.authority = ctx.accounts.authority.key();
    cp.device_key_id = device_key_id;
    cp.last_sequence = 0;
    cp.last_receipt_sha256 = [0u8; 32];
    cp.bundle_sha256 = [0u8; 32];
    cp.receipt_count = 0;
    cp.recorded_slot = Clock::get()?.slot;
    cp.updates = 0;
    cp.bump = ctx.bumps.checkpoint;
    Ok(())
}

pub fn handle_record_receipt_checkpoint(
    ctx: Context<RecordReceiptCheckpoint>,
    last_sequence: u64,
    last_receipt_sha256: [u8; 32],
    bundle_sha256: [u8; 32],
    receipt_count: u32,
) -> Result<()> {
    require!(last_receipt_sha256 != [0u8; 32] && bundle_sha256 != [0u8; 32], SettlementError::InvalidHash);
    let cp = &mut ctx.accounts.checkpoint;
    // Append-only: the chain only grows, so a checkpoint can never move backwards or be replayed.
    require!(last_sequence > cp.last_sequence, SettlementError::CheckpointNotAdvancing);
    require!(receipt_count > 0 && u64::from(receipt_count) <= last_sequence, SettlementError::CheckpointNotAdvancing);
    let slot = Clock::get()?.slot;
    cp.last_sequence = last_sequence;
    cp.last_receipt_sha256 = last_receipt_sha256;
    cp.bundle_sha256 = bundle_sha256;
    cp.receipt_count = receipt_count;
    cp.recorded_slot = slot;
    cp.updates = cp.updates.checked_add(1).ok_or(SettlementError::MathOverflow)?;
    emit!(ReceiptCheckpointRecorded {
        checkpoint: cp.key(),
        authority: cp.authority,
        device_key_id: cp.device_key_id,
        last_sequence,
        last_receipt_sha256,
        bundle_sha256,
        receipt_count,
        slot,
    });
    Ok(())
}
