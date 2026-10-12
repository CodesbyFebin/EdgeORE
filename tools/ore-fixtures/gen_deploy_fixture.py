#!/usr/bin/env python3
"""Independent (non-Kotlin) derivation of the ORE v3 Deploy instruction, from regolith-labs/ore @ 48c203bd:
api/src/sdk.rs deploy(), api/src/instruction.rs (Deploy = 6, {amount:[u8;8], squares:[u8;4]}, steel discriminator prefix),
api/src/state/mod.rs PDA seeds, and entropy-api 0.1.4 (Cargo.lock checksum bfdd094b...) var_pda + program id.
Writes app/src/test/resources/ore/deploy-fixture.json. Self-checks the PDA code against the published BOARD/TREASURY/CONFIG addresses."""
import hashlib, json, struct, sys, pathlib
A='123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz'
def b58d(s):
    n=0
    for c in s: n=n*58+A.index(c)
    b=n.to_bytes(32,'big'); return b
def b58e(b):
    n=int.from_bytes(b,'big'); s=''
    while n: n,r=divmod(n,58); s=A[r]+s
    return '1'*(len(b)-len(b.lstrip(b'\0')))+s
p=2**255-19; d=(-121665*pow(121666,p-2,p))%p
def on_curve(b):
    y=int.from_bytes(b,'little')&((1<<255)-1)
    if y>=p: return False
    u=(y*y-1)%p; v=(d*y*y+1)%p
    x2=u*pow(v,p-2,p)%p
    if x2==0: return True
    return pow(x2,(p-1)//2,p)==1
def pda(seeds,prog):
    for bump in range(255,-1,-1):
        h=hashlib.sha256(b''.join(seeds)+bytes([bump])+prog+b'ProgramDerivedAddress').digest()
        if not on_curve(h): return h,bump
ORE=b58d('oreV3EG1i9BEgiAJ8b177Z2S2rMarzak4NMv1kULvWv'); ENT=b58d('3jSkUuYBoJzQPMEzTvkDFXCZUBksPamrVhrnHR9igu2X'); SYS=bytes(32)
board=pda([b'board'],ORE)[0]; treasury=pda([b'treasury'],ORE)[0]; config=pda([b'config'],ORE)[0]
assert b58e(board)=='BrcSxdp1nXFzou1YyDnQJcPNBNHgoypZmTsyKBSLLXzi'
assert b58e(treasury)=='45db2FSR4mcXdSVVZbKbwojU6uYDpMyhpEi7cC8nHaWG'
assert b58e(config)=='9c9X7aDRAF41faiDs94ELjT19UrGnn72wBW9hPsS4Awy'
def cu16(n):
    o=b''
    while True:
        b=n&0x7f; n>>=7
        if n==0: return o+bytes([b])
        o+=bytes([b|0x80])
cases=[]
for name,signer,round_id,amount,squares in [
    ('lite-all-25', bytes(range(1,33)), 437127, 400_000, list(range(25))),
    ('pro-3-squares', hashlib.sha256(b'edgeore-fixture-signer').digest(), 7, 1_000_000, [0,12,24]),
]:
    bh=hashlib.sha256(b'fixture-blockhash-'+name.encode()).digest()
    automation=pda([b'automation',signer],ORE)[0]; miner=pda([b'miner',signer],ORE)[0]
    rnd=pda([b'round',struct.pack('<Q',round_id)],ORE)[0]; var=pda([b'var',board,struct.pack('<Q',0)],ENT)[0]
    mask=0
    for i in squares: mask|=1<<i
    data=bytes([6])+struct.pack('<Q',amount)+struct.pack('<I',mask)
    # sdk.rs account order: signer(w,s), authority(w)=signer, automation, board, config, miner, round, treasury (w),
    # system(ro), ore program(ro), entropy var(w), entropy program(ro)
    keys=[signer,automation,board,config,miner,rnd,treasury,var,SYS,ORE,ENT]
    metas=[signer,signer,automation,board,config,miner,rnd,treasury,SYS,ORE,var,ENT]
    msg=bytes([1,0,3])+cu16(len(keys))+b''.join(keys)+bh+cu16(1)+bytes([keys.index(ORE)])+cu16(len(metas))+bytes(keys.index(k) for k in metas)+cu16(len(data))+data
    cases.append(dict(name=name,signer=b58e(signer),roundId=round_id,amountPerSquareLamports=amount,squares=squares,mask=mask,
        blockhash=b58e(bh),accounts=dict(automation=b58e(automation),miner=b58e(miner),round=b58e(rnd),entropyVar=b58e(var)),
        dataHex=data.hex(),messageHex=msg.hex(),messageSha256=hashlib.sha256(msg).hexdigest()))
out=dict(source='regolith-labs/ore@48c203bd75db3cc45105ec29d8f8db719e5a2263 api/src/sdk.rs deploy(); entropy-api 0.1.4',
    programId=b58e(ORE),entropyProgramId=b58e(ENT),board=b58e(board),treasury=b58e(treasury),config=b58e(config),cases=cases)
pathlib.Path(sys.argv[1]).write_text(json.dumps(out,indent=1)+'\n'); print('ok',[c['messageSha256'][:12] for c in cases])
