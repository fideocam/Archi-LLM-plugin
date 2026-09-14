---
id: pattern-application-contract
group: pattern
title: Application with contract
prompt: Add an Application with contract fragment (component, interface, data object, and serving a business service). Reuse existing elements when they match. Do not create a new view unless I ask for one.
mode: changes
---

Instantiate the Application with contract pattern. Respond ONLY with CHANGES JSON (no prose, no markdown fence).

Fragment to produce (reuse existing elements from the XML when they match; otherwise create):

- ApplicationComponent
- ApplicationInterface served by or assigned from the component (ServingRelationship or AssignmentRelationship, as fits ArchiMate: component assigned to / serving the interface)
- DataObject with AccessRelationship from the component (source = component, target = data object)
- BusinessService served by the application component or by an ApplicationService of that component (ServingRelationship)

Prefer: ApplicationComponent — Serving → ApplicationInterface; ApplicationComponent — Access → DataObject; ApplicationComponent or ApplicationService — Serving → BusinessService.

Rules:

- Official ArchiMate 3.2 types only. Reuse existing ids. New ids: id- plus 32 hex.
- Omit "diagram" unless the user asked for a new view. Do not duplicate existing elements.
- Use the user’s domain language in names when given.
- If you cannot apply the pattern, return {"elements":[],"relationships":[],"error":"<reason>"}.
