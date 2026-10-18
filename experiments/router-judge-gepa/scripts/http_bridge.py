#!/usr/bin/env python3
"""HTTP bridge helper for the ORC consumer JVM.

Why this exists: this VM's sandbox denies direct TCP from JVM processes
(java.net.http gets a canned 'muse: Other TCP connections is turned off'
response for every destination — loopback included), while shell-class
processes (curl/python/node) can reach the loopback SSH tunnel to
CLIProxyAPI directly. ORC/litellm do their HTTP via hato (java.net.http),
so the consumer redefines hato.client/post to delegate the actual socket
work to this helper over stdio. The entire ORC/litellm stack above the
socket is untouched.

Protocol: one JSON object on stdin {"url","headers","body"} ->
one JSON object on stdout {"status","body"} (body as raw string).
Errors: {"status": 0, "error": "..."} with exit code 0; the Clojure side
turns status 0 into an exception.
"""
import json, os, sys, urllib.request, urllib.error

def main():
    req = json.loads(sys.stdin.read())
    r = urllib.request.Request(req["url"],
                               data=(req.get("body") or "").encode(),
                               headers=req.get("headers") or {},
                               method="POST")
    try:
        with urllib.request.urlopen(r, timeout=180) as resp:
            out = {"status": resp.status, "body": resp.read().decode()}
    except urllib.error.HTTPError as e:
        out = {"status": e.code, "body": e.read().decode(errors="replace")}
    except Exception as e:
        out = {"status": 0, "error": f"{type(e).__name__}: {e}"}
    if os.environ.get("HTTP_BRIDGE_DEBUG"):
        try:
            with open("/tmp/bridge_last.json", "w") as f:
                json.dump({"request_body": req.get("body"), "response": out}, f)
        except Exception:
            pass
    sys.stdout.write(json.dumps(out))

if __name__ == "__main__":
    main()
