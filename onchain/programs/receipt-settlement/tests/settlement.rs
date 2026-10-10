//! LiteSVM (in-process) run of every scenario in `tests/common/mod.rs`.

#[macro_use]
mod common;

scenario_tests!(svm;
    happy_path_create_accept_prove_settle_refund_close,
    create_job_binds_terms_verifier_and_clock_deadline,
    create_job_rejects_invalid_parameters,
    create_and_refund_in_one_transaction_is_rejected,
    accept_records_node_consent_and_limits,
    accept_rejects_wrong_terms_zero_hashes_range_and_duplicate,
    deadline_from_clock_is_inclusive_then_closes,
    submit_proof_only_by_accepted_node,
    submit_proof_rejects_bad_charge_second_proof_and_duplicate_output,
    submit_proof_over_budget_is_rejected,
    settle_pays_recorded_node_and_substituted_payout_fails,
    duplicate_settle_moves_zero_lamports_on_every_account,
    settle_without_proof_is_rejected,
    settle_bad_signature_is_rejected_by_ed25519_program,
    settle_missing_ed25519_instruction_is_rejected,
    settle_ed25519_offsets_into_other_instruction_are_rejected,
    settle_wrong_verifier_is_rejected,
    settle_signature_over_different_values_is_rejected,
    refund_crank_is_permissionless_after_deadline_only,
    refund_returns_unsettled_reservations,
    rotate_verifier_allowed_before_accept_and_locked_after,
    rotate_verifier_rejects_non_creator_default_and_same,
    close_assignment_only_after_refund_rent_to_node,
    close_job_requires_refund_and_no_open_accounts,
    recreated_job_rejects_replay_of_old_signature,
);
