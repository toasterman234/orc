#!/usr/bin/env python3
"""Phase 0 — build the labeled dataset for the ORC judge+GEPA router experiment.

Deterministic join of the gitignored snapshots in ../data/ ONLY. No network,
no live systems. Output (also gitignored, in ../data/):
  labeled_v1.jsonl  — one row per distinct source_id
  labeled_v1.manifest.json — counts, split rule, content + seed hashes

Design rules (PLAN.md Phase 0 + the no-invented-gold rule):
- Locked split: val = the 31 shadow31 items. Train = every other labeled item.
  The split is a property of source cohort, never re-drawn.
- A gold label is emitted ONLY where a source determines it:
  * ben_decision approve  -> route gold = the decision's route_key
    (apply_decision semantics: approve keeps the router's previous route, so
    route_key IS the route Ben endorsed) + Ben's final stage routed_ready.
  * ben_decision reference -> Ben's final stage reference_only only.
    Route is NOT labeled (the decision record carries route_key=null).
  * ben_feedback -> a field is labeled "incorrect" only when it appears in
    wrong_parts / reasons. The correct value is unknown (null) — Ben's text
    is carried verbatim in the gitignored dataset for later reflective use,
    but no correct value is parsed out of it. Feedback with empty
    wrong_parts+reasons is comment-only: accounted, no field labels.
  * disagreement_key -> NOT Ben gold (the sample is ungraded, per PLAN.md).
    Its v1 verdict is carried as a comparator for the v2_router verdict,
    labeled label_source=disagreement_key, nature=v1_comparator.
- shadow31_rows.json fields are router PREDICTIONS (the Registry-v1 shadow
  run), not gold. Per-item Run-2 gold IS now in the snapshots
  (run2_active31.json = stored Run-2 results, the file gates.py replayed
  stages against; run2_active31_raw.json = the raw Run-2 Jev answers those
  results were derived from — stored stage/gate reproduces from raw via
  gates A-D, 31/31). Val rows gain run2_gold labels for the fields the
  stored file determines (kind/effort/planning/impact/stage/gate/route/
  is_actionable probability), with two honest mappings:
  * route: Run 2 used the old 9-route catalog; every one of the 9 renames
    1-to-1 onto a Registry-v1 key (ROUTE_MAP_9_TO_12 below, the mapping
    EVAL_PHASE3 used). The other 3 Registry routes (subscription_management,
    health_medication, finance_transaction) have no Run-2 counterpart, so a
    prediction of one of them can never match mapped gold — structural,
    counted as disagreement exactly as EVAL_PHASE3 counted it.
  * domain: Run 2 used a single-choice domain (old 11 areas + none), not
    Registry topics. Where DOMAIN_MAP (copied from the Phase-3 eval code)
    maps a domain onto a Registry topic key, the mapped topic_primary is
    carried in the run2_gold block; `none` and unlisted domains are marked
    unmappable, never forced. (No topic label is emitted: shadow31_rows
    carries no topic prediction to score it against.)
  Gold fields with no shadow31 prediction counterpart (blockers,
  blocker_type, time_sensitive, part_of_bigger, domain) are carried in the
  run2_gold block only, for future runs that do predict them.
- No item titles/notes are copied into the dataset. Feedback text is the
  only free text carried (it is Ben's verdict text, needed as the
  reflective seed), and it never leaves the gitignored data/ dir.
"""
import hashlib
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
DATA = HERE.parent / "data"

# wrong_parts / reasons vocabulary from the Things Router app feedback sheet
WRONG_PART_FIELDS = {
    "Labels": "topics",
    "Route": "route",
    "Actionable call": "is_actionable",
    "Kind/Effort": "kind_effort",
    "Stage decision": "stage",
    "Other": "other",
}
REASON_FIELDS = {
    "Wrong topic": "topics",
    "Wrong destination": "route",
}

# Run-2 9-route catalog -> Registry v1 keys (the mapping EVAL_PHASE3 used:
# "Route agreement is 19/31 against Run 2's 9-route catalog mapped to the
# new 12"). All 9 are 1-to-1 renames; the remaining 3 Registry routes have
# no Run-2 counterpart.
ROUTE_MAP_9_TO_12 = {
    "do_myself": "do_myself_action",
    "agent_research": "agent_research",
    "agent_build": "agent_build",
    "agent_plan_first": "agent_plan_first",
    "my_decision": "my_decision_required",
    "errand_out": "errand_out",
    "keep_reference": "keep_reference",
    "archive": "archive_item",
    "other": "other_unclassified",
}

# Run-2 single-choice domain -> Registry v1 topic key, copied verbatim from
# the Phase-3 eval code (windmill-llm-router/eval_compare.py DOMAIN_MAP).
# Domains absent here (foods, digital_nomad, ndt_log, none) are unmappable.
DOMAIN_MAP = {
    "business_ideas": "business_ideas",
    "finance": "finance",
    "health_medication": "health_medication",
    "memphis_houses": "memphis_houses",
    "investment_strategies": "investment_strategy",
    "concentrated_bets": "investment_strategy",
    "long_term_stocks": "investment_strategy",
    "passive_income": "investment_strategy",
}


def sha256_file(p: Path) -> str:
    return hashlib.sha256(p.read_bytes()).hexdigest()


def load_jsonl(name):
    return [json.loads(l) for l in (DATA / name).read_text().splitlines() if l.strip()]


def main():
    shadow = {r["source_id"]: r for r in json.loads((DATA / "shadow31_rows.json").read_text())}
    dis_key = json.loads((DATA / "disagreement_key.json").read_text())
    disagreement = {v["source_id"]: v for v in dis_key.values()}
    feedback = load_jsonl("feedback.jsonl")
    decisions = load_jsonl("decisions.jsonl")
    fb_by_id = {d["item_id"]: d for d in feedback}
    dc_by_id = {}
    for d in decisions:  # 15 lines, all distinct item_ids (verified); last wins if ever duplicated
        dc_by_id[d["item_id"]] = d

    # Run-2 gold: stored results carry the derived per-item fields; the raw
    # file carries the Jev answers + full probability maps they came from.
    run2_stored = {r["source_id"]: r for r in
                   json.loads((DATA / "run2_active31.json").read_text())["items"]}
    run2_raw = {r["source_id"]: r for r in
                json.loads((DATA / "run2_active31_raw.json").read_text())["items"]}
    assert set(run2_stored) == set(run2_raw), "stored/raw Run-2 id sets differ"
    run2_by_id = {}
    for sid, s in run2_stored.items():
        raw = run2_raw[sid]
        assert raw["answers"]["kind"]["choice"] == s["kind"], "raw/stored kind differ"
        assert raw["answers"]["route"]["choice"] == s["route"], "raw/stored route differ"
        mapped_topic = DOMAIN_MAP.get(s["domain"])
        run2_by_id[sid] = {
            "kind": s["kind"],
            "effort": s["effort_label"],
            "planning_required": s["planning_required"],
            "impact": s["impact"],
            "stage": s["stage"],
            "gate": s["gate"],
            "route_run2": s["route"],
            "route_mapped": ROUTE_MAP_9_TO_12[s["route"]],
            "is_actionable": s["is_actionable"],
            "blockers": s["blockers"],
            "blocker_type": s["blocker_type"],
            "time_sensitive": s["time_sensitive"],
            "part_of_bigger": s["part_of_bigger"],
            "domain_run2": s["domain"],
            "topic_primary_mapped": mapped_topic,
            "topic_mapping": ("mapped" if mapped_topic else
                              "unmappable_none" if s["domain"] == "none" else "unmappable_no_registry_counterpart"),
        }

    all_ids = sorted(set(shadow) | set(disagreement) | set(fb_by_id) | set(dc_by_id) | set(run2_by_id))
    rows = []
    for sid in all_ids:
        sources = []
        if sid in shadow:
            sources.append("shadow31")
        if sid in disagreement:
            sources.append("disagreement_key")
        if sid in fb_by_id:
            sources.append("ben_feedback")
        if sid in dc_by_id:
            sources.append("ben_decision")
        if sid in run2_by_id:
            sources.append("run2_gold")

        router_output = None
        if sid in shadow:
            s = shadow[sid]
            router_output = {
                "source": "shadow31_run",
                "kind": s["kind"], "effort": s["effort"], "planning": s["planning"],
                "impact": s["impact"], "route_key": s["route_key"],
                "stage": s["stage"], "stage_reason": s["stage_reason"],
                # NOTE: topics and is_actionable are absent from shadow31_rows.json
                "topic_primary": None, "topics": None, "is_actionable": None,
            }
        elif sid in fb_by_id:
            f = fb_by_id[sid]
            router_output = {
                "source": "feedback_record",
                "kind": None, "effort": None, "planning": None, "impact": None,
                "route_key": f["route_key"], "stage": f["stage"], "stage_reason": None,
                "topic_primary": None, "topics": None, "is_actionable": None,
            }

        disagreement_pred = None
        if sid in disagreement:
            d = disagreement[sid]
            disagreement_pred = {
                "source": "disagreement_key.v2_router",  # from the full-679 run, not shadow31
                "kind": d["v2_router"][0], "topic_primary": d["v2_router"][1],
            }

        labels = []
        if sid in disagreement:
            d = disagreement[sid]
            labels.append({"field": "kind", "gold_value": d["v1"][0],
                           "label_source": "disagreement_key", "nature": "v1_comparator",
                           "prediction_source": "disagreement_key.v2_router"})
            labels.append({"field": "topic_primary", "gold_value": d["v1"][1],
                           "label_source": "disagreement_key", "nature": "v1_comparator",
                           "prediction_source": "disagreement_key.v2_router"})
        if sid in fb_by_id:
            f = fb_by_id[sid]
            wrong_fields = []
            for part in f.get("wrong_parts") or []:
                fld = WRONG_PART_FIELDS.get(part, "other")
                if fld not in wrong_fields:
                    wrong_fields.append(fld)
            for reason in f.get("reasons") or []:
                fld = REASON_FIELDS.get(reason)
                if fld and fld not in wrong_fields:
                    wrong_fields.append(fld)
            for fld in wrong_fields:
                labels.append({"field": fld, "gold_value": None, "verdict": "incorrect",
                               "label_source": "ben_feedback", "nature": "field_incorrect_correct_unknown"})
        if sid in dc_by_id:
            d = dc_by_id[sid]
            if d["decision"] == "approve":
                labels.append({"field": "route", "gold_value": d["route_key"],
                               "label_source": "ben_decision", "nature": "endorsed_route"})
                labels.append({"field": "ben_final_stage", "gold_value": d["result"]["stage"],
                               "label_source": "ben_decision", "nature": "ben_final"})
            elif d["decision"] == "reference":
                labels.append({"field": "ben_final_stage", "gold_value": d["result"]["stage"],
                               "label_source": "ben_decision", "nature": "ben_final"})
            elif d["decision"] == "reroute":
                labels.append({"field": "route", "gold_value": d["route_key"],
                               "label_source": "ben_decision", "nature": "corrected_route"})
        if sid in run2_by_id:
            g = run2_by_id[sid]
            for fld in ("kind", "effort", "planning_required", "impact", "stage"):
                labels.append({"field": fld, "gold_value": g[fld],
                               "label_source": "run2_gold", "nature": "stored_run2_result"})
            labels.append({"field": "route", "gold_value": g["route_mapped"],
                           "label_source": "run2_gold", "nature": "mapped_from_run2_9_route_catalog"})
            labels.append({"field": "is_actionable", "gold_value": g["is_actionable"],
                           "label_source": "run2_gold", "nature": "gold_probability_prediction_absent_in_shadow31"})

        rows.append({
            "source_id": sid,
            "split": "val" if sid in shadow else "train",
            "in_sources": sources,
            "router_output": router_output,
            "disagreement_prediction": disagreement_pred,
            "run2_gold": run2_by_id.get(sid),
            "labels": labels,
            "ben_feedback": ({"wrong_parts": fb_by_id[sid].get("wrong_parts") or [],
                              "reasons": fb_by_id[sid].get("reasons") or [],
                              "text": fb_by_id[sid].get("text") or "",
                              "received_at": fb_by_id[sid].get("received_at")}
                             if sid in fb_by_id else None),
            "ben_decision": ({"decision": dc_by_id[sid]["decision"],
                              "route_key": dc_by_id[sid].get("route_key"),
                              "result_stage": dc_by_id[sid]["result"]["stage"],
                              "received_at": dc_by_id[sid].get("received_at")}
                             if sid in dc_by_id else None),
        })

    out = DATA / "labeled_v1.jsonl"
    with out.open("w") as fh:
        for r in rows:
            fh.write(json.dumps(r, sort_keys=True) + "\n")

    from collections import Counter
    manifest = {
        "dataset": "labeled_v1.jsonl",
        "dataset_sha256": sha256_file(out),
        "rows_total": len(rows),
        "split_counts": dict(Counter(r["split"] for r in rows)),
        "rows_by_source": {s: sum(1 for r in rows if s in r["in_sources"])
                           for s in ("shadow31", "disagreement_key", "ben_feedback", "ben_decision", "run2_gold")},
        "label_counts_by_source": dict(Counter(l["label_source"] for r in rows for l in r["labels"])),
        "feedback_entries_accounted": len(feedback),
        "decision_lines_accounted": len(decisions),
        "decision_kinds": dict(Counter(d["decision"] for d in decisions)),
        "run2_gold": {
            "items": len(run2_by_id),
            "id_set_equals_shadow31": set(run2_by_id) == set(shadow),
            "route_map_9_to_12": ROUTE_MAP_9_TO_12,
            "domain_map": DOMAIN_MAP,
            "topic_mapping_counts": dict(Counter(g["topic_mapping"] for g in run2_by_id.values())),
        },
        "split_rule": "val = source_id in shadow31_rows.json (31); train = all other labeled items. Locked; never re-drawn.",
        "seed_hashes": {n: sha256_file(DATA / n) for n in
                        ("questions_fixed.json", "labels_final.json", "workflows_final.json",
                         "shadow31_rows.json", "disagreement_key.json",
                         "feedback.jsonl", "decisions.jsonl",
                         "run2_active31.json", "run2_active31_raw.json")},
        "known_gaps": [
            "shadow31_rows.json carries no topics and no is_actionable probability, so topic and actionable-band scoring on val is still not recomputable: Run-2 gold for both now exists, the shadow31 PREDICTIONS do not.",
            "Run-2 domain is a single-choice old-vocab field, not Registry topics: 14/31 items map onto a Registry topic via DOMAIN_MAP, 17/31 are domain=none (unmappable).",
            "disagreement_sample is ungraded by Ben (PLAN.md): disagreement_key v1 is a comparator, not Ben gold.",
        ],
    }
    (DATA / "labeled_v1.manifest.json").write_text(json.dumps(manifest, indent=1, sort_keys=True) + "\n")
    print(json.dumps(manifest, indent=1, sort_keys=True))


if __name__ == "__main__":
    main()
