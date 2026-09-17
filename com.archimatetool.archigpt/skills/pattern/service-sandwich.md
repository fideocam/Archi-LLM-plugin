---
id: pattern-service-sandwich
group: pattern
title: Add a service sandwich
prompt: Add a service sandwich for this slice (business, application, and technology services with a realizing component and node). Reuse existing elements when they match. Do not create a new view unless I ask for one.
mode: changes
---

Instantiate the Service sandwich pattern. Respond ONLY with CHANGES JSON (no prose, no markdown fence).

Fragment to produce (reuse an existing element when type+name or id already matches the XML; otherwise create it):

- BusinessService (the business-facing service)
- ApplicationService that serves the business service (ServingRelationship, source = application service, target = business service)
- TechnologyService that serves the application service (ServingRelationship)
- ApplicationComponent that realizes the application service (RealizationRelationship)
- Node that realizes the technology service (RealizationRelationship)

Optional if the user named them: BusinessRole assigned to the business service’s delivering process is out of scope unless asked.

Rules:

- Use official ArchiMate 3.2 types only. Technology node type is Node, not TechnologyNode.
- New ids: id- plus 32 hex. Existing elements keep their ids from the XML.
- Omit the "diagram" object unless the user explicitly asked for a new view/diagram. If a primary diagram is in selection context, add to that view by omitting "diagram".
- Do not duplicate elements that already exist. Do not add unrelated layers.
- If the user named a domain (e.g. "customer self-service"), use that in element names.
- If you cannot apply the pattern, return {"elements":[],"relationships":[],"error":"<reason>"}.
