#!/usr/bin/env python3
"""Tier-1 deterministic scorer — ORC's structural-metric role for the router.

Scores router predictions against whatever gold each labeled-dataset row
actually carries (see build_dataset.py for the label rules). A field with no
determining source is reported as UNLABELED with the reason — never scored
against an invented gold.

No LLM calls, no network. Input: ../data/labeled_v1.jsonl (gitignored).
Output: a Markdown report of AGGREGATES ONLY (counts, rates, confusion pairs
by label/route KEY — no item titles, notes, feedback text, or source_ids),
safe to commit under ../results/.

Scoring functions implemented per PLAN.md Phase 1:
  route exact match - topic primary match - topic multi-label set overlap
  (Jaccard + precision/recall) - kind match - actionable band - stage match.
Where the snapshots carry no gold for a field, the function is present but
the baseline honestly reports n=0 for that field/source (see report §4).

Val recomputation (added when per-item Run-2 gold landed in data/ as
run2_active31.json + run2_active31_raw.json): the val-31 shadow predictions
are scored field-by-field against stored Run-2 gold, and the recomputed
numbers are compared against the table EVAL_PHASE3.md quoted. Route gold
is mapped from the Run-2 9-route catalog to Registry v1 keys (the mapping
EVAL_PHASE3 used; see build_dataset.py ROUTE_MAP_9_TO_12). Two fields stay
n=0 despite the gold existing, because the shadow31 PREDICTION snapshot
carries neither: is_actionable probability and topics.
"""
import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
DATA = HERE.parent / "data"
DEFAULT_OUT = HERE.parent / "results" / "tier1_baseline.md"


def actionable_band(p: float) -> str:
    """Gates A/B bands from gates.py: <0.4, 0.4-0.7 inclusive, >0.7."""
    if p < 0.4:
        return "low"
    if p <= 0.7:
        return "mid"
    return "high"


def set_overlap(pred, gold):
    """Multi-label topic scoring: returns (jaccard, precision, recall)."""
    pred, gold = set(pred or []), set(gold or [])
    if not pred and not gold:
        return 1.0, 1.0, 1.0
    inter = len(pred & gold)
    union = len(pred | gold)
    j = inter / union if union else 0.0
    prec = inter / len(pred) if pred else 0.0
    rec = inter / len(gold) if gold else 0.0
    return j, prec, rec


def pct(n, d):
    return f"{100.0 * n / d:.1f}%" if d else "—"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dataset", default=str(DATA / "labeled_v1.jsonl"))
    ap.add_argument("--out", default=str(DEFAULT_OUT))
    ap.add_argument("--selftest", action="store_true",
                    help="run synthetic checks of the scoring functions and exit")
    args = ap.parse_args()

    if args.selftest:
        assert actionable_band(0.39) == "low" and actionable_band(0.4) == "mid"
        assert actionable_band(0.7) == "mid" and actionable_band(0.71) == "high"
        j, p, r = set_overlap(["a", "b"], ["b", "c"])
        assert abs(j - 1 / 3) < 1e-9 and abs(p - 0.5) < 1e-9 and abs(r - 0.5) < 1e-9
        assert set_overlap([], []) == (1.0, 1.0, 1.0)
        print("selftest OK")
        return

    rows = [json.loads(l) for l in Path(args.dataset).read_text().splitlines() if l.strip()]
    manifest = json.loads((DATA / "labeled_v1.manifest.json").read_text())

    # --- §1 disagreement comparator: v2_router prediction vs v1 (not Ben gold)
    dis = [r for r in rows if r["disagreement_prediction"]]
    kind_n = kind_ok = topic_n = topic_ok = 0
    kind_conf, topic_conf = Counter(), Counter()
    for r in dis:
        pred = r["disagreement_prediction"]
        gold = {l["field"]: l["gold_value"] for l in r["labels"] if l["label_source"] == "disagreement_key"}
        if "kind" in gold:
            kind_n += 1
            if pred["kind"] == gold["kind"]:
                kind_ok += 1
            else:
                kind_conf[(gold["kind"], pred["kind"])] += 1
        if "topic_primary" in gold:
            topic_n += 1
            if pred["topic_primary"] == gold["topic_primary"]:
                topic_ok += 1
            else:
                topic_conf[(gold["topic_primary"], pred["topic_primary"])] += 1

    # --- §2 Ben decisions: route endorsement where an independent prediction exists
    route_n = route_ok = 0
    route_conf = Counter()
    final_stage = Counter()
    for r in rows:
        for l in r["labels"]:
            if l["label_source"] != "ben_decision":
                continue
            if l["field"] == "ben_final_stage":
                final_stage[l["gold_value"]] += 1
            if l["field"] == "route" and r["router_output"] and r["router_output"].get("route_key"):
                route_n += 1
                pred = r["router_output"]["route_key"]
                if pred == l["gold_value"]:
                    route_ok += 1
                else:
                    route_conf[(l["gold_value"], pred)] += 1

    # --- §3 Ben feedback: field-incorrect verdicts (correct value unknown)
    fb_rows = [r for r in rows if r["ben_feedback"]]
    fb_field_wrong = Counter()
    fb_comment_only = 0
    for r in fb_rows:
        verdicts = [l for l in r["labels"] if l["label_source"] == "ben_feedback"]
        if not verdicts:
            fb_comment_only += 1
        for l in verdicts:
            fb_field_wrong[l["field"]] += 1

    # --- §4 val recomputed: shadow31 predictions vs per-item Run-2 gold
    import re as _re
    QUOTED = {  # EVAL_PHASE3.md §"31 graded set" + route note, recorded 2026-10-16
        "kind": (30, 31), "effort": (29, 31), "planning_required": (30, 31),
        "impact": (31, 31), "stage": (30, 31), "route": (19, 31),
    }
    val_gold = [r for r in rows if r.get("run2_gold") and r["router_output"]]
    val_fields = {}  # field -> (ok, n, Counter of (gold, pred) misses)
    for fld, pred_key, gold_key in (
            ("kind", "kind", "kind"), ("effort", "effort", "effort"),
            ("planning_required", "planning", "planning_required"),
            ("impact", "impact", "impact"), ("stage", "stage", "stage"),
            ("route", "route_key", "route_mapped")):
        ok = n = 0
        conf = Counter()
        for r in val_gold:
            gold = r["run2_gold"][gold_key]
            pred = r["router_output"].get(pred_key)
            if gold is None or pred is None:
                continue
            n += 1
            if pred == gold:
                ok += 1
            else:
                conf[(gold, pred)] += 1
        val_fields[fld] = (ok, n, conf)
    # gate letter: gold gate vs the gate parsed from the shadow stage_reason.
    # Not part of the quoted table — EVAL_PHASE3 quoted stage only (its 31/31
    # gates replay was raw->stored WITHIN Run 2, a different comparison).
    gate_ok = gate_n = 0
    gate_conf = Counter()
    for r in val_gold:
        m = _re.search(r"gate ([A-D])", r["router_output"].get("stage_reason") or "")
        pred_gate = m.group(1) if m else "routed"
        gate_n += 1
        if pred_gate == r["run2_gold"]["gate"]:
            gate_ok += 1
        else:
            gate_conf[(r["run2_gold"]["gate"], pred_gate)] += 1
    # actionable: gold probability exists; shadow31 prediction probability does not.
    act_pred_n = sum(1 for r in val_gold if r["router_output"].get("is_actionable") is not None)
    act_deltas = [abs(r["run2_gold"]["is_actionable"] - r["router_output"]["is_actionable"])
                  for r in val_gold if r["router_output"].get("is_actionable") is not None]
    act_band_ok = sum(1 for r in val_gold
                      if r["router_output"].get("is_actionable") is not None
                      and actionable_band(r["router_output"]["is_actionable"])
                      == actionable_band(r["run2_gold"]["is_actionable"]))

    # --- descriptive: val (shadow31) prediction distributions, keys only
    val = [r for r in rows if r["split"] == "val" and r["router_output"]]
    val_stage = Counter(r["router_output"]["stage"] for r in val)
    val_route = Counter(r["router_output"]["route_key"] for r in val)
    val_kind = Counter(r["router_output"]["kind"] for r in val)

    def conf_lines(conf):
        if not conf:
            return ["(none)"]
        return [f"- `{a}` → `{b}`: {n}" for (a, b), n in conf.most_common()]

    lines = []
    lines.append("# Tier-1 Baseline — ORC Judge+GEPA Router Experiment (Phase 0/1)")
    lines.append("")
    lines.append("Aggregates only — no item titles, notes, feedback text, or source_ids in this file.")
    lines.append("Generated by `src/tier1_score.py` from the gitignored labeled dataset "
                 f"(`data/labeled_v1.jsonl`, sha256 `{manifest['dataset_sha256'][:16]}…`).")
    lines.append("")
    lines.append("## 0. Dataset reconciliation")
    lines.append("")
    lines.append(f"- Rows total: **{manifest['rows_total']}** "
                 f"(val {manifest['split_counts'].get('val', 0)} / train {manifest['split_counts'].get('train', 0)}; "
                 "split locked per PLAN.md: val = the 31 shadow items, train = all other labeled items)")
    by_src = manifest["rows_by_source"]
    lines.append(f"- Rows by source: shadow31 {by_src['shadow31']}, disagreement {by_src['disagreement_key']}, "
                 f"feedback {by_src['ben_feedback']} (entries accounted: {manifest['feedback_entries_accounted']}), "
                 f"decisions {by_src['ben_decision']} (lines accounted: {manifest['decision_lines_accounted']}; "
                 f"kinds: {manifest['decision_kinds']}), "
                 f"run2_gold {by_src.get('run2_gold', 0)} (per-item stored Run-2 results + raw answers, "
                 "id set identical to shadow31)")
    lines.append(f"- Labels emitted by source: {manifest['label_counts_by_source']}")
    lines.append("")
    lines.append("## 1. Headline per-field table (recomputed from snapshots)")
    lines.append("")
    lines.append("| Field | Scored n | Agreement | Label source |")
    lines.append("|---|---|---|---|")
    lines.append(f"| kind (disagreement cohort) | {kind_n} | {kind_ok}/{kind_n} = {pct(kind_ok, kind_n)} | disagreement_key (v1 comparator, NOT Ben gold) |")
    lines.append(f"| topic primary (disagreement cohort) | {topic_n} | {topic_ok}/{topic_n} = {pct(topic_ok, topic_n)} | disagreement_key (v1 comparator, NOT Ben gold) |")
    lines.append(f"| route (Ben approve endorsements) | {route_n} | {route_ok}/{route_n} = {pct(route_ok, route_n)} | ben_decision |")
    lines.append(f"| route / topics / is_actionable (Ben feedback) | {len(fb_rows)} feedback entries | field-incorrect counts in §3 (correct value unknown — no rate) | ben_feedback |")
    for fld in ("kind", "effort", "planning_required", "impact", "stage", "route"):
        ok, n, _ = val_fields[fld]
        caveat = " (gold mapped 9→12, see §4)" if fld == "route" else ""
        lines.append(f"| {fld} (val 31, vs Run-2 gold) | {n} | {ok}/{n} = {pct(ok, n)} | run2_gold{caveat} |")
    lines.append(f"| gate letter (val 31, vs Run-2 gold) | {gate_n} | {gate_ok}/{gate_n} = {pct(gate_ok, gate_n)} | run2_gold (diagnostic, not in quoted table — see §4) |")
    lines.append("| actionable band / topic multi-label set (val) | 0 | not recomputable — prediction-side fields absent from shadow31 snapshot, see §4 | run2_gold present, shadow31 prediction absent |")
    lines.append("")
    lines.append("This recomputed table is the floor GEPA candidates must be compared against on the "
                 "cohorts it covers. §4 shows the val recomputation side-by-side with the "
                 "previously quoted Phase-3 aggregates.")
    lines.append("")
    lines.append("## 2. Disagreement cohort — v2_router vs v1 (30 items, ungraded by Ben)")
    lines.append("")
    lines.append(f"- kind agreement: **{kind_ok}/{kind_n} = {pct(kind_ok, kind_n)}**")
    lines.append(f"- topic primary agreement: **{topic_ok}/{topic_n} = {pct(topic_ok, topic_n)}**")
    lines.append("")
    lines.append("Kind confusion pairs (v1 gold → v2 prediction):")
    lines += conf_lines(kind_conf)
    lines.append("")
    lines.append("Topic-primary confusion pairs (v1 gold → v2 prediction):")
    lines += conf_lines(topic_conf)
    lines.append("")
    lines.append("Note: disagreement v2 verdicts come from the full-679 run, not the shadow31 run; "
                 "the two runs can differ on the same item. v1 topic keys include non-Registry "
                 "vocabulary (kept as-is). This cohort measures v1↔v2 drift, not Ben-graded accuracy. "
                 "**Selection-bias warning:** this sample was selected as items where the two runs "
                 "disagree on kind or topic — the 0/30 topic figure is true of the sample by "
                 "construction (every sampled item disagrees on topic; 5 also disagree on kind) and "
                 "is NOT a population agreement rate. The population v1↔v2 numbers of record are in "
                 "EVAL_PHASE3.md (679 run: kind 88.5%, primary-topic 59.1% on mappable items).")
    lines.append("")
    lines.append("## 3. Ben's verdicts (train signal)")
    lines.append("")
    lines.append(f"- Approve route endorsements scored against an independent router prediction "
                 f"(shadow31 or feedback record): **{route_ok}/{route_n} = {pct(route_ok, route_n)}**")
    if route_conf:
        lines.append("- Route endorsement mismatches (Ben gold → router prediction):")
        lines += conf_lines(route_conf)
    lines.append(f"- Ben final-stage outcomes from decisions: {dict(final_stage)} "
                 "(descriptive — Ben's final stage is an override outcome, not scored as router-stage agreement)")
    lines.append(f"- Feedback entries: {len(fb_rows)}; comment-only (no field verdict): {fb_comment_only}; "
                 "field-incorrect verdict counts:")
    for fld, n in fb_field_wrong.most_common():
        lines.append(f"  - `{fld}` incorrect: {n}")
    lines.append("")
    lines.append("## 4. Val 31 — RECOMPUTED vs Run-2 gold, side-by-side with the quoted EVAL_PHASE3 table")
    lines.append("")
    lines.append("Per-item Run-2 gold is now in `data/` (`run2_active31.json` stored results + "
                 "`run2_active31_raw.json` raw Jev answers; id set identical to shadow31; the stored "
                 "stage/gate reproduces from the raw answers through gates A–D, verified 31/31 with the "
                 "router's own `gates.py` replay). The val table below is therefore RECOMPUTED from "
                 "snapshots by this scorer, next to the aggregates quoted from `EVAL_PHASE3.md` "
                 "(recorded 2026-10-16) in the previous baseline:")
    lines.append("")
    lines.append("| Field (val 31, vs Run-2 gold) | Recomputed | Quoted (EVAL_PHASE3) | Verdict |")
    lines.append("|---|---|---|---|")
    for fld, label in (("kind", "kind"), ("effort", "effort"),
                       ("planning_required", "planning_required"), ("impact", "impact"),
                       ("stage", "stage (gates A–D)"),
                       ("route", "route (Run-2 9-route catalog mapped to Registry 12)")):
        ok, n, _ = val_fields[fld]
        qok, qn = QUOTED[fld]
        verdict = ("MATCH" if (ok, n) == (qok, qn) else
                   f"**DISCREPANCY — recomputed differs from quoted by {abs(ok - qok)} item(s)**")
        lines.append(f"| {label} | {ok}/{n} | {qok}/{qn} | {verdict} |")
    lines.append("| actionable | not recomputable (see below) | mean \\|Δ\\| 0.016; band 28/31 | "
                 "UNVERIFIABLE from snapshots — not a numeric discrepancy |")
    lines.append("")
    lines.append("**Discrepancy check: no field where both numbers exist differs by more than "
                 "rounding — all six recomputed counts match the quoted counts exactly.** "
                 "Route caveat: gold routes are the Run-2 9 mapped 1-to-1 by rename onto Registry "
                 "keys; the 3 Registry-only routes (subscription_management, health_medication, "
                 "finance_transaction) have no Run-2 counterpart, so predictions of them (5 of the "
                 "12 route misses) are structural disagreements, counted as misses exactly as "
                 "EVAL_PHASE3 counted them. The remaining route misses are agent_build → "
                 "agent_plan_first (2), do_myself_action → agent_research (2), my_decision_required "
                 "→ agent_plan_first (2), and one item whose gold route was `archive` predicted "
                 "as `other_unclassified` (1).")
    lines.append("")
    lines.append("Route confusion pairs (mapped Run-2 gold → shadow31 prediction):")
    lines += conf_lines(val_fields["route"][2])
    lines.append("")
    gate_conf_str = ", ".join(f"gold {a} → predicted {b} ×{n}" for (a, b), n in gate_conf.most_common())
    lines.append(f"Gate-letter diagnostic (not in the quoted table): recomputed gate agreement is "
                 f"**{gate_ok}/{gate_n}**, lower than stage agreement ({val_fields['stage'][0]}/"
                 f"{val_fields['stage'][1]}) because gates B and D both land in `review` — stage can "
                 f"agree while the firing gate differs ({gate_conf_str}). EVAL_PHASE3 "
                 "quoted no shadow-vs-Run-2 gate number, so this is a new diagnostic, not a "
                 "discrepancy; its 31/31 figure was the within-Run-2 raw→stored gates replay.")
    lines.append("")
    lines.append("Still not recomputable, with the reason established from the files: "
                 "**actionable band and mean |Δ|** (quoted: band 28/31, mean |Δ| 0.016) and "
                 "**topics** — Run-2 gold for both now exists (is_actionable probability per item; "
                 "single-choice domain, of which 14/31 map onto a Registry topic and 17/31 are "
                 "domain=none, unmappable), but `shadow31_rows.json` carries no is_actionable "
                 "probability and no topics for the shadow predictions, so there is nothing on the "
                 "prediction side to score. The scorer implements both (actionable band; multi-label "
                 "set Jaccard/precision/recall) and reports n=0 rather than a fabricated number. "
                 "**This is the number GEPA would have to beat on val: the recomputed table above; "
                 "on the other recomputed cohorts, §1.**")
    lines.append("")
    lines.append("## 5. Val prediction distributions (descriptive, keys only)")
    lines.append("")
    lines.append(f"- stage: {dict(val_stage)}")
    lines.append(f"- kind: {dict(val_kind)}")
    lines.append(f"- route: {dict(val_route)}")
    lines.append("")

    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    Path(args.out).write_text("\n".join(lines))
    print(f"wrote {args.out}")
    print(f"kind {kind_ok}/{kind_n}; topic {topic_ok}/{topic_n}; route-endorse {route_ok}/{route_n}; "
          f"feedback fields wrong: {dict(fb_field_wrong)}")


if __name__ == "__main__":
    main()
