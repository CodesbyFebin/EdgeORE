# Project #3 judge customization

Target: https://github.com/users/CodesbyFebin/projects/3

**Live editing status: BLOCKED from the execution environment.** The public project was readable. It was titled `@solana Seeker EdgeORE` and its README contained “production-grade”; this contradicts the recorded runtime qualification. GitHub GraphQL access returned Forbidden, and the connector exposes repository operations but no Projects edit tool. No claim is made that these settings have been applied to the live board.

## Prepared settings

Title: **EdgeORE — Solana Mobile Judge Review**

Short description:

> Native Solana Android review candidate: exact-message review, separate sign/submit and portable receipt integrity. Recorded build evidence; device gates NOT_RUN. Read-only ORE board. No earnings or token.

Project README: replace the current README with `README.md` in this directory. This removes the unsupported production-grade wording and gives judges an evidence-first path through the app and its limitations.

## Views

Keep existing work-progress statuses. Do not rename Done to PASS or delete existing items.

| Existing view | Judge-facing name | Presentation |
|---|---|---|
| Prioritized backlog | Judge review — Start here | Table; show Title, Scope, Evidence gate, Evidence URL, Tested source and Priority; sort Priority |
| Status board | Implementation progress | Board grouped by Status; display Evidence gate on cards |
| Roadmap | Post-win launch | Scope = Post-win launch; no artificial submission deadline |
| Bugs | Runtime blockers | Scope = Device qualification; gate NOT_RUN/BLOCKED/FAIL |
| In review | Source changes for review | Retain In review filter; include draft PR #20 |
| My items | Maintainer work | Keep as a secondary owner view; not the judges' entry point |

Create fields only if absent: Evidence gate (PASS / NOT_RUN / BLOCKED / FAIL), Evidence URL (text), Tested source (text), Scope (Submission review / Device qualification / Post-win launch / Research). Preserve current Priority and Status choices.

## Apply with GitHub CLI on a machine allowed to reach GraphQL

Requirements: `gh` already authenticated with this project's write access (`project` scope for an appropriate classic token, or equivalent project permission). Do not paste credentials into this repository or chat.

```bash
python3 docs/judges/project-3/apply.py --plan
python3 docs/judges/project-3/apply.py --apply
```

The script verifies the owner/project identity, updates title/description/README, creates missing fields and upserts the prepared **draft project cards** by exact title. It does not change visibility, delete existing work, replace Status choices, publish a release or move money. Existing matching draft cards retain their work progress. View presentation/renaming is listed above for the GitHub UI; the script makes no unsupported claim about a view-edit API.

Add source PR #20 to the project separately if desired; its tests and device limitations are in the PR body. The script is locally syntax-checked and its plan validated; live GraphQL execution is BLOCKED, not PASS.
