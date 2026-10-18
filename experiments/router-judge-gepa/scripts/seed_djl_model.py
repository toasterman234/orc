#!/usr/bin/env python3
"""curl-seed the DJL HuggingFace-pytorch embedding model
(sentence-transformers/all-MiniLM-L6-v2, DJL artifact 0.0.1) into DJL's
cache layout, replicating ai.djl.repository.RemoteRepository.locate +
AbstractRepository.prepare:

  ~/.djl.ai/cache/repo/model/nlp/text_embedding/ai/djl/huggingface/pytorch/
      sentence-transformers/all-MiniLM-L6-v2/metadata.json   (from mlrepo)
      sentence-transformers/all-MiniLM-L6-v2/0.0.1/<zip contents>

DJL is then run with -Dai.djl.offline=true (see run-clj.sh) so locate()
accepts the cached metadata without a freshness check and prepare()
short-circuits on the existing resource dir.
"""
import io, json, os, urllib.request, zipfile

MRL = ("model/nlp/text_embedding/ai/djl/huggingface/pytorch/"
       "sentence-transformers/all-MiniLM-L6-v2")
BASE = f"https://mlrepo.djl.ai/{MRL}"
ROOT = os.path.expanduser(f"~/.djl.ai/cache/repo/{MRL}")

def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "curl-seed/1.0"})
    return urllib.request.urlopen(req, timeout=600)

def main():
    os.makedirs(ROOT, exist_ok=True)
    meta_path = os.path.join(ROOT, "metadata.json")
    if not os.path.exists(meta_path):
        with fetch(f"{BASE}/metadata.json") as r:
            meta = json.loads(r.read())
        with open(meta_path, "w") as f:
            json.dump(meta, f)
        print("metadata seeded")
    art_dir = os.path.join(ROOT, "0.0.1", "all-MiniLM-L6-v2")
    marker = os.path.join(art_dir, "all-MiniLM-L6-v2.pt")
    if not os.path.exists(marker):
        with fetch(f"{BASE}/0.0.1/all-MiniLM-L6-v2.zip") as r:
            data = r.read()
        print(f"zip downloaded: {len(data)} bytes")
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            names = z.namelist()
            z.extractall(art_dir)
        print("extracted:", [n for n in names if not n.endswith("/")][:12])
    print("done:", sorted(os.listdir(art_dir)))

if __name__ == "__main__":
    main()
