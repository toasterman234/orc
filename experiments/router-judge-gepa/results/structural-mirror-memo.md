# Structural-v1 lab mirror — mapping / loss memo (FIT #2, ORC-native)

- **Date:** 2026-10-17 · **Phase:** 1 of the FIT follow-through (Ben's
  2026-10-17 ORC-native decision, PLAN.md §8; agreement:
  `../EXPORT-AGREEMENT.md`).
- **Source (read-only):** the canonical 0.1 repo, Mac checkout `the 0.1 checkout on the Mac`
  @ `b2b99e880266a3986863dfe8bfe6b4fbb8575764` (branch
  `work/inventory-f10-f11-fix-v0.1`), files `registry/model/schema.yaml`
  (sha256 `392cb6a6739c…`, byte-identical to the HEAD blob) and
  `registry/relationships/registry.yaml` (sha256 `7f33c8f7763f…`).
  Working copies live in gitignored `data/canonical-repo/`; the payload
  and full read-backs live in gitignored `data/structural-mirror/`.
  This memo is structural (names, counts, method) — no schema prose is
  reproduced beyond the short class-purpose samples in §5.
- **Result:** a NEW ontology in the consumer's local store —
  id `4b5548c5-366f-50fd-bcb4-8b2d74b598f4`, base-uri
  `urn:canonical-repo:structural-v1:`, scope `:custom`, named
  "Structural-v1 vocabulary (the canonical 0.1 repo) — LAB MIRROR, not
  canonical". **LLM calls in this phase: 0** (deterministic transform
  `src/build_structural_mirror.py` + lifecycle commands via
  `consumer/src/router_loop/structural_mirror.clj`).

## 1. The count question: 41 classes, not 38

The FIT assessment's headline said "38 classes"; its own enumeration
names 39, "plus Provenance and Confidence". The pinned schema's
`classes:` map defines **41** entries. The mirror follows the schema —
one concept per class, all 41: Entity, Domain, GovernedClassifier,
Goal, Project, Plan, Issue, Actor, Role, Capability, Skill, Tool,
Resource, Claim, Playbook, Process, Policy, Rule, Constraint, Gate,
Decision, Event, ObservationEvent, Evidence, Action, ActionRequest,
Run, Result, Verification, Change, Outcome, AccessGrant,
AuthorityGrant, Source, SourceObject, SourceBinding, EvaluationCase,
Relationship, SemanticBundle, Provenance, Confidence. (Provenance and
Confidence are attribute-only structures with no `is_a`; SemanticBundle
likewise has no parent. Nothing was excluded to force the count to 38.)

## 2. How each source construct was mapped (ORC-native choices)

| Source construct | ORC-native landing |
|---|---|
| Class (41) | One concept in the new ontology: uri `urn:canonical-repo:structural-v1:<Class>`, label = class name, scope `:custom`, deterministic uuid5 concept id |
| Class purpose | Concept `:description` = the schema's own first sentence where one exists (5 classes: Provenance, Confidence, GovernedClassifier, Plan, SemanticBundle); otherwise a derived structural line ("specializes X — see Living Description"), never invented prose |
| Class semantics | One **Living Description** per class, granularity `:tree-class`, target-id = concept id (the same keying ORC's seed tree-classes use). `:summary` carries, as readable text: the LAB stamp + source pin, the purpose, lifecycle/state vocabularies, and all declared properties in prose (`name (range, required, multivalued)`). `:capabilities` lists the class's references; `:representative-uses` lists its state vocabularies; `:avoid-when` carries the not-canonical warning |
| `is_a` (37) | `skos:broader` edge child → parent |
| Declared property with a class range (137 declarations) + relationships-registry predicates (67 over 53 pairs) | One `skos:related` edge per unique class pair; the declaring property names and/or registry predicate names are recorded in the edge's `metadata.source-reference` |
| Lifecycle / status enums (17 classes carry them: Goal, Project, Plan, Issue ×3, Actor, Resource, Playbook, Process, Decision, Source, EvaluationCase, Gate, Run, both Grants, Claim, Tool) | **Text only** — the value list appears in the owning class's description ("State vocabulary via lifecycle (GoalLifecycle): proposed, active, satisfied, abandoned"), explicitly labeled vocabulary-only |
| All 29 enums (188 values) | **No enum-value concepts** (no long tail). Values live as text in the owning classes' descriptions |
| RelationshipType vocabulary (67 types; the enum and the registry agree exactly) | Recorded in full in §4 below and in the Relationship concept's description in the store |
| Provenance | Concept `:provenance {:kind :imported, :source-reference "the canonical 0.1 repo@b2b99e8… registry/model/schema.yaml — LAB MIRROR — not canonical", :created-by …}`. ORC's provenance-kind enum is closed (`human-authored / agent-authored / source-extracted / imported / system-static`) — `:imported` is the honest value; the lab-mirror stamp rides in the source-reference and in the ontology/concept/description texts |

**Two discoveries that shaped the mapping (found live, recorded here):**
1. **ORC projects `skos:related` symmetrically** and its
   create-relationship command refuses a reverse edge as a projected
   duplicate (`:conflict "Relationship already exists"` — first hit on
   Capability→Actor after Actor→Capability existed). 15 class pairs are
   bidirectional in the source (Actor↔Capability, Actor↔Decision,
   Actor↔Resource, Actor↔Run, Claim↔Decision, Decision↔Entity,
   Decision↔Playbook, Entity↔Event, Entity↔Evidence,
   Entity↔Relationship, Entity↔SourceBinding, Event↔SourceObject,
   Issue↔Project, Result↔Run, Source↔SourceObject). They are stored
   **once each**, with both directions' evidence combined in the
   edge metadata: 135 directed pairs → **120 stored `skos:related`
   edges**.
2. **Self-relationships are forbidden** by the command. 30 declared
   self-references (e.g. Plan→Plan `parent_plan_ref`, Claim→Claim
   `supersedes_claim_ref`, the registry's symmetric Entity→Entity
   `relates_to`) have no edge; each is carried as a text line in the
   source concept's description capabilities, and the full list is in
   `data/structural-mirror/detail.json`.

Also: description `:strengths` / `:weaknesses` are **empty** for all 41
— ORC principle entries require a confidence and an evidence count,
and a mirror with zero observed executions has neither; inventing them
would fabricate learning. Inherited properties are edged only on the
class that declares them (Entity); subclasses reach them through the
is_a chain rather than by re-edging (a documented flattening, §3).

## 3. Losses (enumerated, per the export agreement — none silent)

1. **Slots as typed properties.** Property ranges survive only as
   prose in descriptions and as pair-level edges. ORC concepts have no
   property schema: nothing can ask "what is the range of
   `goal_refs`" and get a typed answer from the graph.
2. **Cardinality / multivalued / required.** The flags are printed in
   the description prose (333 declared properties carry them) but are
   not machine-readable and nothing enforces them.
3. **Validation constraints.** None exist in ORC's concept model (the
   read model does not even expose a per-concept lifecycle field).
   The mirror can answer "what exists / what's related / what's
   similar" — never "is this record valid".
4. **Lifecycle transition rules.** States survive as vocabulary text;
   which transitions are legal, under what gates, does not. (The
   source's enforcement lives in its Postgres TransitionController and
   stays there.)
5. **Inheritance semantics.** Beyond the `is_a` edge chain, inheritance
   is flattened: a subclass's description lists only its own declared
   properties (plus a pointer to its parent), not the inherited set.
6. **Structured Confidence.** Confidence exists as a concept (it is a
   schema class), but its structured basis/mode fields are prose, not
   a queryable object.
7. **`field_authority` / merge semantics.** These live in
   `registry/integration/` in the source, not in `schema.yaml`; they
   were not mirrored at all and have no ORC home.
8. **Named relationship predicates.** 67 named predicates (with
   inverses and symmetry flags in the registry) collapse to ORC's
   `skos:related` + the predicate names preserved as metadata text.
   The names survive; their machine-readable semantics do not.
9. **Class descriptions for 36 of 41 classes** do not exist in the
   source schema; the mirror's one-line concept descriptions for them
   are structural derivations (parent + property counts), labeled as
   lab-mirror text — not source prose.

## 4. The relationship-type vocabulary (summary)

67 types, identical in the `RelationshipType` enum and
`registry/relationships/registry.yaml` (which also gives each a
source/target class pair — those pairs fed the §2 edges). The kinds
represented: causal, dependency, authority/governance,
membership/containment, production and use, classification, and
evaluation/verification relations. The full predicate list is
source-schema vocabulary: it is recorded in the local mirror record
(`data/structural-mirror/`) and not reproduced in this published
record.

## 5. Verification (foreground, this pass)

- **Store census on SQLite copies** of `consumer/runtime/events.db`
  (before / after the mirror runs): 1,629 → 2,109 events; the delta is
  exactly +1 `:ontology/ontology-created`, +41
  `:ontology/concept-created`, +41 `:ontology/tree-description-updated`,
  +157 `:ontology/relationship-created`, +240 `:grain/tx` (one per
  command: 1 + 41 + 41 + 157 = 240). **No other event type changed** —
  no executions, no judge activity, no consolidation requests, no
  ColBERT surprises.
- **Read-model reconciliation:** concepts under the new ontology = 41;
  concept-created events for it = 41; relationship-created events for
  it = 157 (37 `skos:broader` + 120 `skos:related`); descriptions
  present for 41/41 classes, each with exactly **1 history version and
  0 claims**. Store-wide concept statistics after:
  `{total 81, by-scope {tree-class 23, behavioral-subtree 17, custom 41}}`.
- **Read-back samples** were taken via `ontology/get-concept-by-uri`
  and `get-description` for three concepts (the Goal,
  GovernedClassifier, and Relationship classes) and the Goal
  description: concept ids, scopes, broader/related projections,
  provenance kind, and description history/claims all matched the
  apply record above (each description at exactly 1 history version,
  0 claims; the Goal description carries the class's specialization,
  declared properties, and lifecycle state vocabulary as prose under
  the lab-mirror banner). The verbatim samples — including
  Living Description body excerpts and the provenance
  source-reference string — are recorded in the local-only full set
  (`data/structural-mirror/verify.json`) and are not reproduced in
  this published record.
- **Idempotency:** the apply ran twice (the first run stopped at the
  symmetric-related discovery, §2); the second run created only the
  missing 96 relationship edges and nothing else — counts above are the
  final, reconciled state.

## 6. Non-actions

Nothing was pushed. the canonical 0.1 repo was read-only throughout (SSH
reads + the file copies in `data/`). No live system was written. The
payload, detail, and full read-backs stay in gitignored `data/`; the
store itself is the local `consumer/runtime/` event store.
