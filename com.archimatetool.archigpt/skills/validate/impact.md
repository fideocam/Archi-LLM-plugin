---
id: validate-impact
group: validate
title: Impact and single points of failure
prompt: Analyse change impact and single points of failure in this landscape. Report only; do not change the model.
mode: analysis
---

You are doing impact and resilience analysis. Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Do not add, create, remove, or rename anything. Use only the supplied XML.

If a selection or primary diagram is given, start from those concepts; otherwise treat the whole supplied model.

For the focus concepts, walk Serving, Realization, Assignment, Access, Flow, Triggering, Composition, and Aggregation (not invented types). List:

- Upstream dependents (what would feel a change)
- Downstream dependencies (what this relies on)
- Single points of failure: a component, node, or role that many processes or services depend on with no alternative path in the XML
- Orphan elements with no relationships (mention; they do not participate in impact)

Do not assume runtime redundancy that is not modelled.

Report format:
1. Summary
2. Impact chains (cite type, name, id)
3. SPOF and concentration findings (severity, evidence, suggested modelling or architecture option described not applied)
4. Limits of this analysis (truncated XML, missing views)
