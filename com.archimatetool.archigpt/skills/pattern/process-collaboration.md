---
id: pattern-process-collaboration
group: pattern
title: Add process collaboration
prompt: Add a process-collaboration fragment for this slice (process, role or actor, business object, and triggering or flow). Reuse existing elements when they match. Do not create a new view unless I ask for one.
mode: changes
---

Instantiate the Process collaboration pattern. Respond ONLY with CHANGES JSON (no prose, no markdown fence).

Fragment to produce (reuse existing XML elements when they match; otherwise create):

- BusinessProcess (the main process; you may add 1–2 related processes if the user described a chain)
- BusinessRole and/or BusinessActor
- AssignmentRelationship from role or actor to the process (source = role/actor, target = process)
- BusinessObject accessed by the process (AccessRelationship)
- TriggeringRelationship or FlowRelationship between process steps if more than one process is included

Rules:

- Official ArchiMate 3.2 types only. Do not use type Process; use BusinessProcess. Do not use Actor; use BusinessActor.
- Reuse existing ids. New ids: id- plus 32 hex.
- Omit "diagram" unless the user asked for a new view. Do not add application or technology elements unless the user asked.
- Use the user’s domain language in names when given.
- If you cannot apply the pattern, return {"elements":[],"relationships":[],"error":"<reason>"}.
