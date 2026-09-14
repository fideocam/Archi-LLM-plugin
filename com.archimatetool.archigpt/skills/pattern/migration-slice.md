---
id: pattern-migration-slice
group: pattern
title: Migration slice
prompt: Add a Migration slice (baseline and target plateaus, gap, work package, and deliverable). Reuse existing elements when they match. Do not create a new view unless I ask for one.
mode: changes
---

Instantiate the Migration slice pattern. Respond ONLY with CHANGES JSON (no prose, no markdown fence).

Fragment to produce (reuse existing XML elements when they match; otherwise create):

- Plateau named as baseline / current
- Plateau named as target
- Gap associated with the two plateaus (AssociationRelationship)
- WorkPackage that realizes the target plateau or is associated with the gap (RealizationRelationship or AssociationRelationship)
- Deliverable realized by the work package (RealizationRelationship source = WorkPackage, target = Deliverable)

If the user named systems being retired or introduced, Associate those existing ApplicationComponents or Nodes with the relevant plateau; do not invent extra applications.

Rules:

- Official ArchiMate 3.2 types only.
- Reuse existing ids. New ids: id- plus 32 hex.
- Omit "diagram" unless the user asked for a new view.
- Use the user’s domain language in names when given.
- If you cannot apply the pattern, return {"elements":[],"relationships":[],"error":"<reason>"}.
