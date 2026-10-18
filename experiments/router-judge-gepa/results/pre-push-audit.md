# PRE-PUSH AUDIT — branch `work/router-judge-gepa-experiment` as a publication unit

- **Date:** 2026-10-17 · **Auditor role:** pre-push audit only. Nothing
  was redacted, fixed, or published in this pass.
- **Unit audited:** `git diff main..HEAD` on branch
  `work/router-judge-gepa-experiment` — base `main` @ `1851212`
  (pristine upstream), HEAD `33a4177` (18 commits: the experiment's
  17 + the HANDOFF commit), **41 added files, 471,380 diff bytes**.
  Target if ever pushed: the **PUBLIC** fork `toasterman234/orc`.
> **Published edition (2026-10-17).** This is the redacted edition of the
> audit for publication: the findings, counts, and verdict are unchanged,
> but the values the audit named (one item title, network identifiers,
> filesystem paths, the canonical repo's name, the proxy service's name)
> are replaced with the generic forms used in `results/redaction-log.md`,
> and the provider key's fingerprint is omitted. The full audit is
> retained in local branch history only. Fixes and redactions were
> applied after this audit was written; see `results/redaction-log.md`.

- **This file** is committed separately, after the audit, and is not
  part of the audited unit. It contains locations and classifications
  only — no needle text, no secret material. (One exception, flagged
  where it occurs: identifying the real item title in Flag F1
  requires naming it; it is already in the audited record at
  `PLAN.md:115`.)

## Verdict summary

**NOT publishable as-is. Publishable after one fix (B1) + Ben's
decisions on B2 and the flag register.**

- **BLOCKERS: 2** — B1 (substantive: eval-suite prose embedded in a
  committed builder script) and B2 (verbatim-hit listings the audit
  rule records as blockers; auditor classifies them as structural
  identifiers — Ben may reclassify).
- **FLAGS: 6 register entries** (F1–F6), Ben's judgment calls.
- **Files: 41 total — 1 BLOCKER, 13 FLAG, 27 CLEAN** (per-file
  table in §5).
- **Secrets: none found.** The provider API key's exact value occurs
  **0 times** in the diff (see §2). No personal router data (titles,
  notes, feedback text) occurs anywhere in the diff (§3).

## 1. Method

- Enumerated every file in `git diff --name-status main..HEAD` with
  its HEAD size; scanned the full patch text and each file's content.
- **Secret scan:** regex classes (private-key headers, GitHub /
  OpenAI-style / AWS token shapes, URL-embedded credentials, Bearer
  literals, credential-assignment literals), a high-entropy token
  pass (≥40 chars, Shannon entropy ≥ 4.2, excluding hex hashes and
  UUIDs), and an **exact-value scan** for the provider key: the value
  was read from gitignored `data/cliproxy.env` in memory only,
  compared by exact match and by SHA-256 fingerprint
  (fingerprint checked in the local edition;
  omitted here — a fingerprint of a short value is brute-forceable) — the value is never printed in
  this audit.
- **Personal-data scan:** needle sets built from the private corpora
  — long strings (≥25 chars; JSON string values, quoted strings, and
  prose lines) from `data/labeled_v1.jsonl`, `data/feedback.jsonl`,
  and every file under `data/eval-suites/blocker-triage/` — searched
  against whitespace-normalized branch text with line mapping. Two
  tiers: prose needles (contain spaces) and slug needles
  (identifier-shaped, no spaces). Any hit is recorded as a blocker
  per the audit rule, with the auditor's classification beside it.
- **Boundary evidence:** `git ls-tree -r HEAD` contains **0** paths
  under `experiments/router-judge-gepa/data/` and **0** under
  `consumer/runtime/`; `git check-ignore -v` confirms both are
  covered (`.gitignore:5` → `data/`, `.gitignore:7` →
  `consumer/runtime/`), including the provider env file and the
  event-store database specifically.
- **Content classification:** manual review of the Structural-v1
  material (`results/structural-mirror-memo.md`,
  `results/loop_state.json`), infrastructure identifiers (grouped
  counts, not per-line spam), and anything else publication-
  sensitive.

## 2. Secret scan — CLEAN

| Check | Result |
|---|---|
| Provider API key, exact value | **0 hits** in patch and in every file (fingerprint-checked) |
| Private-key headers / GitHub / OpenAI-style / AWS token shapes / URL-embedded credentials / Bearer literals | **0 hits** |
| Credential-assignment literals (`api_key`/`token`/`secret`/`password` = literal) | **0 hits** — code references the env var *name* and sources the gitignored env file; no literal values |
| High-entropy token review (5 candidates) | All false positives: workspace file paths (`PLAN.md:102/106/107`), the Adoptium download URL (`PREFLIGHT.md:76`), a DJL cache path (`PREFLIGHT.md:329`) |
| Email-shaped strings in the full diff | **0** |
| Provider base-URL value (`CLIPROXY_BASE_URL`, the loopback tunnel URL) | Present in `PREFLIGHT.md` (×1) and `consumer/src/router_loop/system.clj` (×2). **Not a credential** — a `127.0.0.1` address; counted under Flag F4 |

## 3. Personal-data scan — router data CLEAN; suite prose BLOCKER

| Corpus | Prose needles | Prose hits | Slug needles | Slug hits |
|---|---|---|---|---|
| `data/labeled_v1.jsonl` (Ben's Things items + verdicts) | 90 | **0** | 4 | 4 → B2 |
| `data/feedback.jsonl` (Ben's feedback text) | 24 | **0** | 0 | 0 |
| `data/eval-suites/blocker-triage/` (12 files) | 599 | **45 instances / 37 unique** → B1 + boilerplate note | 38 | 16 → B2 |

No Things title, note, feedback sentence, or decision text from
Ben's real data appears anywhere in the branch. The hits are all
in the blocker-triage material, below.

### BLOCKER B1 — eval-suite prose embedded in `src/build_blocker_triage.py`

35 substantive hit instances (35 unique needles, passages up to
**592 chars**) sit in one committed file:
`experiments/router-judge-gepa/src/build_blocker_triage.py`.
What is embedded, by construction of that script:

- **The judge `CRITERIA` text** (string constant at line 111 ff.) —
  the governing-principle / anti-pattern criteria authored from the
  suite, matching `data/eval-suites/blocker-triage/judge_criteria.txt`
  and the derived GEPA datasets passage-for-passage.
- **The pilot harness's deterministic rubric, “replicated exactly”**
  (line 60 ff.) — including a 163-char passage traceable directly
  to the suite's original `run_agent_blocker_triage_pilot.py`.
- **The counterexample decision statements** (`TWINS`, line 127 ff.)
  and the scenario/response passages that constitute the **locked
  valset** (needles traceable to `gepa_valset.json` /
  `gepa_trainset.json` in `data/`; one 74-char passage traceable
  directly to the original suite YAML).

Why this is a blocker, not a flag: the experiment's own boundary —
restated in `PLAN.md` §3, `EXPORT-AGREEMENT.md` §4, and the mapping
memo itself — is that eval-suite content lives only in gitignored
`data/`. The memo's claim that “Suite content (scenario text,
responses) appears nowhere in this repo”
(`results/blocker-triage-mapping-memo.md`) is **inaccurate for the
builder script**, and a locked exam's items should not sit in a
public repo in any case. **Fix sketch (not applied):** make the
builder read the passages from `data/eval-suites/` at runtime and
keep only structure/constants in code; correct the memo's claim;
re-run this scan.

*Boilerplate note:* the remaining 10 prose-hit instances are 2
needles — `from __future__ import annotations` (34 chars) and
`if __name__ == "__main__":` (26 chars) — shared by every Python
file in the branch and the suite's pilot runner. False positives,
recorded here so the appendix reconciles (45 = 35 + 10).

### BLOCKER B2 — verbatim hits the audit rule records; auditor classifies as structural

Recorded as blockers because the audit rule is *any* verbatim hit.
The auditor's classification: **identifiers and schema vocabulary,
not item content or prose** — Ben may reclassify these to accepted
structure, in which case they leave the blocker list without edits.

- `data/labeled_v1.jsonl` slug hits (4), all in
  `src/build_dataset.py` (lines 196, 222, 242, 244): the builder's
  own label-provenance constants (e.g. its `label_source` /
  `nature` vocabulary). Direction of travel is builder → dataset:
  the dataset contains them because the builder wrote them there.
- Suite slug hits (16): the 10 counterexample **item IDs** in
  `results/blocker-triage-calibration.md` (lines 13–22 — they name
  the scenario variations, so they do disclose the exam's themes);
  4 in `src/build_blocker_triage.py`; 1 in
  `consumer/src/router_loop/blocker_triage.clj` (line 10); the
  suite ID in `results/blocker-triage-mapping-memo.md` (line 5).

## 4. Flag register — Ben's judgment calls

- **F1 — a real Things item title, with its router scores.**
  “the scope-trade feedback item” and its classification scores appear at
  `PLAN.md:115` (carried over from the Phase 3 eval record), and
  the title is repeated at `HANDOFF.md:115`. It is the only real
  item title in the branch; the needle scan missed it only because
  it is shorter than 25 chars. Ben's call: accept (it is a
  long-standing diagnostic case he has ruled on) or redact before
  any push.
- **F2 — network identifiers.** Console tailnet hostname
  (`the Mac's tailnet name`, ×1, the console URL in
  `HANDOFF.md`); Mac tailnet IP `the Mac's tailnet IP` (×3: `PLAN.md`,
  `PREFLIGHT.md`, `scripts/cliproxy-tunnel.sh`); Mac account name
  in SSH commands (×3, same files); the proxy, tunnel, and console
  ports (×7 / ×8 / ×1) across docs, `system.clj`, and the tunnel script.
  Tailnet addresses are not internet-routable, but they name Ben's
  machines and services.
- **F3 — Mac filesystem paths.** `the router app dir on the Mac` (×3,
  `PLAN.md`), `the 0.1 checkout on the Mac` (×3: `FIT-ASSESSMENT.md`,
  `HANDOFF.md`, mirror memo), `the console app dir on the Mac` (×3,
  `HANDOFF.md`), and the the Mac proxy service configuration path under
  `~/Library/Application Support/the proxy app's data directory/` (×2, `PREFLIGHT.md`
  — the key's *location*; the value itself is absent, §2).
- **F4 — provider base-URL literal.** The loopback tunnel URL
  string in `PREFLIGHT.md` and `system.clj` (see §2). Not a
  credential; grouped here so nothing about the provider wiring is
  unlisted.
- **F5 — Structural-v1 material beyond class names.** Class names
  and URIs throughout are structure — the mirror's subject matter,
  expected. Beyond names: (a) the mirror memo records the full
  67-type relationship vocabulary in §4 (lines 114–131);
  (b) its §5 (from line ~148) prints **read-back samples including
  excerpts of mirror Living Description bodies** — short, but they
  are description bodies derived from the schema, and
  `EXPORT-AGREEMENT.md` §4 says derived material is not committed
  “in any form”; the auditor flags this as the flag closest to the
  line; (c) `results/loop_state.json` carries the mirror ontology's
  own 340-char description and provenance strings (structure-level);
  (d) `FIT-ASSESSMENT.md:84–85` quotes three short phrases verbatim
  from a the canonical 0.1 repo process contract.
- **F6 — subject-matter naming, branch-wide.** The docs necessarily
  name Ben's systems, projects, and providers throughout:
  the canonical 0.1 repo (×105), provider/model names — Codex,
  gpt-5.6-luna/terra, Groq, TypeSafe, Jev (×99), the Mac proxy service (×52),
  the governance system (×13), the Windmill workspace name and the live DB
  name (×2 / ×3). No single line is sensitive; the aggregate is a
  map of Ben's stack. Ben's call, once, for the whole branch.

**Explicitly checked and CLEAN:** the smoke outputs and the
consolidation / judge-persistence / learning-diagnosis records
contain only synthetic text (the library-budget article, the
planted-contradiction contract) and ORC-internal IDs — including
the loop-written summarizer descriptions, which generalize
synthetic executions only. `results/tier1_baseline.md` is
aggregates-only (0 needle hits). The tenant ID in the exports is
the all-ones test tenant. Upstream ORC content is untouched by the
diff (all 41 files are additions under
`experiments/router-judge-gepa/`).

## 5. Per-file verdicts (41 files)

| File | Bytes | Verdict |
|---|---|---|
| `experiments/router-judge-gepa/.gitignore` | 509 | CLEAN |
| `experiments/router-judge-gepa/EXPORT-AGREEMENT.md` | 3676 | CLEAN |
| `experiments/router-judge-gepa/FIT-ASSESSMENT.md` | 39787 | FLAG — F5/F6 — three short verbatim quotes from a 0.1 process contract (lines 84–85), system/project names |
| `experiments/router-judge-gepa/HANDOFF.md` | 21880 | FLAG — F1/F2/F3/F6 — console tailnet URL, ports, Mac app path, “the scope-trade feedback item” (line 115) |
| `experiments/router-judge-gepa/PLAN.md` | 20186 | FLAG — F1/F2/F3/F6 — tailnet IP + Mac username (source table), “the scope-trade feedback item” title + scores (line 115), system names |
| `experiments/router-judge-gepa/PREFLIGHT.md` | 24667 | FLAG — F2/F3 — tailnet IP + Mac username, the Mac proxy service config path (×2), ports |
| `experiments/router-judge-gepa/consumer/deps.edn` | 713 | CLEAN |
| `experiments/router-judge-gepa/consumer/src/router_loop/blocker_triage.clj` | 11733 | FLAG — B2 — 1 suite-derived slug (line 10) |
| `experiments/router-judge-gepa/consumer/src/router_loop/bridge.clj` | 2070 | CLEAN |
| `experiments/router-judge-gepa/consumer/src/router_loop/consolidation_experiment.clj` | 12684 | CLEAN |
| `experiments/router-judge-gepa/consumer/src/router_loop/consolidation_node_instance.clj` | 11592 | CLEAN |
| `experiments/router-judge-gepa/consumer/src/router_loop/export_loop.clj` | 17694 | CLEAN |
| `experiments/router-judge-gepa/consumer/src/router_loop/judged_sheet.clj` | 8899 | CLEAN |
| `experiments/router-judge-gepa/consumer/src/router_loop/smokes.clj` | 10343 | CLEAN |
| `experiments/router-judge-gepa/consumer/src/router_loop/structural_mirror.clj` | 12817 | CLEAN |
| `experiments/router-judge-gepa/consumer/src/router_loop/system.clj` | 8326 | FLAG — F2/F4 — loopback ports + provider base-URL literal (non-secret) |
| `experiments/router-judge-gepa/results/blocker-triage-calibration.md` | 2921 | FLAG — B2 — 10 suite counterexample item IDs (lines 13–22) |
| `experiments/router-judge-gepa/results/blocker-triage-mapping-memo.md` | 9457 | FLAG — B2 — suite ID (line 5) |
| `experiments/router-judge-gepa/results/consolidation-node-instance.md` | 6054 | CLEAN |
| `experiments/router-judge-gepa/results/consolidation-wiring.md` | 10573 | CLEAN |
| `experiments/router-judge-gepa/results/judge-persistence.md` | 6612 | CLEAN |
| `experiments/router-judge-gepa/results/learning-diagnosis.md` | 16674 | CLEAN |
| `experiments/router-judge-gepa/results/loop_state.json` | 64504 | FLAG — F5 — Structural-v1 class URIs/labels + mirror ontology description (340 chars) + provenance strings (structure-level) |
| `experiments/router-judge-gepa/results/loop_state.md` | 14550 | FLAG — F5 — mirror class list + ontology description (structure-level) |
| `experiments/router-judge-gepa/results/smoke-a-workflow.txt` | 1757 | CLEAN |
| `experiments/router-judge-gepa/results/smoke-b-judge.txt` | 1073 | CLEAN |
| `experiments/router-judge-gepa/results/smoke-c-gepa.txt` | 314 | CLEAN |
| `experiments/router-judge-gepa/results/smoke-d-router-judge.txt` | 328 | CLEAN |
| `experiments/router-judge-gepa/results/smoke-full-loop.txt` | 75 | CLEAN |
| `experiments/router-judge-gepa/results/structural-mirror-memo.md` | 12460 | FLAG — F5 — §4 relationship vocabulary in full (lines 114–131); §5 read-back samples incl. description excerpts (lines ~148–178) |
| `experiments/router-judge-gepa/results/tier1_baseline.md` | 8428 | CLEAN |
| `experiments/router-judge-gepa/scripts/cliproxy-tunnel.sh` | 1815 | FLAG — F2 — Mac tailnet IP + username in the SSH command |
| `experiments/router-judge-gepa/scripts/http_bridge.py` | 1829 | CLEAN |
| `experiments/router-judge-gepa/scripts/run-clj.sh` | 1044 | CLEAN |
| `experiments/router-judge-gepa/scripts/seed_deps.py` | 7897 | CLEAN |
| `experiments/router-judge-gepa/scripts/seed_djl.py` | 2192 | CLEAN |
| `experiments/router-judge-gepa/scripts/seed_djl_model.py` | 1973 | CLEAN |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 19309 | **BLOCKER — B1** |
| `experiments/router-judge-gepa/src/build_dataset.py` | 15659 | FLAG — B2 — 4 dataset-provenance constants (lines 196–244) |
| `experiments/router-judge-gepa/src/build_structural_mirror.py` | 17966 | CLEAN |
| `experiments/router-judge-gepa/src/tier1_score.py` | 19091 | CLEAN |
## 6. Recommendation

1. **Do not push yet.** One fix is required first: **B1** — remove
   the embedded suite passages from `src/build_blocker_triage.py`
   (read them from `data/` at runtime), and correct the mapping
   memo's “appears nowhere in this repo” claim to match reality.
2. **Ben decides, in one pass:** B2 (accept structural identifiers
   as-is, or strip the item IDs from the calibration results),
   F1 (the one real item title), F3 (the provider config path),
   F5(b) (the §5 description excerpts), and F6 (the stack map in
   aggregate). F2/F4 are low-risk tailnet/loopback detail; accept
   or strip with the same tap.
3. **After any fix:** re-run this audit's scans against the new
   diff (same needle sets, same method) and record the delta as an
   addendum to this file — do not edit this audit's findings away.
4. The publish tap itself, and the push mechanics (this clone
   holds no push credential; the route is Ben's to choose), are
   separate from this audit.

## Appendix — suite-corpus prose hits, all 45 instances

Branch file + line, needle length (chars) and SHA-256 fingerprint
(first 12), and the `data/eval-suites/blocker-triage/` file(s) the
needle was drawn from. Needle text is never reproduced. Rows marked
“boilerplate” are the two Python boilerplate lines described in §3;
all other rows are in `src/build_blocker_triage.py` and constitute
Blocker B1.

| Branch file | Line | Needle len | Needle sha12 | Suite source file(s) | Note |
|---|---|---|---|---|---|
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 302 | 125 | `2bb29ad70acf` | valset.lock.json |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 138 | 361 | `41ab04cba77d` | gepa_valset.json |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 219 | 32 | `a800d2357db6` | gepa_valset.json |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 150 | 380 | `c8b27f8bf7bc` | gepa_valset.json |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 96 | 163 | `295fe638644b` | run_agent_blocker_triage_pilot.py |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 149 | 403 | `0651512c407e` | gepa_valset.json |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 122 | 243 | `16fe13cbb208` | judge_criteria.txt |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 145 | 424 | `d8147ec93588` | gepa_valset.json |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 43 | 34 | `a6284e6d3d43` | run_agent_blocker_triage_pilot.py | boilerplate: `from __future__ import annotations` |
| `experiments/router-judge-gepa/src/build_structural_mirror.py` | 41 | 34 | `a6284e6d3d43` | run_agent_blocker_triage_pilot.py | boilerplate: `from __future__ import annotations` |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 134 | 324 | `22c337b1a8dd` | gepa_valset.json |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 115 | 592 | `b84eb042dd8c` | judge_criteria.txt |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 99 | 55 | `3de37cab8b5a` | run_agent_blocker_triage_pilot.py |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 111 | 103 | `6d221715f09b` | judge_criteria.txt |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 113 | 74 | `6f54550b91a2` | agent-blocker-triage-v0.1.yaml |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 133 | 397 | `07e95ed7d0d2` | gepa_valset.json |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 142 | 363 | `8f6af2ba4cdf` | gepa_valset.json |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 72 | 33 | `a67bea7f4b2c` | run_agent_blocker_triage_pilot.py |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 72 | 26 | `b2300b80435a` | run_agent_blocker_triage_pilot.py |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 301 | 26 | `204862bad35b` | valset.lock.json |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 269 | 39 | `465d6c1ce0eb` | gepa_trainset.json |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 113 | 327 | `a5153cd1a9f2` | judge_criteria.txt |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 125 | 513 | `c994befd1c11` | judge_criteria.txt |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 302 | 136 | `78f3d8e9d351` | valset.lock.json |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 137 | 438 | `c1752c50ff21` | gepa_valset.json |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 70 | 32 | `07e52f56bbe1` | run_agent_blocker_triage_pilot.py |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 118 | 87 | `742cdcd4daa0` | judge_criteria.txt |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 119 | 155 | `1fcd5819a1c1` | judge_criteria.txt |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 115 | 35 | `ea1b88246be6` | judge_criteria.txt |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 141 | 428 | `e38797da2e06` | gepa_valset.json |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 120 | 182 | `2fff67f21778` | judge_criteria.txt |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 123 | 154 | `b74a9ab08e1d` | judge_criteria.txt |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 146 | 366 | `8fab8d7c56f5` | gepa_valset.json |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 121 | 165 | `132e5dfe40f4` | judge_criteria.txt |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 207 | 31 | `cf51d19f9411` | gepa_valset.json |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 76 | 42 | `cd0c95dba1b5` | run_agent_blocker_triage_pilot.py |  |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 117 | 46 | `908307de3604` | judge_criteria.txt |  |
| `experiments/router-judge-gepa/scripts/http_bridge.py` | 41 | 26 | `3412809c1cf4` | run_agent_blocker_triage_pilot.py | boilerplate: `if __name__ == "__main__":` |
| `experiments/router-judge-gepa/scripts/seed_deps.py` | 191 | 26 | `3412809c1cf4` | run_agent_blocker_triage_pilot.py | boilerplate: `if __name__ == "__main__":` |
| `experiments/router-judge-gepa/scripts/seed_djl.py` | 51 | 26 | `3412809c1cf4` | run_agent_blocker_triage_pilot.py | boilerplate: `if __name__ == "__main__":` |
| `experiments/router-judge-gepa/scripts/seed_djl_model.py` | 47 | 26 | `3412809c1cf4` | run_agent_blocker_triage_pilot.py | boilerplate: `if __name__ == "__main__":` |
| `experiments/router-judge-gepa/src/build_blocker_triage.py` | 319 | 26 | `3412809c1cf4` | run_agent_blocker_triage_pilot.py | boilerplate: `if __name__ == "__main__":` |
| `experiments/router-judge-gepa/src/build_dataset.py` | 306 | 26 | `3412809c1cf4` | run_agent_blocker_triage_pilot.py | boilerplate: `if __name__ == "__main__":` |
| `experiments/router-judge-gepa/src/build_structural_mirror.py` | 445 | 26 | `3412809c1cf4` | run_agent_blocker_triage_pilot.py | boilerplate: `if __name__ == "__main__":` |
| `experiments/router-judge-gepa/src/tier1_score.py` | 337 | 26 | `3412809c1cf4` | run_agent_blocker_triage_pilot.py | boilerplate: `if __name__ == "__main__":` |