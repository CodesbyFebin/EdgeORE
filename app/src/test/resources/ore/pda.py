import hashlib
p=2**255-19; d=(-121665*pow(121666,p-2,p))%p
B58='123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz'
def b58d(s):
    n=0
    for c in s: n=n*58+B58.index(c)
    b=n.to_bytes(32,'big'); return b
def b58e(b):
    n=int.from_bytes(b,'big'); s=''
    while n: n,r=divmod(n,58); s=B58[r]+s
    return '1'*(len(b)-len(b.lstrip(b'\0')))+s
def on_curve(b):
    y=int.from_bytes(b,'little')&((1<<255)-1)
    if y>=p: return False
    u=(y*y-1)%p; v=(d*y*y+1)%p
    x2=u*pow(v,p-2,p)%p
    if x2==0: return True
    return pow(x2,(p-1)//2,p)==1
def pda(seeds,prog):
    pid=b58d(prog)
    for bump in range(255,-1,-1):
        h=hashlib.sha256(b''.join(seeds)+bytes([bump])+pid+b'ProgramDerivedAddress').digest()
        if not on_curve(h): return b58e(h),bump
PROG='oreV3EG1i9BEgiAJ8b177Z2S2rMarzak4NMv1kULvWv'
if __name__=='__main__':
    print(pda([b'board'],PROG), pda([b'treasury'],PROG))
