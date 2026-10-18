#!/usr/bin/env python3
"""Build the ORC-native Structural-v1 lab-mirror payload (FIT #2, Ben's
2026-10-17 ORC-native decision — PLAN.md §8).

Deterministic transform, NO LLM: reads the master-repo-0.1 LinkML schema
+ relationships registry from the gitignored data/ copies (pinned commit
b2b99e880266a3986863dfe8bfe6b4fbb8575764, byte-verified against the Mac
checkout) and emits an ORC-shaped payload (EXPORT-AGREEMENT.md form):

  data/structural-mirror/payload.json  — ontology + concepts + living
      description bodies + relationship edges for the Clojure runner
      (router-loop.structural-mirror). Content-bearing; data/ only.
  data/structural-mirror/detail.json   — full working detail for the
      mapping/loss memo (property tables, edge provenance, inventories).

Native modeling choices (recorded in results/structural_mirror_memo.md):
  * one rich concept per schema class; semantics ride in the concept
    description + a Living Description body (:summary prose carries
    purpose, lifecycle states as text, notable slots in prose) — the
    LinkML formalism itself (typed slots, cardinality, required,
    validation, transition rules) is NOT translated; it is listed as
    loss in the memo.
  * no enum-value concept long tail; enum values live as text inside
    the owning class's description (state vocabularies) and, for
    RelationshipType, inside the Relationship concept.
  * edges only where the source declares them:
      is_a                              -> skos:broader
      declared property with class range -> skos:related (one edge per
                                            unique source->target pair,
                                            properties named in metadata)
      relationships-registry predicate  -> folded into the same pair
        (source class -> target class)     edge's metadata; pairs with
                                            no schema-property edge get
                                            their own skos:related edge
    Self-references cannot be ORC edges (the create-relationship
    command forbids them); they are recorded in detail + carried in the
    source concept's description prose.
  * inherited properties are edged only on the class that declares
    them (Entity); subclasses reach them through the is_a chain.
"""
from __future__ import annotations

import hashlib
import json
import re
import uuid
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
EXP = HERE.parent
DATA = EXP / "data"
OUT = DATA / "structural-mirror"

SOURCE_COMMIT = "b2b99e880266a3986863dfe8bfe6b4fbb8575764"
SOURCE_SHORT = "b2b99e8"
BASE_URI = "urn:master-repo-0.1:structural-v1:"
STAMP = "LAB MIRROR — not canonical"
NS = uuid.NAMESPACE_URL


def uid(name: str) -> str:
    return str(uuid.uuid5(NS, name))


def first_sentence(text: str) -> str:
    text = " ".join((text or "").split())
    m = re.split(r"(?<=[.!?])\s+", text)
    return m[0] if m else text


def prop_table(cls_name, cls, slots):
    """Own declared properties of a class: slots (resolved through the
    top-level slot definitions, slot_usage applied) + attributes."""
    props = {}
    for sl in cls.get("slots") or []:
        d = dict(slots.get(sl) or {})
        usage = (cls.get("slot_usage") or {}).get(sl) or {}
        d.update({k: v for k, v in usage.items() if v is not None})
        props[sl] = {
            "range": d.get("range") or "string",
            "required": bool(d.get("required")),
            "multivalued": bool(d.get("multivalued")),
        }
    for name, a in (cls.get("attributes") or {}).items():
        a = a or {}
        props[name] = {
            "range": a.get("range") or "string",
            "required": bool(a.get("required")),
            "multivalued": bool(a.get("multivalued")),
        }
    return props


def prop_prose(name, p):
    flags = []
    if p["required"]:
        flags.append("required")
    if p["multivalued"]:
        flags.append("multivalued")
    return f"{name} ({p['range']}{', ' + ', '.join(flags) if flags else ''})"


def main() -> None:
    schema_path = DATA / "master-repo-0.1" / "schema.yaml"
    registry_path = DATA / "master-repo-0.1" / "relationships-registry.yaml"
    schema_sha = hashlib.sha256(schema_path.read_bytes()).hexdigest()
    registry_sha = hashlib.sha256(registry_path.read_bytes()).hexdigest()
    schema = yaml.safe_load(schema_path.read_text())
    registry = yaml.safe_load(registry_path.read_text())

    classes = schema["classes"]
    slots = schema.get("slots") or {}
    enums = schema.get("enums") or {}
    predicates = registry["relationship_registry"]["predicates"]

    ontology_id = uid(f"{BASE_URI}@{SOURCE_COMMIT}")
    stamp_ref = (
        f"master-repo-0.1@{SOURCE_COMMIT} registry/model/schema.yaml — {STAMP}"
    )

    concepts = []
    descriptions = []
    detail_classes = {}
    # pair -> {"props": [...], "registry": [...]}
    related_pairs: dict[tuple[str, str], dict[str, list[str]]] = {}
    self_refs: dict[str, list[str]] = {}
    isa_edges = []

    for name in classes:
        cls = classes[name] or {}
        props = prop_table(name, cls, slots)
        uri = f"{BASE_URI}{name}"
        cid = uid(uri)
        parent = cls.get("is_a")
        schema_desc = (cls.get("description") or "").strip()

        class_refs = {k: v["range"] for k, v in props.items() if v["range"] in classes}
        enum_refs = {k: v["range"] for k, v in props.items() if v["range"] in enums}

        # edges from declared class-ranged properties (computed first:
        # the concept's capabilities text carries any self-references)
        for prop_name, target in sorted(class_refs.items()):
            if target == name:
                self_refs.setdefault(name, []).append(
                    f"references itself via declared property {prop_name} "
                    f"(self-reference — carried as text; ORC forbids "
                    f"self-relationships, so no edge exists)"
                )
            else:
                related_pairs.setdefault((name, target), {"props": [], "registry": []})[
                    "props"
                ].append(prop_name)
        state_vocabs = {
            k: (list(((enums[v] or {}).get("permissible_values") or {}).keys()), v)
            for k, v in enum_refs.items()
        }

        # concept one-line description
        if schema_desc:
            concept_desc = first_sentence(schema_desc)
        elif parent:
            concept_desc = (
                f"Structural-v1 class {name} (specializes {parent}). "
                f"Lab mirror concept — see its Living Description for the "
                f"mirrored semantics; the source schema is authoritative."
            )
        else:
            concept_desc = (
                f"Structural-v1 class {name}. Lab mirror concept — see its "
                f"Living Description for the mirrored semantics; the source "
                f"schema is authoritative."
            )

        # --- Living Description summary (readable semantics) ---
        parts = [
            f"{STAMP}. Structural-v1 class {name}, mirrored deterministically "
            f"from master-repo-0.1 registry/model/schema.yaml @ {SOURCE_SHORT} "
            f"(pinned commit {SOURCE_COMMIT}); master-repo-0.1 is the "
            f"authority for this vocabulary."
        ]
        if schema_desc:
            parts.append(" ".join(schema_desc.split()))
        else:
            bits = []
            if parent:
                bits.append(f"specializes {parent} (is_a)")
            if cls.get("abstract"):
                bits.append("abstract class in the source schema")
            bits.append(
                f"declares {len(props)} properties of its own "
                f"({len(class_refs)} referencing other classes, "
                f"{len(enum_refs)} enum-valued)"
            )
            parts.append(f"Class {name}: " + "; ".join(bits) + ".")
        if parent:
            parts.append(f"Inheritance: is_a {parent}.")
        for prop_name, (values, enum_name) in state_vocabs.items():
            parts.append(
                f"State vocabulary via {prop_name} ({enum_name}): "
                + ", ".join(values)
                + ". (Vocabulary only — the source schema's lifecycle "
                "transition rules are not modeled in ORC.)"
            )
        if props:
            parts.append(
                "Declared properties: "
                + "; ".join(prop_prose(k, props[k]) for k in sorted(props))
                + "."
            )
        if name == "Relationship":
            rt_values = list(
                ((enums["RelationshipType"] or {}).get("permissible_values") or {}).keys()
            )
            parts.append(
                f"Relationship-type vocabulary ({len(rt_values)} types, from the "
                f"RelationshipType enum and registry/relationships/registry.yaml, "
                f"which agree exactly): " + ", ".join(rt_values) + "."
            )
        if name == "Entity":
            parts.append(
                "Note: in the source schema every other Entity subclass "
                "inherits Entity's declared properties; this mirror edges "
                "them only on Entity, and subclasses reach them through "
                "the is_a chain."
            )
        summary = " ".join(parts)

        capabilities = []
        if parent:
            capabilities.append(f"specializes {parent} via is_a")
        for prop_name in sorted(class_refs):
            capabilities.append(
                f"references {class_refs[prop_name]} via declared property {prop_name}"
            )
        for src_name in self_refs.get(name, []):
            capabilities.append(src_name)

        representative_uses = []
        for prop_name, (values, enum_name) in sorted(state_vocabs.items()):
            representative_uses.append(
                f"{enum_name} states via {prop_name}: " + ", ".join(values)
            )

        avoid_when = [
            "Do not treat this lab mirror as canonical: master-repo-0.1 "
            "(registry/model/schema.yaml) is the authority. This concept "
            "cannot answer validity, cardinality, or lifecycle-transition "
            "questions — those live only in the source schema and its "
            "governed stores."
        ]
        if cls.get("abstract"):
            avoid_when.append(
                "Abstract in the source schema — a category, not something "
                "to instantiate directly."
            )

        concepts.append(
            {
                "concept_id": cid,
                "uri": uri,
                "label": name,
                "description": concept_desc,
                "scope": "custom",
                "broader": [],
                "indicators": [],
                "provenance": {
                    "kind": "imported",
                    "source_reference": stamp_ref,
                    "created_by": "orc-router-experiment structural-mirror (deterministic transform, no LLM)",
                },
            }
        )
        descriptions.append(
            {
                "target_id": cid,
                "class": name,
                "body": {
                    "capabilities": capabilities,
                    "strengths": [],
                    "weaknesses": [],
                    "representative-uses": representative_uses,
                    "avoid-when": avoid_when,
                    "summary": summary,
                    "version": 1,
                    "consolidated-from-event-count": 0,
                },
            }
        )

        if parent:
            isa_edges.append(
                {
                    "relationship_id": uid(f"{BASE_URI}{name} skos:broader {parent}"),
                    "source_uri": uri,
                    "target_uri": f"{BASE_URI}{parent}",
                    "predicate": "skos:broader",
                    "source_reference": f"LinkML is_a — registry/model/schema.yaml (master-repo-0.1@{SOURCE_SHORT})",
                }
            )
        detail_classes[name] = {
            "is_a": parent,
            "abstract": bool(cls.get("abstract")),
            "has_schema_description": bool(schema_desc),
            "properties": {k: props[k] for k in sorted(props)},
            "class_refs": class_refs,
            "enum_refs": enum_refs,
        }

    # registry predicates folded into pair edges
    registry_pairs = {}
    for pred, spec in predicates.items():
        src, tgt = spec.get("source"), spec.get("target")
        if src == tgt:
            self_refs.setdefault(src, []).append(
                f"relationship-registry predicate {pred} targets its own "
                f"class (self-pair — carried as text; no ORC edge)"
            )
            registry_pairs.setdefault((src, tgt), []).append(pred)
            continue
        registry_pairs.setdefault((src, tgt), []).append(pred)
        related_pairs.setdefault((src, tgt), {"props": [], "registry": []})[
            "registry"
        ].append(pred)

    def pair_bits(src, info):
        bits = []
        if info["props"]:
            bits.append(
                f"LinkML declared properties on {src}: {', '.join(sorted(info['props']))}"
            )
        if info["registry"]:
            bits.append(
                f"relationship-registry predicates: {', '.join(sorted(info['registry']))}"
            )
        return "; ".join(bits)

    # ORC projects skos:related symmetrically and refuses a reverse edge
    # as a projected duplicate, so bidirectional pairs are stored ONCE
    # (one edge per unordered pair) with both directions' evidence in
    # the metadata. Unidirectional pairs keep their declared direction.
    related_edges = []
    merged_pairs = []
    seen_unordered = set()
    for (src, tgt), info in sorted(related_pairs.items()):
        key = frozenset((src, tgt))
        if key in seen_unordered:
            continue
        seen_unordered.add(key)
        reverse = related_pairs.get((tgt, src))
        if reverse:
            merged_pairs.append(f"{src}<->{tgt}")
            related_edges.append(
                {
                    "relationship_id": uid(f"{BASE_URI}skos:related {src} <> {tgt}"),
                    "source_uri": f"{BASE_URI}{src}",
                    "target_uri": f"{BASE_URI}{tgt}",
                    "predicate": "skos:related",
                    "source_reference": (
                        f"{src}->{tgt}: {pair_bits(src, info)}; "
                        f"{tgt}->{src}: {pair_bits(tgt, reverse)} "
                        f"(master-repo-0.1@{SOURCE_SHORT}; stored once — ORC "
                        f"projects skos:related symmetrically)"
                    ),
                }
            )
        else:
            related_edges.append(
                {
                    "relationship_id": uid(f"{BASE_URI}{src} skos:related {tgt}"),
                    "source_uri": f"{BASE_URI}{src}",
                    "target_uri": f"{BASE_URI}{tgt}",
                    "predicate": "skos:related",
                    "source_reference": pair_bits(src, info)
                    + f" (master-repo-0.1@{SOURCE_SHORT})",
                }
            )

    payload = {
        "source": {
            "repo": "master-repo-0.1",
            "commit": SOURCE_COMMIT,
            "schema_path": "registry/model/schema.yaml",
            "schema_sha256": schema_sha,
            "registry_path": "registry/relationships/registry.yaml",
            "registry_sha256": registry_sha,
            "stamp": STAMP,
        },
        "ontology": {
            "ontology_id": ontology_id,
            "name": "Structural-v1 vocabulary (master-repo-0.1) — LAB MIRROR, not canonical",
            "scope": "custom",
            "base_uri": BASE_URI,
            "description": (
                f"{STAMP}. Deterministic ORC-native mirror of the "
                f"master-repo-0.1 Structural-v1 class vocabulary "
                f"(registry/model/schema.yaml @ {SOURCE_COMMIT}), created "
                f"under the 2026-10-17 export agreement for lab use only. "
                f"master-repo-0.1 remains the sole authority; deleting "
                f"this ontology has zero effect on it."
            ),
        },
        "concepts": concepts,
        "descriptions": descriptions,
        "relationships": isa_edges + related_edges,
    }

    detail = {
        "source": payload["source"],
        "counts": {
            "classes": len(classes),
            "concepts": len(concepts),
            "descriptions": len(descriptions),
            "isa_edges": len(isa_edges),
            "directed_related_pairs": len(related_pairs),
            "bidirectional_pairs_merged": len(merged_pairs),
            "related_edges": len(related_edges),
            "relationships_total": len(isa_edges) + len(related_edges),
            "self_references_carried_as_text": sum(len(v) for v in self_refs.values()),
            "registry_predicates": len(predicates),
            "registry_unique_pairs": len(registry_pairs),
            "enums": len(enums),
            "slots": len(slots),
        },
        "classes": detail_classes,
        "self_refs": self_refs,
        "related_pairs": {
            f"{s}->{t}": v for (s, t), v in sorted(related_pairs.items())
        },
        "merged_bidirectional_pairs": merged_pairs,
        "enums": {
            name: list(((e or {}).get("permissible_values") or {}).keys())
            for name, e in enums.items()
        },
    }

    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "payload.json").write_text(json.dumps(payload, indent=2) + "\n")
    (OUT / "detail.json").write_text(json.dumps(detail, indent=2) + "\n")
    print(json.dumps(detail["counts"], indent=2))
    print("ontology_id:", ontology_id)


if __name__ == "__main__":
    main()
