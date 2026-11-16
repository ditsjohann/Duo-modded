#!/usr/bin/env python3
"""
Downloads every mod from a .mrpack that runs on a server into a mods folder (for CI testing).
Usage: install_server_mods.py <pack.mrpack> <mods-dir>
"""
import json
import os
import sys
import urllib.request
import zipfile

HEADERS = {"User-Agent": "duo-modded-pack-tester/1.0"}


def main():
    pack, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    with zipfile.ZipFile(pack) as z:
        index = json.loads(z.read("modrinth.index.json"))
    count = 0
    for f in index["files"]:
        if f.get("env", {}).get("server") == "unsupported":
            continue
        name = os.path.basename(f["path"])
        dest = os.path.join(out, name)
        for attempt in range(4):
            try:
                req = urllib.request.Request(f["downloads"][0], headers=HEADERS)
                with urllib.request.urlopen(req, timeout=60) as resp, open(dest, "wb") as fh:
                    fh.write(resp.read())
                break
            except Exception as e:
                print(f"retry {name}: {e}", flush=True)
        count += 1
    print(f"Installed {count} server-side mods into {out}")


if __name__ == "__main__":
    main()
