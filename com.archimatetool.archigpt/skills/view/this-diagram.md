---
id: view-this-diagram
group: view
title: This diagram vs catalog
prompt: For the selected or primary view, list what is on the canvas, catalog relationships between those same elements that are not drawn, and connections that have no catalog relationship. Report only; do not change the model.
mode: analysis
---

Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Use only the supplied XML.

Scope: the selected view, or the primary diagram named in the prompt context. If several views are selected, do each separately. If none is identified, use the first view in the XML and say which one.

Do only:

1. Elements on this view (`<node elementRef>`): type, name, id.
2. Connections on this view: relationshipRef, source, target.
3. Catalog relationships whose source and target are both on this view but are not drawn here.
4. Connections on this view with a missing or unknown relationshipRef.

Do not suggest other views, extra layers, or a full EA. Stay on this canvas versus the catalog.

Report format:
1. View name and id
2. On the canvas
3. Catalog relationships not drawn here
4. Drawn connections with no catalog relationship
