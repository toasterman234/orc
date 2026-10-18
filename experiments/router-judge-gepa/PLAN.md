# Experiment Plan — ORC Judge + GEPA loop mapped onto the Windmill Things Router

- **Status:** PLAN OF RECORD for this experiment. The fork (`toasterman234/orc`)
  has Issues disabled, so this file in-repo is the plan of record.
- **Branch:** `work/router-judge-gepa-experiment` (local only — see Boundaries).
- **Base:** `main` @ `1851212396cced2fc4533fda30b824bd4b691390` (= upstream
  `ObneyAI/orc` HEAD at fork time).
- **Started:** 2026-10-17, on Ben's order (“run this in an isolated experiment”).
- **Design source:** the ORC→Router mapping designed 2026-10-17 (judge + GEPA
  mapping session). This plan implements that mapping; it does not redesign it.

## 1. Purpose

Test whether ORC's judge + GEPA loop improves the Windmill Things Router's
classification quality, in isolation, before any change to the live router is
proposed.

The router today: Windmill flow in the production Windmill workspace, one Jev call per Things
item answering 34 questions (10 fixed + 22 topic booleans + 1 route choice +
derived fields), deterministic gates A–D in code, results in the Postgres
the live router database store, Registry v1 frozen by Ben (22 labels, 12 workflows), and
Ben's own Approve / Reroute / Park decisions + feedback in the Things Router
app as ground truth.

The loop under test (ORC, `docs/JUDGE-ARCHITECTURE.md`,
`docs/EVALUATION-COMPONENT.md`, `docs/GEPA-GUIDE.md`):

```
judge scores (evidence before score) → reflective dataset of failures
  → proposer LLM rewrites ONE instruction → subsample gate
  → full valset eval → per-example Pareto frontier → winner = proposal
```

### Optimizable instruction families (the ONLY GEPA targets)

ORC GEPA optimizes a named `:llm` node's `:instruction` string. The router
has no single instruction string; it has three instruction families, and
GEPA runs against **one family, one target at a time** (ORC's own
sub-behavior pattern — each sheet optimizes independently):

| Family | Target unit | Source of current text |
|---|---|---|
| **A — Fixed questions** | The `instructions` + per-answer `criteria` of ONE of the 10 fixed Jev questions (kind, effort, is_actionable, time_sensitive, planning_required, impact, blockers, blocker_type, part_of_bigger, + route instructions) | `questions_fixed.json` / `flow_classify_direct.py` `FIXED` |
| **B — Registry labels** | The `definition` + `criteria` (“Use when… / Not when…”) of ONE of the 22 active labels; Jev's per-topic question is generated from exactly this text | Registry v1 (candidate generation in `labels_final.json` — note: that file is the pre-freeze 27-label candidate set; the frozen 22 are the active subset in the live DB) |
| **C — Workflows** | The `owns_when` of ONE of the 12 workflows; Jev's route question criteria are generated from exactly this text | Registry v1 `workflows` (`workflows_final.json`) |

**NOT optimizable:** gates A–D and their thresholds (0.4 / 0.7 actionable
band, 0.5 blockers, 0.6 low-confidence) in `gates.py`. Gates are code and
policy. GEPA must never rewrite them; a failure diagnosed as a gate artifact
is logged as such and exits the GEPA path. Threshold tuning, if ever, is a
separate numeric pass on labeled data with its own proposal to Ben.

**Failure-type triage before any mutation** (a step ORC does not need and
this router does): *definition blur* → mutate criteria; *overlap* → sharpen
the “Not when…” clause; *missing option* (the correct label/route is not in
the registry — Jev's known near-chance failure) → registry proposal for Ben,
GEPA stops; *gate artifact* → logged, GEPA stops.

## 2. Isolation boundaries (hard)

- **No writes to the live Windmill router database.** No writes at all to any
  live system: no Windmill flow/script changes, no Registry changes, no
  governance-system changes, no Things changes.
- **Read-only exported snapshots only.** All router data enters this
  experiment as file copies in the gitignored `data/` dir (§3). Nothing in
  this experiment opens a connection to the live DB.
- **No execution / dispatch.** This experiment classifies and scores offline
  snapshots. It never dispatches a workflow, stages a draft in the live
  system, or executes a routed item.
- **Local branch only. Nothing is pushed** — no push, no PR, no publish of
  any kind from this repo clone without Ben's explicit publish tap, given
  separately.
- **Nothing is cloned or installed on the Mac mini.** Its disk is a known
  critical resource. Runtime installs (§5, Phase 2) happen user-locally under
  `~/workspace` on the experiment VM only.
- **Winner is a proposal, never an application.** A GEPA winner becomes a
  v+1 diff proposal (Registry / Questions) for Ben's freeze tap, in the same
  form as the Registry v1 freeze review. It is never auto-applied to the
  live router, the registry, or this repo's upstream files.

## 3. Privacy rule (the fork is PUBLIC)

- `github.com/toasterman234/orc` is a **public** repo. Ben's real Things
  titles, notes, feedback text, and decisions must **NEVER** be committed or
  pushed.
- Real data lives **only** in `experiments/router-judge-gepa/data/`, which
  is gitignored (see `.gitignore` in this dir) and verified with
  `git check-ignore` before every commit in this experiment.
- Anything ever proposed for push must be **synthetic or aggregated/redacted**
  (counts, scores, anonymized patterns) and passes a manual review for
  titles/names/identifiers before Ben's publish tap.
- `feedback.jsonl` / `decisions.jsonl` in `data/` are **read-only copies**
  fetched from the Mac (the router app dir) over `mac-ssh.sh`. The Mac
  originals are never edited from this experiment.

## 4. Data sources (all local, all copies)

In `data/` (gitignored):

| File | Source | Contents / role |
|---|---|---|
| `shadow31_rows.json` | `~/workspace/windmill-llm-router/shadow31_rows.json` | The 31-item shadow run results — the gold/val cohort (see §5 Phase 0) |
| `disagreement_sample.md` + `disagreement_key.json` | `~/workspace/windmill-llm-router/` | 30-item blind v1-vs-v2 disagreement sample (still ungraded by Ben; usable as train material only once graded) |
| `labels_final.json` | `~/workspace/windmill-llm-router/generation/labels_final.json` | One-time LLM generation output: 27 candidate labels with definitions/criteria/examples (22 were frozen active) |
| `workflows_final.json` | `~/workspace/windmill-llm-router/generation/workflows_final.json` | The 12 workflows with `owns_when` and stages |
| `questions_fixed.json` | `~/workspace/windmill-llm-router/questions_fixed.json` | The 10 fixed Jev questions (family A seed text) |
| `EVAL_PHASE3.md` | `~/workspace/windmill-llm-router/EVAL_PHASE3.md` | Phase 3 eval report: 31-item agreement table, 679-run stats, the scope-trade failure analysis — the baseline this experiment must beat or match |
| `feedback.jsonl` | Mac, the router app dir (copy via `mac-ssh.sh` to the Mac's SSH address) | Ben's 7 real feedback entries (wrong_parts, reasons, text per item) — primary reflective seed |
| `decisions.jsonl` | Mac, the router app dir (copy via `mac-ssh.sh`) | Ben's app decisions audit (approve / reroute / reference with timestamps) — ground-truth labels |

Baseline numbers of record (from `EVAL_PHASE3.md`): 31 gold — kind 30/31,
effort 29/31, planning 30/31, impact 31/31, stage 30/31, actionable mean
|Δ| 0.016. 679 run — kind agreement vs v1 88.5%, actionable band 91.9%,
primary-topic agreement on mappable items 59.1% (weakest axis). Acceptance
items: HVAC ×3 PASS; the scope-trade feedback item FAIL (Jev's
top-ranked topic won over the runners-up) — Ben accepted Jev's
read as-is on
2026-10-16; in this experiment it is a *diagnostic* case, not a defect to
silently “fix” against his decision.

## 5. Phases

### Phase 0 — Labeled dataset build
Join `decisions.jsonl` + `feedback.jsonl` + `shadow31_rows.json` results on
`source_id` into one versioned labeled dataset (JSON/JSONL in `data/`):
each row carries the item's router output (full Jev answers/probabilities
where present, route, stage, gate), Ben's verdict where one exists
(approve = correct; reroute = route wrong + correct route; park/reference =
stage verdict; feedback wrong_parts/reasons/text), and its split assignment.

- **Locked split:** val = the 31 gold items (the `shadow31` cohort, graded
  against Run-2 gold). Train = everything else labeled (Ben's feedback and
  decision items not in the 31). The split is written to a manifest with a
  content hash and **never re-drawn**; GEPA never trains on val items.
- Seed instruction state is hashed at build time: Registry v1 text +
  `questions_fixed.json` as the flow builds them.
- **Success:** dataset builds deterministically from the snapshots alone;
  row counts reconcile against sources (31 val rows; 7 feedback entries and
  every decisions.jsonl line accounted for as joined or explicitly
  unjoinable with reason); manifest + hashes recorded.
- **Stop:** any source file missing/corrupt, or join ambiguity that would
  guess a label — stop and report, do not infer labels.

### Phase 1 — Judges offline (Tier-1 scorer + Tier-2 LLM judges)
- **Tier-1 (primary, deterministic):** a scorer (Python is fine) computing
  per-field agreement vs Ben's verdicts/gold — route exact-match, topic
  primary + multi-label set, kind, actionable band, stage. This is ORC's
  structural-metric role and the Phase 3 eval, made standing.
- **Tier-2 (only where no Ben verdict exists):** LLM judges in ORC's shape —
  `criteria × stance × scale` kept separate, discrete 1–5 bands with the
  score derived deterministically from the band, adversarial stance
  (defend the null), evidence lists written *before* the score. Four
  router judges per the mapping: Route Grounding, Label Fidelity,
  Cross-Field Consistency (e.g. label `personal` but route
  `agent_research`), Missing-Option (candidate-recall check).
- Implementation route for Tier-2: ORC-native (the `orc-evaluation`
  package's judges on a router behavior-tree sheet) once Phase 2 lands, or
  a faithful local re-implementation if Phase 2 blocks — the choice and its
  fidelity notes are recorded in this plan's experiment log before Phase 3.
- **Calibration gate (hard):** Tier-2 judges are not trusted — and do not
  feed GEPA — until they reproduce Ben's verdicts on his **7 feedback
  items** and agree with gold on the **31 val items** at a pre-registered
  rate (per-judge agreement recorded; any judge below 80% field-level
  agreement on calibrated items is reported as uncalibrated and excluded,
  not tuned against the same items it was tested on).
- **Success:** Tier-1 scorer reproduces the EVAL_PHASE3 31-item table from
  the snapshots; Tier-2 calibration report written with per-judge numbers.
- **Stop:** Tier-1 cannot reproduce the Phase 3 table (means the snapshots
  don't carry what the eval used — report exactly what's missing).

### Phase 2 — ORC runtime pre-flight (this VM only)
- Install **Temurin JDK 21+** and the **Clojure CLI** user-locally under
  `~/workspace` on this VM (no system packages, nothing on the Mac).
- Configure ORC per `docs/GETTING-STARTED.md`: a `ctx` with a Grain event
  store (in-memory or SQLite for a single process), the LMDB projection
  cache, a tenant poller, and an LLM provider. **Provider decision
  (Ben, 2026-10-17): his Codex subscription via the Mac proxy service on the Mac
  mini** — NOT OpenRouter, no new key. The VM reaches the Mac proxy service through an
  SSH local-forward (`the tunnel's loopback address` → Mac `the proxy service's loopback address`,
  `scripts/cliproxy-tunnel.sh`); the existing key was copied once from
  the Mac proxy service's config into the gitignored `data/cliproxy.env`
  (0600) — no other credential is moved or created. **Model:
  gpt-5.6-luna** for nodes, judges and the GEPA proposer (evidence: one
  tiny completion each — gpt-6-luna 200 in 7.0 s, gpt-5.6-luna 200 in
  1.8 s, identical usage; luna is the proven efficient tier). Escalation
  if the Phase-1 calibration gate fails: gpt-5.6-terra. Full install
  record: PREFLIGHT §7.
- Run ORC's own Getting Started example end-to-end: Phase 1 (core tree),
  Phase 2 (attach a built-in judge, read the `:judge/score-emitted` event),
  Phase 4 (a small `gepa/optimize!` run on the example tree with a judge
  metric). **Package (Ben's order, 2026-10-17, superseding the original
  exclusion below): the FULL self-improving loop** — the umbrella
  `projects/orc` (orc-service + evaluation + GEPA + ontology + ColBERT +
  MCP sheet builder), pulled into an experiment consumer project via
  `:local/root`; Living-Descriptions opt-in enabled, baseline corpus
  seeded, ColBERT bootstrap-reindex run. (Original text, now superseded:
  "Packages: `obneyai/orc-evaluation` for judges; `obneyai/orc-gepa` for
  GEPA. The umbrella `projects/orc` (ontology/ColBERT/DJL) is not needed
  and not pulled.")
- **Success:** the example tree executes, a judge score event is read back,
  and a bounded `optimize!` returns a best candidate — all logged with
  versions (JDK, Clojure, ORC sha).
- **Stop:** install or dependency resolution fails after the documented
  path is followed (Grain git deps + Maven Central) — record the actual
  error in PREFLIGHT.md and stop; do not improvise a different runtime.

### Phase 3 — Bounded GEPA loop (per the mapping)
- One run = one family (A/B/C), one target (one question / one label / one
  workflow). Seed = current frozen text for that target.
- Reflective dataset from **train** failures only: `{Inputs, Generated
  Outputs (full Jev probability map + stage + gate), Feedback}` where
  Feedback = Tier-1 verdict + Ben's verbatim feedback (train items) +
  calibrated Tier-2 weakest-first diagnosis.
- Proposer = a generative LLM (never Jev — Jev returns typed picks only).
  Proposer model + budget are named in the run record before the run.
- Failure-type triage (§1) runs before mutation; missing-option and
  gate-artifact failures exit with a proposal/log entry, no candidate.
- **Subsample gate:** candidate is first tested on 5 items (the failing
  item + 2 neighbors sharing the target + 2 currently-passing controls).
  No improvement → candidate discarded, no valset spend.
- **Full eval:** surviving candidates run the locked 31-item valset through
  Jev with the candidate text swapped in (offline; the live flow is never
  touched — Jev is called the same way the router calls it, against
  snapshot items).
- **Pareto frontier:** per-example best across route / topic /
  actionable-band / stage — a candidate that fixes one val item by breaking
  another does not dominate. (HVAC ×3 are named non-regression controls.)
- **Budget cap:** ~30–50 metric calls per run (ORC's `max-metric-calls`
  range), stated in the run record; the run stops at the cap regardless of
  frontier state.
- **Output:** the winner, if any, as a **v+1 diff proposal** — one row per
  changed text: target, old text, new text, val deltas per field, frontier
  evidence — written as a proposal file in this experiment dir (aggregated/
  redacted form only; no real item titles in the committed proposal —
  examples are referenced by source_id or synthetic paraphrase). Ben's
  freeze tap is the only path from proposal to live.
- **Success:** at least one run completes inside budget with a frontier and
  either (a) a dominating winner → proposal, or (b) an honest null result
  (no candidate dominates the seed) — a null result is a successful
  experiment outcome, not a failure.
- **Stop:** budget cap hit; proposer or Jev route errors on >20% of calls
  in a run; any candidate would need a gates/registry-key change to take
  effect (out of scope — record and stop that run).

## 6. Global stop conditions

- Any step would write to a live system, move a credential, push to GitHub,
  or install anything on the Mac → stop, report, wait for Ben.
- Any file containing real Things titles/notes/feedback appears in
  `git status` as tracked/staged → stop immediately, unstage, fix the
  ignore rule, and report before committing anything.
- Judge calibration (Phase 1) fails and cannot be explained from the
  snapshots → the GEPA phase does not start on uncalibrated judges.
- Costs: any phase's LLM/Jev spend is estimated before it runs; a run that
  would exceed its stated cap stops at the cap.

## 8. Decision addendum — 2026-10-17 (FIT-ASSESSMENT follow-through)

Ben approved the FIT-ASSESSMENT recommendations **with the ORC-native
amendment** (2026-10-17): candidates #1 and #2 are implemented the
ORC-native way rather than as 1:1 LinkML translations, and candidate #3
is reduced to a lightweight ORC-shaped export agreement
(`EXPORT-AGREEMENT.md` in this dir). This addendum records what "native"
means concretely; the isolation boundaries (§2) and privacy rule (§3)
are unchanged and apply in full.

- **#2 native (Structural-v1 vocabulary → concepts).** The 38 classes of
  `registry/model/schema.yaml` (the canonical 0.1 repo @
  `b2b99e880266a3986863dfe8bfe6b4fbb8575764`) become 38 rich concepts in a
  **new** ontology, base-uri `urn:canonical-repo:structural-v1:` —
  never inside the seed `tree-class:` / `behavioral-subtree:` ontologies.
  Semantics travel as readable text, not as translated formalism: each
  class's Living Description summary carries its purpose, its lifecycle
  states (from its lifecycle enum) as prose, and its notable slots
  summarized in prose. There is **no** enum-value concept long tail.
  Edges exist only where the schema declares them: `is_a` →
  `skos:broader`; a slot whose range is another schema class →
  `skos:related` with the slot named in the relationship metadata. The
  relationship-type vocabulary (`registry/relationships/registry.yaml`)
  is recorded in the mapping memo, not fabricated into edges. Every
  concept is provenance-stamped (`:kind :imported`, source-reference =
  repo @ commit + path) and labeled a lab mirror — **not canonical**;
  the canonical 0.1 repo remains the only authority (its ADR-0002; extraction
  may propose, never canonize). Known losses are enumerated in the
  mapping memo, not discovered later: slots-as-typed-properties,
  cardinality, required, validation constraints, lifecycle transition
  rules, structured Confidence, `field_authority`.
- **#1 native (blocker-triage eval suite → judge + GEPA datasets).**
  One judge config authored in ORC's tier-1 native idiom
  (criteria × stance × discrete 1–5 scale, evidence before score),
  using the tier-1 per-call criteria-override seam: criteria are derived
  from the suite's `target_behavior` principle, and
  `must_not_generalize_to` is folded into the criteria as explicit
  failure conditions (the tier-1 seam overrides criteria, not stance;
  the stance remains the dimension's built-in adversarial stance, and
  the memo says so). Datasets are in ORC GEPA's string-keyed map
  format: valset = scenarios carrying expected verdicts, **locked**;
  trainset = the remaining material. Suite content lives only in
  gitignored `data/`; committed artifacts are code, the generic mapping
  memo, and aggregate results (no scenario text). Calibration is
  bounded: **≤ 60 LLM calls**, gpt-5.6-luna via the existing Mac proxy service
  path, and the verdict (passed / partial / failed) is reported with
  per-scenario numbers, as-is.
- **#3 (export agreement).** `EXPORT-AGREEMENT.md`: any future
  0.1→ORC movement arrives as ORC-shaped JSON (concepts / relationships
  / provenance), pinned to a 0.1 commit, losses documented in the
  export itself. No pipeline is built under #3; the agreement is the
  deliverable.

## 7. What this experiment deliberately does NOT do

- No adoption of ORC as the router's runtime; no Clojure rewrite of the
  router; no behavior-tree replacement of the Windmill flow.
- Living Descriptions / ontology / ColBERT are **installed** for the
  experiment (Ben's full-loop order, 2026-10-17 — see §Phase 2) but are
  **not adopted into the live router**: no auto-promotion of new labels
  or workflows — new vocabulary is proposed to Ben, never minted.
- No changes to gates, thresholds, the Registry, or any live artifact.
