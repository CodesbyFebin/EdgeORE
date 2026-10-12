import json, urllib.request, base64, struct, sys, datetime
from pda import pda, PROG
RPC=sys.argv[1] if len(sys.argv)>1 else 'https://api.mainnet-beta.solana.com'
def call(m,p):
    r=urllib.request.Request(RPC,json.dumps({"jsonrpc":"2.0","id":1,"method":m,"params":p}).encode(),{"content-type":"application/json"})
    return json.load(urllib.request.urlopen(r,timeout=30))
B='BrcSxdp1nXFzou1YyDnQJcPNBNHgoypZmTsyKBSLLXzi'; T='45db2FSR4mcXdSVVZbKbwojU6uYDpMyhpEi7cC8nHaWG'
r1=call('getMultipleAccounts',[[B,T],{"encoding":"base64","commitment":"confirmed"}])
board=base64.b64decode(r1['result']['value'][0]['data'][0])
rid,start,end,ema=struct.unpack_from('<4Q',board,8)
R,bump=pda([b'round',struct.pack('<Q',rid)],PROG)
r2=call('getMultipleAccounts',[[R],{"encoding":"base64","commitment":"confirmed"}])
slot=r2['result']['context']['slot']
perf=call('getRecentPerformanceSamples',[1])['result'][0]
bt=call('getBlockTime',[r1['result']['context']['slot']]).get('result')
out={"rpc":RPC,"capturedAtUtc":datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='seconds'),
 "programId":PROG,"oreSourceCommit":"48c203bd75db3cc45105ec29d8f8db719e5a2263",
 "board":{"address":B,"contextSlot":r1['result']['context']['slot'],"owner":r1['result']['value'][0]['owner'],"dataBase64":r1['result']['value'][0]['data'][0]},
 "treasury":{"address":T,"contextSlot":r1['result']['context']['slot'],"owner":r1['result']['value'][1]['owner'],"dataBase64":r1['result']['value'][1]['data'][0]},
 "round":{"address":R,"bump":bump,"contextSlot":slot,"owner":r2['result']['value'][0]['owner'],"dataBase64":r2['result']['value'][0]['data'][0]},
 "boardContextBlockTime":bt,"performanceSample":perf}
print(json.dumps(out,indent=1))
rd=base64.b64decode(out['round']['dataBase64'])
print('board',rid,start,end,ema,'round len',len(rd),'round id',struct.unpack_from('<Q',rd,8)[0],'tre ml',struct.unpack_from('<Q',base64.b64decode(out['treasury']['dataBase64']),8)[0]/1e11,file=sys.stderr)
print('deployed',[x/1e9 for x in struct.unpack_from('<25Q',rd,16)],file=sys.stderr)
