---
id: tech-redundancy
group: technology
title: Peers missing a second host
prompt: Where some applications on this view or folder are already realized by more than one Node, Device, or Location, which peer applications have only a single host? Report only; do not change the model.
mode: analysis
---

Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Use only the supplied XML.

Question: which applications lack the redundancy this model already shows for their peers?

First find ApplicationComponent (or ApplicationService) elements that already have Realization from two or more distinct Nodes, Devices, or Locations. That is the convention (a second host or site is modelled somewhere).

Then list peer applications on the same view, or in the same folder if no view is in scope, that have exactly one such host and no second Location.

If nothing has more than one host, say redundancy is not modelled here and stop. Do not score the file against an external high-availability checklist. Do not invent clustering, load balancers, or failover that the XML does not show.

Cite type, name, and id. Contrast each single-host peer with a multi-host example.

Report format:
1. Convention found (examples with two or more hosts)
2. Peer applications with only one host
3. Not applicable (if no redundant hosting exists)
