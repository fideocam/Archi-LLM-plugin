---
id: ea-uneven-alternatives
group: ea
title: Uneven alternatives
prompt: Where some processes or services already have more than one supporting application or node in the XML, which peers have only a single supporter? Report only; do not change the model.
mode: analysis
---

Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Use only the supplied XML.

First find BusinessProcess, BusinessService, or ApplicationService elements that already have two or more distinct ApplicationComponents or Nodes supporting them via Serving or Realization. That is the convention (an alternative path is modelled somewhere).

Then list peers of that same type on the same view, or in the same folder if no view is in scope, that have exactly one supporter and no second path.

If nothing has more than one supporter, say alternatives are not modelled and stop. Do not assume operational redundancy. Do not scan the whole landscape for single points of failure against an external resilience checklist.

Cite type, name, and id. Contrast each single-supporter peer with a multi-supporter example.

Report format:
1. Convention found (examples with two or more supporters)
2. Peers with only one supporter
3. Not applicable (if no alternative paths exist)
