---
id: ea-uneven-application-support
group: ea
title: Uneven application support
prompt: Where some BusinessProcesses on the same view or folder already have Serving or Realization to an application, which peer processes have none? Report only; do not change the model.
mode: analysis
---

Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Use only the supplied XML.

First find processes (or BusinessFunctions) that already have Serving or Realization to or from an ApplicationComponent, ApplicationService, or ApplicationInterface. That is the convention.

Then list peer processes on the same view, or in the same folder if no view is in scope, that have no such link.

If no process has application support, say that pattern is not modelled and stop. Do not require every process in the enterprise to have an application.

Cite type, name, and id. Show the supported example next to each unsupported peer.

Report format:
1. Convention found (examples with ids)
2. Peer processes with no application link
3. Not applicable (if the convention never appears)
