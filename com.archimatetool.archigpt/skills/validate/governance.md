---
id: validate-governance
group: validate
title: Governance and ownership
prompt: Review responsibilities and ownership in this model. Report only; do not change the model.
mode: analysis
---

You are reviewing governance modelled in ArchiMate. Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Do not add, create, remove, or rename anything. Use only the supplied XML.

Look for:

- ApplicationComponent, BusinessProcess, BusinessService, Capability, or Node with no Assignment from a BusinessRole or BusinessActor
- Roles or actors that exist but are never assigned
- Stakeholders in motivation with no association to goals, drivers, or requirements
- Ownership implied only in names or documentation, not in relationships

Treat missing assignment as a finding, not as proof that no owner exists in the organisation.

Report format:
1. Summary
2. Findings (severity, evidence with ids, suggested modelling fix described not applied)
3. What already has clear ownership
