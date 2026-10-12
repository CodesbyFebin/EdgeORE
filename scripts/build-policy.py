#!/usr/bin/env python3
"""Offline declaration policy. Not a resolved Gradle graph or a credential scanner.

Checks: catalog and plugin coordinates, per-file (configuration, coordinate) declarations including project()
dependencies, dependency exclusions, the repository set in settings.gradle.kts, the wrapper JAR pin and
distribution, dynamic versions, and credential-like BuildConfig field names. It reads text with regular
expressions, so it can be fooled by unusual formatting; the resolved-graph comparison is the real check.
"""
import hashlib
import json
import re
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
# Dependency-level exclusions (group/module); the Test-filter exclude("...") strings are not matched.
EXCLUDE = re.compile(r'exclude\(group\s*=\s*"[^"]*"(?:\s*,\s*module\s*=\s*"[^"]*")?\)')
REPOSITORY = re.compile(r'\b(google\(\)|mavenCentral\(\)|gradlePluginPortal\(\)|mavenLocal\(\)|jcenter\(\)|maven\s*\([^)]*\)|maven\s*\{[^}]*\}|ivy\s*\{[^}]*\}|flatDir\s*\{[^}]*\})')
WRAPPER_SHA = '498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17'

def coordinates(catalog):
    result = {}
    for alias, entry in catalog['libraries'].items():
        result[alias.replace('-', '.')] = entry['module'] + (':' + entry['version'] if 'version' in entry else '')
    return result

def declarations(text, aliases):
    rows = []
    # Any configuration name that Gradle/AGP uses for dependencies (implementation, testRuntimeOnly, kapt, ksp,
    # lintChecks, coreLibraryDesugaring, ...), so a new dependency cannot hide in a configuration not listed here.
    pattern = r'^\s*((?:[a-z][A-Za-z]*)?(?:[Ii]mplementation|[Oo]nly|[Aa]pi)|kapt[A-Za-z]*|ksp[A-Za-z]*|lintChecks|lintPublish|coreLibraryDesugaring|annotationProcessor)\((?:platform\(|enforcedPlatform\()?\s*("[^"\n]+"|libs\.[A-Za-z0-9.]+|project\("[^"\n]+"\))'
    for config, value in re.findall(pattern, text, re.M):
        if value.startswith('project('):
            rows.append([config, 'project' + value[len('project('):-1].strip('"')])
            continue
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
        if sorted(EXCLUDE.findall(text)) != baseline.get('exclusions', {}).get(path, []): errors.append('Dependency exclusion drift: ' + path)
        if re.search(r'buildConfigField[^\n]*(?:TOKEN|SECRET|PASSWORD|PRIVATE_KEY|API_?KEY|CREDENTIAL)', text, re.I):
            errors.append('Credential-like BuildConfig field requires review: ' + path)
    settings = (root / 'settings.gradle.kts').read_text()
    if sorted(set(REPOSITORY.findall(settings))) != baseline.get('repositories', []) or 'FAIL_ON_PROJECT_REPOS' not in settings:
        errors.append('Repository policy changed in settings.gradle.kts')
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
