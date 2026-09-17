---
id: tech-hosting-stack
group: technology
title: Hosted peers missing a technology layer this model already uses
prompt: Where some applications on this view or folder are already realized by more than one of Node, Device, and SystemSoftware, which hosted peers have only a subset of that stack? Report only; do not change the model.
mode: analysis
---

Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Use only the supplied XML.

Question: which hosted applications lack a technology layer that this model already uses for peers?

First find ApplicationComponent (or ApplicationService) elements that already have Realization from more than one of Node, Device, and SystemSoftware. That combination is the stack.

Then list peer applications on the same view, or in the same folder if no view is in scope, that already have at least one such host but are missing a layer that the stacked examples have.

Ignore applications with no technology host at all.

If no application has more than one of Node, Device, and SystemSoftware, say a multi-layer hosting stack is not modelled here and stop. Do not demand Device or SystemSoftware for the whole estate. Do not invent Artifacts, containers, or a reference stack from outside the file.

Cite type, name, and id. Contrast each thin stack with a complete example.

Report format:
1. Convention found (the layers and examples with ids)
2. Hosted peers missing a layer
3. Not applicable (if no multi-layer stack exists)
