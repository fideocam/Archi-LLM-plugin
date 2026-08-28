# Example: technology / deployment viewpoint

When the user asks for **deployment**, **infrastructure**, or **technology** of a system:

Typical elements: Node, Device, SystemSoftware, CommunicationNetwork, TechnologyService, Artifact, Path.

Typical relationships: AssignmentRelationship (node to system software / artifact), ServingRelationship (infrastructure service to application), RealizationRelationship where it applies, AssociationRelationship only if no more specific type fits.

Layout: one Node per runtime (or cluster), SystemSoftware for the OS or runtime, Artifact for the deployable, CommunicationNetwork for the LAN/VLAN. Do not put BusinessActor on this view unless asked.
