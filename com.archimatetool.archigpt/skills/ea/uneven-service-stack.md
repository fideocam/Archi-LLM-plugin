---
id: ea-uneven-service-stack
group: ea
title: Uneven service stack
prompt: Where this model already completes a serving stack for some BusinessServices, which peer services on the same view or folder lack that stack? Report only; do not change the model.
mode: analysis
---

Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Use only the supplied XML.

First find the convention this XML already uses. Example: some BusinessService elements have Serving from an ApplicationService or ApplicationComponent, and optionally from a TechnologyService. Only then look for peer BusinessServices that lack the same kind of stack.

A peer is another BusinessService on the same view, or in the same folder if no view is in scope.

Do not flag every service in the model against an ArchiMate sandwich. If no service has a serving stack, say that pattern is not modelled and stop.

Cite type, name, and id. Contrast each gap with the example that established the convention.

Report format:
1. Convention found (examples with ids)
2. Peer services missing that stack
3. Not applicable (if the convention never appears)
