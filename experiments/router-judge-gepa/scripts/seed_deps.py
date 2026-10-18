#!/usr/bin/env python3
"""Iteratively seed the local Maven repo so Clojure tools.deps can resolve
the ORC full-package graph on this VM.

Why: Java-side HTTPS from this VM is answered by a middlebox in plain HTTP
("Unsupported or unrecognized SSL message"); aether ignores settings.xml
proxies here. curl through the egress proxy (env HTTPS_PROXY) works, so:
run resolution -> parse every missing coordinate from the error output ->
curl POM+JAR from Maven Central / Clojars into /home/hatch/.m2/repository
with _remote.repositories tracking -> repeat until resolution succeeds.

Usage: seed_deps.py [max_rounds]   (default 80)
Log:   /tmp/seed_deps.log (also prints a summary tail on stdout)
"""
import os, re, subprocess, sys, urllib.request
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))
EXP = os.path.dirname(HERE)
RUN_CLJ = os.path.join(HERE, "run-clj.sh")
LOCAL_REPO = "/home/hatch/.m2/repository"
LOG = "/tmp/seed_deps.log"
REPOS = {"central": "https://repo1.maven.org/maven2",
         "clojars": "https://repo.clojars.org"}
COORD = re.compile(r"([A-Za-z0-9_.\-]+):([A-Za-z0-9_.\-]+):(pom|jar)(?::([A-Za-z0-9_.\-]+))?:([A-Za-z0-9_.\-+]+)")
POM_NS = {"m": "http://maven.apache.org/POM/4.0.0"}
harvested_poms = set()

seeded = set()
logf = open(LOG, "w")

def log(*a):
    print(*a, file=logf, flush=True)
    print(*a, flush=True)

def fetch(url, dest):
    req = urllib.request.Request(url, headers={"User-Agent": "curl-seed/1.0"})
    try:
        with urllib.request.urlopen(req, timeout=120) as r, open(dest + ".part", "wb") as f:
            while True:
                chunk = r.read(1 << 20)
                if not chunk:
                    break
                f.write(chunk)
        os.replace(dest + ".part", dest)
        return True
    except Exception as e:
        try: os.unlink(dest + ".part")
        except OSError: pass
        log("  fetch-fail", url, "->", e)
        return False

def seed(g, a, typ, v, clf=None):
    key = (g, a, v, clf)
    base = f"{g.replace('.', '/')}/{a}/{v}"
    ok_any = False
    # files this artifact needs locally: pom + jar (+ classifier variant)
    names = [f"{a}-{v}.pom", f"{a}-{v}.jar"]
    if clf:
        names.append(f"{a}-{v}-{clf}.jar")
    for repo_id, repo_base in REPOS.items():
        d = os.path.join(LOCAL_REPO, base)
        os.makedirs(d, exist_ok=True)
        got = False
        for name in names:
            dest = os.path.join(d, name)
            if os.path.exists(dest) and os.path.getsize(dest) > 0:
                got = True
                continue
            if fetch(f"{repo_base}/{base}/{name}", dest):
                got = True
                ok_any = True
        if got:
            # (re)write tracking file listing both repos we may have used
            with open(os.path.join(d, "_remote.repositories"), "w") as f:
                for name in names:
                    if os.path.exists(os.path.join(d, name)):
                        f.write(f"{name}>central=\n")
                        f.write(f"{name}>clojars=\n")
            break
    if got:
        # record even when nothing new was fetched, so a coord whose files
        # exist but aether still rejects surfaces as NO FRESH with the raw
        # error tail instead of looping silently
        seeded.add(key)
    return ok_any

def harvest_coords():
    """Parse every POM in the local repo (once each) and return the coords of
    its parent + declared dependencies (with ${property} substitution from
    that POM's own <properties>). Best-effort: anything unparsable is left
    for the error-driven round loop."""
    found = set()
    for root, _dirs, files in os.walk(LOCAL_REPO):
        for fn in files:
            if not fn.endswith(".pom"):
                continue
            path = os.path.join(root, fn)
            if path in harvested_poms:
                continue
            harvested_poms.add(path)
            try:
                tree = ET.parse(path)
            except Exception:
                continue
            r = tree.getroot()
            props = {}
            pel = r.find("m:properties", POM_NS)
            if pel is not None:
                for child in pel:
                    tag = child.tag.split("}")[-1]
                    if child.text:
                        props["${" + tag + "}"] = child.text.strip()
            def txt(el, name):
                c = el.find(f"m:{name}", POM_NS)
                return c.text.strip() if c is not None and c.text else None
            pg = txt(r, "groupId"); pv = txt(r, "version")
            par = r.find("m:parent", POM_NS)
            if par is not None:
                pg = pg or txt(par, "groupId"); pv = pv or txt(par, "version")
            props["${project.groupId}"] = pg or ""
            props["${project.version}"] = pv or ""
            props["${pom.groupId}"] = pg or ""
            props["${pom.version}"] = pv or ""
            def subst(s):
                if s is None:
                    return None
                for _ in range(3):
                    s2 = s
                    for k, v in props.items():
                        s2 = s2.replace(k, v)
                    if s2 == s:
                        break
                    s = s2
                return s
            if par is not None:
                g, a, v = txt(par, "groupId"), txt(par, "artifactId"), subst(txt(par, "version"))
                if g and a and v and "${" not in v and not any(c in v for c in "[](),"):
                    found.add((g, a, v))
            for dep in r.iter("{http://maven.apache.org/POM/4.0.0}dependency"):
                scope = txt(dep, "scope")
                if scope in ("test", "provided", "system", "import"):
                    continue
                opt = txt(dep, "optional")
                if opt == "true":
                    continue
                g, a, v = txt(dep, "groupId"), txt(dep, "artifactId"), subst(txt(dep, "version"))
                if g and a and v and "${" not in v and not any(c in v for c in "[](),"):
                    found.add((g, a, v))
    return found

def harvest_and_seed():
    coords = [c for c in sorted(harvest_coords()) if c not in seeded]
    if not coords:
        return 0
    with ThreadPoolExecutor(max_workers=8) as ex:
        list(ex.map(lambda c: seed(c[0], c[1], "jar", c[2]), coords))
    log(f"  harvest: attempted {len(coords)} coords from POM graph")
    return len(coords)

def main():
    max_rounds = int(sys.argv[1]) if len(sys.argv) > 1 else 80
    for rnd in range(1, max_rounds + 1):
        p = subprocess.run(["bash", RUN_CLJ, "-Spath"], capture_output=True, text=True, timeout=600)
        out = p.stdout + p.stderr
        if p.returncode == 0 and ":" in out:
            log(f"ROUND {rnd}: RESOLUTION SUCCEEDED")
            log("classpath entries:", out.count(":") + 1)
            with open("/tmp/classpath.txt", "w") as f:
                f.write(out)
            log(f"total artifacts seeded this run: {len(seeded)}")
            return 0
        coords = set()
        for m in COORD.finditer(out):
            g, a, typ, clf, v = m.groups()
            if v and not any(c in v for c in "[](),") and not v.endswith(".lastUpdated"):
                coords.add((g, a, v, clf))
        # Also catch 'Failed to read artifact descriptor for g:a:jar:v'
        fresh = [c for c in sorted(coords, key=str) if c not in seeded]
        log(f"ROUND {rnd}: rc={p.returncode} missing-coords={len(coords)} fresh={len(fresh)}")
        if not fresh:
            log("NO FRESH COORDS PARSED — raw tail of output:")
            log("\n".join(out.splitlines()[-40:]))
            return 2
        for (g, a, v, clf) in fresh:
            seed(g, a, "jar", v, clf)
    log("ROUND LIMIT REACHED")
    return 3

if __name__ == "__main__":
    sys.exit(main())
