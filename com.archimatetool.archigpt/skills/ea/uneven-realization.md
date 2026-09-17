---
id: ea-uneven-realization
group: ea
title: Capabilities missing realization
prompt: Where some capabilities or services on the same view or folder already have a Realization from a process, application, or resource, which peers have none? Report only; do not change the model.
mode: analysis
---

Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Use only the supplied XML.

Do not list every Capability without Realization. That is not the question.

First find Capability, BusinessService, or ApplicationService elements that already have a RealizationRelationship from a BusinessProcess, BusinessFunction, ApplicationComponent, ApplicationService, or Resource.

Then list peers of that same type on the same view, or in the same folder if no view is in scope, that have no Realization of that kind.

If nothing in the XML is realized that way, say the pattern is not modelled and stop. Do not apply a capability-map checklist.

Cite type, name, and id. Contrast each gap with the realized example.

Report format:
1. Convention found (examples with ids)
2. Peers of the same type with no matching Realization
3. Not applicable (if the convention never appears)
