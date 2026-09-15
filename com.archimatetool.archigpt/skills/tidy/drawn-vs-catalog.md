---
id: tidy-drawn-vs-catalog
group: tidy
title: Drawn connections vs catalog
prompt: Find view connections with no matching model relationship, and model relationships between two elements that sit together on a view but are not drawn there. Report only; do not change the model.
mode: analysis
---

Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Do not add or remove anything. Use only the supplied XML.

Two checks only:

1. Orphan drawings: a `<connection>` whose `relationshipRef` is empty or does not match any `<relationship id>`.
2. Missing drawings: a catalog relationship whose source and target both appear as nodes on the same view, but that view has no `<connection relationshipRef>` for that relationship id.

Cite view name/id, relationship type and id, and the two element names/ids.

Do not discuss viewpoint quality, crowding, or missing enterprise layers. If views are truncated, say so.

Report format:
1. Orphan drawings
2. Missing drawings
3. None found (if true)
