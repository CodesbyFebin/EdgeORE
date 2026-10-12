#!/usr/bin/env python3
"""Read-only: snapshots the ORE v3 mainnet accounts a Deploy touches, for the LiteSVM mainnet-fork test.
Writes fixtures/ (gitignored). Sends no transaction. Usage: python3 fetch.py [rpc]"""
import base64, json, os, struct, subprocess, sys, urllib.request
RPC = sys.argv[1] if len(sys.argv) > 1 else "https://api.mainnet-beta.solana.com"
ORE = "oreV3EG1i9BEgiAJ8b177Z2S2rMarzak4NMv1kULvWv"; ENTROPY = "3jSkUuYBoJzQPMEzTvkDFXCZUBksPamrVhrnHR9igu2X"
BOARD = "BrcSxdp1nXFzou1YyDnQJcPNBNHgoypZmTsyKBSLLXzi"; TREASURY = "45db2FSR4mcXdSVVZbKbwojU6uYDpMyhpEi7cC8nHaWG"
CONFIG = "9c9X7aDRAF41faiDs94ELjT19UrGnn72wBW9hPsS4Awy"; VAR = "BWCaDY96Xe4WkFq1M7UiCCRcChsJ3p51L5KrGzhxgm2E"
def rpc(method, params):
    req = urllib.request.Request(RPC, json.dumps({"jsonrpc": "2.0", "id": 1, "method": method, "params": params}).encode(), {"content-type": "application/json"})
    return json.load(urllib.request.urlopen(req, timeout=20))["result"]
os.makedirs("fixtures", exist_ok=True)
for pid, name in [(ORE, "ore"), (ENTROPY, "entropy")]:
    subprocess.run(["solana", "program", "dump", "-u", RPC, pid, f"fixtures/{name}.so"], check=True, stdout=subprocess.DEVNULL)
def accounts(keys):
    r = rpc("getMultipleAccounts", [keys, {"encoding": "base64", "commitment": "confirmed"}])
    return r["context"]["slot"], dict(zip(keys, r["value"]))
_, b = accounts([BOARD])
round_id = struct.unpack_from("<Q", base64.b64decode(b[BOARD]["data"][0]), 8)[0]
# Independent derivation with the Solana CLI (not the app's Kotlin code).
ROUND = subprocess.run(["solana", "find-program-derived-address", ORE, "string:round", f"u64le:{round_id}"], check=True, capture_output=True, text=True).stdout.split()[0]
keys = [BOARD, TREASURY, CONFIG, VAR, ROUND]
slot, accts = accounts(keys)
board = base64.b64decode(accts[BOARD]["data"][0]); rid, start, end = struct.unpack_from("<QQQ", board, 8)
if rid != round_id: sys.exit("round rolled over between reads; run again")
out = {"rpc": RPC, "slot": slot, "roundId": round_id, "roundAddress": ROUND, "startSlot": start, "endSlot": end, "accounts": {}}
for k, v in accts.items():
    out["accounts"][k] = {"owner": v["owner"], "lamports": v["lamports"], "data": v["data"][0]}
json.dump(out, open("fixtures/snapshot.json", "w"), indent=1)
print(json.dumps({k: out[k] for k in ("slot", "roundId", "roundAddress", "startSlot", "endSlot")}))
