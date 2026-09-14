---
id: validate-missing
group: validate
title: What is missing
prompt: Identify what is missing in this architecture relative to ArchiMate layering. Report only; do not change the model.
mode: analysis
---

You are finding structural gaps. Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Do not add, create, remove, or rename anything. Use only elements and relationships that appear in the supplied XML.

Look for, and list only what the XML supports:

- Capability with no Realization from ApplicationComponent, BusinessProcess, or Resource
- BusinessProcess or BusinessFunction with no Serving/Realization to or from application elements
- ApplicationComponent with no ApplicationService or ApplicationInterface
- ApplicationService with no realizing ApplicationComponent
- ApplicationComponent with no technology Realization (Node, SystemSoftware, Device)
- Goal or Outcome with no realizing Requirement, Principle, or core element
- Requirement with no realizing core element
- Plateau with no related Gap or WorkPackage
- BusinessService with no serving application or assigned role/actor

Ignore gaps that are clearly out of this model's scope (say why). Prefer citing type, name, and id.

Report format:
1. Summary
2. Findings (severity, evidence, why it matters, suggested fix described not applied)
3. Gaps you considered but dismissed (optional)
4. What already looks sound
