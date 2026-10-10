//! The same scenarios against a running local solana-test-validator.
//!
//! Ignored by default. Run with:
//!   anchor test --skip-build --validator legacy --script validator
//! (which starts solana-test-validator with the program at genesis and runs
//! `cargo test --test validator -- --ignored`).
//!
//! Omitted here: `close_job_rejected_in_creation_slot`, which needs two
//! transactions to land in the job's creation slot. Only LiteSVM can pin the
//! slot like that.

#[macro_use]
mod common;

scenario_tests!(validator;
    happy_path_submit_claim_cancel,
    bad_signature_is_rejected_by_ed25519_program,
    missing_ed25519_instruction_is_rejected,
    ed25519_offsets_into_other_instruction_index_are_rejected,
    wrong_verifier_is_rejected,
    tampered_receipt_fields_after_signing_are_rejected,
    over_budget_is_rejected,
    charge_above_quote_is_rejected,
    section_out_of_range_is_rejected,
    duplicate_receipt_id_is_rejected,
    duplicate_section_is_rejected,
    duplicate_output_hash_in_same_job_is_rejected,
    double_claim_is_rejected,
    claim_by_wrong_worker_is_rejected,
    cancel_with_pending_claims_is_rejected_until_claimed,
    cancel_by_non_creator_is_rejected,
    create_job_rejects_invalid_parameters,
    claim_window_recorded_on_receipt_and_job,
    early_cancel_with_unclaimed_receipt_is_rejected_until_deadline_passes,
    cancel_after_deadline_reclaims_unclaimed_funds,
    claim_at_deadline_allowed_after_deadline_rejected,
    later_receipt_extends_job_deadline,
    rotated_verifier_old_key_rejected_new_key_accepted,
    rotate_verifier_rejects_non_creator_default_same_and_inactive,
    worker_closes_settled_receipt_while_job_active_and_replay_still_blocked,
    completed_job_full_rent_reclaim_lifecycle,
    creator_cleans_up_expired_receipt_rent_goes_to_worker,
    recreated_job_rejects_replay_of_old_signed_receipt,
);
