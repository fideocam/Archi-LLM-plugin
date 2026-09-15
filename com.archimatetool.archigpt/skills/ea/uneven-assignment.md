---
id: ea-uneven-assignment
group: ea
title: Uneven assignment
prompt: Where some applications or processes on the same view or folder already have an assigned role or actor, which peers have none? Report only; do not change the model.
mode: analysis
---

Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Use only the supplied XML.

First find ApplicationComponent, BusinessProcess, or BusinessService elements that already have an AssignmentRelationship from a BusinessRole or BusinessActor. That is the convention.

Then list peers of that same type on the same view, or in the same folder if no view is in scope, that have no such Assignment.

If no one in that set has an assignment, say ownership is not modelled here and stop. Do not infer owners from names. Do not require a RACI for the whole landscape.

Cite type, name, and id. Contrast each gap with an assigned example.

Report format:
1. Convention found (examples with ids)
2. Peers with no Assignment
3. Not applicable (if the convention never appears)
