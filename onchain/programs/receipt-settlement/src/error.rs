use anchor_lang::prelude::*;

#[error_code]
pub enum SettlementError {
    #[msg("Budget must be greater than zero")]
    InvalidBudget,
    #[msg("Section count must be greater than zero")]
    InvalidSectionCount,
    #[msg("Verifier must be a non-default public key")]
    InvalidVerifier,
    #[msg("Job is not active")]
    JobNotActive,
    #[msg("Section index is outside the job's section range")]
    SectionOutOfRange,
    #[msg("Actual charge must be greater than zero")]
    ZeroCharge,
    #[msg("Actual charge exceeds the quoted price")]
    ChargeExceedsQuote,
    #[msg("Charge would exceed the job's remaining budget")]
    OverBudget,
    #[msg("Missing Ed25519 verification instruction immediately before submit_receipt")]
    MissingEd25519Instruction,
    #[msg("Malformed or unsupported Ed25519 verification instruction")]
    InvalidEd25519Instruction,
    #[msg("Ed25519 signer is not the job's authorized verifier")]
    VerifierMismatch,
    #[msg("Signed message does not equal the canonical receipt digest")]
    DigestMismatch,
    #[msg("Receipt already settled")]
    AlreadySettled,
    #[msg("Job still has unclaimed receipts")]
    PendingClaims,
    #[msg("Vault balance is insufficient for this payment")]
    InsufficientVault,
    #[msg("Arithmetic overflow or underflow")]
    MathOverflow,
}
