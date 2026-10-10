use anchor_lang::prelude::*;

/// Codes are 6000 + declaration index. Listed in docs/SETTLEMENT-SPEC.md.
#[error_code]
pub enum SettlementError {
    #[msg("Budget must be greater than zero")]
    InvalidBudget, // 6000
    #[msg("Section count must be greater than zero")]
    InvalidSectionCount, // 6001
    #[msg("Verifier must be a non-default public key")]
    InvalidVerifier, // 6002
    #[msg("deadline_slots must be in 1..=MAX_DEADLINE_SLOTS")]
    InvalidDeadline, // 6003
    #[msg("Hash must not be all zero")]
    InvalidHash, // 6004
    #[msg("Job is not open")]
    JobNotOpen, // 6005
    #[msg("The job deadline has passed")]
    DeadlinePassed, // 6006
    #[msg("The job deadline has not passed yet")]
    DeadlineNotReached, // 6007
    #[msg("Section index is outside the job's section range")]
    SectionOutOfRange, // 6008
    #[msg("Terms hash does not match the job's terms hash")]
    TermsMismatch, // 6009
    #[msg("A proof was already submitted for this assignment")]
    ProofAlreadySubmitted, // 6010
    #[msg("No proof has been submitted for this assignment")]
    NoProof, // 6011
    #[msg("Actual charge must be greater than zero")]
    ZeroCharge, // 6012
    #[msg("Actual charge exceeds the quoted price")]
    ChargeExceedsQuote, // 6013
    #[msg("Charge would exceed the job's remaining budget")]
    OverBudget, // 6014
    #[msg("Missing Ed25519 verification instruction immediately before verify_and_settle")]
    MissingEd25519Instruction, // 6015
    #[msg("Malformed or unsupported Ed25519 verification instruction")]
    InvalidEd25519Instruction, // 6016
    #[msg("Ed25519 signer is not the job's verifier")]
    VerifierMismatch, // 6017
    #[msg("Signed message does not equal the canonical settlement digest")]
    DigestMismatch, // 6018
    #[msg("Assignment already settled")]
    AlreadySettled, // 6019
    #[msg("Payout account is not the node recorded at accept_job")]
    PayoutMismatch, // 6020
    #[msg("Refund account is not the job creator")]
    CreatorMismatch, // 6021
    #[msg("Vault balance is insufficient for this payment")]
    InsufficientVault, // 6022
    #[msg("Arithmetic overflow or underflow")]
    MathOverflow, // 6023
    #[msg("New verifier equals the current verifier")]
    SameVerifier, // 6024
    #[msg("Verifier is locked once any node has accepted")]
    VerifierLocked, // 6025
    #[msg("Signer may not close this account")]
    Unauthorized, // 6026
    #[msg("Job must be refunded before its accounts can be closed")]
    JobStillOpen, // 6027
    #[msg("Output marker must be passed exactly when a proof was submitted")]
    OutputMarkerMismatch, // 6028
    #[msg("Job still has open assignment or output-marker accounts")]
    OpenAccountsRemain, // 6029
    #[msg("Job cannot be closed in the slot it was created")]
    CloseTooEarly, // 6030
}
