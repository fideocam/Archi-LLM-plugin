---
id: ea-cross-view-gaps
group: ea
title: Present in one view, missing in another
prompt: Find elements or relationships that this model already shows on one view but omits on a different overlapping view that already contains part of the same story. Report only; do not change the model.
mode: analysis
---

Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Use only the supplied XML.

Question: where does this model already tell a story in one view, and leave it incomplete on another view that includes some of the same elements?

Method:

1. Use `<viewsAndDiagrams>`. For each pair of views that share at least one element (`elementRef`), compare catalog relationships whose source and target are both on at least one of those views.
2. A finding is: view A draws that relationship (or places the related element), view B contains one or both ends but does not draw it and does not place the related element.
3. Also flag an element that appears with a related neighbour on view A (connected there) but sits alone on view B with no neighbour of that type, when that neighbour exists in the catalog.

Cite view names/ids and element/relationship type, name, id.

If there is only one view, or views do not overlap, say so and stop. Do not invent a third view or a full EA. Do not treat “not on any view” (that is a Tidy tool).

Report format:
1. Overlapping views
2. Echo gaps (shown here, missing there)
3. None found (if true)
