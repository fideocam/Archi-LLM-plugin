---
id: ea-uneven-technology
group: ea
title: Uneven technology host
prompt: Where some application components on the same view or folder are already realized by a Node, SystemSoftware, or Device, which peer applications have no host? Report only; do not change the model.
mode: analysis
---

Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Use only the supplied XML.

First find ApplicationComponent (or ApplicationService) elements that already have Realization from a Node, SystemSoftware, or Device. That is the convention.

Then list peer applications on the same view, or in the same folder if no view is in scope, that have no such host.

If no application is hosted in the XML, say technology realization is not modelled here and stop. Do not demand a technology layer for the whole estate.

Cite type, name, and id. Contrast each gap with a hosted example.

Report format:
1. Convention found (examples with ids)
2. Peer applications with no host
3. Not applicable (if the convention never appears)
