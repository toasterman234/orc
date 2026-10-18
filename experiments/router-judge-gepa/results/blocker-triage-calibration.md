# Blocker-triage judge calibration — results (FIT #1, ORC-native)

- **Date:** 2026-10-17 · **Verdict: PARTIAL** · agreement rate 0.667 (7 full matches, 2 partials, 3 mismatches / 12 items)
- **Judge:** ORC tier-1 `:reasoning` (criteria × adversarial stance × discrete 1–5 scale, evidence before score), criteria overridden per call with text authored from the suite (principle + must_not_generalize_to as failure conditions + the six suite criteria + hard-fail list). Model gpt-5.6-luna via the Mac proxy service (Codex subscription). Stance is the dimension's built-in adversarial stance — the tier-1 seam overrides criteria, not stance.
- **Exam:** locked valset, sha256 `6c7c28a633c389d6bb862c2942ac5eed4699349db6c7c53dee8d6e78effef3b6` (data-only; lock record in data). 12 items: the suite's harness-scored pair (baseline captured response, ground truth 1/10 → fail-side; candidate response, 10/10 → pass-side) + 5 counterexample templates × authored correct/wrong decision statements.
- **Scoring rule (pre-registered in `consumer/src/router_loop/blocker_triage.clj`):** level ≥ 4 = pass-side, ≤ 2 = fail-side, 3 = partial (½ credit). PASSED = rate ≥ 0.80 with both base items full matches; FAILED = rate < 0.50 or a base item fully mismatched; else PARTIAL.
- **LLM spend:** 12 judge calls (cap 60), ~43739 total tokens reported by the provider across items (per-item usage in the data-only detail file).

| Item | Expected | Judge level | Judge score | Judge side | Agreement |
|---|---|---|---|---|---|
| item-01 | fail | 2 | 0.25 | fail | 1.0 |
| item-02 | pass | 3 | 0.50 | partial | 0.5 |
| item-03 | pass | 2 | 0.25 | fail | 0.0 |
| item-04 | fail | 2 | 0.25 | fail | 1.0 |
| item-05 | pass | 2 | 0.25 | fail | 0.0 |
| item-06 | fail | 2 | 0.25 | fail | 1.0 |
| item-07 | pass | 2 | 0.25 | fail | 0.0 |
| item-08 | fail | 2 | 0.25 | fail | 1.0 |
| item-09 | pass | 3 | 0.50 | partial | 0.5 |
| item-10 | fail | 1 | 0.00 | fail | 1.0 |
| item-11 | pass | 4 | 0.75 | pass | 1.0 |
| item-12 | fail | 2 | 0.25 | fail | 1.0 |

Full judge reasoning/evidence per item: `data/eval-suites/blocker-triage/calibration_detail.json` (gitignored — it quotes scenario material). Method, field mapping, and limitations: `blocker-triage-mapping-memo.md`. Suite ground truth: baseline 1/10, candidate 10/10, pass threshold 8/10 (the suite's own deterministic harness, replicated token-for-token in `src/build_blocker_triage.py`).

*Correction (2026-10-17, B1 fix):* the harness is no longer replicated in
`src/build_blocker_triage.py` — the builder now imports the suite pilot
runner's own `score()` / `hard_fail()` from the data copy, and all suite
prose it emits is read from data-side builder sources. Outputs are
byte-identical; the valset sha256 above is unchanged. See the correction
addendum in `blocker-triage-mapping-memo.md`.

Item labels above are neutralized for publication (audit item B2): the mapping from `item-NN` labels to the locked valset's item ids is retained only in the data-only record (`data/eval-suites/blocker-triage/published_id_map.json`).
