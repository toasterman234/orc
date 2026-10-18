#!/usr/bin/env python3
"""Build the blocker-triage calibration datasets (FIT #1, ORC-native).

Reads the canonical 0.1 repo's agent-blocker-triage suite from the
gitignored copies in data/eval-suites/blocker-triage/ and produces, all
inside data/ (suite content never enters the repo):

  judge_criteria.txt   — the ORC tier-1 judge criteria, authored from
      the suite's target_behavior principle, its must_not_generalize_to
      anti-pattern, the six scoring criteria, and the hard-fail list.
  gepa_valset.json     — the LOCKED calibration exam (12 items).
  gepa_trainset.json   — GEPA reflective examples from the remaining
      material (guidance / expected-action-sequence / hard-fail
      exemplars over the base scenario's facts).
  valset.lock.json     — sha256 of the valset + source files, item ids.
  ground_truth.json    — the suite harness's deterministic scores for
      the baseline and candidate responses, produced by the suite's
      own pilot runner (imported below, not replicated).

CONTENT BOUNDARY (B1 fix, 2026-10-17 — see
results/blocker-triage-mapping-memo.md correction addendum and
results/pre-push-audit.md): this script embeds NO suite prose. Every
content string it emits — the judge criteria text, the counterexample
decision statements, the expected-basis / feedback templates, the lock
note, the dataset item ids and the lock date — is read at runtime from data/eval-suites/blocker-triage/
builder_sources/ (criteria.txt, twins.json, templates.json), and the
deterministic scoring rubric is the suite pilot runner's own
score()/hard_fail(), imported from the data copy. What remains here
is structure and transformation logic only.

Valset composition (expected_side = the verdict a correct judge must
land on, pre-registered mapping: level >= 4 pass-side, <= 2 fail-side,
3 partial): the suite's captured baseline response (harness scores it
below the pass threshold -> fail-side), the suite's post-guidance
candidate response (at/above threshold, no hard fails -> pass-side),
and, per counterexample template, an authored correct decision
statement (pass-side) with a wrong twin (fail-side). The statements
live in builder_sources/twins.json; they assert each template's
classification and argue from its decisive fact, so the judge is
tested on the discrimination itself — see the committed mapping memo
for the stated limitations.
"""
from __future__ import annotations

import hashlib
import importlib.util
import json
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
EXP = HERE.parent
SUITE = EXP / "data" / "eval-suites" / "blocker-triage"
SOURCES = SUITE / "builder_sources"


def sha(p: Path) -> str:
    return hashlib.sha256(p.read_bytes()).hexdigest()


def load_pilot_harness():
    """The suite's own deterministic rubric, imported from the data copy."""
    spec = importlib.util.spec_from_file_location(
        "blocker_triage_pilot", SUITE / "run_agent_blocker_triage_pilot.py"
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.score, module.hard_fail


def render_input(spec: dict, variation: str | None = None) -> str:
    inp = spec["input"]
    facts = inp["observed_preflight_facts"]
    lines = [
        "TASK: " + inp["task_context"].strip(),
        "",
        "PLAN CONSTRAINTS AT BASELINE:",
    ]
    lines += [f"- {c}" for c in inp["plan_constraints_at_baseline"]]
    lines += ["", "OBSERVED PREFLIGHT FACTS:"]

    def walk(prefix, v):
        if isinstance(v, dict):
            for k, vv in v.items():
                walk(f"{prefix}{k}.", vv)
        else:
            lines.append(f"- {prefix[:-1]}: {v}")

    walk("", facts)
    if variation:
        lines += ["", f"VARIATION FOR THIS CASE: {variation}"]
    return "\n".join(lines)


def main() -> None:
    criteria_text = (SOURCES / "criteria.txt").read_text()
    twins = json.loads((SOURCES / "twins.json").read_text())
    tpl = json.loads((SOURCES / "templates.json").read_text())
    harness_score, harness_hard_fail = load_pilot_harness()

    spec = yaml.safe_load((SUITE / "agent-blocker-triage-v0.1.yaml").read_text())
    counter = yaml.safe_load(
        (SUITE / "agent-blocker-triage-counterexamples-v0.1.yaml").read_text()
    )["cases"]
    candidate_text = (SUITE / "agent-blocker-triage-candidate-v0.1.md").read_text()
    guidance_text = (SUITE / "agent-blocker-triage-guidance-v0.1.md").read_text()
    baseline_text = spec["input"]["observed_agent_response"]

    gt = {
        "baseline": {**harness_score(baseline_text),
                     "hard_failures": harness_hard_fail(baseline_text)},
        "candidate": {**harness_score(candidate_text),
                      "hard_failures": harness_hard_fail(candidate_text)},
        "pass_threshold": spec["scoring"]["pass_threshold"],
        "total_points": spec["scoring"]["total_points"],
    }
    assert gt["baseline"]["score"] < gt["pass_threshold"], gt["baseline"]
    assert gt["candidate"]["score"] >= gt["pass_threshold"], gt["candidate"]
    assert not gt["candidate"]["hard_failures"]
    (SUITE / "ground_truth.json").write_text(json.dumps(gt, indent=2) + "\n")

    base_input = render_input(spec)
    templates = {t["id"]: t for t in spec["counterexample_templates"]}
    expected = {c["id"]: c["classification"] for c in counter}

    valset = [
        {
            "id": tpl["id_baseline"],
            "input": base_input,
            "response": baseline_text,
            "expected_side": "fail",
            "expected_basis": tpl["basis_baseline"].format(
                score=gt["baseline"]["score"],
                total=gt["total_points"],
                threshold=gt["pass_threshold"],
            ),
        },
        {
            "id": tpl["id_candidate"],
            "input": base_input,
            "response": candidate_text,
            "expected_side": "pass",
            "expected_basis": tpl["basis_candidate"].format(
                score=gt["candidate"]["score"],
                total=gt["total_points"],
            ),
        },
    ]
    for cid in sorted(templates):
        correct, wrong = twins[cid]
        inp = render_input(spec, templates[cid]["change"])
        valset.append({
            "id": f"counterexample-{cid}-correct",
            "input": inp,
            "response": correct,
            "expected_side": "pass",
            "expected_basis": tpl["basis_counterexample_correct"].format(
                cid=cid, classification=expected[cid]
            ),
        })
        valset.append({
            "id": f"counterexample-{cid}-wrong",
            "input": inp,
            "response": wrong,
            "expected_side": "fail",
            "expected_basis": tpl["basis_counterexample_wrong"].format(
                cid=cid, classification=expected[cid]
            ),
        })

    trainset = []
    for i, cond in enumerate(spec["hard_fail_conditions"]):
        trainset.append({
            "id": f"train-hardfail-{i+1}",
            "input": base_input,
            "response": tpl["train_hardfail_response"].format(cond=cond),
            "feedback": tpl["train_hardfail_feedback"].format(cond=cond),
        })
    seq = spec["expected_action_sequence"]
    trainset.append({
        "id": tpl["id_train_sequence"],
        "input": base_input,
        "response": tpl["train_sequence_response_prefix"] + " ".join(
            tpl["train_sequence_step"].format(n=n + 1, step=s)
            for n, s in enumerate(seq)
        ),
        "feedback": tpl["train_sequence_feedback"],
    })
    for i, line in enumerate(
        [ln.strip() for ln in guidance_text.splitlines() if ln.strip().startswith(tuple("12345"))]
    ):
        trainset.append({
            "id": f"train-guidance-{i+1}",
            "input": base_input,
            "response": tpl["train_guidance_response"].format(line=line),
            "feedback": tpl["train_guidance_feedback"],
        })

    (SUITE / "judge_criteria.txt").write_text(criteria_text + "\n")
    (SUITE / "gepa_valset.json").write_text(json.dumps(valset, indent=2) + "\n")
    (SUITE / "gepa_trainset.json").write_text(json.dumps(trainset, indent=2) + "\n")
    lock = {
        "valset_sha256": sha(SUITE / "gepa_valset.json"),
        "valset_items": [v["id"] for v in valset],
        "locked_at": tpl["lock_date"],
        "note": tpl["lock_note"],
        "sources": {
            "suite_yaml_sha256": sha(SUITE / "agent-blocker-triage-v0.1.yaml"),
            "counterexamples_sha256": sha(SUITE / "agent-blocker-triage-counterexamples-v0.1.yaml"),
            "candidate_sha256": sha(SUITE / "agent-blocker-triage-candidate-v0.1.md"),
            "guidance_sha256": sha(SUITE / "agent-blocker-triage-guidance-v0.1.md"),
            "pilot_runner_sha256": sha(SUITE / "run_agent_blocker_triage_pilot.py"),
        },
        "suite_status": spec["status"],
        "suite_id": spec["id"],
    }
    (SUITE / "valset.lock.json").write_text(json.dumps(lock, indent=2) + "\n")
    print("baseline harness score:", gt["baseline"]["score"], "| candidate:", gt["candidate"]["score"])
    print("valset items:", len(valset), "| trainset items:", len(trainset))
    print("valset sha256:", lock["valset_sha256"])


if __name__ == "__main__":
    main()
