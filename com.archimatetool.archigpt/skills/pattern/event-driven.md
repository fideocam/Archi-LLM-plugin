---
id: pattern-event-driven
group: pattern
title: Event-driven process
prompt: Add an Event-driven process fragment (business event triggering a business process). Reuse existing elements when they match. Do not create a new view unless I ask for one.
mode: changes
---

Instantiate the Event-driven process pattern. Respond ONLY with CHANGES JSON (no prose, no markdown fence).

Fragment to produce (reuse existing XML elements when they match; otherwise create):

- BusinessEvent
- BusinessProcess
- TriggeringRelationship from the event to the process (source = event, target = process)
- Optional BusinessRole or BusinessActor assigned to the process if the user named who handles it
- Optional BusinessObject if the user named a payload

Rules:

- Official ArchiMate 3.2 types only. Do not use type Event; use BusinessEvent. Do not use Process; use BusinessProcess.
- Reuse existing ids. New ids: id- plus 32 hex.
- Omit "diagram" unless the user asked for a new view.
- Use the user’s domain language in names when given.
- If you cannot apply the pattern, return {"elements":[],"relationships":[],"error":"<reason>"}.
