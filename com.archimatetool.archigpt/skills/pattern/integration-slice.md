---
id: pattern-integration-slice
group: pattern
title: Add an integration between systems
prompt: Add an integration slice (two application components, interfaces, and a flow). Reuse existing elements when they match. Do not create a new view unless I ask for one.
mode: changes
---

Instantiate the Integration slice pattern. Respond ONLY with CHANGES JSON (no prose, no markdown fence).

Fragment to produce (reuse existing XML elements when they match; otherwise create):

- Two ApplicationComponents (caller and provider, or left and right)
- ApplicationInterface for each side that is meant to be a contract (reuse one shared interface if the user described a single API)
- ServingRelationship or AssignmentRelationship from each component to its interface as appropriate
- FlowRelationship between the components (or between interfaces if that matches the XML style already used)
- Optional ApplicationCollaboration aggregating the two components if the user described a platform or hub

Rules:

- Official ArchiMate 3.2 types only. Do not use type Connection. FlowRelationship is the integration flow.
- Reuse existing ids. New ids: id- plus 32 hex.
- Omit "diagram" unless the user asked for a new view. Do not add a generic Technology Node unless asked.
- Use the user’s domain language in names when given.
- If you cannot apply the pattern, return {"elements":[],"relationships":[],"error":"<reason>"}.
