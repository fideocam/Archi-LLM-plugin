---
id: pattern-capability-map
group: pattern
roles: ea
title: Add a capability map slice
prompt: Add a capability-map slice (capability realized by an application and optionally a process). Reuse existing elements when they match. Do not create a new view unless I ask for one.
mode: changes
---

Instantiate the Capability map slice pattern. Respond ONLY with CHANGES JSON (no prose, no markdown fence).

Fragment to produce (reuse existing XML elements when they match; otherwise create):

- One or more Capability elements (a small slice, not a full enterprise map unless the user asked)
- ApplicationComponent that realizes each capability (RealizationRelationship, source = component, target = capability)
- Optional BusinessProcess that realizes the same capability if the user described work, not only systems

Rules:

- Official ArchiMate 3.2 types only. Type is Capability, not BusinessCapability.
- Reuse existing ids. New ids: id- plus 32 hex.
- Omit "diagram" unless the user asked for a new view. Do not duplicate capabilities that already exist.
- Use the user’s domain language in names when given.
- If you cannot apply the pattern, return {"elements":[],"relationships":[],"error":"<reason>"}.
