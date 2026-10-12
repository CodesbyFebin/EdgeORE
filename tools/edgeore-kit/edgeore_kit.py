"""Offline EdgeORE extension planning and evidence checks. Never executes a workload."""
import argparse
import hashlib
import json
import re
from pathlib import Path, PurePosixPath
from urllib.parse import urlsplit

DIGEST = re.compile(r'[0-9a-f]{64}')
MAX_MANIFEST = 1_000_000

def strict_json(raw):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result: raise ValueError('Duplicate JSON field')
            result[key] = value
        return result
    def reject(value): raise ValueError('Non-finite JSON number')
    return json.loads(raw, object_pairs_hook=pairs, parse_constant=reject)

def consent_bytes(consent):
    """Canonical ASCII JSON for a deliberately small v1 consent format; not a signature."""
    required = {'version','workload','destination','dataCategories','maxBytes','maxSeconds','retentionSeconds','expiresAt','revocationId'}
    if set(consent) != required or consent['version'] != 1 or type(consent['version']) is not int:
        raise ValueError('Unknown consent format or fields')
    for field in ['maxBytes','maxSeconds','retentionSeconds','expiresAt']:
        if type(consent[field]) is not int or not 0 <= consent[field] <= 2**53 - 1:
            raise ValueError('Invalid bounded integer: ' + field)
    if consent['maxBytes'] == 0 or consent['maxSeconds'] == 0: raise ValueError('Zero workload budget')
    for field in ['workload','destination','revocationId']:
        value = consent[field]
        if not isinstance(value, str) or not value or len(value) > 512 or any(ord(c) < 32 for c in value):
            raise ValueError('Invalid consent string: ' + field)
    categories = consent['dataCategories']
    if not isinstance(categories,list) or not categories or len(categories)>16 or any(not isinstance(x,str) or not re.fullmatch(r'[a-z_]{1,40}',x) for x in categories) or len(set(categories)) != len(categories):
        raise ValueError('Invalid data categories')
    normalized = dict(consent, dataCategories=sorted(categories))
    return json.dumps(normalized, sort_keys=True, ensure_ascii=True, separators=(',', ':')).encode('ascii')

def plan(consent, workload, now, *, qualified=False, available=False, revoked=False, requested_bytes=0, requested_seconds=0, allowed_origins=()):
    """Advisory preflight. Caller flags are not evidence and this does not authorize execution."""
    canonical = consent_bytes(consent)
    if any(type(flag) is not bool for flag in (qualified, available, revoked)):
        raise ValueError('Boolean qualification/availability/revocation required')
    reasons = []
    if type(now) is not int or now < 0: raise ValueError('Invalid current time')
    if type(requested_bytes) is not int or type(requested_seconds) is not int or requested_bytes < 0 or requested_seconds <= 0:
        raise ValueError('Invalid requested budget')
    if consent['workload'] != workload: reasons.append('WORKLOAD_MISMATCH')
    if revoked: reasons.append('CONSENT_REVOKED')
    if now >= consent['expiresAt']: reasons.append('CONSENT_EXPIRED')
    if requested_bytes > consent['maxBytes'] or requested_seconds > consent['maxSeconds']: reasons.append('BUDGET_EXCEEDED')
    if not qualified: reasons.append('RUNTIME_NOT_QUALIFIED')
    if not available: reasons.append('CAPABILITY_UNAVAILABLE')
    destination = consent['destination']
    if destination != 'on-device':
        parsed = urlsplit(destination)
        if parsed.scheme != 'https' or not parsed.hostname or parsed.username is not None or parsed.password is not None or parsed.query or parsed.fragment or parsed.path not in ('','/'):
            reasons.append('INVALID_DESTINATION')
        elif destination.rstrip('/') not in {x.rstrip('/') for x in allowed_origins}:
            reasons.append('DESTINATION_NOT_ALLOWED')
    return {'decision':'REFUSED' if reasons else 'ELIGIBLE_FOR_REVIEW','reasons':reasons,'consentSha256':hashlib.sha256(canonical).hexdigest(),'execution':'NOT_RUN','settlement':'NOT_OBSERVED'}

def verify_bundle(directory):
    """Check explicitly listed local artifacts. Unsigned manifest does not prove authorship."""
    root = Path(directory).resolve()
    manifest = root / 'manifest.json'
    if manifest.is_symlink() or manifest.stat().st_size > MAX_MANIFEST: raise ValueError('Manifest refused')
    doc = strict_json(manifest.read_bytes())
    if set(doc) != {'version','sourceCommit','environment','files'} or type(doc['version']) is not int or doc['version'] != 1:
        raise ValueError('Unknown evidence manifest format')
    if not isinstance(doc['sourceCommit'],str) or not re.fullmatch(r'[0-9a-f]{40}', doc['sourceCommit']): raise ValueError('Full source commit required')
    if not isinstance(doc['environment'],str) or not doc['environment'].strip() or len(doc['environment'])>512: raise ValueError('Named environment required')
    records = doc['files']
    if not isinstance(records,list) or not 1 <= len(records) <= 256: raise ValueError('Invalid artifact count')
    seen = set()
    for record in records:
        if not isinstance(record,dict) or set(record) != {'path','sha256','bytes'}: raise ValueError('Invalid artifact record')
        name = record['path']
        if not isinstance(name,str) or not name or '\\' in name or len(name)>512: raise ValueError('Invalid artifact path')
        parts = PurePosixPath(name)
        if parts.is_absolute() or any(p in ('..','.') for p in name.split('/')) or '' in name.split('/') or name=='manifest.json' or name in seen:
            raise ValueError('Ambiguous or escaping artifact path')
        seen.add(name)
        path = root / name
        cursor = root
        for part in parts.parts:
            cursor = cursor / part
            if cursor.is_symlink(): raise ValueError('Symlink artifact refused')
        if not path.is_file() or not path.resolve().is_relative_to(root): raise ValueError('Missing artifact')
        if type(record['bytes']) is not int or not 0 <= record['bytes'] <= 256_000_000: raise ValueError('Invalid artifact length')
        if path.stat().st_size != record['bytes'] or not isinstance(record['sha256'],str) or not DIGEST.fullmatch(record['sha256']): raise ValueError('Artifact metadata mismatch')
        digest = hashlib.sha256()
        total = 0
        with path.open('rb') as stream:
            while block := stream.read(65536):
                total += len(block)
                if total > record['bytes']: raise ValueError('Artifact grew during verification')
                digest.update(block)
        if total != record['bytes'] or digest.hexdigest() != record['sha256']: raise ValueError('Artifact integrity mismatch')
    return {'status':'CONTENT_MATCH','filesChecked':len(records),'sourceCommit':doc['sourceCommit'],'environment':doc['environment'],'authorship':'NOT_VERIFIED','runtimeClaims':'NOT_VERIFIED'}

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['verify-evidence','hash-consent'])
    parser.add_argument('path')
    args = parser.parse_args()
    try:
        if args.command == 'verify-evidence': result = verify_bundle(args.path)
        else:
            p = Path(args.path)
            if p.stat().st_size > MAX_MANIFEST: raise ValueError('Consent file too large')
            result = {'consentSha256':hashlib.sha256(consent_bytes(strict_json(p.read_bytes()))).hexdigest(),'authorization':'NOT_GRANTED'}
        print(json.dumps(result, indent=2))
    except (ValueError,TypeError,KeyError,OSError) as e:
        parser.exit(1, 'REFUSED: '+str(e)+'\n')
