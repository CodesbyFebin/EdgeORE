#!/usr/bin/env python3
"""Offline declaration policy. Not a resolved Gradle graph or a credential scanner."""
import hashlib
import json
import re
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WRAPPER_SHA = '498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17'

def coordinates(catalog):
    result = {}
    for alias, entry in catalog['libraries'].items():
        result[alias.replace('-', '.')] = entry['module'] + (':' + entry['version'] if 'version' in entry else '')
    return result

def declarations(text, aliases):
    rows = []
    pattern = r'^\s*(implementation|runtimeOnly|api|compileOnly|testImplementation|androidTestImplementation|debugImplementation)\((?:platform\()?\s*("[^"\n]+"|libs\.[A-Za-z0-9.]+)'
    for config, value in re.findall(pattern, text, re.M):
        if value.startswith('libs.'):
            key = value[5:]
            if key not in aliases: raise ValueError('Unknown library alias: ' + key)
            coordinate = aliases[key]
        else:
            coordinate = value.strip('"')
        if ':' in coordinate: rows.append([config, coordinate])
    return sorted(rows)

def check(root=ROOT):
    errors = []
    catalog = tomllib.loads((root / 'gradle/libs.versions.toml').read_text())
    aliases = coordinates(catalog)
    baseline = json.loads((root / 'docs/build/dependency-baseline.json').read_text())
    actual = sorted(aliases.values())
    if actual != baseline['libraries']: errors.append('Catalog coordinates differ from reviewed baseline')
    actual_plugins = sorted(e['id'] + ':' + e['version'] for e in catalog['plugins'].values())
    if actual_plugins != baseline['plugins']: errors.append('Plugin coordinates differ from reviewed baseline')
    for path, expected in baseline['declarations'].items():
        text = (root / path).read_text()
        if declarations(text, aliases) != expected: errors.append('Dependency/configuration drift: ' + path)
        if re.search(r'buildConfigField[^\n]*(?:TOKEN|SECRET|PASSWORD|PRIVATE_KEY)', text, re.I):
            errors.append('Credential-like BuildConfig field requires review: ' + path)
    jar = root / 'gradle/wrapper/gradle-wrapper.jar'
    if hashlib.sha256(jar.read_bytes()).hexdigest() != WRAPPER_SHA: errors.append('Gradle wrapper JAR does not match reviewed pin')
    props = (root / 'gradle/wrapper/gradle-wrapper.properties').read_text()
    if 'gradle-8.9-bin.zip' not in props or 'validateDistributionUrl=true' not in props: errors.append('Wrapper distribution policy changed')
    for alias, entry in catalog['libraries'].items():
        version = entry.get('version', '')
        if '+' in version or version.startswith('latest.') or 'SNAPSHOT' in version: errors.append('Unpinned version: ' + alias)
    return errors

if __name__ == '__main__':
    try: errors = check()
    except (KeyError, ValueError, OSError) as e: errors = [str(e)]
    for error in errors: print('FAIL:', error)
    if not errors: print('PASS: declaration parity, pinned coordinates, wrapper JAR and BuildConfig field policy')
    sys.exit(bool(errors))
