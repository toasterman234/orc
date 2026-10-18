# HANDOFF — ORC judge + GEPA router experiment

**For:** a competent agent taking over cold, with no chat context.
**State date:** 2026-10-17 (America/Chicago).
**Read first:** this file for orientation, `PLAN.md` for the plan of
record, `PREFLIGHT.md` for capability/install/run detail, and the
`results/` file named beside each completed item below. Where this file
and an older phase note disagree, the newest dated `results/` record
wins; several early blockers in `PREFLIGHT.md` were later resolved in
its §6–§7.

## What this is

An isolated experiment in Ben's fork of ObneyAI/orc
(`toasterman234/orc`, public) that maps ORC's judge + GEPA loop onto
Ben's live Windmill Things Router — 34 Jev questions, deterministic
gates A–D, frozen Registry v1 (22 labels, 12 workflows) — without
touching the live system. A second thread assessed which parts of
Ben's canonical 0.1 repo fit ORC and implemented the top recommendations
the ORC-native way. Everything lives on the local branch
`work/router-judge-gepa-experiment` in the clone at
`~/workspace/orc-router-experiment`, under
`experiments/router-judge-gepa/`. A read-only Lab Console for Ben is
hosted on the Mac mini (see Current state).

## Current state (2026-10-17)

- **Loop installed and working, consumer-side only.** The full ORC
  umbrella (service + evaluation + GEPA + ontology/ColBERT + MCP
  Sheet Builder) runs as a consumer project in `consumer/`; no ORC
  source file was changed. Toolchain is user-local on this VM:
  Temurin JDK 21 at `~/workspace/tools/jdk21`, Clojure CLI at
  `~/workspace/tools/clojure`.
- **Store:** SQLite Grain event store in gitignored
  `consumer/runtime/`; **2,296 events, 44 types, one tenant**.
  - **3 ontologies:** ORC's two seed ontologies (tree-class,
    behavioral-subtree) plus **“Structural-v1 vocabulary
    (the canonical 0.1 repo) — LAB MIRROR, not canonical”**
    (`urn:canonical-repo:structural-v1:`), pinned to the canonical 0.1 repo
    commit `b2b99e880266a3986863dfe8bfe6b4fbb8575764`.
  - **81 concepts:** 40 ORC seed + 41 Structural-v1 class concepts,
    with 157 mirror relationships (37 broader, 120 related).
  - **116 Living Description targets, 0 claims anywhere.** Two
    descriptions were written by the loop itself via consolidation
    (node-type `leaf` v1; the smoke-a summarizer node-instance v1).
    All other targets are seed or mirror scaffolding.
  - **Judge scores persist:** `:judge/score-emitted` = 4 (plus 2
    composite events); the judge-scores read model holds 4 score
    entries across 2 execution tuples.
  - **Consolidation ledger fully answered: 8 requested / 2 completed /
    6 failed (`:caller-interrupted`, attempts 0) / 0 unanswered.**
  - Still zero, by design or by non-exercise: tree profiles, node
    experiences, task classifications, claim deltas, MCP servers,
    MCP-generated sheets, evolutionary-builder extractions.
- **Provider path:** Ben's Codex subscription via the Mac proxy service
  (headless service on the Mac, the proxy service's local port) → SSH local-forward
  managed by `scripts/cliproxy-tunnel.sh` → VM `the tunnel's loopback address`.
  Model for nodes, judges, GEPA proposer, and consolidation
  reflection: **gpt-5.6-luna**. The API key lives in
  the Mac proxy service's headless configuration; the VM's copy is only in
  gitignored `data/cliproxy.env` (mode 0600). Never print, log, or
  commit the value.
- **Console:** the console's tailnet URL —
  tailnet-only, served from the Mac's `the console app dir on the Mac/` (static
  `index.html` + a stdlib Python server with POST `/decision` and
  GET `/decisions.json`, under a LaunchAgent). The editable source
  is the fullstack console artifact; its static twin is
  the static console artifact. **Standing delivery rule: Ben cannot open
  fullstack artifacts — never hand him one as the deliverable.**
  Redeploy after any loop-state re-export: edit both artifacts,
  export the static twin, copy it hash-verified over the Mac's
  `index.html`, then verify local 200 + tailnet 200 + identical
  sha256 across export/Mac/served bytes + `/decisions.json` intact.
- **Publication state:** the branch is **local only. Nothing has
  been pushed.** See `results/pre-push-audit.md` for the audit of
  the full branch diff and Ben's outstanding publish decision.

## What's been done + verified

| Item | Outcome (one line) | Record |
|---|---|---|
| Phase 0 dataset | Deterministic labeled dataset in `data/`: 61 rows (val 31 locked / train 30) from shadow rows, Ben's 7 feedback entries, and 15 decisions (7 approve / 8 reference / 0 reroute) | `results/tier1_baseline.md`, `src/build_dataset.py` |
| Tier-1 baseline | Deterministic scorer reproduces the recorded field agreements; route endorsements 7/7; disagreement-cohort kind 25/30 against the v1 comparator (not Ben gold) | `results/tier1_baseline.md`, `src/tier1_score.py` |
| Run-2 gold wiring | Per-item Run-2 gold (217 labels) joined to the dataset; recomputed val matches the quoted Phase 3 eval on all six comparable fields (kind 30/31, effort 29/31, planning 30/31, impact 31/31, stage 30/31, route 19/31). Actionable band and topics remain unverifiable from the snapshots | commit `dd43955`; `results/tier1_baseline.md` |
| Toolchain + full-loop install | JDK/Clojure installed; dependency graph solved by mechanical curl seeding; JVM networking bridged consumer-side; DJL/ColBERT seeded offline; full loop starts with Living Descriptions enabled | `PREFLIGHT.md` §6–§7 |
| Smokes (a)–(d) + full | (a) workflow success; (b) grounding judge scored 0.25 / band 2 and caught the planted contradiction; (c) GEPA completed, best 0.875 = the seed (honest null, improvement 0.0); (d) 3 val decision records judged [0.25, 0.0, 0.25]; full-loop wiring verified (embeddings 384 dims) | `results/smoke-*.txt` |
| FIT assessment | 16 candidates from the canonical 0.1 repo inventoried and ranked with fit, mode, effort, caveats, sequencing, and kill criteria. Assessment only at filing time | `FIT-ASSESSMENT.md` |
| FIT #3 — export agreement | Lightweight ORC-shaped export agreement adopted: pinned commit, losses named in the export, local-only landing, “LAB — not canonical” stamps, deletion must be free | `EXPORT-AGREEMENT.md` |
| FIT #2 — Structural-v1 mirror (native) | Deterministic, 0 LLM calls: 41 class concepts + 157 relationships + 41 Living Descriptions in a new ontology; losses enumerated (typed slots, cardinality, validation, transition rules, structured Confidence, `field_authority`) | `results/structural-mirror-memo.md` |
| FIT #1 — blocker-triage calibration (native) | **PARTIAL, banked.** Agreement 0.667 (7 full / 2 partial / 3 mismatch of 12; 12 luna calls). Pattern: perfect on fail-side items, over-fails correct answers — a harsh critic, not yet a balanced grader. This does **not** satisfy the router calibration gate below | `results/blocker-triage-calibration.md`, `results/blocker-triage-mapping-memo.md` |
| Learning diagnosis | Three independent breaks proven from ORC source: offline `evaluate-single` writes no events; profiles/experiences/classification are consumer-command-only (automatic feeder retired upstream); the consolidation processor's namespace was never loaded, so requests were orphaned | `results/learning-diagnosis.md` |
| Judge-score persistence | Judges attached to a rebuilt summarizer sheet the real way; 2 executions → 4 persisted scores, verified in-process, by SQLite census, and by cold read-back | `results/judge-persistence.md`, `consumer/src/router_loop/judged_sheet.clj` |
| Consolidation wiring + leaf completion | Consumer now loads the consolidator namespace and pins its reflection model (registry 34 → 35). One on-demand request swept leaf's 3 orphans to `:caller-interrupted` failures and completed: `leaf` description v1 from 35 executions (1 call, 15,684 tokens) | `results/consolidation-wiring.md` |
| Node-instance orphans answered | One on-demand request swept the summarizer instance's 3 orphans the same way and completed: instance description v1 from 33 executions (1 call, 13,272 tokens). Ledger fully answered | `results/consolidation-node-instance.md` |
| Loop-state exports + console | Read-only exports after each state change (census before = after, 0 LLM calls, privacy scans clean); console Loop tab refreshed and hash-verified each time | `results/loop_state.md` + `loop_state.json` |

## Decisions of record (Ben's)

- **2026-10-17 — fork + isolation.** Run the experiment in his fork,
  fully isolated; the plan lives in-repo because the fork has Issues
  disabled.
- **2026-10-17 — provider + scope.** Use his Codex subscription via
  the Mac proxy service, and the efficient luna tier; install the
  **full** self-improving loop, not a reduced package set. Recorded
  escalation if quality requires it: gpt-5.6-terra (not used yet).
- **2026-10-17 — UI delivery rule.** UIs for Ben are delivered as
  Mac-hosted apps behind tailnet links, never as fullstack artifact
  cards (recurring failure, his standing order).
- **2026-10-17 — FIT follow-through, ORC-native.** Implement FIT
  #1/#2 natively rather than as 1:1 LinkML translations; #3 becomes
  the lightweight export agreement. See `PLAN.md` §8.
- **2026-10-17 — blocker-triage PARTIAL banked.** The two known
  levers (criteria reframing on luna; a terra re-run) are **parked
  by his choice — do not re-offer them unprompted.**
- **2026-10-16, standing — the scope-trade feedback item.** Ben accepted Jev's
  read as-is; in this experiment it is a diagnostic case, not a
  defect to silently fix against his decision.
- **Standing — publish tap.** Nothing leaves this machine (push, PR,
  publish) without Ben's explicit tap, given separately for each
  send. Approval does not carry over.

## Boundaries that bind any successor

- **No writes to live systems:** no live router database writes, no
  Windmill flow/script changes, no Registry changes, no governance-system
  changes, no Things changes, no dispatch. Router data enters only
  as read-only file copies in gitignored `data/`.
- **Consumer-side only.** ORC's own sources are never modified; all
  wiring lives in `consumer/`, `scripts/`, and `src/`. If a fix
  seems to require an ORC source change, stop and surface it.
- **The fork is PUBLIC.** Ben's real Things titles/notes/feedback,
  decisions text, and eval-suite content live only in gitignored
  `data/` and the local store. Committed files carry structure,
  code, counts, and aggregates only. Re-verify `git check-ignore`
  for `data/` and `consumer/runtime/` before every commit.
- **the canonical 0.1 repo stays canonical and read-only.** ORC-side
  mirrors are labeled proposals under `EXPORT-AGREEMENT.md`;
  extraction may propose, never canonize. Anything lab-produced
  returns only as a proposal through 0.1's own promotion flow, on
  Ben's approval.
- **Gates A–D are never GEPA targets** (code/policy). A GEPA winner
  is a v+1 diff proposal for Ben's freeze tap, never an application.
- **Merges, publishes, production changes, and credential moves**
  each need Ben's explicit tap.

## How to run things

Toolchain: `~/workspace/tools/jdk21` + `~/workspace/tools/clojure`
(already installed). `scripts/run-clj.sh` assembles the environment
(JAVA_HOME, pinned JVM `user.home`, LMDB flags, offline DJL,
ColBERT path, provider env from `data/cliproxy.env`) and runs from
`consumer/`. Start the provider tunnel first.

```bash
cd ~/workspace/orc-router-experiment/experiments/router-judge-gepa
scripts/cliproxy-tunnel.sh start|status|stop

# Install smokes (a|b|c|d|full) — PREFLIGHT §7.8
scripts/run-clj.sh -M -m router-loop.smokes a

# Attached-judge sheet (the score-persistence proof)
scripts/run-clj.sh -M -m router-loop.judged-sheet

# Consolidation experiments (bounded, on-demand, one target each)
scripts/run-clj.sh -M -m router-loop.consolidation-experiment
scripts/run-clj.sh -M -m router-loop.consolidation-node-instance

# Structural mirror: default applies from the data/ payload; `verify` only re-checks
scripts/run-clj.sh -M -m router-loop.structural-mirror verify

# Blocker-triage calibration runner (bounded; verdicts in results/)
scripts/run-clj.sh -M -m router-loop.blocker-triage

# Read-only loop-state export → results/loop_state.{json,md}
scripts/run-clj.sh -M -m router-loop.export-loop
```

Python builders/scorers in `src/` (`build_dataset.py`,
`tier1_score.py`, `build_blocker_triage.py`,
`build_structural_mirror.py`) regenerate the `data/` payloads from
their sources; run them only against the gitignored copies.

**Rebuilding on a fresh VM:** follow `PREFLIGHT.md` §6–§7 in order
— toolchain tarballs, `scripts/seed_deps.py` (error-driven Maven
seeding), `scripts/seed_djl.py` + `scripts/seed_djl_model.py`
(offline DJL/ColBERT caches). Do not improvise a second method.

**Committing in this clone:** it is a partial (blob:none) clone;
porcelain commits trigger a mass promisor prefetch and stall. Use
the plumbing method in `PREFLIGHT.md` §1 (temp index, `read-tree`,
`add`, `write-tree --missing-ok`, `commit-tree`, `update-ref`),
verify the new SHA and a clean tree in the foreground, and refresh
the real index to HEAD before trusting `git status`.

## Gotchas

- **JVM TCP is denied in this VM sandbox** (loopback included).
  ORC's one HTTP call is delegated consumer-side to
  `scripts/http_bridge.py` over stdio (`router_loop/bridge.clj`).
  Do not “fix” this in ORC sources.
- **Register the provider as `:openai`** even though the endpoint is
  the Mac proxy service/Codex. A custom provider keyword drops ORC into DSPy
  marker mode and its banding gate rejects judge outputs
  (`PREFLIGHT.md` §7.5).
- **JVM `user.home` ≠ shell `$HOME`** here; `run-clj.sh` pins it.
  Bare `clojure` invocations will look in the wrong home.
- **DJL runs offline** (`-Dai.djl.offline=true`). Models are
  curl-seeded caches; a cache miss is a seeding problem, not a
  download opportunity.
- **Offline judge calls persist nothing.** `evaluate-single` is a
  pure function. Scores land only via judges attached to executed
  sheets (`:evaluation/record-judge-score`). See
  `results/learning-diagnosis.md` before re-discovering this.
- **The consolidator must be loaded explicitly.**
  `router-loop.system` requires
  `ai.obney.orc.ontology.core.consolidator` (a namespace-load side
  effect registers its processor) and pins
  `:ontology-consolidator-model` in ctx. Registry is 35 processors,
  not 34, for this reason.
- **No startup replay.** Processors consume the event pubsub, not a
  store replay: after any restart, an unanswered consolidation
  request stays invisible until a same-target request triggers the
  handler's scoped orphan sweep. (Currently 0 unanswered.)
- **Claims are tree-class-only.** Node-type and node-instance
  consolidations write whole-body descriptions; claim deltas exist
  only on the `:tree-class` claim path (CC-5), which needs
  classified repl-researcher campaigns — never exercised here.
- **Mirror count correction:** the FIT headline said 38 classes;
  the pinned schema defines **41**, and the mirror follows the
  schema. The mirror is pinned @ `b2b99e8` and will drift as 0.1
  moves; refresh = re-export + reconcile per `EXPORT-AGREEMENT.md`,
  never a silent edit of mirrored content.
- **The 0.1 checkout is not on main.** The 0.1 checkout on the Mac sits on
  `work/inventory-f10-f11-fix-v0.1` @ `b2b99e8` with untracked
  working dirs. It is a read-only source; do not clean, switch, or
  “fix” it from this experiment.
- **Snapshot blind spots:** the Phase 0 snapshots carry no
  `is_actionable` probabilities or topic sets, so actionable-band
  and topic accuracy are unverifiable until a fresh labeled pull
  exists. Ben's decisions contain **0 reroutes**, so route-error
  learning leans on feedback + gold. The 30-item disagreement
  sample is graded only against the v1 comparator, not by Ben.
- **Mac mini disk is critical.** Nothing is cloned or installed on
  the Mac from this experiment; the only Mac-side pieces are the
  pre-existing Mac proxy service, the read-only source copies,
  and the console app.
- **The system is a bench, not a daemon.** It starts per run and
  exits; async work (judges, consolidation) needs the process kept
  alive with bounded polls until outcomes land.

## What's open / next

1. **Router calibration gate — awaiting Ben's go (the next real
   step).** Build the four Tier-2 router judges (Route Grounding,
   Label Fidelity, Cross-Field Consistency, Missing-Option) in
   ORC's shape and prove them against Ben's 7 feedback items + the
   31 gold at the pre-registered rate in `PLAN.md` Phase 1 (any
   judge below 80% field-level agreement is reported uncalibrated
   and excluded). No GEPA on router instructions before this gate.
2. **After the gate:** failure-type triage on the known clusters,
   a shadow proposer, then one bounded GEPA run on **one** router
   instruction family (A, B, or C in `PLAN.md` §1), ending in a v+1
   diff table for Ben's freeze tap.
3. **Tree-class claim path:** needs execution classification /
   repl-researcher campaigns before any claim-level learning can
   exist. Unexercised; larger than anything done so far.
4. **Profiles / experiences:** consumer-command-only by upstream
   design. Issuing `record-tree-strength/weakness` or
   `record-node-pattern` deliberately is a design decision for
   Ben, not a wiring fix.
5. **FIT candidates assessed, not started:** #4 process-contract
   port (paper mapping first, Ben reviews before any sheet), #5
   inventory mirror and #7 profile/prompt seeds (behind Ben's
   explicit tap, local-only), #8/#9 MCP directions (when their
   preconditions exist).
6. **Publication:** the branch stays local until Ben's publish
   tap. `results/pre-push-audit.md` lists what a push would expose
   and what must be decided first.

## File map

All paths under `experiments/router-judge-gepa/` unless noted.

**Orientation / governance**
- `HANDOFF.md` — this file: cold-start orientation and state.
- `PLAN.md` — plan of record: mapping, boundaries, phases, §8 FIT
  decision addendum.
- `PREFLIGHT.md` — capability pre-flight, toolchain + full-loop
  install record, rerun instructions (§7.8), plumbing-commit
  method (§1).
- `FIT-ASSESSMENT.md` — the 16-candidate 0.1 fit
  assessment (ranked shortlist, caveats, kill criteria).
- `EXPORT-AGREEMENT.md` — rules for any future 0.1 → ORC movement.
- `.gitignore` — excludes `data/`, `consumer/runtime/`, caches, and
  local env files; the privacy boundary in file form.

**Consumer (Clojure) — `consumer/`**
- `deps.edn` — consumer deps: full ORC umbrella via local root +
  the Grain SQLite event-store brick.
- `src/router_loop/system.clj` — system assembly: event store, LMDB
  cache, processors, Living-Description wiring, consolidator
  require + reflection-model pin.
- `src/router_loop/bridge.clj` — stdio HTTP bridge for the JVM's
  one provider call.
- `src/router_loop/smokes.clj` — install smokes (a)–(d) + full-loop.
- `src/router_loop/judged_sheet.clj` — summarizer sheet with
  attached judges (score-persistence proof).
- `src/router_loop/blocker_triage.clj` — FIT #1 judge runner +
  pre-registered calibration scoring.
- `src/router_loop/structural_mirror.clj` — FIT #2 mirror apply +
  verify against the `data/` payload.
- `src/router_loop/consolidation_experiment.clj` — leaf
  consolidation driver (observe → fire → settle).
- `src/router_loop/consolidation_node_instance.clj` — the same for
  the summarizer node-instance target.
- `src/router_loop/export_loop.clj` — read-only layers 6–8 export
  behind the console's Loop tab.

**Scripts — `scripts/`**
- `cliproxy-tunnel.sh` — start/stop/status for the SSH
  local-forward to the Mac proxy service.
- `run-clj.sh` — the environment-assembling Clojure entry point.
- `http_bridge.py` — performs the provider HTTP call for the JVM.
- `seed_deps.py` — error-driven Maven/Clojars seeding into the
  local repository.
- `seed_djl.py`, `seed_djl_model.py` — offline DJL/libtorch and
  embedding-model cache seeding.

**Python builders / scorers — `src/`**
- `build_dataset.py` — Phase 0 labeled dataset builder (writes
  `data/` only).
- `tier1_score.py` — deterministic Tier-1 scorer vs gold/verdicts.
- `build_blocker_triage.py` — builds the FIT #1 GEPA datasets +
  locked valset from the suite copies in `data/`.
- `build_structural_mirror.py` — deterministic Structural-v1 →
  ORC-shaped mirror payload builder.

**Results — `results/`**
- `tier1_baseline.md` — Phase 0 reconciliation + Tier-1 baseline
  (aggregates only).
- `smoke-a-workflow.txt`, `smoke-b-judge.txt`, `smoke-c-gepa.txt`,
  `smoke-d-router-judge.txt`, `smoke-full-loop.txt` — install
  smoke outputs.
- `structural-mirror-memo.md` — FIT #2 mapping, count correction,
  losses, reconciliation.
- `blocker-triage-mapping-memo.md` — FIT #1 field mapping and
  native-idiom choices.
- `blocker-triage-calibration.md` — FIT #1 calibration results:
  PARTIAL 0.667, per-item table, spend.
- `learning-diagnosis.md` — why the loop learned nothing: the
  three breaks, source-cited.
- `judge-persistence.md` — attached-judge wiring + three-way
  persistence proof.
- `consolidation-wiring.md` — consolidator wiring + leaf
  completion, orphan dispositions.
- `consolidation-node-instance.md` — node-instance orphans
  answered; ledger closed.
- `loop_state.json`, `loop_state.md` — read-only layers 6–8 store
  export; the console Loop tab's data source.
- `pre-push-audit.md` — audit of the full branch diff as a
  publication unit for the public fork.

**Local only (never committed)**
- `data/` — real router snapshots, the labeled dataset, eval-suite
  copies, mirror payloads + read-backs, provider env file.
- `consumer/runtime/` — event store, LMDB cache, ColBERT model +
  indexes, tunnel pid/log.
