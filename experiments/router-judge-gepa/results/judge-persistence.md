# Judge-score persistence — Part B proof (Ben's directive: "make judges score persist")

- **Date:** 2026-10-17 (run at 2026-10-18T00:11Z, VM clock UTC)
- **Directive:** wire judge scores so they are emitted as events through
  ORC's own judge machinery and land in the `:evaluation/judge-scores`
  read model, proven by read-back. Diagnosis: `learning-diagnosis.md`.
- **Verdict: DONE and verified three ways** — in-process read model,
  raw event census on SQLite file copies, and a cold read-back from a
  fresh process.

## What was built (consumer-side only; no ORC source changes)

`consumer/src/router_loop/judged_sheet.clj` (new namespace
`router-loop.judged-sheet`): the smoke-a summarizer sheet rebuilt as
`summarizer-judged` with judges **attached in the real way**
(`docs/GETTING-STARTED.md` Phase 2 pattern):

- `(orc/judges {:summary-grounding {:type :grounding :weight 0.5
  :provider :openai :model "gpt-5.6-luna"}
  :summary-reasoning {:type :reasoning :weight 0.5
  :provider :openai :model "gpt-5.6-luna"}})` — provider keyword
  `:openai` is the consumer's Mac proxy registration (PREFLIGHT §7.5).
- The `summarize` `:llm` leaf carries `:judges ["summary-grounding"
  "summary-reasoning"]`; same instruction and same synthetic
  city-council article input as smoke-a. No personal data anywhere.

On each `:sheet/node-execution-completed`, the already-registered
`:evaluation/on-node-execution-completed` processor
(`evaluation/core/judge_runtime.clj`) resolved the node's judges, ran
them in its background future, and dispatched
`:evaluation/record-judge-score` commands — the sole writers of
`:judge/score-emitted` (`evaluation/core/commands.clj`). Nothing was
hand-written: every score event below arrived through that machinery.

Precondition check inside the run: `ontology/get-living-description-enabled?`
read back **true** on the started system (the judge runtime's gate).

## Run record

- Sheet: `18e903d4-fd1f-5f04-bb35-945287308ec6` ("summarizer-judged");
  judged node `30de5ba4-ed98-536d-8f1f-319701f9681c` ("summarize", `:leaf`).
- Executions: **2**, both `:status :success`, each producing a faithful
  2-sentence summary of the article.
- Async settle: after each execution, the read model was polled
  (5 s interval, max 12 rounds); both judges' entries for execution 1
  were present at the first successful check, and 4/4 after execution 2.
  No timeout was hit.

## Proof

**Read model (`:evaluation/judge-scores`) — 4 entries, 2 per execution:**

| tick | judge | score | dimensions | emitted-at |
|------|-------|-------|------------|------------|
| `881a28da…` (exec 1) | summary-grounding | 1.0 | [{Source Grounding, 1.0, w 1.0}] | 2026-10-18T00:11:30.037Z |
| `881a28da…` (exec 1) | summary-reasoning | 1.0 | [{Reasoning Quality, 1.0, w 1.0}] | 2026-10-18T00:11:30.070Z |
| `18a1f42a…` (exec 2) | summary-grounding | 1.0 | [{Source Grounding, 1.0, w 1.0}] | 2026-10-18T00:11:45.191Z |
| `18a1f42a…` (exec 2) | summary-reasoning | 1.0 | [{Reasoning Quality, 1.0, w 1.0}] | 2026-10-18T00:11:45.227Z |

Feedback excerpts (synthetic content): grounding, exec 1 — "The summary
is fully grounded in the source and accurately preserves the key facts,
vote count, funding items, concerns, and construction timeline.";
reasoning, exec 1 — "The summary is logically sound, faithful to the
source, and exactly two sentences. No substantive revision is needed."

**Event census (SQLite file copies taken before the run and after the
process exited; per-type delta over the whole store):**

- Total events 2,109 → 2,185 (+76), and the delta is exactly this run's
  activity: sheet build (+1 `:sheet/sheet-created`, +2
  `:sheet/judge-declared`, +1 `:sheet/node-judges-set`, node/key/instruction
  declarations), 2 executions (ticks, node started/completed, value
  writes), +36 `:grain/tx` wrappers — and the two new types:
  - **`:judge/score-emitted`: 0 → 4**
  - **`:judge/composite-score-computed`: 0 → 2** (Gap-8 composite, one
    per execution, fired because ≥2 judges scored)
- **Zero ontology events of any kind were appended** — downstream
  non-movement confirmed at the file level, not just by projection.

**Cold read-back:** a separate fresh process started the system and
projected `:evaluation/judge-scores` from the event store alone:
4 entries returned, identical tuples; the public query
`eval/get-judge-scores` for the exec-1 (sheet, node, tick) tuple
returned its 2 entries. The scores are durable event-store facts, not
in-process residue.

**LLM accounting:** 6 calls total (2 node executions + 4 judge calls),
all gpt-5.6-luna via the Mac proxy service — inside the 30-call cap. Judge-call usage
from the score events' `:model-provenance`: 2,178 + 2,143 + 2,140 +
2,179 = **8,640 tokens** (provider `:openai` = the Mac proxy service endpoint,
model `gpt-5.6-luna` on every event).

Scores are all 1.0: the summaries are in fact faithful to the article,
so full marks from both judges is the honest outcome for this input —
the point of the run was the persistence path, not score discrimination
(that was the blocker-triage calibration's job, and its scores came from
the offline path by design).

## Downstream observation (Part B.4 — observed, nothing forced)

In-process projections before → after the 2 judged executions:

| Read model / counter | Before | After | Moved? |
|---|---|---|---|
| `:evaluation/judge-scores` entries | 0 | 4 | ✅ (the authorized change) |
| `:judge/score-emitted` events | 0 | 4 | ✅ |
| `:judge/composite-score-computed` events | 0 | 2 | ✅ |
| `:ontology/consolidation-delta-counters` — `:node-type :leaf` delta | 3 | 5 | ✅ ticked +1 per execution (threshold trigger is live) |
| `:ontology/consolidation-requested` events | 6 | 6 | No — counter at 5, below the default threshold of 10; no new request, as expected |
| Tree profiles (`:ontology/tree-profiles`) | 0 | 0 | No |
| Node experiences (`:ontology/node-experiences`) | 0 | 0 | No |
| `:ontology/claim-deltas-recorded` events | 0 | 0 | No |
| Consolidation skipped / failed events | 0 / 0 | 0 / 0 | No |
| `:ontology/task-classified` events | 0 | 0 | No |

Exactly the expected shape: scores now persist and the consolidation
*trigger* counts them toward its threshold, but no learning step beyond
score persistence occurred — consolidation itself remains unwired (its
processor's namespace is never loaded; see `learning-diagnosis.md`),
and profiles/experiences/classification remain consumer-command-driven
with no issuer. No classification, record-tree-*, record-node-pattern
or consolidation commands were issued by this work.
