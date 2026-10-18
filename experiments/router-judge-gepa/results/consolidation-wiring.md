# Consolidation wiring — the loop's learning step now answers

- **Date:** 2026-10-17 (run in the 2026-10-18T00:4xZ process window, VM clock UTC)
- **Directive:** Ben — "Wire consolidation next" (following `results/learning-diagnosis.md`,
  which proved the root cause and specified this experiment).
- **Verdict: COMPLETED.** The chain now answers end-to-end: a consolidation
  request was consumed by the (newly loaded) processor, its reflection ran on
  the pinned model, and a durable description update landed as the request's
  outcome. The 3 same-target orphans were swept to durable failure records in
  the same handler invocation. The 3 other-target orphans remain pending —
  by design, explained below.

## The wiring (consumer-side only; zero ORC source changes)

Both changes in `consumer/src/router_loop/system.clj`:

1. **Load the consolidator namespace.** Added the bare require
   `[ai.obney.orc.ontology.core.consolidator]` to the consumer's side-effect
   registration block. Grain registers a `defprocessor` as a namespace-load
   side effect; neither ORC's `ontology.interface` nor this consumer loaded
   it, so `:ontology/consolidate-on-request` was never registered
   (root cause in the diagnosis). Upstream's own consumers load it exactly
   this way (`development/bench/cc17_costs.clj:25`,
   `development/src/c_loop_1_live_verify.clj:26` — the latter with a comment
   describing this same failure mode).
2. **Pin the reflection model.** Added `:ontology-consolidator-model
   "gpt-5.6-luna"` to the ctx. `consolidator.clj` resolves the reflection
   `:llm` node's model as `(or (:ontology-consolidator-model ctx)
   (:model ctx))` in both `consolidate-body!` (~line 2070) and
   `consolidate-claims!` (~line 1928); the ctx previously carried **neither**
   key, so the reflection would have been built with `:model nil`.

The experiment driver is `consumer/src/router_loop/consolidation_experiment.clj`
(phases OBSERVE → FIRE → SETTLE, bounded polls 5s × 12 rounds per phase,
extend-once-on-activity — the extension was not needed).

## Wiring verification (in-run, foreground)

- **Processor registry: 34 → 35 registered**; the delta is exactly
  `:ontology/consolidate-on-request`. Started: 34 (the ColBERT reindex
  processor remains deferred at boot, as before). The threshold trigger that
  produced the orphan requests was already among the 34 — the asymmetry the
  diagnosis described (requests produced, never consumed) is closed.
- **Model pin read back from the started system's ctx:**
  `{:ontology-consolidator-model "gpt-5.6-luna", :model nil}`.
- **The pin is what the reflection actually used:** the reflection's
  `:sheet/node-execution-completed` event records `:model "gpt-5.6-luna"`
  (sheet `e9317a75-42e4-587d-9759-c539d235e755`, node
  `5207093c-10bc-523c-8666-33a43282fea1`). The diagnosis's secondary
  precondition — "would nil-model resolution fall back to the provider
  default?" — is moot in this install: the specific key is set and honored.

## The distinguishing experiment

**Pre-run census** (SQLite file copy, read-only): 2,185 events —
`:ontology/consolidation-requested` 6, every outcome type 0,
`:ontology/node-type-description-updated` 40.

**Phase OBSERVE — does the newly loaded processor replay history at
startup? NO.** 12 poll rounds (~60s) after system start: zero change in
every counter (requested 6, unanswered 6, failed/skipped/claim-deltas 0).
Todo processors here consume the event pubsub, not a store replay — the 6
historical requests are invisible to the processor until something makes it
look at their target. Loading the namespace alone answers nothing; it has
to be triggered per target.

**Phase FIRE — one on-demand request.** Dispatched
`:ontology/request-consolidation` (`:on-demand? true`) for
`:node-type :leaf` at **2026-10-18T00:47:58.58Z** → new request
`01a14c7a-cc7a-7066-9a8b-2ccd506e0ad1`. (Leaf had the richest history:
3 orphans, delta counter 5 after Part B, and it is the node type of every
judged execution in this install.)

**The sweep fired first, exactly as the source says.** The
`consolidate-on-request` handler (consolidator.clj:2287) runs the PR-2
orphan sweep scoped to the target being answered, excluding the in-flight
request, *before* consolidating. Leaf's 3 historical requests — 6.5 hours
old, far past the 30-minute grace — were converted at
00:47:58.68Z / .72Z / .74Z into `:ontology/description-consolidation-failed`
records, **reason `:caller-interrupted`, attempts 0** (a vanished request
evidences no consumed provider attempt). Requested 6→7, failed 0→3,
unanswered 6→4 within a second of the dispatch.

**Phase SETTLE — the new request COMPLETED.** The reflection executed as
an ORC sheet (built per consolidation by `consolidate-body!`), one `:llm`
node execution on gpt-5.6-luna, completed **00:48:30.40Z**; the outcome
landed **00:48:30.87Z**: `:ontology/node-type-description-updated`
(40 → 41), recorded against the new request-id. The request ledger
(`orm/get-consolidation-outcome`) reports the new request's status as
**`:description-updated`**. Settle finished at poll round 7 (~35s), no
extension needed. Request → outcome wall time: ~32s.

## What the consolidation produced

Target `:node-type :leaf` had **no description at all before** (verified
in-run: `get-description` → absent, history 0 — the seed corpus's 10
node-type descriptions cover DSL kinds like `:sequence`/`:llm`, not
`:leaf`). The consolidation created it from the evidence window:

- **Version 1** (history count 0 → 1), `:consolidated-from-event-count 35`
  (the node's execution history in the store).
- Body: **5 capabilities, 3 strengths, 2 weaknesses**, summary beginning
  "The leaf is a highly reliable language-processing component for
  transforming input text into concise, source-grounded summaries while
  respecting explicit formatting constraints and writing the result …"
  (truncated at 200 chars in the run log) — i.e. the reflection generalized
  from today's judged summarizer executions, which dominate leaf's window.
- **Claims: 0 — by design, not a miss.** `:node-type` targets consolidate
  through the legacy **body path**; claim deltas
  (`:ontology/claim-deltas-recorded`, still 0) exist only on the **claim
  path, which is `:tree-class`-only** (CC-5, consolidator.clj:1547 —
  node-type target-ids are keywords and cannot key the claim model).
  For this target type, a whole-body description update *is* the learning
  artifact.

## Disposition of every request

| Request | Target | Outcome |
|---|---|---|
| `01a14b06-13a8…` (2026-10-17T18:00:51Z) | `:node-type :leaf` | **answered — failed** `:caller-interrupted`, attempts 0 (swept 2026-10-18T00:47:58.68Z) |
| `01a14b08-3253…` (18:03:10Z) | `:node-type :leaf` | **answered — failed** `:caller-interrupted` (swept 00:47:58.72Z) |
| `01a14b0a-265e…` (18:05:18Z) | `:node-type :leaf` | **answered — failed** `:caller-interrupted` (swept 00:47:58.74Z) |
| `01a14b06-140b…` (18:00:51Z) | `:node-instance` [smoke-a sheet, summarize] | **still pending** — no outcome |
| `01a14b08-3292…` (18:03:10Z) | `:node-instance` [smoke-a sheet, summarize] | **still pending** — no outcome |
| `01a14b0a-26e7…` (18:05:18Z) | `:node-instance` [smoke-a sheet, summarize] | **still pending** — no outcome |
| `01a14c7a-cc7a…` (2026-10-18T00:47:58Z, this run) | `:node-type :leaf` | **COMPLETED** — `:description-updated` at 00:48:30.87Z |

The 3 node-instance orphans remain pending because the sweep is scoped to
the target of the request being answered, and no request for that
node-instance target has been dispatched since the wiring. They are now
*answerable* the same way (one on-demand request for that target would
sweep all 3 and consolidate the instance) — not fired here: Ben authorized
wiring consolidation + the leaf experiment, and one answered chain proves
the mechanism for both granularities (the handler and body path are shared;
`:node-instance` differs only in the description command name).

**Unanswered requests: 6 → 3.**

## Spend

- **LLM calls: 1** reflection node execution (cap was 20). The judged-sheet
  executions, judges and GEPA were not re-run.
- **Tokens (from the execution event's `:usage`): 15,684** — 14,520 prompt
  + 1,164 completion, model gpt-5.6-luna via the proxy bridge.
- The evidence window is the cost driver: 35 historical executions rendered
  into the reflection prompt.

## Store delta (file-copy census, before → after)

2,185 → **2,261 events (+76)**, itemized: `:grain/tx` +32,
`:sheet/execution-value-written` +13, `:sheet/key-declared` +13 (the
reflection sheet's build), `:ontology/consolidation-requested` +1,
`:ontology/description-consolidation-failed` +3,
`:ontology/node-type-description-updated` +1,
`:sheet/node-execution-completed` +1 and the remaining +1s are the
reflection sheet's lifecycle events. Full per-type table in the run log
and the refreshed `results/loop_state.json`.

## Notes / surprises

- **A first failed launch attempt** (paren imbalance in the new experiment
  namespace) died at load time before the system started — the store was
  untouched; the run above is a single clean execution of the final code.
- The processor does **not** replay on startup (OBSERVE). Consequence for
  operations: after any future restart, orphaned requests stay orphaned
  until a same-target request (threshold or on-demand) triggers the sweep.
  The unscoped startup sweep the PR-2 docstring mentions has no caller in
  this consumer — wiring one is a deliberate choice, not a default.
- Consolidation for `:node-type`/`:node-instance` targets can never produce
  claim deltas (CC-5 representability blocker). If claim-level learning is
  ever wanted, it runs through `:tree-class` targets — which need
  classified `:repl-researcher` campaigns (diagnosis link 4), still never
  exercised in this install.

---

## Resolution note — 2026-10-18T01:37Z (appended, history preserved)

The 3 `:node-instance` orphans left pending above are now **answered**
(Ben's follow-on directive: "Answer the 3 node-instance orphans too").
One on-demand request for that target (`01a14ca7-e6bc…`, 2026-10-18T01:37:14Z)
swept all 3 to `:ontology/description-consolidation-failed`
(`:caller-interrupted`, attempts 0) and then **completed**, creating the
instance's first description (v1, from 33 executions). Final store
ledger: **8 requested — 2 completed, 6 failed, 0 unanswered.** Full
record: `consolidation-node-instance.md`.
