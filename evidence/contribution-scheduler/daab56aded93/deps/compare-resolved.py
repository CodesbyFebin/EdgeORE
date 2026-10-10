#!/usr/bin/env python3
"""Parse `gradle dependencies` tree output into {group:artifact: resolved_version}."""
import re, sys
def parse(path):
    out = {}
    for line in open(path):
        m = re.search(r'--- ([\w.\-]+):([\w.\-]+)(?::([\w.\-+]+))?(?: -> ([\w.\-+]+))?', line)
        if not m: continue
        g, a, v, r = m.groups()
        ver = r or v
        if ver is None: continue
        out[f"{g}:{a}"] = ver
    return out
if __name__ == "__main__":
    base, branch = parse(sys.argv[1]), parse(sys.argv[2])
    changed = {k: (base[k], branch[k]) for k in base if k in branch and base[k] != branch[k]}
    removed = sorted(k for k in base if k not in branch)
    added = sorted(f"{k}:{branch[k]}" for k in branch if k not in base)
    print(f"modules main={len(base)} branch={len(branch)}")
    print(f"CHANGED existing versions: {len(changed)}")
    for k,(a,b) in sorted(changed.items()): print(f"  {k}: {a} -> {b}")
    print(f"REMOVED: {len(removed)}"); [print("  "+k) for k in removed]
    print(f"ADDED (new modules): {len(added)}"); [print("  "+k) for k in added]
    sys.exit(1 if changed or removed else 0)
