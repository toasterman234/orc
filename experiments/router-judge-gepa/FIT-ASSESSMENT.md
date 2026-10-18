# FIT ASSESSMENT — parts of the canonical 0.1 repo into ORC

**Date:** 2026-10-17 · **Status:** ASSESSMENT ONLY — nothing was ingested, copied, or integrated.
**Question (Ben's):** what parts of the canonical 0.1 repo are a good fit to add into ORC (in the
context of the isolated router experiment), how would each fit, what to be aware of, and what
ensures a favorable outcome.

**Sources inventoried (both read-only):**
- **the canonical 0.1 repo**, Mac checkout `the 0.1 checkout on the Mac`, HEAD `b2b99e880266a3986863dfe8bfe6b4fbb8575764`
  ("fix(model): required name for Resource + inventory names; register ADR-0002 Graphiti
  decision…", the F10/F11 fix). NOTE: the checkout sits on branch
  `work/inventory-f10-f11-fix-v0.1`, not `main`, and carries untracked dirs (`.planning/`,
  `.venv-terminusdb/`, `experiments/terminusdb/`, `runtime/deployment/zima-cognee-v0.1/`).
  Tracked tree otherwise matches the HEAD commit.
- **ORC** (this repo, local branch `work/router-judge-gepa-experiment` @ `51c8b53`):
  `docs/ONTOLOGY.md`, `docs/ONTOLOGY-MCP.md`, `docs/MCP-SHEET-BUILDER-GUIDE.md`,
  `docs/LIVING-DESCRIPTIONS.md`, `docs/SELF-IMPROVING-LOOP.md`, `docs/COMPONENT-MAP.md`,
  the evolutionary builder interface (`components/ontology/.../interface/evolutionary.clj`),
  the MCP sheet builder interface (`components/mcp-sheet-builder/.../interface.clj`), and
  `results/loop_state.json` (read-only export of the installed loop's store).

**Privacy rule applied throughout:** this file describes 0.1 parts generically (names, kinds,
counts). It contains no personal instance data, no playbook/inventory text, no credentials.
The fork is PUBLIC; anything derived from 0.1 *content* (as opposed to structure) belongs in
the experiment's gitignored `data/` or the local-only experiment store, never in this repo.

---

## Ranked shortlist

| # | 0.1 part | Verdict | Integration mode | Effort |
|---|----------|---------|------------------|--------|
| 1 | Eval suites (`tests/evals/agent-judgment/`, EvaluationCase model) | **Strong** | GEPA trainset/valset + judge criteria source | M |
| 2 | Structural-v1 schema vocabulary (`registry/model/schema.yaml` classes + enums) | **Strong, as mirror only** | Deterministic translation → concepts in a NEW ontology namespace (builder `rdf`/TTL path or `create-concept!`) | M |
| 3 | Canonical IR record shape (`compiler/contracts/canonical-ir.yaml`) | **Strong as contract, not code** | Adopt as the interchange format for any future 0.1→ORC export | S |
| 4 | Evaluation-driven improvement process (`registry/execution/evaluation-driven-improvement.yaml`) | **Partial–strong** | Hand-port as an ORC sheet (behavior tree); its invariants become judge criteria | L |
| 5 | Inventory records (`inventory/system-v0.1.yaml`, `activity-v0.1.yaml`) | **Partial** | Deterministic transform → concepts + relationships in the same new ontology (local store only) | M |
| 6 | Jev question sets (`playbooks/router-replay/jev-questions.json`, question-bank seed) | **Partial (pattern, not content)** | Reuse the typed choice+criteria pattern as ORC judge criteria / `llm-condition` design; Jev runtime itself stays outside ORC | S |
| 7 | Pi profile + prompts + skills (`runtime/agents/pi/`) | **Partial** | Mirror as GEPA seed instructions / Living-Description seed material for agent-behavior sheets | M |
| 8 | Executor / component-onboarding plane (`onboarding/`, `registry/execution/component-onboarding.yaml`) | **Partial, speculative** | If/when capabilities are exposed as MCP servers: MCP Sheet Builder generates workflows from their tools | L |
| 9 | ORC ontology exposed back to Pi (reverse direction) | **Partial** | ORC's own Ontology MCP Server (13 read/exploration tools) as a read surface for Pi | S |
| 10 | Promotion governance package (`promotion/structural-v1/`, `registry/meta/`) | **Keep-out as machinery; adopt as template** | No code path; its matrix/authority-map/acceptance shape governs any future proposal flow | S |
| 11 | Postgres adapter + TransitionController (`adapters/postgres/`) | **Keep-out** | Runtime stays outside ORC; its governed-transition pattern is a design reference only | — |
| 12 | Graphiti feeder scripts (`context/`) | **Poor** | No path — a second derived-projection stack inside ORC duplicates 0.1's own | — |
| 13 | Evidence corpus (`evidence/`, 83 files) | **Poor in bulk** | No bulk ingestion; hand-picked receipts only, as judge-calibration examples | S (if ever) |
| 14 | LikeC4 architecture views (`architecture/`) | **Poor** | No path — 0.1's own projection layer; ORC console is a different surface | — |
| 15 | Experiments tree (`experiments/`, 21 dirs) | **Poor wholesale** | No path; individual experiments are references, not material | — |
| 16 | Router-replay playbook bundle (`playbooks/router-replay/`) | **Keep-out** | Historical artifact of the retired n8n router; superseded taxonomy | — |

---

## Inventory summary of the canonical 0.1 repo

Measured 2026-10-17 at HEAD `b2b99e8` (above). Repo scale: ~950 tracked files across the
top-level areas below (experiments 337 and runtime 174 dominate, much of it working
material rather than canonical content).

**Ontology / semantics — the canonical core.**
`registry/model/schema.yaml` is the Structural-v1 LinkML schema (1,533 lines): **38 classes**
(Entity, Domain, GovernedClassifier, Goal, Project, Plan, Issue, Actor, Role, Capability,
Skill, Tool, Resource, Claim, Playbook, Process, Policy, Rule, Constraint, Gate, Decision,
Event, ObservationEvent, Evidence, Action, ActionRequest, Run, Result, Verification, Change,
Outcome, AccessGrant, AuthorityGrant, Source, SourceObject, SourceBinding, EvaluationCase,
Relationship, SemanticBundle, plus Provenance and Confidence), **~50 slots**, and **~28
enums** — many of them per-class lifecycle enumerations (Goal/Project/Plan/Work/Case/
Resource/Playbook/Process/Decision/Source/EvaluationCase lifecycles, GateStatus, GrantStatus,
ReviewStatus, ClaimCurrentness, ProvenanceMethod, ConfidenceBasis, RelationshipType…).
Around it: `registry/meta/` (semantic invariants, id-prefix registry, type-domain registry,
shared conventions), `registry/relationships/registry.yaml`, `registry/integration/`
(field-authority, merge-semantics), `registry/guidance/guidance-registry.yaml` (a derived,
non-authoritative navigation view contract — explicitly "links to authoritative records;
does not duplicate them"), and `registry/execution/` process contracts (below).
`inventory/system-v0.1.yaml` holds **91 canonical records** (25 of them Relationship
records; by type: Domain 7, SourceObject 8, SourceBinding 8, Resource 8, Rule 5, Issue 5,
Source 4, Plan 4, Actor 4, Tool 2, Project 2, Policy 2, Decision 2, Capability/Gate/
Evidence/Change/ObservationEvent 1 each) and `inventory/activity-v0.1.yaml` 66 more.
`architecture/` is a LikeC4 model + 9 views (governance, learning, liveness, runtime,
onboarding…) — a projection of the semantics, not semantics itself.

**Process contracts (`registry/execution/`).** Four canonical YAML contracts:
`evaluation-driven-improvement.yaml` (status: draft canonical process contract) — a complete
failure→EvaluationCase→baseline→improvement-proposal→regression-verification→resolution
loop with triggers, inputs/outputs typed in schema classes, and hard invariants (a measured
baseline precedes any improvement claim; an unsafe bypass may not be traded against a lower
false-block rate; improvement requires post-change verification);
`meta-governance-playbooks.yaml` (a SystemEvolutionLoop: detect novelty/contradiction →
investigate → propose → evaluate → decide → apply → verify → monitor);
`component-onboarding.yaml`; `document-projection-v0.1.yaml`.

**Playbooks / procedures.** `playbooks/domain-discovery-and-modeling.md` and the
`playbooks/router-replay/` bundle (README, artifact-build brief, two n8n workflow JSONs, and
`jev-questions.json` — typed Jev choice questions with per-option criteria, from the retired
Things-router replay). Small count, mixed currency.

**Adapters / integrations.** Exactly one adapter: `adapters/postgres/` — a Python
PostgresCanonicalStore plus a **TransitionController** implementing governed
compare-and-swap lifecycle transitions over Canonical IR records (expected-revision CAS,
idempotency keys, verification-required errors, read-back mismatch detection, atomic
StateTransitionEvent outbox), with 3 SQL migrations. Everything else integration-shaped
lives in `onboarding/` (Executor gateway record, Pi config-sync record, Directus record, a
component-onboarding template, and the Executor MCP tool-plane plan) and in deployment
folders under `runtime/deployment/` (Graphiti, Kestra, Directus, Cognee, disk-hygiene).

**Compiler / runtime machinery.** `compiler/contracts/canonical-ir.yaml` defines the
Canonical IR record (required: canonical_id, entity_type, schema_ref/version,
primary_domain, properties, relationships, source_bindings, field_authority, provenance,
state_refs, unresolved_refs), `compiler/mappings/linkml-to-ir.yaml` maps LinkML→IR, and
`compiler/reference/compile_ir.py` is the reference compiler. `runtime/` (174 files) is
mostly the Pi agent runtime area: `runtime/agents/pi/` (profile manifests, staged prompts,
drift checker, config sync, an ontology-mapping file) and `runtime/deployment/` recipes.
There is **no XState runtime** in 0.1 — the word appears once, as a referenced formalism in
the Pi ontology-mapping file. Lifecycle enforcement = schema enums + the Postgres
TransitionController.

**Evals / tests.** `tests/` (111 files) is a governed proof suite: LinkML/semantic/IR/
reconciliation/target-round-trip validators, Postgres proof runners, promotion-equivalence
verification, hash-pinned proof inputs. `tests/evals/agent-judgment/` holds the
agent-behavior eval suites — each as a candidate/guidance/counterexamples triple plus a
pilot runner (blocker-triage, diagnostic-blocker, instruction-induced-approval-loop; at
least blocker-triage is marked `pilot_verified` with live-Pi evidence), and
`tests/evals/repository-governance/` holds drift evals. `experiments/` (21 dirs, 337 files)
includes the Pi profile exploration with its E-series evals, a question-bank seed
(`experiments/question-bank-v0.1/question-bank-seed.json`), ontology-change and
TerminusDB/Cognee explorations, and the domain-model workshop (historical provenance for
Structural-v1).

**Context / profile material.** `runtime/agents/pi/prompts/` (AGENTS.md, APPEND_SYSTEM.md,
a supervisor candidate), staged vs live manifests, several imported skills, and
`context/` — Python scripts that feed Ben's sessions/sources into Graphiti (a derived,
non-authoritative context projection on Zima; see the ADR-0002 Graphiti decision — note
there are **two** files named ADR-0002 in `evidence/adr/`: the Graphiti/FalkorDB one and
the legacy-repo-cutover one this assessment's constraints cite).

**Evidence / promotion governance.** `evidence/` (83 files: ADRs, pilots, cutover and
proof records, including explicitly RETRACTED evidence kept for forensic lineage) and
`promotion/structural-v1/` — a complete promotion package (promotion matrix classifying
artifacts canonical/supporting/experimental, authority map, canonical layout,
generated-artifact policy, promotion acceptance with named proof gates and approvals
still required). `status/current.yaml` + `render.py` generate STATUS.md (last reviewed
2026-10-08 — visibly older than the 2026-10-16 HEAD work; the projection lags the tree).

**What ORC currently holds (for contrast):** 2 seed ontologies (tree-class,
behavioral-subtree), 40 concepts, 54 relationships, 73 Living Descriptions with **0 claims**,
6 consolidation requests all unanswered, 0 auto-classified executions, judges + GEPA proven
on synthetic smokes only, MCP Sheet Builder loaded but never called. The store is a blank
lab bench with the safety equipment still in its wrapping.

---

## Per-candidate fit assessments

### 1. Eval suites → GEPA trainset/valset + judge criteria — STRONG (M)
**What it is.** 0.1's agent-judgment evals are scenario files with a precise shape:
`target_behavior` (a principle + an explicit `must_not_generalize_to` anti-pattern),
baseline context/constraints, observed facts, and an expected discrimination (real blocker
vs resolvable constraint, etc.), each tied to a schema EvaluationCase and pilot evidence.
**Why it fits.** This is almost exactly the input ORC's loop eats. The principle maps to a
judge's criteria; `must_not_generalize_to` maps to the judge's adversarial stance /
weakest-first feedback; the scenarios become GEPA reflective examples (Inputs / Generated
Outputs / Feedback) and a scored valset. Unlike every ontology candidate, this flow moves
*behavioral expectations*, not Ben's semantic canon — nothing in ORC becomes a second
source for what the cases mean; 0.1 keeps the cases, ORC borrows them as an exam.
**First concrete step.** Transform ONE suite (blocker-triage, already `pilot_verified` in
0.1) into the experiment's GEPA trainset format + one judge config, as data files only,
and write a field-survival note (which YAML fields have no ORC home). No run yet.
**Caveats.** The suites judge *agent behavior in work scenarios*, not router
classifications — their value is for Pi/agent workflows executed as ORC sheets, not for the
Things router directly. Scenario text is work-context prose; keep it in gitignored `data/`.
Suite statuses vary (pilot vs candidate); only pilot-verified suites should anchor a
valset.

### 2. Structural-v1 schema vocabulary → mirrored concepts — STRONG, as mirror only (M)
**What it is.** The 38 LinkML classes + ~28 enums + relationship types of Structural-v1 —
the *vocabulary* of Ben's semantic system, with no instance data.
**Why it fits.** ORC's ontology is built for exactly this shape: classes and enum values
become concepts (uri + label + scope + provenance), relationship types become typed
relationships, and ORC can export Turtle (`concepts->turtle`). The builder's supported
input formats (csv/json/sql/text/rdf via `build-from-sources`, config `:base-uri`,
`:similarity-threshold`, content-hash source dedupe) or the deterministic
`create-concept!` lifecycle API both reach it. Crucially, translation must be
**deterministic** (a small transformer over the schema YAML), NOT the builder's
LLM-backed extraction — extraction may propose, never canonize, and a deterministic map
is auditable field-by-field.
**First concrete step.** Generate, from the schema at a pinned 0.1 commit, a Turtle/N-Triples
rendering of classes+enums+relationship types under base-uri
`urn:canonical-repo:structural-v1:` and diff-count it (38 classes, N enums) before any
ingest is even proposed.
**Caveats.** This is the candidate where the semantic-mismatch losses concentrate — see
Cross-cutting caveats. The mirror must be stamped as a *proposal-layer snapshot*
(provenance + source SHA on every concept) and versioned per 0.1 commit; the moment anyone
treats ORC's copy as the schema, ADR-0002 is violated. Enum *values* survive; enum
*semantics* (what transitions are legal) do not.

### 3. Canonical IR shape → interchange contract — STRONG as contract (S)
**What it is.** 0.1's compiled record format: canonical_id, entity_type, schema_ref,
properties, relationships, source_bindings, field_authority, provenance, state_refs,
unresolved_refs. 0.1 itself classifies IR as *derived* interchange, not authority — which
is precisely the status an ORC mirror may hold.
**Why it fits.** If anything ever moves 0.1→ORC, it should move as IR-shaped records, not
raw authored YAML: the id convention (`type:uuid` canonical_ids) maps cleanly onto ORC
concept URIs, `relationships` maps onto typed concept relationships, and `provenance`
maps onto ORC's typed concept provenance. Adopting the shape costs nothing and prevents
an ad-hoc export format from being invented later.
**First concrete step.** One page in the experiment plan: "any future 0.1 export arrives
IR-shaped, pinned to a commit, with field_authority preserved in the export file (even
where ORC cannot store it), so losses are visible rather than silent."
**Caveats.** `field_authority`, `state_refs`, and property bags have no ORC concept-model
home (see caveats); the IR file can carry them, the ORC graph cannot. Don't let the
contract's existence be mistaken for a pipeline.

### 4. Evaluation-driven improvement process → ORC sheet — PARTIAL–STRONG (L)
**What it is.** 0.1's canonical process contract for turning observed failures into
EvaluationCases, baselines, minimal improvement proposals, verified changes, and resolved
Cases — with invariants that read like judge criteria.
**Why it fits.** It is, structurally, the same loop ORC implements (execute → judge →
reflect → propose → verify), specified in Ben's own governance language. Ported as a
hand-authored behavior tree (sequence/fallback over llm/code/condition nodes), its
invariants become the sheet's judge configs and gate conditions — e.g. "no improvement
claim without a baseline" is a condition node checking a baseline Run exists; "unsafe
bypass cannot be traded" is an adversarial judge stance. This gives the experiment a
*real* workflow shaped by Ben's governance instead of synthetic smokes.
**First concrete step.** A paper port first: map each contract stage to a tree node and
each invariant to a judge/condition, in the experiment plan, for Ben's review — before
any sheet is built.
**Caveats.** The contract is prose + typed I/O, not executable steps; a port is a design
task per playbook (hence L), and every interpretation choice is a place to accidentally
redefine Ben's process semantics inside ORC. The port must cite the contract version and
be treated as an *implementation proposal*, with 0.1's text remaining the authority.
`meta-governance-playbooks.yaml` (SystemEvolutionLoop) is the same shape one level up —
reference material for the port, not a second port.

### 5. Inventory records → mirrored instances — PARTIAL (M)
**What it is.** The 91 system + 66 activity canonical records: Domains, Actors, Tools,
Resources, Sources, Bindings, Plans, Issues, Rules… with provenance blocks and
lifecycle/health fields.
**Why it partially fits.** Same deterministic route as the schema mirror, one level down:
canonical_ids become URIs, Relationship records become relationships, and the result is a
queryable, embeddable picture of Ben's system *inside the lab* — useful for retrieval
experiments (e.g. "which registered components govern X" answered by ColBERT search) and
as Living-Description targets.
**First concrete step.** None until candidate 2's mirror exists and Ben explicitly taps
instance ingestion; then a one-shot transform of `system-v0.1.yaml` only, into the same
pinned ontology, with a read-back count reconciliation (91 in → 91 concepts + 25
relationships).
**Caveats.** This is the privacy-sensitive candidate: inventory content is Ben's real
infrastructure detail. Local store only, never committed in any derived form, and any
LLM/judge step over it sends blackboard content through the experiment's LLM path
(the Mac proxy service → Codex subscription) — content leaves this VM for Ben's own provider account.
Inventory is also a *snapshot* (authored 2026-10-07); a mirror goes stale silently unless
the freshness stamp travels with it. Property fidelity is low: health/lifecycle/
availability fields flatten into labels or are dropped.

### 6. Jev question sets → judge design pattern — PARTIAL (S for the pattern)
**What it is.** Jev questions are typed judgments: a `choice` question with instructions
and per-option natural-language criteria (the router-replay set classifies personal task
items by kind/domain). 0.1 also carries a question-bank seed experiment and Jev-renderer
fixtures in its component-onboarding tests.
**Why it partially fits.** ORC has no Jev — its `:llm` nodes are free-text and its judges
are structured differently (criteria × stance × scale, discrete 1–5 bands,
evidence-before-score). But the *pattern* transfers directly: a Jev question's option
criteria are exactly the raw material for an ORC judge's criteria or an `llm-condition`'s
instruction, and this experiment's router mapping already treats criteria wording as the
optimizable asset. Jev-the-runtime could only enter a sheet as an external HTTP call from
a code node (the TypeSafe endpoint the live router uses), bypassing ORC's LLM accounting
and the sandbox's JVM-network bridge — possible, but it makes ORC a shell around Jev
rather than a user of it.
**First concrete step.** None for content. If a future sheet needs a typed classification,
write its judge criteria in Jev's question shape (choice + per-option criteria) from the
start — a style rule for the experiment, zero imports.
**Caveats.** The router-replay question *content* is stale for the live system: its domain
taxonomy predates the frozen Registry v1 (22 labels / 12 workflows). Reusing the words
would silently re-import a superseded taxonomy into the lab. Keep the bundle itself out
(candidate 16).

### 7. Pi profile, prompts, skills → seed material — PARTIAL (M)
**What it is.** `runtime/agents/pi/`: the Pi agent's staged prompts (AGENTS.md,
APPEND_SYSTEM.md, supervisor candidate), config manifests with provenance, imported
skills, a drift checker, and an ontology-mapping file relating Pi config to the schema.
**Why it partially fits.** If agent-behavior sheets (candidate 4) or agent evals
(candidate 1) run in ORC, they need instruction strings — and GEPA optimizes exactly
named `:llm` nodes' instruction strings. The profile is the natural *seed* corpus for
such nodes and for Living-Description seeds about node types (e.g. "supervisor" patterns).
**First concrete step.** None standalone. When candidate 1 or 4 is built, pull the specific
prompt fragment a node needs, pinned to its manifest version, as a seed — never the whole
profile.
**Caveats.** Profile text is long-form prose; ORC node instructions are short strings —
wholesale import doesn't type-check conceptually. The profile is itself governed work in
0.1 (agent-config authority experiments, its own drift machinery): a mirror must be
read-only, version-pinned, and never edited on the ORC side. Prompts are also
agent-operating instructions for Ben's systems — local-only handling, same as inventory.

### 8. Executor / onboarding plane → MCP Sheet Builder — PARTIAL, speculative (L)
**What it is.** 0.1 intends Executor Cloud as its preferred MCP/tool plane (#46, still
open), with canonical capability identity/policy remaining in 0.1. ORC's Layer 8 consumes
MCP servers: `connect` (`:stdio` / `:streamable-http`) → `list-tools` → `analyze-tools` →
`generate-sheet` / one-call `build-sheet-from-mcp!` / `build-from-mcp`, generating
behavior trees from tool schemas against built-in patterns.
**Why it partially fits.** This is the only candidate where the integration direction is
the one both systems already chose: tools exposed over MCP, workflows generated from
their schemas. If a 0.1 capability is ever served as an MCP server, ORC can generate
sheets over it without 0.1 semantics moving anywhere.
**First concrete step.** None now. Precondition is 0.1-side: a live MCP endpoint whose
tools are worth sheet-generating. The experiment-side proof already possible today —
point the sheet builder at any trivial MCP server — doesn't need 0.1 at all.
**Caveats.** Depends on #46 maturing; building ORC-side MCP servers to imitate Executor's
plane would duplicate it and split capability identity — exactly what 0.1's design
forbids. Generated sheets are code-shaped proposals: they enter the same review gate as
everything else.

### 9. ORC ontology → Pi (reverse direction) — PARTIAL (S)
**What it is.** ORC ships an Ontology MCP Server (`docs/ONTOLOGY-MCP.md`): 13
read/exploration tools (search-concepts, get-concept, scoped retrieval…) over HTTP,
explicitly read-only — mutation stays on the JVM lifecycle API.
**Why it partially fits.** Pi already consumes MCP tool planes; exposing the *lab's*
ontology as a read surface would let Pi (and Ben, via Pi) inspect what the loop has
learned without any 0.1 content moving into ORC. It is the cheapest cross-system surface
that exists, and it points the safe direction (reads out of the lab, writes nowhere).
**First concrete step.** Stand the server up in the experiment consumer against the
existing store and list its tools — a half-day spike, only if Ben wants Pi-side
visibility into the lab.
**Caveats.** The experiment's JVM networking is sandbox-bridged (see PREFLIGHT §7); an
HTTP server inside that sandbox needs the same care as the outbound bridge. Read-only
does not mean content-free: whatever is in the lab store becomes retrievable, which is
another reason candidates 5/7 content stays lab-local and labeled.

### 10. Promotion governance package — KEEP-OUT as machinery, ADOPT as template (S)
**What it is.** `promotion/structural-v1/` + `registry/meta/`: the matrix/authority-map/
acceptance machinery by which 0.1 promotes proven experiment content into canonical
layout, with explicit approvals and proof gates.
**Why keep out / adopt.** ORC has no promotion machinery to bolt this onto, and 0.1's
governance must not be re-implemented inside the lab (a second, weaker copy of the gate
is worse than no gate). But its *shape* is exactly the governance any future ORC→0.1
proposal should be packaged in: classify (canonical/supporting/experimental), name the
authority home, list proof gates, record approvals. Adopt the paperwork, not the code.
**First concrete step.** When the first real proposal exists (likely from candidate 1 or
4), file it in 0.1 as an Issue/PR in this package's format. Until then, nothing.

### 11. Postgres adapter + TransitionController — KEEP-OUT (—)
**What it is.** 0.1's canonical store: IR persistence + governed lifecycle transitions
(CAS, idempotency, verification-required, read-back) over PostgreSQL.
**Why keep out.** ORC already has its persistence (Grain event store); the experiment's
whole premise is that ORC is a lab bench, not a store of record. Importing a second
canonical store — or worse, pointing ORC at 0.1's — would blur the authority line
ADR-0002 draws and entangle the isolated experiment with live infrastructure. The
*pattern* (no transition without verification + read-back) is worth copying into the
experiment's own promotion discipline; the code is not a candidate.

### 12. Graphiti feeder scripts (`context/`) — POOR (—)
**What it is.** Python that projects 0.1 content + Ben's sessions into Graphiti/FalkorDB
as a derived current-context graph for Pi, under its own ADR-0002 decision.
**Why poor.** ORC already contains a concept graph + embeddings + ColBERT. Rebuilding
0.1's derived projection inside ORC creates a *third* context system competing with both
— the precise multi-source-of-truth failure mode 0.1's architecture exists to prevent.
If the lab ever needs 0.1 context, candidates 2/5 (a pinned, labeled mirror) are the
honest route.

### 13. Evidence corpus — POOR in bulk (S if ever)
**What it is.** 83 files of proof records, pilot results, ADRs, and retracted claims.
**Why poor in bulk.** Evidence is forensic lineage, not knowledge-graph fodder: bulk
ingestion would swamp the shared embedding index (see caveats) with process text and
could surface retracted claims as if current — 0.1's own status doc warns about exactly
that trap. The narrow exception: individual pilot receipts are the natural calibration
companions to candidate 1's eval suites, picked by hand, cited, and kept as files.

### 14. LikeC4 architecture views — POOR (—)
**Why.** A projection layer *of* 0.1 *for* humans, generated from the model. ORC's
console is a different surface for a different system; nothing in the views is material
ORC can execute, judge, or store. If a unified picture is ever wanted, it belongs in
0.1's cockpit/LikeC4 world, fed (maybe) by ORC read models — not the reverse.

### 15. Experiments tree — POOR wholesale (—)
**Why.** 21 experiment dirs are working material at every maturity from sketch to
promoted-and-done; several are already superseded by canonical content counted above.
Cherry-picks are already covered: question-bank seed (candidate 6), Pi-profile evals
(candidates 1/7). The ash-runtime-lab and TerminusDB/Cognee explorations are *candidate
runtime* evaluations — Ben's record treats Ash as a candidate, never approved; nothing
there is ORC material.

### 16. Router-replay playbook bundle — KEEP-OUT (—)
**Why.** It is the retired n8n Things-router replay: historical provenance for how the
router was once evaluated, carrying a superseded taxonomy. The live router's authority is
the frozen Registry v1 in Windmill, and this experiment's mapping is built on that. The
bundle's only live value was extracted in candidate 6 (the Jev question *pattern*).

---

## Cross-cutting caveats

**Two sources of truth, and the #89 rule.** ADR-0002 makes the canonical 0.1 repo the forward
semantic authority; the legacy predecessor repo is historical record; issue #89's principle is that
extraction may *propose*, never *canonize*. Every candidate above is shaped by that rule:
anything ORC holds from 0.1 is a **labeled mirror/proposal with provenance and a pinned
source commit**, and the only path back is Ben promoting it through 0.1's own gates. The
failure mode to design against is not technical — it is a future reader (human or agent)
finding a concept in ORC's store and treating it as Ben's semantics. Provenance-kind and
source-SHA on every mirrored concept, "LAB MIRROR — not canonical" in every export, and no
ORC-side editing of mirrored content are the minimum hygiene.

**The fork is public.** This assessment is safe to commit because it is generic. Nothing
derived from 0.1 *content* — mirrored concepts, inventory instances, prompt text, eval
scenario text — may be committed to this repo, in any form, including test fixtures and
example files. Such material lives in gitignored `data/` or the local-only store. (The
seed corpus already in the store is ORC's own upstream content; that is the only reason
the Loop tab could display it.)

**Namespace / scope design.** ORC supports many ontologies in one store, each with its own
`:ontology-id` and base-uri. 0.1 material must land in a **new ontology per source
version** (e.g. base-uri `urn:canonical-repo:structural-v1:` plus the source commit in
the ontology's provenance), never inside `tree-class:` or `behavioral-subtree:` — those
are ORC's behavior-pattern vocabularies, and mixing Ben's system semantics into them would
corrupt both the loop's pattern retrieval and the mirror's meaning. Scoped queries exist
(hybrid-search is ontology-id-scoped per ONTOLOGY.md), but scoping is not universal — the
docs flag a BFS scoping limitation — so queries must be written scoped-on-purpose, and a
separate *tenant* for 0.1 mirrors is the stronger isolation if the mirror ever grows.

**Semantic mismatch: LinkML → ORC concepts.** What survives translation: class names,
enum names and values, relationship types, labels/descriptions, provenance method, and
id-prefix conventions (as URI prefixes). What does **not** survive: slots as typed
properties with ranges; cardinality / multivalued / required; class inheritance semantics
beyond a flattened relationship; validation constraints of any kind (ORC concepts carry
no schema to validate against — the installed read model does not even expose a lifecycle
field per concept); lifecycle *transition rules* (which states may follow which, under
what gates); Confidence as a structured object (basis/mode) — it flattens to provenance
labels or claims text; field-level authority (`field_authority`) and merge semantics.
Practical consequence: an ORC mirror can answer "what exists and how is it related" and
"find things like this"; it cannot answer "is this record valid" or "may this Work move
from proposed to approved" — those questions must always route back to 0.1/Postgres.
Anyone designing a use case on the mirror must check it only needs the surviving half.

**Jev.** 0.1 uses Jev as a typed-judgment runtime (choice questions, per-option criteria,
served over the TypeSafe API in the Windmill world). ORC's LLM layer is free-text nodes
plus structured judges with a different contract (criteria × stance × discrete scale,
evidence before score, no self-reported floats). There is no Jev runtime path into the
JVM; the transferable asset is the *question design pattern* (candidate 6). Note also that
this experiment deliberately runs its LLM calls through Ben's Codex subscription via the
Mac the Mac proxy service — a Jev call inside a sheet would be a second, unaccounted LLM path.

**Lifecycle machinery.** 0.1's lifecycles are (a) per-class enum sets in the schema and
(b) enforced transitions in the Postgres TransitionController (CAS + verification +
read-back + idempotency). ORC behavior trees have *node execution statuses*, and its
ontology has an ontology-level lifecycle record — but concepts have no lifecycle state
and nothing enforces transitions between concept states. If lifecycle information is
mirrored, it can only be *described* (claims on Living Descriptions), never *enforced*.
Enforcement stays where it lives today: 0.1's store and the governance system. Do not let a demo
imply ORC "runs" 0.1 lifecycles.

**Shared embedding index noise.** The lab store's semantic search spans the tenant.
Adding hundreds of 0.1 concepts beside ORC's 40 seed concepts means pattern retrieval for
the self-improving loop can start surfacing Ben's system concepts as candidate *behavior
patterns*, and vice versa. Mitigations: separate ontology-ids + scoped queries (above),
separate tenant if needed, and keeping bulk low-signal text (evidence corpus, candidate
13) out entirely. This is a correctness issue for the loop, not just tidiness: the
consolidator retrieves by similarity.

**Versioning / drift.** 0.1 moves: the checkout inventoried here is itself a work branch
tip, and canonical content lands by PR. A mirror without a freshness discipline becomes
a liar within weeks. The builder already provides the honest primitives — source
registration with SHA-256 dedupe (`register-source`, `check-source-processed`), and
`evolve` for incremental updates that prefer existing URIs. Any mirror must record
(source commit, source path, content hash, mirrored-at) per batch, re-run as evolve (not
fresh builds) on update, and display its as-of commit wherever it is surfaced. Mirror
**sources** (`registry/`, `inventory/`), never **projections** (STATUS.md is generated and
already lags; Graphiti is derived; `.generated/` is disposable by policy).

**Layer maturity risk (ORC side).** The parts of ORC these candidates touch are not
equally proven *in this install*: judges and GEPA ran end-to-end on synthetic smokes;
the ontology read paths and seed corpus are verified; but the self-improving loop's
consolidation has never completed a cycle here (6 requests, 0 completions) and ORC
itself treats behavior promotion as alpha. Candidates that depend only on judges/GEPA/
ontology-reads (1, 2, 3, 9) stand on proven ground; anything that requires the loop to
*learn* from 0.1 material (the ambitious readings of 4, 5, 7) inherits the alpha risk
and should be sequenced last, behind a consolidation-completion proof that has nothing
to do with 0.1.

**Effort realism.** Every "ingest" candidate hides a transformer + a reconciliation
report + a freshness mechanism behind its S/M/L. The transformer is the small part; the
reconciliation (counts in = counts stored, losses enumerated) is what makes a mirror
trustworthy, and it is also 0.1's own house style (its proof suite is built on exactly
that discipline) — budget for it.

---

## Ensuring a favorable outcome

**Recommended sequencing.**
1. **Eval-suite pilot (candidate 1).** One pilot-verified suite → GEPA trainset + judge
   config as data files, then one bounded run. Zero personal-data ingestion, zero
   ontology commitment, and it tests the one value exchange both systems are actually
   built for: 0.1 says what good behavior is; ORC's loop measures and tunes toward it.
2. **Schema-vocabulary mirror (candidates 2 + 3).** Deterministic, IR-shaped,
   classes/enums/relationship-types only, pinned to a commit, in a new ontology. Produces
   the field-survival/loss report that every later decision depends on.
3. **Process-contract port, on paper (candidate 4).** Stage/invariant → node/judge mapping
   for Ben's review. Build the sheet only if the paper port survives his read.
4. **Instance mirror (candidate 5) and profile seeds (candidate 7)** — only behind Ben's
   explicit tap, only after 1–2 prove out, local-only forever.
5. **MCP directions (candidates 8, 9)** — when their preconditions exist (a live MCP
   plane worth generating from; a Ben request for Pi-side lab visibility).

**Proof-of-fit milestone.** A single scored ORC run of one 0.1 eval suite whose result
Ben can compare line-by-line against that suite's existing 0.1 pilot evidence — produced
with zero writes outside the experiment store and zero 0.1 content committed anywhere.
If that milestone lands, the schema mirror is worth its M. If it doesn't, nothing else
on the list should start.

**Success criteria.** (i) The milestone run reproduces the suite's expected
discriminations at a rate Ben accepts as signal, with judge evidence he can audit.
(ii) For any mirror: reconciliation is exact (counts match; every loss is on the loss
list, none discovered later). (iii) Every ORC-side artifact derived from 0.1 carries
provenance + source commit and the words "not canonical". (iv) Deleting the entire
ORC-side mirror is a one-command operation with zero effect on 0.1, the router, or the governance system.
(v) Ben's review gates were the only promotion path used — ORC proposed, 0.1 disposed.

**Kill criteria — what would make this a bad idea.** Stop if: a use case turns out to
need the semantics that don't survive translation (validation, lifecycle enforcement,
field authority) *inside* ORC — that need means the work belongs in 0.1 or the governance system, not the lab;
if the consolidation loop cannot complete cycles reliably enough for learned
descriptions to be evidence rather than noise; if mirror freshness cannot be kept honest
at a cost lower than the mirror's value; if keeping the public fork clean requires
heroics (it should require only the data/ rule); or if any step starts needing ORC to be
treated as canonical "just this once" — that is the ADR-0002 violation arriving in
disguise, and it ends the experiment's premise.

**Governance shape.** Ben approves every stage transition above. ORC-side artifacts stay
in the lab (this repo's local branch + the local store). Anything the lab produces that
deserves to live — a tuned instruction, a proposed concept, a process-port design — goes
back as a proposal into 0.1's own flow (an Issue/PR there, packaged per candidate 10's
promotion format) and is promoted or rejected by 0.1's gates, never by the lab. The live
router, Windmill, and the governance system are untouched by all of it, at every stage.

---

## What was NOT done

Assessment only. No content from the canonical 0.1 repo was ingested, extracted, or copied into ORC, the
experiment store, or this repository (beyond the generic structural descriptions above).
No files in the canonical 0.1 repo were modified (read-only SSH reads only; the checkout's
branch/untracked state is as found). No ORC code was changed; the loop store was only
read (via the pre-existing `results/loop_state.json`). Nothing was pushed anywhere.
