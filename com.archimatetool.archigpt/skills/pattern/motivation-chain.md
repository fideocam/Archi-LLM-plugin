---
id: pattern-motivation-chain
group: pattern
title: Motivation chain
prompt: Add a Motivation chain (stakeholder, driver, goal, requirement, realized by a core element). Reuse existing elements when they match. Do not create a new view unless I ask for one.
mode: changes
---

Instantiate the Motivation chain pattern. Respond ONLY with CHANGES JSON (no prose, no markdown fence).

Fragment to produce (reuse existing XML elements when they match; otherwise create):

- Stakeholder
- Driver associated with the stakeholder (AssociationRelationship)
- Goal associated with or influenced by the driver (AssociationRelationship or InfluenceRelationship)
- Requirement that realizes or is associated with the goal (RealizationRelationship from requirement to goal is not ArchiMate-typical; prefer Goal realized by Requirement: RealizationRelationship source = Requirement, target = Goal — in ArchiMate a Requirement realizes a Goal)
- One core element (BusinessProcess, ApplicationComponent, or Capability already in the model if present) that realizes the requirement (RealizationRelationship source = core element, target = Requirement)

ArchiMate: Requirement realizes Goal; core element realizes Requirement.

Rules:

- Official ArchiMate 3.2 types only.
- Reuse existing ids. New ids: id- plus 32 hex.
- Omit "diagram" unless the user asked for a new view.
- Use the user’s domain language in names when given.
- If you cannot apply the pattern, return {"elements":[],"relationships":[],"error":"<reason>"}.
