#!/usr/bin/env python3
"""Print project build-output cleanup candidates. Never deletes anything."""
from pathlib import Path
root = Path(__file__).resolve().parent.parent
for name in ['build', 'app/build', 'ondevice-llm/build', 'tools/receipt-checker/build']:
    p = root / name
    if p.is_symlink():
        print('REFUSED symlink:', name)
    elif p.is_dir() and p.resolve().is_relative_to(root):
        print('Build output candidate:', name)
print('Plan only. Source, evidence, credentials and global caches are never included.')
