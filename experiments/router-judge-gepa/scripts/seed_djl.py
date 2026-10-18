#!/usr/bin/env python3
"""curl-seed DJL's PyTorch natives into DJL's cache layout (DJL 0.31.1 /
PyTorch 2.5.1), replicating ai.djl.pytorch.jni.LibUtils.downloadPyTorch +
findJniLibrary exactly, because the JVM cannot download them itself on
this VM (sandbox denies JVM direct TCP; proxied urllib works).

Layout produced (user.home is pinned to /home/hatch by run-clj.sh):
  ~/.djl.ai/pytorch/2.5.1.txt                      (the files.txt index)
  ~/.djl.ai/pytorch/2.5.1-cpu-linux-x86_64/<libs>  (gunzipped, flat)
  ~/.djl.ai/pytorch/2.5.1-cpu-linux-x86_64/0.31.1-libdjl_torch.so  (JNI)
"""
import gzip, os, sys, urllib.request

BASE = "https://publish.djl.ai/pytorch/2.5.1"
CACHE = os.path.expanduser("~/.djl.ai/pytorch")
DIR = os.path.join(CACHE, "2.5.1-cpu-linux-x86_64")

def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "curl-seed/1.0"})
    return urllib.request.urlopen(req, timeout=600)

def main():
    os.makedirs(DIR, exist_ok=True)
    idx = os.path.join(CACHE, "2.5.1.txt")
    if not os.path.exists(idx):
        with fetch(f"{BASE}/files.txt") as r, open(idx, "wb") as f:
            f.write(r.read())
        print("index saved")
    lines = [l.strip() for l in open(idx) if l.strip().startswith("cpu/linux-x86_64/")]
    print(f"{len(lines)} native files for cpu/linux-x86_64")
    for line in lines:
        name = line.rsplit("/", 1)[-1]
        if name.endswith(".gz"):
            name = name[:-3]
        dest = os.path.join(DIR, name)
        if os.path.exists(dest) and os.path.getsize(dest) > 0:
            continue
        with fetch(f"{BASE}/{line}") as r, open(dest + ".part", "wb") as f:
            f.write(gzip.decompress(r.read()))
        os.replace(dest + ".part", dest)
        print("seeded", name)
    jni = os.path.join(DIR, "0.31.1-libdjl_torch.so")
    if not os.path.exists(jni):
        url = f"{BASE}/jnilib/0.31.1/linux-x86_64/cpu/libdjl_torch.so"
        with fetch(url) as r, open(jni + ".part", "wb") as f:
            f.write(r.read())
        os.replace(jni + ".part", jni)
        print("seeded JNI lib")
    print("done:", len(os.listdir(DIR)), "files in", DIR)

if __name__ == "__main__":
    sys.exit(main())
