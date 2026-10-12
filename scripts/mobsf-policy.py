#!/usr/bin/env python3
"""Triage two bounded platform findings; preserve reports and fail on unknown highs."""
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def evaluate(report, scorecard, config):
    if report.get('package_name') != 'com.edgeore.app' or not isinstance(report.get('code_analysis', {}).get('findings'), dict):
        raise ValueError('Report package or code findings missing')
    findings = scorecard.get('high')
    if not isinstance(findings, list):
        raise ValueError('Scorecard high findings missing')
    tree = ET.parse(config).getroot()
    base = tree.find('base-config')
    domains = tree.findall('domain-config')
    loopback_only = base is not None and base.get('cleartextTrafficPermitted') == 'false'
    for entry in domains:
        if entry.get('cleartextTrafficPermitted') == 'true':
            names = entry.findall('domain')
            loopback_only = loopback_only and bool(names) and all(
                n.text in ('127.0.0.1', 'localhost') and n.get('includeSubdomains') == 'false'
                for n in names)
    code = report.get('code_analysis', {}).get('findings', {})
    cbc = code.get('cbc_padding_oracle', {}).get('files', {})
    biometric_only = bool(cbc) and all(
        name.replace('\\', '/').endswith('androidx/biometric/CryptoObjectUtils.java') for name in cbc)
    remaining = []
    for finding in findings:
        title = finding.get('title', '')
        if title == 'Domain config is insecurely configured to permit clear text traffic to these domains in scope' and loopback_only:
            print('REVIEWED: loopback-only cleartext for MWA and explicit owned-host tunnels; remote cleartext remains denied')
        elif title == 'The App uses the encryption mode CBC with PKCS5/PKCS7 padding. This configuration is vulnerable to padding oracle attacks.' and biometric_only:
            print('REVIEWED: AndroidX Biometric CryptoObjectUtils platform authentication helper; app vault/key wrapping uses AES-GCM')
        else:
            remaining.append(finding)
    print(f'MobSF raw high={len(findings)} unreviewed high={len(remaining)}; raw reports retained')
    for finding in remaining:
        print('UNREVIEWED HIGH:', finding.get('title'))
    return 1 if remaining else 0


if __name__ == '__main__':
    try:
        folder = Path(sys.argv[1])
        result = evaluate(json.loads((folder / 'report.json').read_text()),
                          json.loads((folder / 'scorecard.json').read_text()),
                          'app/src/main/res/xml/network_security_config.xml')
    except (OSError, ValueError, KeyError, IndexError, TypeError, ET.ParseError) as exc:
        print(f'FAIL: incomplete scan evidence: {exc}', file=sys.stderr)
        result = 2
    sys.exit(result)
