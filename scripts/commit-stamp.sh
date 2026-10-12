#!/usr/bin/env bash
# Canonical commit stamp (hardening 3). One source commit is named by README.md, docs/HANDOVER.md,
# evidence/COMMIT-STAMP.json and the APK's BuildConfig.SOURCE_STAMP (read from the JSON at build time).
#   scripts/commit-stamp.sh write   stamp the current HEAD (tree must be clean), then commit the 3 files
#   scripts/commit-stamp.sh check   all three agree, and the stamped commit is HEAD or an ancestor that differs
#                                   from HEAD only in README/HANDOVER stamp blocks or evidence/ (evidence about that commit)
# BuildConfig.GIT_COMMIT stays the exact build revision; SOURCE_STAMP is the stamped source commit.
set -euo pipefail
cd "$(dirname "$0")/.."
JSON=evidence/COMMIT-STAMP.json
FILES=(README.md docs/HANDOVER.md "$JSON")
BEGIN='<!-- commit-stamp:begin -->'; END='<!-- commit-stamp:end -->'
block() { printf '%s\nCanonical source commit: `%s` (stamped by `scripts/commit-stamp.sh`; the same value is in `%s` and the APK as `BuildConfig.SOURCE_STAMP`).\n%s\n' "$BEGIN" "$1" "$JSON" "$END"; }
case "${1:-check}" in
write)
  [ -z "$(git status --porcelain --untracked-files=no)" ] || { echo "Refused: commit your changes first" >&2; exit 2; }
  C="$(git rev-parse HEAD)"
  mkdir -p evidence
  printf '{\n  "schema": "edgeore.commit-stamp.v1",\n  "sourceCommit": "%s",\n  "short": "%s",\n  "stampedAt": "%s"\n}\n' "$C" "${C:0:12}" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$JSON"
  for f in README.md docs/HANDOVER.md; do
    python3 - "$f" "$(block "$C")" "$BEGIN" "$END" <<'P'
import re,sys
f,blk,b,e=sys.argv[1:]
s=open(f).read()
if b in s: s=re.sub(re.escape(b)+r'.*?'+re.escape(e), lambda m: blk, s, flags=re.S)
else:
    lines=s.split('\n',1); s=lines[0]+'\n\n'+blk+'\n'+(lines[1] if len(lines)>1 else '')
open(f,'w').write(s)
P
  done
  echo "Stamped $C into ${FILES[*]}";;
check)
  C="$(python3 -c 'import json;print(json.load(open("evidence/COMMIT-STAMP.json"))["sourceCommit"])')"
  for f in README.md docs/HANDOVER.md; do grep -q "Canonical source commit: \`$C\`" "$f" || { echo "FAIL: $f does not name $C" >&2; exit 1; }; done
  git merge-base --is-ancestor "$C" HEAD || { echo "FAIL: $C is not HEAD or an ancestor" >&2; exit 1; }
  extra="$(git diff --name-only "$C" HEAD -- . ':!README.md' ':!docs/HANDOVER.md' ':!evidence/**')"
  [ -z "$extra" ] || { echo "FAIL: code changed after the stamped commit:"; echo "$extra"; exit 1; } >&2
  echo "PASS commit stamp ${C:0:12} (README, HANDOVER, $JSON agree)";;
*) echo "usage: $0 write|check" >&2; exit 2;;
esac
