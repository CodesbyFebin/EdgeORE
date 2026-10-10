//! Canonical receipt digest and Ed25519 instruction introspection.

use anchor_lang::prelude::*;
use solana_instructions_sysvar::{load_current_index_checked, load_instruction_at_checked};

use crate::{error::SettlementError, state::ReceiptArgs};

/// Domain separator; bump the version if the layout ever changes.
pub const DIGEST_DOMAIN: &[u8] = b"EdgeORE/receipt-settlement/v1";

/// sha256(
///   DIGEST_DOMAIN || program_id(32) || job(32) || job_id(u64 LE) ||
///   receipt_id(32) || section(u16 LE) || worker(32) ||
///   input_hash(32) || output_hash(32) || model_hash(32) ||
///   quoted_price(u64 LE) || actual_charge(u64 LE)
/// )
///
/// Binding the program id, job account and worker prevents a signed receipt
/// from being replayed against another deployment, another job, or paid to a
/// different worker.
pub fn receipt_digest(
    program_id: &Pubkey,
    job: &Pubkey,
    job_id: u64,
    worker: &Pubkey,
    args: &ReceiptArgs,
) -> [u8; 32] {
    solana_sha256_hasher::hashv(&[
        DIGEST_DOMAIN,
        program_id.as_ref(),
        job.as_ref(),
        &job_id.to_le_bytes(),
        &args.receipt_id,
        &args.section.to_le_bytes(),
        worker.as_ref(),
        &args.input_hash,
        &args.output_hash,
        &args.model_hash,
        &args.quoted_price.to_le_bytes(),
        &args.actual_charge.to_le_bytes(),
    ])
    .to_bytes()
}

const SIGNATURE_OFFSETS_START: usize = 2;
const SIGNATURE_OFFSETS_LEN: usize = 14;
const PUBKEY_LEN: usize = 32;
const SIGNATURE_LEN: usize = 64;
/// In the Ed25519 native program, an instruction index of u16::MAX means
/// "the data of this same Ed25519 instruction".
const THIS_INSTRUCTION: u16 = u16::MAX;

fn read_u16(data: &[u8], at: usize) -> Result<u16> {
    let end = at
        .checked_add(2)
        .ok_or(SettlementError::InvalidEd25519Instruction)?;
    let b = data
        .get(at..end)
        .ok_or(SettlementError::InvalidEd25519Instruction)?;
    Ok(u16::from_le_bytes([b[0], b[1]]))
}

fn slice(data: &[u8], offset: u16, len: usize) -> Result<&[u8]> {
    let start = offset as usize;
    let end = start
        .checked_add(len)
        .ok_or(SettlementError::InvalidEd25519Instruction)?;
    data.get(start..end)
        .ok_or_else(|| error!(SettlementError::InvalidEd25519Instruction))
}

/// Require that the instruction immediately preceding the current one is an
/// Ed25519 native-program instruction that verified exactly one signature by
/// `expected_signer` over exactly `expected_message`.
///
/// The Ed25519 program itself fails the whole transaction if the signature is
/// invalid, so reaching this code means the signature verified; what we must
/// check is *which* key and *which* message it verified, and that all offsets
/// point into that same instruction's data (not into attacker-chosen bytes of
/// another instruction).
pub fn verify_preceding_ed25519(
    instructions_sysvar: &AccountInfo,
    expected_signer: &Pubkey,
    expected_message: &[u8; 32],
) -> Result<()> {
    let current = load_current_index_checked(instructions_sysvar)?;
    require!(current > 0, SettlementError::MissingEd25519Instruction);
    let prev_index = (current as usize)
        .checked_sub(1)
        .ok_or(SettlementError::MathOverflow)?;
    let ix = load_instruction_at_checked(prev_index, instructions_sysvar)?;

    require_keys_eq!(
        ix.program_id,
        solana_sdk_ids::ed25519_program::ID,
        SettlementError::MissingEd25519Instruction
    );
    require!(
        ix.accounts.is_empty(),
        SettlementError::InvalidEd25519Instruction
    );

    let data = ix.data.as_slice();
    require!(
        data.len() >= SIGNATURE_OFFSETS_START + SIGNATURE_OFFSETS_LEN,
        SettlementError::InvalidEd25519Instruction
    );
    // Exactly one signature; the padding byte must be zero.
    require!(data[0] == 1, SettlementError::InvalidEd25519Instruction);
    require!(data[1] == 0, SettlementError::InvalidEd25519Instruction);

    let o = SIGNATURE_OFFSETS_START;
    let signature_offset = read_u16(data, o)?;
    let signature_ix = read_u16(data, o + 2)?;
    let pubkey_offset = read_u16(data, o + 4)?;
    let pubkey_ix = read_u16(data, o + 6)?;
    let message_offset = read_u16(data, o + 8)?;
    let message_size = read_u16(data, o + 10)?;
    let message_ix = read_u16(data, o + 12)?;

    require!(
        signature_ix == THIS_INSTRUCTION
            && pubkey_ix == THIS_INSTRUCTION
            && message_ix == THIS_INSTRUCTION,
        SettlementError::InvalidEd25519Instruction
    );
    require!(
        message_size as usize == expected_message.len(),
        SettlementError::DigestMismatch
    );

    // Bounds-check all three regions inside this instruction's data.
    let _signature = slice(data, signature_offset, SIGNATURE_LEN)?;
    let pubkey = slice(data, pubkey_offset, PUBKEY_LEN)?;
    let message = slice(data, message_offset, message_size as usize)?;

    require!(
        pubkey == expected_signer.as_ref(),
        SettlementError::VerifierMismatch
    );
    require!(
        message == expected_message.as_slice(),
        SettlementError::DigestMismatch
    );
    Ok(())
}
