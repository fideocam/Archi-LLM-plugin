---
id: tech-continuity
group: technology
title: Peers missing a second location or plateau
prompt: Where some applications or nodes on this view or folder are already tied to more than one Location or to both a baseline and target Plateau, which peers exist in only one place? Report only; do not change the model.
mode: analysis
---

Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Use only the supplied XML.

Question: which applications or nodes lack the continuity (second site or plateau) this model already shows for peers?

First find ApplicationComponent, Node, or Device elements that already have:

- Association or Aggregation to two or more Locations, or
- Realization / Aggregation involving two Plateaus (baseline and target), or
- Realization by hosts that themselves sit in different Locations

That is the convention. Then list peers on the same view, or in the same folder if no view is in scope, that sit in only one Location (or one Plateau) while that convention exists.

If nothing has a second Location or Plateau, say continuity across sites is not modelled here and stop. Do not invent RPO, RTO, DR runbooks, or a second data centre.

Cite type, name, and id. Contrast each single-site peer with a multi-site or multi-plateau example.

Report format:
1. Convention found (examples with ids)
2. Peers in only one location or plateau
3. Not applicable (if no second site or plateau exists)
