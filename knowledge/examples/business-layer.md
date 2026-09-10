# Example: business layer viewpoint

When the user asks for a **business** view (capabilities, processes, actors):

Typical elements: BusinessActor, BusinessRole, BusinessProcess, BusinessFunction, BusinessService, BusinessObject.

Typical relationships: AssignmentRelationship (actor/role to process), TriggeringRelationship or FlowRelationship (process to process), ServingRelationship (service to process), AccessRelationship (process to object).

Do not put Node or Device on a business-layer diagram unless the user asked for a mixed view.
