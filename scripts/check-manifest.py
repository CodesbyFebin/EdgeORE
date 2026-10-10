#!/usr/bin/env python3
"""Release-manifest policy check (hardening backlog H2). Exit 0 = pass, 1 = violation, 2 = cannot read.

Usage: check-manifest.py MERGED_AndroidManifest.xml
Fails if the merged manifest is debuggable, or exports any component other than the app's launcher activity and
androidx.profileinstaller's receiver (which Android protects with android.permission.DUMP), or declares any
androidx.test / compose ui-tooling / bare ComponentActivity component at all."""
import sys
import xml.etree.ElementTree as ET

A = "{http://schemas.android.com/apk/res/android}"
ALLOWED_EXPORTED = {"com.edgeore.app.MainActivity", "androidx.profileinstaller.ProfileInstallReceiver"}
FORBIDDEN_PREFIXES = ("androidx.test.", "androidx.compose.ui.tooling.")
FORBIDDEN_EXACT = {"androidx.activity.ComponentActivity"}


def main(argv):
    if len(argv) != 2:
        print(__doc__.strip(), file=sys.stderr)
        return 2
    try:
        root = ET.parse(argv[1]).getroot()
    except (OSError, ET.ParseError) as e:
        print(f"MANIFEST CHECK ERROR: {e}", file=sys.stderr)
        return 2
    problems = []
    app = root.find("application")
    if app is not None and app.get(A + "debuggable") == "true":
        problems.append("application is debuggable")
    for tag in ("activity", "activity-alias", "service", "receiver", "provider"):
        for e in root.iter(tag):
            name = e.get(A + "name", "")
            if name.startswith(FORBIDDEN_PREFIXES) or name in FORBIDDEN_EXACT:
                problems.append(f"{tag} {name} is declared (test/tooling component)")
            elif e.get(A + "exported") == "true" and name not in ALLOWED_EXPORTED:
                problems.append(f"{tag} {name} is exported")
    for p in problems:
        print(f"MANIFEST FINDING: {p}")
    print(f"MANIFEST CHECK: {'FAIL' if problems else 'PASS'} ({argv[1]})")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
