import json,base64,struct,urllib.request,hashlib
RPC="https://api.mainnet-beta.solana.com"
def rpc(m,p):
    r=urllib.request.Request(RPC,json.dumps({"jsonrpc":"2.0","id":1,"method":m,"params":p}).encode(),{"Content-Type":"application/json"})
    return json.load(urllib.request.urlopen(r,timeout=20))["result"]
def acct(a):
    v=rpc("getAccountInfo",[a,{"encoding":"base64","commitment":"confirmed"}])
    return v["context"]["slot"],base64.b64decode(v["value"]["data"][0]),v["value"]["owner"]
slot,b,o=acct("BrcSxdp1nXFzou1YyDnQJcPNBNHgoypZmTsyKBSLLXzi")
rid,start,end=struct.unpack_from("<QQQ",b,8)
print("slot",slot,"owner",o,"board round",rid,"start",start,"end",end, "end-start",end-start if end<2**63 else "MAX")
_,c,_=acct("9c9X7aDRAF41faiDs94ELjT19UrGnn72wBW9hPsS4Awy")
# Config: disc 8, admin(32+32+8), protocol(32+32+8+8+8)
off=8+72
pa,pf=c[off:off+32],c[off+32:off+64]; fee,inter,rs=struct.unpack_from("<QQQ",c,off+64)
print("config len",len(c),"protocol fee_rate",fee,"intermission",inter,"round_slots",rs)
_,t,_=acct("45db2FSR4mcXdSVVZbKbwojU6uYDpMyhpEi7cC8nHaWG")
print("treasury motherlode grams",struct.unpack_from("<Q",t,8)[0])
