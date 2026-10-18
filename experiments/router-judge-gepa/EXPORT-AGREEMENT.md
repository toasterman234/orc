# Export Agreement — the canonical 0.1 repo → ORC (lab)

- **Status:** AGREED 2026-10-17 (Ben's decision; FIT-ASSESSMENT candidate
  #3, reduced to this lightweight form). This is an agreement, not a
  pipeline: nothing is built under it until a future, separately
  approved movement of material.
- **Authority:** the canonical 0.1 repo is the sole semantic authority
  (its ADR-0002). Anything held on the ORC side is a **lab mirror —
  LAB, not canonical** — and extraction may propose, never canonize
  (0.1 issue #89's principle). The only path back into 0.1 is Ben
  promoting a proposal through 0.1's own gates.

## The agreement

1. **ORC-shaped JSON.** Any future 0.1→ORC movement arrives as one JSON
   export shaped for ORC's own model — `concepts`, `relationships`,
   `provenance` — not as raw authored YAML and not as a 1:1 translation
   of the source formalism (LinkML). Sketch (structure only):

   ```json
   {
     "source":       {"repo": "the canonical 0.1 repo", "commit": "<sha>",
                      "path": "<source path>", "content_sha256": "<hash>",
                      "exported_at": "<iso8601>"},
     "ontology":     {"base_uri": "<urn:...>", "name": "<label>",
                      "stamp": "LAB — not canonical"},
     "concepts":     [{"uri": "...", "label": "...", "description": "...",
                      "provenance": {"kind": "imported",
                                     "source_reference": "<repo @ commit + path>"}}],
     "relationships":[{"source_uri": "...", "target_uri": "...",
                      "predicate": "<an ORC-supported predicate>",
                      "metadata": {"source_reference": "..."}}],
     "losses":       ["<each source construct with no ORC home, named>"]
   }
   ```

2. **Pinned.** Every export names the exact source commit, source path,
   and a content hash of what was read. A mirror that cannot name its
   as-of commit is not refreshed — it is deleted and re-exported.
3. **Losses are in the export.** Constructs the ORC model cannot hold
   (for the Structural-v1 schema: typed slots with ranges, cardinality /
   required, validation constraints, lifecycle transition rules,
   structured Confidence, `field_authority`) are listed under `losses`
   in the export file itself — visible at the doorway, never silently
   dropped and never discovered downstream.
4. **Local-only landing.** Export files and anything derived from them
   live only in the experiment's gitignored `data/` or the local event
   store. Nothing derived from 0.1 content is committed to this (public)
   repo, in any form — including test fixtures and examples.
5. **Stamps travel.** Every concept, relationship, and description
   created from an export carries the provenance stamp (source repo,
   commit, path) and the words "LAB — not canonical" wherever it is
   surfaced (exports, console, reports).
6. **One-way, read-only source.** The source checkout is read over SSH
   and never modified by this experiment. The flow is 0.1 → lab, only.
7. **Deletion is free.** Deleting the entire ORC-side mirror (the
   ontology and its concepts/descriptions, or the whole experiment
   store) is a local, one-command operation with **zero effect** on
   the canonical 0.1 repo, the live router, Windmill, or the governance system. If a
   proposed movement would make deletion costly, it does not happen
   under this agreement.
8. **Refresh = re-export + evolve, never silent drift.** Updates re-run
   the export at a new pinned commit and reconcile counts (in = stored)
   with a written reconciliation note; projections of the source
   (generated status docs, derived graphs) are never mirrored — sources
   only.
