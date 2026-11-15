#!/usr/bin/env python3
"""
Builds a Modrinth modpack (.mrpack) for Minecraft 1.21.1 + NeoForge from modpack/mods.txt.

For every listed mod it finds the newest NeoForge 1.21.1 version on Modrinth, adds the mods it
requires, and writes a report of anything it could not find. The Tethered jar is included as an
override file. Uses only the Python standard library.

Usage: build_pack.py <mods.txt> <tethered.jar> <neoforge-version> <out-dir>
"""
import difflib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile

MC = "1.21.1"
LOADER = "neoforge"
API = "https://api.modrinth.com/v2"
HEADERS = {"User-Agent": "duo-modded-pack-builder/1.0"}

_cache = {}


def get(path, params=None):
    url = API + path + ("?" + urllib.parse.urlencode(params) if params else "")
    if url in _cache:
        return _cache[url]
    result = None
    for attempt in range(6):
        try:
            req = urllib.request.Request(url, headers=HEADERS)
            with urllib.request.urlopen(req, timeout=30) as resp:
                result = json.load(resp)
            break
        except urllib.error.HTTPError as e:
            if e.code == 404:
                break
            time.sleep(15 if e.code == 429 else 2 * (attempt + 1))
        except Exception:
            time.sleep(2 * (attempt + 1))
    _cache[url] = result
    time.sleep(0.15)  # stay well under Modrinth's rate limit
    return result


def best_version(project_id):
    versions = get(f"/project/{project_id}/version",
                   {"loaders": json.dumps([LOADER]), "game_versions": json.dumps([MC])}) or []
    if not versions:
        return None
    releases = [v for v in versions if v.get("version_type") == "release"]
    return (releases or versions)[0]  # newest first


def norm(s):
    return re.sub(r"[^a-z0-9]", "", s.lower())


def resolve(name, slug):
    """Returns (project, version, how) or (None, None, why)."""
    if slug:
        project = get(f"/project/{slug}")
        if project:
            version = best_version(project["id"])
            if version:
                return project, version, "slug"
    facets = json.dumps([[f"versions:{MC}"], [f"categories:{LOADER}"], ["project_type:mod"]])
    found = get("/search", {"query": name, "facets": facets, "limit": 10})
    hits = (found or {}).get("hits", [])
    best, best_score = None, 0.0
    for hit in hits:
        a, b = norm(name), norm(hit["title"])
        score = difflib.SequenceMatcher(None, a, b).ratio()
        if a and b and (a in b or b in a):
            score = max(score, 0.8 + 0.2 * min(len(a), len(b)) / max(len(a), len(b)))
        if score > best_score:
            best, best_score = hit, score
    if best is not None and best_score >= 0.6:
        project = get(f"/project/{best['project_id']}")
        version = best_version(best["project_id"]) if project else None
        if project and version:
            return project, version, f"search ({best_score:.2f})"
    return None, None, "no NeoForge 1.21.1 version found on Modrinth"


def env_for(project):
    client = "unsupported" if project.get("client_side") == "unsupported" else "required"
    server = "unsupported" if project.get("server_side") == "unsupported" else "required"
    return {"client": client, "server": server}


def main():
    mods_file, tethered_jar, neoforge_version, out_dir = sys.argv[1:5]
    entries = []
    with open(mods_file, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            name, _, slug = line.partition("|")
            entries.append((name.strip(), slug.strip() or None))

    chosen = {}       # project id -> (project, version)
    listed = []       # report rows for listed mods
    added_deps = []   # report rows for dependencies
    missing = []
    warnings = []

    for name, slug in entries:
        project, version, how = resolve(name, slug)
        if project is None:
            missing.append((name, how))
            print(f"MISSING  {name}: {how}", flush=True)
            continue
        if project["id"] in chosen:
            warnings.append(f"'{name}' is the same mod as another entry ({project['title']}); added once.")
            continue
        chosen[project["id"]] = (project, version)
        listed.append((name, project["title"], project["slug"], version["version_number"], how))
        print(f"OK       {name} -> {project['title']} {version['version_number']} [{how}]", flush=True)

    # Required dependencies, recursively
    queue = list(chosen.keys())
    while queue:
        pid = queue.pop()
        project, version = chosen[pid]
        for dep in version.get("dependencies", []):
            if dep.get("dependency_type") != "required":
                continue
            dep_pid = dep.get("project_id")
            if not dep_pid and dep.get("version_id"):
                v = get(f"/version/{dep['version_id']}")
                dep_pid = v["project_id"] if v else None
            if not dep_pid or dep_pid in chosen:
                continue
            dep_project = get(f"/project/{dep_pid}")
            dep_version = best_version(dep_pid) if dep_project else None
            if not dep_project or not dep_version:
                warnings.append(f"{project['title']} needs {dep_project['title'] if dep_project else dep_pid}, "
                                f"which has no NeoForge 1.21.1 version. {project['title']} may not load.")
                continue
            chosen[dep_pid] = (dep_project, dep_version)
            added_deps.append((dep_project["title"], project["title"], dep_version["version_number"]))
            print(f"DEP      {dep_project['title']} (needed by {project['title']})", flush=True)
            queue.append(dep_pid)

    # Declared incompatibilities between chosen mods
    for pid, (project, version) in chosen.items():
        for dep in version.get("dependencies", []):
            if dep.get("dependency_type") == "incompatible" and dep.get("project_id") in chosen:
                other = chosen[dep["project_id"]][0]["title"]
                warnings.append(f"{project['title']} says it is INCOMPATIBLE with {other}.")

    files = []
    for pid, (project, version) in sorted(chosen.items(), key=lambda kv: kv[1][0]["title"].lower()):
        primary = next((f for f in version["files"] if f.get("primary")), version["files"][0])
        files.append({
            "path": f"mods/{primary['filename']}",
            "hashes": {"sha1": primary["hashes"]["sha1"], "sha512": primary["hashes"]["sha512"]},
            "env": env_for(project),
            "downloads": [primary["url"]],
            "fileSize": primary["size"],
        })

    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": "0.1.0",
        "name": "Tethered Pack",
        "summary": "Two players, one rope, one sky island. Includes the Tethered mod.",
        "files": files,
        "dependencies": {"minecraft": MC, "neoforge": neoforge_version},
    }

    os.makedirs(out_dir, exist_ok=True)
    pack_path = os.path.join(out_dir, "tethered-pack.mrpack")
    with zipfile.ZipFile(pack_path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", json.dumps(index, indent=2))
        z.write(tethered_jar, "overrides/mods/" + os.path.basename(tethered_jar))

    # Report
    lines = ["# Tethered Pack build report", "",
             f"Minecraft {MC}, NeoForge {neoforge_version}. "
             f"**{len(listed)}** of {len(entries)} listed mods found, **{len(added_deps)}** required mods added, "
             f"**{len(files) + 1}** jars in total (including Tethered).", ""]
    if missing:
        lines += ["## Not found for 1.21.1 NeoForge (left out)", "", "| Mod | Why |", "|---|---|"]
        lines += [f"| {n} | {why} |" for n, why in missing] + [""]
    if warnings:
        lines += ["## Warnings", ""] + [f"- {w}" for w in warnings] + [""]
    lines += ["## Included", "", "| Listed as | Modrinth project | Version | Matched by |", "|---|---|---|---|"]
    lines += [f"| {n} | [{t}](https://modrinth.com/mod/{s}) | {v} | {how} |" for n, t, s, v, how in listed] + [""]
    if added_deps:
        lines += ["## Added because another mod needs them", "", "| Mod | Needed by | Version |", "|---|---|---|"]
        lines += [f"| {t} | {by} | {v} |" for t, by, v in added_deps] + [""]
    report = "\n".join(lines)
    with open(os.path.join(out_dir, "REPORT.md"), "w", encoding="utf-8") as f:
        f.write(report)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(report)
    print()
    print(report)


if __name__ == "__main__":
    main()
