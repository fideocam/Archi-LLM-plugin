---
id: tech-standard-runtime
group: technology
title: Peers on a different runtime than this environment already uses
prompt: Where some applications on this view or folder already run on a named SystemSoftware or Node product, which peers run on a different product or have a host but no such runtime? Report the split against what this file already uses. Report only; do not change the model.
mode: analysis
---

Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Use only the supplied XML.

Question: which applications diverge from a runtime product this environment already uses?

First collect SystemSoftware (then Node if no SystemSoftware) that realizes at least one ApplicationComponent or ApplicationService. Cluster those hosts by name as products already in the file (same or very similar name is the same product).

That set is the environment. Then, on the same view, or in the same folder if no view is in scope:

- If one product already realizes clearly more applications than any other in the same cluster, that majority is the convention. List peer hosted applications that use a different named product, or that have a Node/Device but no SystemSoftware of that kind.
- If two or more products each realize applications and none is a clear majority, report the split (which apps sit on which product). Do not pick a winner.
- Ignore applications that have no technology host at all (that is a different tool).

If no SystemSoftware (or named Node product) realizes an application, say runtimes are not modelled here and stop. Do not recommend a vendor, version, cloud service, or “standard stack” that is not already named in the XML.

Cite type, name, and id. Contrast each divergence with an example that already uses the majority product (or show both sides of a split).

Report format:
1. Products already in use (counts and example ids)
2. Peer applications on a different product, or hosted with no runtime
3. Not applicable (if no runtime product appears)
