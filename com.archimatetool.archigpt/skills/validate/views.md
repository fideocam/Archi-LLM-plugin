---
id: validate-views
group: validate
title: View quality
prompt: Review diagram and viewpoint quality: overcrowding, mixed layers, and relationships on the canvas that the catalog does not support. Report only; do not change the model.
mode: analysis
---

You are reviewing ArchiMate views. Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Do not add, create, remove, or rename anything. Use only the supplied XML.

If a primary diagram or selected view is named, start there; then mention other views only if the XML includes them.

Check:

- Viewpoint value versus the types of elements placed on the view (mixed business + technology on a declared Application viewpoint, and similar)
- Two figures on a view with a connection that has no matching model relationship, or a model relationship between those two that is not shown
- Very large views (many nodes) that mix several concerns and would be clearer split
- Views that contain a single unrelated element
- Elements in the catalog that never appear on any view (only if the XML includes viewsAndDiagrams)

Do not invent layout quality you cannot see (colour, overlap). The XML has names, viewpoint, nodes, and connections.

Report format:
1. Summary
2. Per-view findings (view name, severity, evidence, suggested split or fix described not applied)
3. Views that already look coherent
