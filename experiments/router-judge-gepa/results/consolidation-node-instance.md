# Consolidation — the 3 node-instance orphans answered

- **Date:** 2026-10-17 (run in the 2026-10-18T01:37Z process window, VM clock UTC)
- **Directive:** Ben — "Answer the 3 node-instance orphans too" (follow-on to
  `consolidation-wiring.md`, which wired the consolidator and completed the
  leaf experiment, leaving these 3 by scope).
- **Verdict: COMPLETED.** One on-demand request for the smoke-a summarizer
  node-instance target swept its 3 historical orphans to durable failure
  records and then consolidated the instance: a first-ever description
  (version 1) created from 33 executions. **Unanswered consolidation
  requests in the store: 3 → 0. The ledger is fully answered.**

## The target

All 3 pending requests share one target (read from their
`:ontology/consolidation-requested` events, 2026-10-17T18:00–18:05Z):

- target-type: `:node-instance`
- target-id: `[dd9e3bce-16e4-56ed-bdcd-e32c08852a4b, 90dcc7a5-cd9a-5bc7-b7fa-755090c1d75f]`
  — [smoke-a sheet, its `summarize` node]
- **Before-state (verified in-run): the target had NO description at all**
  (`get-description` → absent, history 0). Neither the seed corpus nor the
  judged runs had ever written one for this instance.

Driver: `consumer/src/router_loop/consolidation_node_instance.clj`
(same OBSERVE/FIRE/SETTLE discipline as the leaf run; wiring re-verified
in-run: **35 processors registered** incl. `:ontology/consolidate-on-request`,
34 started; ctx pin read back `{:ontology-consolidator-model "gpt-5.6-luna",
:model nil}`; LD gate true).

## The run

**Pre-run census** (SQLite file copy, read-only): **2,261 events / 44 types** —
`:ontology/consolidation-requested` 7, `:ontology/description-consolidation-failed` 3,
`:ontology/node-instance-description-updated` 0, unanswered 3.

**FIRE** — dispatched `:ontology/request-consolidation` (`:on-demand? true`)
for the node-instance target at **2026-10-18T01:37:14.43Z** → new request
`01a14ca7-e6bc-7079-aa70-18c9ccd37a8c`.

**The sweep fired first, same handler shape as the leaf run.** The 3
orphans — ~7.6 hours old, far past the 30-minute grace — were converted
within ~0.25s of the dispatch into
`:ontology/description-consolidation-failed` records, **reason
`:caller-interrupted`, attempts 0** (01:37:14.57Z / .63Z / .68Z).
Requested 7→8, failed 3→6, unanswered 3→1 (only the new request).

**SETTLE — the new request COMPLETED.** The reflection executed as an ORC
sheet, one `:llm` node execution on gpt-5.6-luna (completed
01:37:43.40Z); the outcome landed **2026-10-18T01:37:44.09Z**:
`:ontology/node-instance-description-updated` (0 → 1), recorded against
the new request-id. The request ledger (`orm/get-consolidation-outcome`)
reports the new request's status as **`:description-updated`**. Settle
finished at poll round 6 (~30s), no extension needed. Request → outcome
wall time: ~30s.

## What the consolidation produced

- **Version 1 created** (history count 0 → 1),
  `:consolidated-from-event-count 33` (this instance's execution history
  in the store).
- Body: **4 capabilities, 3 strengths, 2 weaknesses**, summary beginning
  "This node instance reliably converts short text inputs into concise
  summaries written to the summary field, with consistent output sizes
  and execution times generally in the low-single-digit-second ra…"
  (200-char truncation in the run log) — the reflection generalized from
  this instance's own summarizer executions.
- **Claims: 0 — by design, as with leaf.** `:node-instance` targets take
  the legacy body path; claim deltas are `:tree-class`-only (CC-5). The
  whole-body description update is the learning artifact for this
  granularity.

## Disposition of every consolidation request in the store (final)

| Request | Target | Outcome |
|---|---|---|
| `01a14b06-13a8…` (2026-10-17T18:00:51Z) | `:node-type :leaf` | **answered — failed** `:caller-interrupted`, attempts 0 (swept 2026-10-18T00:47:58.69Z) |
| `01a14b08-3253…` (18:03:10Z) | `:node-type :leaf` | **answered — failed** `:caller-interrupted` (swept 00:47:58.72Z) |
| `01a14b0a-265e…` (18:05:18Z) | `:node-type :leaf` | **answered — failed** `:caller-interrupted` (swept 00:47:58.74Z) |
| `01a14c7a-cc7a…` (2026-10-18T00:47:58Z, wiring run) | `:node-type :leaf` | **COMPLETED** — `:description-updated` at 00:48:30.87Z (leaf description v1) |
| `01a14b06-140b…` (2026-10-17T18:00:51Z) | `:node-instance` [smoke-a sheet, summarize] | **answered — failed** `:caller-interrupted`, attempts 0 (swept 2026-10-18T01:37:14.57Z, this run) |
| `01a14b08-3292…` (18:03:10Z) | `:node-instance` [smoke-a sheet, summarize] | **answered — failed** `:caller-interrupted` (swept 01:37:14.63Z, this run) |
| `01a14b0a-26e7…` (18:05:18Z) | `:node-instance` [smoke-a sheet, summarize] | **answered — failed** `:caller-interrupted` (swept 01:37:14.68Z, this run) |
| `01a14ca7-e6bc…` (2026-10-18T01:37:14Z, this run) | `:node-instance` [smoke-a sheet, summarize] | **COMPLETED** — `:description-updated` at 01:37:44.09Z (instance description v1) |

**Final ledger: 8 requested — 2 completed, 6 failed (`:caller-interrupted`), 0 unanswered.**

## Spend

- **LLM calls: 1** reflection node execution (cap was 20).
- **Tokens (from the execution event's `:usage`): 13,272** — 12,067 prompt
  + 1,205 completion, model gpt-5.6-luna via the proxy bridge. The
  evidence window (33 executions) is the cost driver, as with leaf (35).

## Store delta (file-copy census, before → after)

2,261 → **2,296 events (+35)**, itemized: `:grain/tx` +11,
`:sheet/execution-value-written` +13,
`:ontology/consolidation-requested` +1,
`:ontology/description-consolidation-failed` +3,
`:ontology/node-instance-description-updated` +1, and +1 each of
`:sheet/execution-traced`, `:sheet/node-execution-completed`,
`:sheet/node-execution-started`, `:sheet/rlm-tree-node-completed`,
`:sheet/tree-tick-completed`, `:sheet/tree-tick-started` (the reflection
sheet's single execution). Full per-type table in the refreshed
`results/loop_state.json`.
