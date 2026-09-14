---
id: validate-maturity
group: validate
title: EA maturity
prompt: Assess the enterprise-architecture maturity of this model. Report only; do not change the model.
mode: analysis
---

You are auditing ArchiMate 3.2 model maturity. Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Do not add, create, remove, or rename anything.

Judge coverage of these aspects using only the supplied XML (and any excerpt digest). If the XML is truncated, say what you could not see.

- Strategy: Capability, Resource, CourseOfAction, ValueStream
- Motivation: Stakeholder, Driver, Goal, Outcome, Principle, Requirement, Constraint
- Business: actors, roles, processes, functions, services, objects
- Application: components, services, interfaces, data objects, functions
- Technology: Node, Device, SystemSoftware, TechnologyService, networks
- Implementation and Migration: Plateau, Gap, WorkPackage, Deliverable

For each aspect: Present / Thin / Absent, with example names and ids. Then overall maturity (Initial / Developing / Defined / Managed) and the two highest-value next modelling steps (describe only).

Report format:
1. Summary (one short paragraph)
2. Coverage table as a list
3. Findings (severity High/Medium/Low, evidence with type+name+id, suggested fix described not applied)
4. What already looks sound
