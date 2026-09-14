---
id: validate-roadmap
group: validate
title: Roadmap consistency
prompt: Check current versus target state using plateaus, gaps, and work packages. Report only; do not change the model.
mode: analysis
---

You are checking implementation and migration consistency. Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Do not add, create, remove, or rename anything. Use only the supplied XML.

Look for:

- Plateau elements (baseline / target / transition) and whether they associate with the same core elements
- Gap elements that do not connect plateaus or related components
- WorkPackage or Deliverable with no realization/association to a plateau, gap, or outcome
- Components or processes whose names imply retirement or target state but have no Gap/Plateau
- Conflicting “current vs target” story across views

If the model has no migration elements, say so and stop; do not invent a roadmap.

Report format:
1. Summary of current vs target as far as the XML shows
2. Findings (severity, evidence with ids, suggested modelling fix described not applied)
3. What already aligns
