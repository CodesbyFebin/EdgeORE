#!/usr/bin/env python3
"""Apply the prepared judge overview to Project #3. No credential values are read or printed."""
import argparse
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OWNER = "CodesbyFebin"
NUMBER = 3
EXPECTED_ID = "PVT_kwHOBeCae84BmrFH"
TITLE = "EdgeORE — Solana Mobile Judge Review"
DESCRIPTION = "Native Solana Android review candidate: exact-message review, separate sign/submit and portable receipt integrity. Recorded build evidence; device gates NOT_RUN. Read-only ORE board. No earnings or token."
BRANCH = "https://github.com/CodesbyFebin/EdgeORE/blob/feature/mint-holographic-design/"
SOURCE = "fefa81facc95"
GATES = BRANCH + "evidence/mint-holographic/RESULTS.md"
BOUNDARIES = BRANCH + "docs/design/mint-implementation-report.md"

CARDS = [
    {"title": "00 — Start here: judge overview and evidence map", "scope": "Submission review", "gate": "PASS", "priority": "P0", "status": "In review", "evidence": BRANCH + "docs/judges/project-3/README.md", "body": "Three-minute judge route: understand exact-message review, inspect the native candidate, read recorded gates, then inspect open device qualification. PASS here means the documentation/evidence map exists; it is not a device or production pass. This Project's live customization must not be claimed until applied."},
    {"title": "01 — Recorded build and regression evidence", "scope": "Submission review", "gate": "PASS", "priority": "P0", "status": "Done", "evidence": GATES, "body": "Recorded separately: Android 336 tests (331 passed, 5 skipped); screenshots/interactions 29/29; standalone checker 14/14; policy tests 12/12; manifest tests 9/9. Lint clean. Do not combine counts or count skipped cases as passes. Environment: Linux build host / Robolectric; no phone."},
    {"title": "02 — Mint native interface: review draft PR #20", "scope": "Submission review", "gate": "PASS", "priority": "P1", "status": "In review", "evidence": "https://github.com/CodesbyFebin/EdgeORE/pull/20", "body": "Shared theme, outline registry, wallet sheet, resource cards, contained mining ring, ORE Bot basic help and optional local reminders. JVM layouts/interactions passed, including 200% text navigation. Draft source proposal; not merged, store-ready or device-qualified. Phone telemetry is source-tagged; absent balances are not zero."},
    {"title": "03 — Receipt integrity: inspect checker and assurance limits", "scope": "Submission review", "gate": "PASS", "priority": "P1", "status": "Done", "evidence": GATES, "body": "Standalone JVM checker 14/14. It verifies the integrity of signed receipt contents. It does not independently confirm chain outcomes, execution, provider acknowledgement or payment. Real-transfer receipt qualification remains a separate NOT_RUN card."},
    {"title": "04 — P0 device gate: wallet, real transfer and observe-only recovery", "scope": "Device qualification", "gate": "NOT_RUN", "priority": "P0", "status": "Backlog", "evidence": BRANCH + "docs/DEVICE-RUNBOOK.md", "body": "Install the exact candidate. Record MWA connect/disconnect, reviewed Devnet transfer, separate submit, RPC confirmation/signature/Explorer evidence, interruption and restart with exactly one send attempt. Obtain separate approval for the specific transfer before signing/submission. No replay when outcome is unknown. No usable Android runtime was attached to the build host."},
    {"title": "05 — P0 device gate: real receipt export and demo video", "scope": "Device qualification", "gate": "NOT_RUN", "priority": "P0", "status": "Backlog", "evidence": BRANCH + "docs/DEVICE-RUNBOOK.md", "body": "After the confirmed device transfer, export its receipt. On a second machine retain original PASS/exit 0 and a valid-JSON signed-body lamports+1 FAIL/exit 1. Record the installed-app walkthrough and artifact hashes. Synthetic checker tests, rendered screens and an old navigation preview cannot close this gate."},
    {"title": "06 — P1 runtime: vault, owned hosts, telemetry and reminders", "scope": "Device qualification", "gate": "NOT_RUN", "priority": "P1", "status": "Backlog", "evidence": BOUNDARIES, "body": "Qualify Keystore/vault, phone-to-node pairing/pin/replay/revoke, configured gateway sessions, owned-host/local AI, native telemetry, reduced motion, notification consent/delivery and TalkBack. Bandwidth relay and cloud workload providers are not implemented/configured. Mine must never display all workers running when a provider is missing."},
    {"title": "07 — Post-win launch: signing, license and dApp Store", "scope": "Post-win launch", "gate": "NOT_RUN", "priority": "P2", "status": "Backlog", "evidence": BOUNDARIES, "body": "Separate from submission review: owner-selected project license, release key custody, non-debuggable signed build, privacy/store declarations, assets and device qualification. Do not publish a release or deploy without specific owner authorization. Owner-supplied rule: dApp Store upload within 30 days of winning; keep it separate from the submission deadline and record the organizer reference before treating it as independently verified."},
    {"title": "08 — Research only: ORE/SKR rewards and paid orchestration", "scope": "Research", "gate": "BLOCKED", "priority": "P2", "status": "Backlog", "evidence": BOUNDARIES, "body": "Current ORE grid is read-only. ORE transactions, verified SKR registry, funded daily claims, authoritative five-start/one-hour eligibility, wallet-backed backend auth, leases and bandwidth/cloud providers are not implemented here. Public balance/RPC runtime checks were blocked by network policy. No ORE/SKR income, payout, token or production claim."},
]

FIELD_QUERY = """query($owner:String!,$number:Int!){user(login:$owner){projectV2(number:$number){id title url fields(first:100){nodes{... on ProjectV2Field{id name dataType} ... on ProjectV2SingleSelectField{id name options{id name}}}}}}}"""

def graphql(query, variables):
    response = subprocess.run(["gh", "api", "graphql", "--input", "-"], input=json.dumps({"query": query, "variables": variables}), text=True, capture_output=True)
    if response.returncode:
        raise RuntimeError("GitHub GraphQL failed: " + response.stderr.strip())
    parsed = json.loads(response.stdout)
    if parsed.get("errors"):
        raise RuntimeError("; ".join(error.get("message", "GraphQL error") for error in parsed["errors"]))
    return parsed["data"]

def project():
    result = graphql(FIELD_QUERY, {"owner": OWNER, "number": NUMBER})
    p = (result.get("user") or {}).get("projectV2")
    if not p or p["id"] != EXPECTED_ID:
        raise RuntimeError("Project identity does not match the inspected user Project #3; no changes applied.")
    return p

def field_map(p):
    return {field["name"]: field for field in p["fields"]["nodes"] if field and field.get("name")}

def set_field(item, field, value):
    if not field:
        return
    if field.get("options") is not None:
        option = next((o for o in field["options"] if o["name"] == value), None)
        if not option:
            raise RuntimeError("Required existing choice missing: " + field["name"] + " / " + value)
        payload = {"singleSelectOptionId": option["id"]}
    else:
        payload = {"text": value}
    graphql("mutation($input:UpdateProjectV2ItemFieldValueInput!){updateProjectV2ItemFieldValue(input:$input){projectV2Item{id}}}", {"input": {"projectId": EXPECTED_ID, "itemId": item, "fieldId": field["id"], "value": payload}})

def apply():
    p = project()
    fields = field_map(p)
    # Resolve required work choices before changing anything. Preserve them; never equate Done with evidence PASS.
    for name, choices in {"Status": {c["status"] for c in CARDS}, "Priority": {c["priority"] for c in CARDS}}.items():
        actual = {o["name"] for o in fields.get(name, {}).get("options", [])}
        if not choices <= actual:
            raise RuntimeError("Existing " + name + " choices do not match the inspected project; review before applying.")
    graphql("mutation($input:UpdateProjectV2Input!){updateProjectV2(input:$input){projectV2{id title url}}}", {"input": {"projectId": EXPECTED_ID, "title": TITLE, "shortDescription": DESCRIPTION, "readme": (ROOT / "README.md").read_text()}})
    new_fields = {"Evidence gate": ["PASS", "NOT_RUN", "BLOCKED", "FAIL"], "Evidence URL": None, "Tested source": None, "Scope": ["Submission review", "Device qualification", "Post-win launch", "Research"]}
    for name, choices in new_fields.items():
        if name in fields:
            continue
        value = {"projectId": EXPECTED_ID, "name": name, "dataType": "SINGLE_SELECT" if choices else "TEXT"}
        if choices:
            value["singleSelectOptions"] = [{"name": c, "color": "GRAY", "description": "Evidence/scope label; never inferred from work Status"} for c in choices]
        graphql("mutation($input:CreateProjectV2FieldInput!){createProjectV2Field(input:$input){field{... on ProjectV2Field{id} ... on ProjectV2SingleSelectField{id}}}}", {"input": value})
    fields = field_map(project())
    drafts = {}
    cursor = None
    while True:
        result = graphql("query($id:ID!,$cursor:String){node(id:$id){... on ProjectV2{items(first:100,after:$cursor){nodes{id content{... on DraftIssue{id title}}} pageInfo{hasNextPage endCursor}}}}}", {"id": EXPECTED_ID, "cursor": cursor})["node"]["items"]
        for item in result["nodes"]:
            content = item.get("content") or {}
            if content.get("title"):
                if content["title"] in drafts:
                    raise RuntimeError("Duplicate existing draft title; resolve before retrying: " + content["title"])
                drafts[content["title"]] = item
        if not result["pageInfo"]["hasNextPage"]:
            break
        cursor = result["pageInfo"]["endCursor"]
    for card in CARDS:
        body = card["body"] + "\n\nEvidence/source: " + card["evidence"] + "\nTested implementation: " + SOURCE + ".\nEvidence gate: " + card["gate"] + "."
        existing = drafts.get(card["title"])
        if existing:
            graphql("mutation($input:UpdateProjectV2DraftIssueInput!){updateProjectV2DraftIssue(input:$input){draftIssue{id}}}", {"input": {"draftIssueId": existing["content"]["id"], "title": card["title"], "body": body}})
            item_id = existing["id"]
        else:
            item_id = graphql("mutation($input:AddProjectV2DraftIssueInput!){addProjectV2DraftIssue(input:$input){projectItem{id}}}", {"input": {"projectId": EXPECTED_ID, "title": card["title"], "body": body}})["addProjectV2DraftIssue"]["projectItem"]["id"]
            set_field(item_id, fields["Status"], card["status"])
        for label, value in [("Evidence gate", card["gate"]), ("Evidence URL", card["evidence"]), ("Tested source", SOURCE), ("Scope", card["scope"]), ("Priority", card["priority"])]:
            set_field(item_id, fields[label], value)
        print("Prepared judge card:", card["title"])
    print("Applied project metadata, fields and judge cards. Configure view presentation using SETUP.md.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="Write the inspected owner Project #3 using authenticated gh")
    parser.add_argument("--plan", action="store_true", help="Print prepared settings only (default)")
    args = parser.parse_args()
    if args.apply and args.plan:
        parser.error("Choose --plan or --apply")
    if len(DESCRIPTION) > 256:
        parser.error("Project description exceeds 256 characters")
    if args.apply:
        try:
            apply()
        except (RuntimeError, ValueError, KeyError) as error:
            raise SystemExit(str(error))
    else:
        print(json.dumps({"owner": OWNER, "number": NUMBER, "title": TITLE, "shortDescription": DESCRIPTION, "readme": str(ROOT / "README.md"), "cards": CARDS}, indent=2))
