# Run explicitly after docker build; intentionally excluded from unittest discovery.
import subprocess, secrets, urllib.request, urllib.error, json, hashlib, time, tempfile, os
D=['docker','--host=unix:///var/run/docker.sock']; name='edgeore-gateway-review-test'; volume=name+'-data'
def docker(*args,check=True): return subprocess.run(D+list(args),check=check,capture_output=True,text=True)
def request(path,method='GET',body=None,auth=True):
    headers={'Authorization':'Bearer '+token} if auth else {}
    if body is not None: headers.update({'X-Ciphertext-SHA256':hashlib.sha256(body).hexdigest(),'Content-Type':'application/octet-stream'})
    try:
        with urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:19845'+path,data=body,headers=headers,method=method),timeout=3) as r:return r.status,r.read()
    except urllib.error.HTTPError as e:return e.code,e.read()
missing=docker('run','--rm','edgeore-storage-gateway:review',check=False)
assert missing.returncode!=0 and 'Set EDGEORE_STORAGE_TOKEN' in missing.stderr + missing.stdout
print('PASS missing credential refuses startup')
token=secrets.token_hex(32)
fd,env=tempfile.mkstemp(); os.chmod(env,0o600); os.write(fd,('EDGEORE_STORAGE_TOKEN='+token+'\n').encode());os.close(fd)
try:
    docker('volume','create',volume)
    docker('run','-d','--name',name,'--read-only','--cap-drop=ALL','--security-opt=no-new-privileges','--tmpfs','/tmp:rw,noexec,nosuid,size=16m','--env-file',env,'-p','127.0.0.1:19845:19844','-v',volume+':/data','edgeore-storage-gateway:review')
    for _ in range(30):
        try:
            if request('/v1/capabilities')[0]==200:break
        except OSError:pass
        time.sleep(.2)
    assert request('/v1/capabilities',auth=False)[0]==401
    print('PASS unauthenticated request refused')
    body=b'opaque test ciphertext';digest=hashlib.sha256(body).hexdigest()
    assert request('/v1/jobs/test-job','PUT',body)[0]==200
    assert request('/v1/objects/'+digest)==(200,body)
    print('PASS authenticated upload and exact-byte retrieval')
    docker('restart',name)
    for _ in range(30):
        try:
            if request('/v1/jobs/test-job')[0]==200:break
        except OSError:pass
        time.sleep(.2)
    assert request('/v1/objects/'+digest)==(200,body)
    print('PASS SQLite persists across container restart')
    assert request('/v1/jobs/test-job/cancel','POST',b'')[0]==200
    assert request('/v1/objects/'+digest)[0]==404
    print('PASS canceled job no longer exposes ciphertext')
    assert docker('exec',name,'id','-u').stdout.strip()=='10001'
    print('PASS nonroot UID 10001 with read-only root filesystem')
finally:
    docker('rm','-f',name,check=False);docker('volume','rm',volume,check=False);os.unlink(env)
