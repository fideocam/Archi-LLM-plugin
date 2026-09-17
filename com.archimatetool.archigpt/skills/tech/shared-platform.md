---
id: tech-shared-platform
group: technology
title: Peers on a private host while a shared platform already exists
prompt: Where two or more applications on this view or folder already realize onto the same Node or SystemSoftware instance, which similar applications have their own private host of that type instead? Report only; do not change the model.
mode: analysis
---

Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Use only the supplied XML.

Question: which applications have a private host while this model already shares a platform among peers?

First find a Node, Device, or SystemSoftware instance that realizes (or is assigned to) two or more different ApplicationComponents or ApplicationServices. That instance is a shared platform.

Then list peer applications on the same view, or in the same folder if no view is in scope, that instead have their own distinct host of the same ArchiMate type (and a similar kind of name) rather than realizing onto that shared instance.

If no host instance is shared by two or more applications, dedicated hosting is the convention here: say so and stop. Do not demand consolidation onto a platform that is not already shared. Do not invent a farm, cluster, or PaaS.

Cite type, name, and id for the shared instance and for each private-host peer.

Report format:
1. Convention found (shared instance and the applications already on it)
2. Peer applications on a private host of the same type
3. Not applicable (if nothing is shared)
