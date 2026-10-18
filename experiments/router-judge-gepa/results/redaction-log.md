# REDACTION LOG — preparing the branch for publication

- **Date:** 2026-10-17 · **Directive:** the user's — "Fix B1 and
  redact flagged items too then push to GitHub."
- **Input:** `results/pre-push-audit.md` (Blockers B1/B2, Flags
  F1–F6, with file/line locations). This log records what was
  changed, where, and in what style. **It quotes no redacted
  values** — items are described by role, as the published record
  requires.
- **Publication shape:** local history is NOT rewritten (it remains
  the full private record). Publication is a single squashed commit
  whose tree is this redacted tree, parented on the fork's `main`.
- **Principle applied, uniformly:** authored prose (docs, results
  narratives, and this log) is redacted; **functional literals in
  code/scripts are retained and registered below** (changing them
  would break the code against the existing local store and
  services); exported data artifacts (`loop_state.*`) are redacted
  only where the audit/task named a field, with the retained data
  registered below.

## B1 — suite prose in the dataset builder (FIXED)

- `src/build_blocker_triage.py` rewritten: it now embeds **no**
  suite prose. The judge criteria text, the counterexample decision
  statements, and the expected-basis / feedback templates are read
  at runtime from data-side builder sources
  (`data/eval-suites/blocker-triage/builder_sources/`); the
  deterministic rubric is the suite pilot runner's own scoring
  functions, imported from the data copy (previously replicated in
  code).
- **Proof the pipeline is unbroken:** re-running the fixed builder
  regenerates all five outputs (`judge_criteria.txt`,
  `gepa_valset.json`, `gepa_trainset.json`, `valset.lock.json`,
  `ground_truth.json`) **byte-identical** to the pre-fix outputs;
  the locked valset's sha256 is unchanged.
- `results/blocker-triage-mapping-memo.md`: a dated **correction
  addendum** names the memo's original claim (that suite content
  appears nowhere in the repo) as false for the builder as
  committed, records what was actually embedded, and records the
  fix. The original claim text is left standing; the addendum is
  the correction of record. A parallel correction note was added
  to `results/blocker-triage-calibration.md`.

## B2 — identifier hits (dispositions)

- `results/blocker-triage-calibration.md`: the 12 per-item row
  labels (which named the exam's scenario variations) are replaced
  with neutral labels `item-01` … `item-12`. The mapping to the
  locked valset's item ids is retained only in the data-only
  record (`data/eval-suites/blocker-triage/published_id_map.json`).
  The valset's sha256 stays in the doc (a hash, not content).
- `src/build_dataset.py` provenance constants (4 slug hits):
  **left, with the call logged here.** They are this builder's own
  invented label-provenance vocabulary (they record which source a
  label came from); verified **not** present in the user's
  registry label set (0 matches in the labels snapshot). They are
  not item content, not prose, and not the user's label names.
- Suite ID in `results/blocker-triage-mapping-memo.md`, the
  suite-id constant in `src/build_blocker_triage.py`, and the
  suite-file path constant in
  `consumer/src/router_loop/blocker_triage.clj`: **left** —
  purely structural identifiers (the suite's own id; a path the
  runner reads), registered here as accepted.

## F1 — one real item title, with its router scores

- `PLAN.md` (baseline-numbers passage): the title and its three
  topic scores are replaced with a neutral descriptor ("the
  scope-trade feedback item") and a genericized outcome (the
  top-ranked topic won over the runners-up). A shortened reference
  to the same item in `PLAN.md`'s source table is genericized the
  same way.
- `HANDOFF.md` (decisions of record): the same title, as a bullet
  lead, is replaced with the same neutral descriptor.
- **Title sweep:** beyond the audit's needle floor (which misses
  short titles), all prose files were manually reviewed against
  the feedback/decisions records for any other real item title.
  Result: this was the only real title in the branch; no other
  title was found or redacted.

## F2 — network identifiers → generic

Replaced in prose (`PLAN.md`, `PREFLIGHT.md`, `HANDOFF.md`):

- The console's tailnet hostname → "the Mac's tailnet name"; the
  full console URL → "the console's tailnet URL".
- The Mac's tailnet IP → "the Mac's tailnet IP"; the SSH target
  (account name + IP) → "the Mac's SSH address".
- The proxy service's port, the tunnel's port, and the console's
  port in prose → role descriptions ("the proxy service's local
  port", "the tunnel's loopback address", as context required).

## F3 — Mac filesystem paths → generic

- The router app directory (×3 in `PLAN.md`) → "the router app
  dir".
- The 0.1 checkout path (in `FIT-ASSESSMENT.md`, `HANDOFF.md`,
  `results/structural-mirror-memo.md`) → "the 0.1 checkout on
  the Mac".
- The console app directory and the two console artifact names
  (`HANDOFF.md`) → "the console app dir on the Mac" / "the
  fullstack console artifact" / "the static console artifact".
- The proxy app's configuration path (×2 in `PREFLIGHT.md`,
  including the location of the provider key) → "the proxy
  service's config file on the Mac". (The key's value was never
  in the branch — audit §2.)

## F4 — provider base-URL literal

- `PREFLIGHT.md`: the loopback tunnel URL is described, not
  printed ("the tunnel's loopback base URL"). The two occurrences
  in `consumer/src/router_loop/system.clj` are the functional
  provider registration — retained, registered below.

## F5 — Structural-v1 material beyond class names

- `results/structural-mirror-memo.md` §4: the full relationship-
  type vocabulary (67 predicates, listed verbatim) → a summary
  (count + the kinds represented); the full list remains in the
  local mirror record.
- §5: the read-back samples quoting Living Description body
  excerpts and the provenance source-reference string → removed;
  replaced with a summary (what was read back, that it matched,
  counts) and a pointer to the local-only full set.
- `results/loop_state.json`: the mirror ontology's 340-character
  description field → a short generic label, **in the committed
  JSON only**; a local re-export restores the store's text. (The
  export's remaining data — class URIs/labels, the ontology's
  recorded name, provenance strings — is retained as structure;
  see the register below.)
- `FIT-ASSESSMENT.md`: three short verbatim quotations from a
  0.1 process contract → paraphrased (same invariants, no quoted
  text).

## F6 — the user's private stack names in aggregate → generic (prose)

Applied across all authored prose (docs + results narratives):

- The canonical repo's name → "the canonical 0.1 repo"; its
  ontology base-URI in prose → the `urn:canonical-repo:` form;
  references to the data-side copies' directory name follow the
  same redacted form in prose (the local directory itself is
  unchanged and local-only). The legacy predecessor repo is
  named generically as well.
- The governance system's name → "the governance system".
- The Mac proxy app/service name → "the Mac proxy app" / "the
  Mac proxy service" where used descriptively. (References that
  name actual files in this tree or local data — the tunnel
  script's file name, the provider env file name, the `CLIPROXY_*`
  env var names — are retained; they are identifiers the
  published tree itself contains.)
- The live router database's name → "the live router database";
  the production Windmill workspace's name → "the production
  Windmill workspace".
- Public product names (ORC, Clojure, the Windmill product,
  model names, GitHub) are not the user's private information
  and are unchanged.

## Accepted-functional register (retained in code/data, with reasons)

| Where | What | Why retained |
|---|---|---|
| `scripts/cliproxy-tunnel.sh` | The SSH target (account + tailnet IP) and the port numbers | The script's function: it opens the tunnel to that exact machine and ports. Redacting it would publish a broken script and falsify the record. |
| `consumer/src/router_loop/system.clj` | The provider base-URL literal and port (×2) | The functional provider registration the consumer runs with. |
| `src/build_structural_mirror.py`, `consumer/src/router_loop/structural_mirror.clj` | The mirror ontology's real base-URI, the ontology name and provenance strings the builder writes, the local data directory name | These literals are written into / read from the existing local event store; changing them would diverge the published code from the store it operates on. Prose around them is redacted (F6). |
| Code comments/docstrings in `consumer/`, `src/`, `scripts/` | Descriptive mentions of the proxy service's nickname and of the canonical repo | The same nickname is inseparable from functional identifiers in the same files (script file name, env var names); comment-only redaction would be theater. No hard identifier (IP, hostname, title, credential) appears in any code comment — verified in the gate scan. |
| `results/loop_state.json` / `loop_state.md` | The mirror's class URIs/labels, recorded ontology name, base-URI, provenance strings | Exported store data (the data plane). The one field the audit/task named — the JSON description — is redacted (F5). The `.md` is generated by the same export; its class list is names (structure). |
| `results/blocker-triage-calibration.md` | The locked valset's sha256 | A hash of data-only content; it is the exam's lock reference. |
| `src/build_dataset.py` | The 4 provenance constants (B2) | Builder-invented vocabulary; not the user's label names (verified). |

## The audit file itself

`results/pre-push-audit.md` names, in its findings, several of the
very values redacted above (that was its job). Publishing it
verbatim would re-publish them. Therefore the published tree
carries a **redacted edition** of the audit at the same path: the
same findings, counts, and verdict, with the values replaced by
the same generic forms used here, and one further removal — the
provider key's SHA-256 fingerprint is dropped from the published
edition (a fingerprint of a short value is brute-forceable and is
treated as secret-adjacent). The full audit remains in local
branch history only. This log is the bridge between the two
editions; the publication receipt (local, post-publication)
records the published tree's identity.
