#!/usr/bin/env python3
"""Summarise JUnit XML by attribute name (not attribute order). Usage: junit-summary.py <dir>"""
import glob, os, sys, xml.etree.ElementTree as ET
d = sys.argv[1] if len(sys.argv) > 1 else "app/build/test-results/testDebugUnitTest"
tot = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
skipped, failed, suites = [], [], 0
for f in sorted(glob.glob(os.path.join(d, "TEST-*.xml"))):
    r = ET.parse(f).getroot()
    suites += 1
    for k in tot: tot[k] += int(r.get(k, "0"))
    for tc in r.iter("testcase"):
        name = f'{tc.get("classname")}.{tc.get("name")}'
        s = tc.find("skipped")
        if s is not None: skipped.append(f'{name} ({s.get("message") or "no reason given"})')
        if tc.find("failure") is not None or tc.find("error") is not None: failed.append(name)
passed = tot["tests"] - tot["failures"] - tot["errors"] - tot["skipped"]
print(f'suites={suites} tests={tot["tests"]} passed={passed} failures={tot["failures"]} errors={tot["errors"]} skipped={tot["skipped"]}')
for s in skipped: print("SKIPPED", s)
for s in failed: print("FAILED", s)
sys.exit(1 if failed else 0)
