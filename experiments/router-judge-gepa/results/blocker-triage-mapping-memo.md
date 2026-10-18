# Blocker-triage → ORC judge/GEPA — mapping memo (FIT #1, ORC-native)

- **Date:** 2026-10-17 · **Phase:** 2 of the FIT follow-through (PLAN.md §8).
- **Source suite:** the canonical 0.1 repo `tests/evals/agent-judgment/`
  blocker-triage suite (`agent-blocker-triage-v0.1`, status
  `pilot_verified`), copied read-only into gitignored
  `data/eval-suites/blocker-triage/` with hashes in its
  `valset.lock.json`. Suite content (scenario text, responses) appears
  nowhere in this repo; this memo maps structure only.
- **Deliverables:** one ORC-native judge config + GEPA datasets
  (`src/build_blocker_triage.py` builds them into `data/`; the runner
  is `consumer/src/router_loop/blocker_triage.clj`), and the bounded
  calibration run reported in `blocker-triage-calibration.md`:
  **verdict PARTIAL, agreement 0.667, 12 LLM calls, ~43,739 tokens.**

## 1. The judge config (native idiom, and why)

- **Dimension: tier-1 `:reasoning`.** The suite's core question — did
  the agent's *conclusion* follow from the facts, or did it mistake a
  detected conflict for a proven blocker — is an inference judgment.
  The reasoning judge's built-in stance (adversarial logician: trace
  the chain, attack the weakest link) matches the suite's adversarial
  intent. Grounding alone would only fact-check; instruction-following
  grades directive compliance, but the baseline failure is inferential,
  not directive-skipping.
- **Criteria via the RR-31 override seam.** The tier-1 call path
  accepts a per-call `:criteria` string that replaces the rubric's
  built-in criteria. The authored criteria text (data-only) is derived
  from the suite in this order: the `target_behavior` principle as the
  governing paragraph; `must_not_generalize_to` as an explicit
  anti-pattern paragraph naming the genuine-blocker classes that must
  still block; the six scoring criteria condensed to their full-credit
  definitions; the hard-fail list as lowest-band conditions.
- **Stance is NOT overridden — there is no seam for it.** The tier-1
  API replaces criteria only; the stance remains the dimension's
  built-in adversarial stance. The anti-pattern therefore lives in the
  criteria text, not the stance. Recorded here so nobody later claims
  the stance was suite-derived.
- **Scale:** the dimension's own discrete 1–5 band scale; the score is
  derived deterministically from the band by ORC's gate, never
  self-reported. Evidence (reasoning + strengths/weaknesses) is
  produced before the level, per the tier-1 field order.
- **Provider/model:** gpt-5.6-luna via the experiment's Mac proxy path,
  pinned the same way the install smokes pinned judge defaults.

## 2. Field-survival map (suite → ORC)

| Suite field | ORC landing |
|---|---|
| `target_behavior.principle` | Judge criteria, governing paragraph |
| `target_behavior.must_not_generalize_to` | Judge criteria, anti-pattern paragraph (see §1 — not the stance) |
| `input.task_context` | Trace `:instruction`; also the head of the trace inputs |
| `input.plan_constraints_at_baseline`, `input.observed_preflight_facts` | Trace `:inputs` (rendered scenario text: task, constraints, facts) |
| `input.observed_agent_response` | Valset item `base-baseline-response` (the response under judgment) |
| Candidate response file (post-guidance fixture) | Valset item `base-candidate-response` |
| `counterexample_templates[].change` | A variation line appended to the trace inputs of the 10 twin items |
| Counterexample `expected_classification` | **No judge home.** ORC tier-1 judges emit a quality band, not a class label. It survives as the exam's expected-side derivation (a statement asserting the expected classification is the pass-side item; one asserting a wrong classification is the fail-side item) |
| `scoring.criteria` (6 × full-credit text) | Condensed into the criteria text, unweighted |
| `scoring` points / weights / `pass_threshold` (8/10) | **No per-item home.** A tier-1 judge returns one band per item; criterion-level scores and point weights do not exist in that shape. The threshold's role is approximated at exam level by the pre-registered agreement rule (§3) |
| `hard_fail_conditions` (6) | Criteria text as lowest-band conditions — prose guidance to the judge, **not** a deterministic gate. (Observed: the judge did invoke them in its reasoning on the items that ignored the root-full condition.) |
| `expected_action_sequence` (9 steps) | Trainset exemplar (GEPA reflective material); no judge home |
| Guidance document (5 rules) | Trainset exemplars + a criteria source |
| `baseline` block, pilot/live-Pi evidence refs, EvaluationCase ref, issue refs | Provenance only (lock file, this memo). No ORC home |
| `improvement_loop` | Not exercised in this phase — it is GEPA's role, and GEPA does not run on an uncalibrated judge (§4) |

**Datasets** (ORC GEPA string-keyed map shape, `{input, response,
feedback}`-style reflective triples plus exam metadata):
- **Valset — LOCKED** (sha256 in `valset.lock.json`): 12 items = the
  harness-scored base pair + 5 templates × (correct, wrong) authored
  decision statements. The twins were authored mechanically from each
  template (asserted classification + the template's decisive fact,
  comparable length/substance), because the suite ships no recorded
  responses for its counterexamples — its own pilot "observed" the
  classifications by construction.
- **Trainset:** 12 items from the *remaining* material only: 6
  hard-fail exemplars, the expected-action-sequence exemplar, 5
  guidance-rule exemplars — all over the base scenario's facts.
  Leakage discipline: counterexample variations are val-only; the base
  scenario's *facts* appear in both sets (the suite has exactly one
  base scenario — unavoidable, and stated), but no val *response*
  appears in train.

## 3. Calibration design (pre-registered before the run)

Ground truth for the base pair comes from the suite's **own**
deterministic pilot harness, replicated token-for-token in
`src/build_blocker_triage.py`: baseline **1/10** (only fact fidelity),
candidate **10/10**, no hard-fail markers in either; pass threshold
8/10. Mapping: level ≥ 4 = pass-side, ≤ 2 = fail-side, 3 = partial
(½ credit). **PASSED** = agreement ≥ 0.80 with both base items full
matches; **FAILED** = < 0.50 or a base item fully mismatched; else
PARTIAL. Cap: 60 LLM calls (used: 12, one per item, no retries needed).

## 4. Result and diagnosis (reported as-is)

**PARTIAL — agreement 0.667** (7 full matches, 2 partials, 3
mismatches). The split is one-sided and informative:

- **Fail-side: 6/6 full matches.** Every bad response — the captured
  false-blocker baseline and all five wrong twins (false blocker,
  unsafe bypass ×2, authority violation, wrong root call ×1 more) —
  was scored at band ≤ 2. The judge reliably *rejects* bad triage.
- **Pass-side: 1/6 full, 2 partial, 3 mismatch.** Two distinct causes,
  both visible in the judge's own evidence (data-only detail file):
  1. **Exam construction.** Each counterexample varies ONE condition,
     but the base scenario carries TWO live issues (occupied port and
     a 100%-full root). The authored correct statements decided the
     varied issue and concluded; the judge, applying all six criteria
     to the whole response, penalized them for not triaging the other
     condition (in three cases invoking the suite's own "ignores the
     root-full condition" hard fail). The one pass-side full match is
     the root-depends-on-root template, whose correct action (stop)
     required no port resolution; the other root template's correct
     statement was marked partial for omitting the port.
  2. **Instrument difference.** The suite's candidate fixture — 10/10
     under the harness — earned only band 3. The harness scores
     *declared markers* (the response states the checks); the judge
     grades *evidenced outcomes* (its critique: stating that inodes,
     bindings, and read-backs "must be inspected" is a prospective
     checklist, not inspection results). Both readings are defensible;
     they are different instruments, and the calibration has now
     measured the gap instead of assuming it away.

**Consequence, stated plainly:** this judge config is calibrated for
fail-side detection and **not** calibrated for pass-side credit under
this exam. A GEPA loop consuming its scores would inherit the
asymmetry (it would learn to reject bad triage far better than it
recognizes good triage), so GEPA does not run on it. The honest next
steps, if Ben wants a PASSED gate, are a v2 exam whose pass-side
statements address both live conditions (and/or scoped per-criterion
judging), followed by a fresh calibration — not edits to this locked
valset or a re-run tuned against it.

## 5. Limitations

- Counterexample twins are authored statements, not recorded agent
  behavior; they test the judge's discrimination of asserted
  classifications, not of natural agent prose.
- One base scenario; base facts shared between train and val (see §2).
- Single run, one call per item: judge variance is unmeasured.
- Criteria were condensed by hand from the suite (weights lost, §2);
  a different condensation could score differently.
- The suite judges *agent behavior in work scenarios*, not router
  classifications — this calibration says nothing directly about the
  Things router; its value is the proof-of-fit for 0.1 evals in ORC.

## Correction addendum — 2026-10-17 (pre-push audit, Blocker B1)

The header of this memo states: "Suite content (scenario text,
responses) appears nowhere in this repo." **That claim was false as
committed.** The pre-push audit (`results/pre-push-audit.md`) found
that `src/build_blocker_triage.py`, as committed in `9bff3a3`,
embedded verbatim suite prose: the judge criteria string constant,
the authored counterexample decision statements, the expected-basis /
feedback templates, the lock note, and the pilot harness's
deterministic rubric replicated in code (35 substantive passages).
The datasets in `data/` were and are correct; the leak was the
builder script itself, and §3's description of the harness as
"replicated token-for-token in `src/build_blocker_triage.py`" was
accurate for that committed version.

**Fix applied (2026-10-17):** the builder now embeds no suite prose.
All content strings are read at runtime from
`data/eval-suites/blocker-triage/builder_sources/` (`criteria.txt`,
`twins.json`, `templates.json`), and the rubric is the suite pilot
runner's own `score()` / `hard_fail()`, imported from the data copy —
imported, no longer replicated. Re-running the fixed builder
regenerates all five outputs (`judge_criteria.txt`,
`gepa_valset.json`, `gepa_trainset.json`, `valset.lock.json`,
`ground_truth.json`) **byte-identical** to the pre-fix outputs; the
locked valset sha256 is unchanged (`6c7c28a6…`). The audit's needle
scan over the fixed builder returns 0 substantive suite-prose
passages. The original claim above is left standing in the header as
the historical record of what was asserted; this addendum is the
correction of record.
