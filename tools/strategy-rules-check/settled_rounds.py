import json,base64,struct,urllib.request
from solders.pubkey import Pubkey
from Crypto.Hash import keccak
RPC="https://api.mainnet-beta.solana.com";P=Pubkey.from_string("oreV3EG1i9BEgiAJ8b177Z2S2rMarzak4NMv1kULvWv")
SPLIT=str(Pubkey.from_string("SpLiT11111111111111111111111111111111111112"))
def rpc(m,p):
    r=urllib.request.Request(RPC,json.dumps({"jsonrpc":"2.0","id":1,"method":m,"params":p}).encode(),{"Content-Type":"application/json"})
    return json.load(urllib.request.urlopen(r,timeout=20))["result"]
def k(b): h=keccak.new(digest_bits=256);h.update(b);return h.digest()
def mask(rid):
    rnd=k(struct.pack("<Q",rid));idx=list(range(25));off=0
    for i in range(24,0,-1):
        if off+2>len(rnd): rnd=k(rnd);off=0
        j=struct.unpack_from("<H",rnd,off)[0]%(i+1);idx[i],idx[j]=idx[j],idx[i];off+=2
    m=0
    for x in idx[:10]: m|=1<<x
    return m
board=base64.b64decode(rpc("getAccountInfo",["BrcSxdp1nXFzou1YyDnQJcPNBNHgoypZmTsyKBSLLXzi",{"encoding":"base64"}])["value"]["data"][0])
cur=struct.unpack_from("<Q",board,8)[0]
ok=0;n=0
for rid in range(cur-1,cur-11,-1):
    a=Pubkey.find_program_address([b"round",struct.pack("<Q",rid)],P)[0]
    v=rpc("getAccountInfo",[str(a),{"encoding":"base64"}])["value"]
    if not v: print(rid,"closed");continue
    d=base64.b64decode(v["data"][0]);o=8
    _id=struct.unpack_from("<Q",d,o)[0];o+=8
    dep=struct.unpack_from("<25Q",d,o);o+=200*3
    sh=d[o:o+32];o+=32
    exp,ml=struct.unpack_from("<QQ",d,o);o+=16+32
    o+=200; tv,tr,tm=struct.unpack_from("<QQQ",d,o);o+=24
    top=str(Pubkey.from_bytes(d[o:o+32]))
    r=0
    for q in range(4): r^=struct.unpack_from("<Q",sh,q*8)[0]
    w=r%25; solo=bool(mask(rid)>>w&1)
    adm=sum(max(x//100,1) for x in dep if x>0); prot=sum(max((x-max(x//100,1))//10,1) for i,x in enumerate(dep) if x>0 and i!=w)
    fees_ok= tv==prot and tr==sum(dep)-adm-prot
    split_ok= (top==SPLIT)==(not solo) if dep[w]>0 else None
    n+=1; ok+= bool(fees_ok and split_ok is not False)
    print(rid,"win",w+1,"solo" if solo else "split","top",top[:6],"split_ok",split_ok,"fees_ok",fees_ok,"motherlode",ml,"total",sum(dep))
print("checked",n,"consistent",ok)
