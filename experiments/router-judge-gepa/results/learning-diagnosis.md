# Learning diagnosis — why the loop has learned nothing in this install

- **Date:** 2026-10-17 (run in the 2026-10-18T00:0xZ process window, VM clock UTC)
- **Question (Ben's):** "Why hasn't the loop learned anything? What would it need?"
- **Method:** ORC source traced in this repo (file/namespace citations per
  claim), the consumer's wiring read as-built, the live processor registry
  enumerated in a foreground JVM eval under the consumer's own requires, and
  the SQLite event store censused read-only (file copies). Store state at
  diagnosis time: 2,109 events, 39 types (the `results/loop_state.md`
  export of the same day).

## The chain, link by link

The self-improving loop is a chain:
execution → attached judge → score event → judge-scores read model →
classification → description claims → consolidation → updated descriptions.
Every link's status in THIS install:

| # | Link | Status | Evidence |
|---|------|--------|----------|
| 1 | Execution → attached judge fires | **NEVER EXERCISED** | Every sheet the consumer built (smoke-a/b/c/d, GEPA, blocker-triage) declares no judges and attaches none: `consumer/src/router_loop/smokes.clj` `summarizer` has no `orc/judges` part and its `orc/llm` node has no `:judges`. The firing machinery is real: todo-processor `:evaluation/on-node-execution-completed` (`components/evaluation/src/ai/obney/orc/evaluation/core/judge_runtime.clj`, `defprocessor` at end of file) subscribes to `:sheet/node-execution-completed`, resolves the node's effective judges (`get-effective-judges-for-node`: explicit `:judges` on the node via the `:sheet/judges` read model, else Gap-5 defaults for `:repl-researcher` nodes only), runs them in a background future, gated on the Living-Description opt-in flag. That processor **is** registered in this install (see registry census below) and the LD gate reads `true` — the link was armed and never used. |
| 2 | Judge → `:judge/score-emitted` event | **NEVER EXERCISED via the only path that writes it; the path actually used cannot write it** | The sole writers of `:judge/score-emitted` are the `:evaluation/record-judge-score` command handlers (`components/evaluation/src/ai/obney/orc/evaluation/core/commands.clj`), dispatched by `judge_runtime.clj`'s `run-judges-and-dispatch!` via `cp/process-command`. Every judge call this install ever made went through `eval/evaluate-single` (`components/evaluation/src/ai/obney/orc/evaluation/core/judges.clj`, `evaluate-single` at line 739): a pure function that invokes the judge executor on an in-memory trace map and returns the result map — it takes no `ctx`, touches no event store, dispatches no command. Smoke-b, smoke-d and the blocker-triage calibration (`consumer/src/router_loop/blocker_triage.clj`, which states this in its own comments) all used it. Store census: **0 `:judge/score-emitted` events**. |
| 3 | Score event → `:evaluation/judge-scores` read model | **PROVEN machinery, empty by consequence** | `defreadmodel :evaluation judge-scores` (`judge_runtime.clj`) folds `:judge/score-emitted` keyed by `[sheet-id tick-id node-id]`; query via `eval/get-judge-scores`. With 0 score events the projection is 0 entries — a downstream symptom of link 2, not an independent fault. (Part B, `results/judge-persistence.md`, proves this link end-to-end once link 1–2 are exercised.) |
| 4 | Execution → classification (`:ontology/task-classified`) → tree-class occurrences | **NEVER EXERCISED** | `:ontology/task-classified` is emitted only by the `:ontology/assign-task-class` command (`components/ontology/src/ai/obney/orc/ontology/core/commands.clj`), which records the result of `ontology/classify-task` — per its own docstring "the classification machinery (classify-task) is pure and runs upstream in the executor wedge", i.e. during `:repl-researcher` campaign execution. No sheet built in this install contains a `:repl-researcher` node, and no consumer code calls classify-task / assign-task-class. Store census: **0 `:ontology/task-classified`**, hence 0 `:ontology/tree-class-occurrence-recorded` (the `:ontology/on-researcher-campaign-terminal` processor in `components/ontology/src/ai/obney/orc/ontology/core/todo_processors.clj` additionally requires a `:repl-researcher` completion + a matching classification). |
| 5 | Execution → tree profiles / node experiences | **NEVER EXERCISED — by design there is no automatic feeder** | Tree profiles are fed **only** by the consumer-facing commands `:ontology/record-tree-strength` / `:ontology/record-tree-weakness` (`components/ontology/src/ai/obney/orc/ontology/core/commands.clj` lines 167/200). The namespace docstring of `todo_processors.clj` records why: "The former automatic tree-profile feeder (a processor on an evaluation event no producer ever emitted) was retired under grill decision D6 / RR-32; tree profiles are fed only by the consumer-facing record-tree-strength / record-tree-weakness commands." Node experiences likewise come only from the consumer command `:ontology/record-node-pattern` (same file, line 285 → `:ontology/node-pattern-learned`). No consumer code in this install issues any of the three commands. Store census: **0 `:ontology/tree-strength-recorded`**, **0 `:ontology/node-pattern-learned`**; both read models empty. |
| 6 | Evidence → Living Description claims | **NEVER EXERCISED** | Claims are written by exactly two automatic routes, both inside the consolidator (`components/ontology/src/ai/obney/orc/ontology/core/consolidator.clj`): the claim path (`consolidate-claims!` → `:ontology/record-claim-deltas`, `:tree-class` targets only) and the legacy body path (`consolidate-body!` → a granularity-matched
   description-updated event — `:ontology/node-type-description-updated`,
   `:ontology/node-instance-description-updated` or
   `:ontology/tree-description-updated` — for `:node-type` / `:node-instance` / `:tree-fingerprint`). Judge scores reach descriptions only as evidence inside the consolidator's reflection window (the Gap-3 join of `:judge/score-emitted` in `gather-recent-tree-class-events`, same file). With no consolidation ever running (link 7) and no scores in the store (link 2), claims are 0 — store census: **0 `:ontology/claim-deltas-recorded`**; all 114 description targets hold 0 claims. (The CV-2 worked-pattern writer in `todo_processors.clj` can also write claim deltas, but only for occurrences of classified RLM campaigns — link 4 never happened.) |
| 7 | Consolidation: `:ontology/consolidation-requested` → reflection → outcome | **WAS BROKEN — root cause below; FIXED AND PROVEN 2026-10-17 (see Resolution + `results/consolidation-wiring.md`)** | The request side works and is **PROVEN**: processor `:ontology/on-execution-completed-check-threshold` (`todo_processors.clj`) ticks per-target delta counters on each `:sheet/node-execution-completed` (read model `:ontology/consolidation-delta-counters`, `read_models.clj`) and issues `:ontology/request-consolidation` when a counter crosses its threshold (default 10, `read_models.clj` `default-consolidation-threshold`). It fired 6 times during the install smokes (GEPA's repeated leaf executions): 2026-10-17T18:00:51Z, 18:03:10Z, 18:05:18Z — each time a pair, `:node-type :leaf` + `:node-instance [smoke-a sheet, summarize node]`, `:on-demand? false` (bodies read from the store). The consuming side never ran — see root cause. Store census: 6 requested, **0** of every possible outcome type: no `:ontology/consolidation-skipped`, no `:ontology/description-consolidation-failed`, no `:ontology/claim-deltas-recorded`, no consolidation-written description updates (the 40 `:ontology/node-type-description-updated` events are the seed corpus's 10 node-types × 4 seed runs). |

## Root cause of the 6 unanswered consolidation requests (proven)

**The consolidator processor is never loaded in this consumer — so it is
never registered, never started, and nothing subscribes to
`:ontology/consolidation-requested`.**

- The processor is `:ontology/consolidate-on-request`, defined by
  `defprocessor` in `components/ontology/src/ai/obney/orc/ontology/core/consolidator.clj`
  (line 2287). Grain registers a `defprocessor` as a namespace-load side
  effect.
- **No production namespace requires `ai.obney.orc.ontology.core.consolidator`.**
  `ai.obney.orc.ontology.interface` requires `core.todo-processors`,
  `core.harvest`, `core.commands`, `core.read-models`, etc. — but not
  `core.consolidator` (verified by grep across `components/*/src`: the only
  requires of the consolidator namespace are in `components/ontology/test/**`
  and upstream's own dev/bench entry points,
  e.g. `development/bench/cc17_costs.clj`, `development/src/c_loop_1_live_verify.clj`,
  which load it explicitly). The consumer (`consumer/src/router_loop/system.clj`)
  doesn't require it either.
- **Live verification:** a foreground JVM eval under the consumer's exact
  requires listed `@tp/processor-registry*`: **34 processors registered —
  the same 34 the loop-state export counted — and
  `:ontology/consolidate-on-request` is not among them**
  (`CONSOLIDATOR-REGISTERED? false`). The threshold trigger
  (`:ontology/on-execution-completed-check-threshold`) IS in the list,
  which is exactly the observed asymmetry: requests are produced, never
  consumed.
- The PR-2 orphan sweep that would eventually convert stale requests into
  `:ontology/description-consolidation-failed` records
  (`sweep-orphaned-consolidation-requests!`, same namespace) is collateral:
  it only runs inside the same unloaded processor, so the requests are not
  even failed — they are simply orphaned, indefinitely.

This is a consumer wiring gap of the same species the install already hit
once (the Grain SQLite brick, PREFLIGHT §7.3: "consumers provide their own
event store, per ORC's design"). ORC's interface loads the ontology's
commands, read models and threshold processors for you; the consolidator
is a load-it-yourself part, and nobody loaded it.

### Secondary precondition waiting behind the root cause (named, not yet hit)

Even with the namespace loaded, the reflection's model resolves as
`(or (:ontology-consolidator-model context) (:model context))`
(`consolidator.clj`, in both `consolidate-claims!` and `consolidate-body!`).
The consumer ctx (`router_loop/system.clj`) carries **neither** key (it has
`:llm-provider` and `:ontology-reranker-model`), so the reflection workflow's
`:llm` node would be built with `:model nil` (unpinned). Whether the
executor then falls back to the litellm provider registration's default
model (the consumer registers `:model "gpt-5.6-luna"` on the provider) is
not determinable from the consolidator source alone. What distinguishes
the two outcomes: load the namespace in a scratch consumer run, fire one
`:ontology/request-consolidation` for the `:node-type :leaf` target, and
read which durable outcome lands — a description-updated event
(model resolution worked) or an `:ontology/description-consolidation-failed`
with a provider/model error (it didn't). Not run here: wiring consolidation
is beyond the authorized step (Ben authorized judge-score persistence; this
diagnosis is the map for the rest).

## Resolution (2026-10-17 — Ben: "wire consolidation next")

Run and recorded in `results/consolidation-wiring.md`. The consumer now
requires `ai.obney.orc.ontology.core.consolidator` (registry 34 → 35,
`:ontology/consolidate-on-request` present and started) and pins
`:ontology-consolidator-model "gpt-5.6-luna"` in the ctx — making the
distinguishing question above moot here: the reflection's execution event
records `:model "gpt-5.6-luna"`, so the specific key is honored. The
experiment's verdicts: the loaded processor does **not** replay historical
requests at startup (12 quiet poll rounds); one on-demand request for
`:node-type :leaf` first swept leaf's 3 orphans to
`:ontology/description-consolidation-failed` (`:caller-interrupted`,
attempts 0) and then **completed** — `:ontology/node-type-description-updated`
landed ~32s after the request, creating leaf's description at version 1
(5 capabilities / 3 strengths / 2 weaknesses, consolidated from 35 events;
claims stay 0 by design — node-type targets take the body path, claim
deltas are `:tree-class`-only). The 3 `:node-instance` orphans remain
pending: the sweep is scoped to the answered request's target, and no
request for that target has been dispatched since the wiring. Unanswered:
6 → 3. Spend: 1 reflection execution, 15,684 tokens.

Two non-causes, checked and cleared: the hourly budget gate
(`consolidate!` skips only at ≥ budget; default 100/hour per target-type,
`read_models.clj` `default-consolidation-budget` — nowhere near reached,
and a skip would have left a `:ontology/consolidation-skipped` event
anyway); and the CC-4 evidence guard (`evidence_guard.clj`) — it filters
claim-path deltas, it cannot silence a `:node-type`/`:node-instance`
request, and its exclusions are themselves events (0 present).

## So why has the loop learned nothing — the whole answer in one paragraph

Three independent breaks, in chain order: (1) judges were only ever run
**offline** (`evaluate-single`), a path that by construction writes
nothing, and no executed node ever had judges **attached**, so the store
contains zero judge evidence; (2) the **learning consumers of execution
evidence that do exist are command-driven, not automatic** (tree
profiles, node patterns, task classification) and no consumer code issues
those commands — plain `:llm` leaf executions teach the ontology nothing
on their own; (3) the one fully automatic learning step, **consolidation,
has its processor in a namespace nothing loads**, so its trigger fires
into a void. Underneath all three: this install's workloads were
short-lived smoke processes running a handful of executions — even a
fully wired loop needs repeated executions of the same targets (default
threshold: 10 completions per target) before its first consolidation is
even requested.

## The minimal set of things the loop needs to learn anything

1. **Judge scores in the store** — judges attached to executed nodes so
   `:judge/score-emitted` events land in `:evaluation/judge-scores`.
   ✅ **Done** (Part B, `results/judge-persistence.md`): LD gate already
   read `true`; attached `:grounding` + `:reasoning` on the summarizer
   leaf produced 4 persisted scores + 2 composites over 2 executions.
2. **The consolidator loaded** — one consumer-side require of
   `ai.obney.orc.ontology.core.consolidator` so
   `:ontology/consolidate-on-request` registers and starts; plus a
   **verified model pin** for its reflection (add
   `:ontology-consolidator-model` — or `:model` — to the consumer ctx,
   per the secondary precondition above).
   ✅ **Done** (2026-10-17, `results/consolidation-wiring.md`): require +
   pin in place and verified in-run; first consolidation completed
   end-to-end (leaf description created at v1). Caveat carried forward:
   the processor does not replay history at startup — orphaned requests
   are only swept when a same-target request is later answered.
3. **Volume per target** — ≥10 completions of the same target (default
   threshold) inside processes that live long enough for the async
   reflection to finish, or an explicit on-demand
   `:ontology/request-consolidation` command.
4. **For tree-class learning specifically** — workloads that produce
   classifications: `:repl-researcher` campaigns (the executor wedge
   runs classify-task + assign-task-class), or a consumer explicitly
   classifying executed tasks. Without `:ontology/task-classified`
   events there are no occurrences, no tree-class counters, no claim-path
   consolidations, no harvest.
5. **For tree profiles / node experiences** — a consumer decision to
   issue `:ontology/record-tree-strength` / `record-tree-weakness` /
   `:ontology/record-node-pattern` from execution outcomes. No automatic
   feeder exists anymore (retired upstream, D6/RR-32); if this install
   wants them populated, that feeder logic is consumer code to write,
   deliberately.

---

## Resolution note — 2026-10-18T01:37Z (appended, history preserved)

The 3 `:node-instance` orphans described above as pending are now
**answered**: one on-demand consolidation request for that target swept
them to `:caller-interrupted` failure records and completed, creating
the instance's description v1 (from 33 executions). No consolidation
request in the store remains unanswered (8 requested — 2 completed,
6 failed, 0 unanswered). Full record: `consolidation-node-instance.md`.
