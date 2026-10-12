import json,hashlib,struct,sys,requests
from solders.keypair import Keypair
from solders.pubkey import Pubkey
from solders.instruction import Instruction, AccountMeta
from solders.message import Message
from solders.transaction import Transaction
from solders.hash import Hash
RPC="https://solana-devnet.api.onfinality.io/public"
def rpc(m,p): r=requests.post(RPC,json={"jsonrpc":"2.0","id":1,"method":m,"params":p},timeout=30).json(); return r
assert rpc("getGenesisHash",[])["result"]=="EtWTRABZaYq6iMfeYKouRu166VU2xqa1wcaWoxPkrZBG"
kp=Keypair.from_bytes(bytes(json.load(open("/home/box/secrets/devnet-test-wallet.json"))))
PID=Pubkey.from_string("8JMkxGfiEfXJ3xJgt9Dh2bzdp6ozL6WQ5E534WfLwHfT"); SYS=Pubkey.from_string("11111111111111111111111111111111")
d=json.load(open("../receipt/receipt-export.json")); c=d["checkpoint"]
key_id=bytes.fromhex(hashlib.sha256(open("../receipt/device-key.der","rb").read()).hexdigest())
cp,_=Pubkey.find_program_address([b"receipt-checkpoint",bytes(kp.pubkey()),key_id],PID)
disc=lambda n: hashlib.sha256(f"global:{n}".encode()).digest()[:8]
init=Instruction(PID,disc("init_receipt_checkpoint")+key_id,[AccountMeta(kp.pubkey(),True,True),AccountMeta(cp,False,True),AccountMeta(SYS,False,False)])
rec=Instruction(PID,disc("record_receipt_checkpoint")+struct.pack("<Q",c["lastSequence"])+bytes.fromhex(c["lastSha256"])+bytes.fromhex(c["bundleSha256"])+struct.pack("<I",c["receiptCount"]),
  [AccountMeta(kp.pubkey(),True,False),AccountMeta(cp,False,True)])
bh=rpc("getLatestBlockhash",[{"commitment":"finalized"}])["result"]["value"]["blockhash"]
tx=Transaction([kp],Message([init,rec],kp.pubkey()),Hash.from_string(bh))
import base64
raw=base64.b64encode(bytes(tx)).decode()
sim=rpc("simulateTransaction",[raw,{"encoding":"base64","sigVerify":True}])
print("simulate:",json.dumps(sim["result"]["value"]["err"]), *sim["result"]["value"]["logs"][-3:],sep="\n")
if sim["result"]["value"]["err"] is not None or "--send" not in sys.argv: sys.exit(0 if "--send" not in sys.argv else 1)
r=rpc("sendTransaction",[raw,{"encoding":"base64","maxRetries":0}])
print(json.dumps(r)); json.dump({"checkpointAccount":str(cp),"deviceKeyId":key_id.hex(),"checkpoint":c,"send":r},open("checkpoint-send.json","w"),indent=2)
