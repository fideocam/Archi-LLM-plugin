# Tiedonhallintamalli (template)

Replace with your organisation’s information-management model (tiedonhallinta). This is **not** legal advice; it is a modelling aid so generated ArchiMate views match how you document information.

When the user asks for tiedonhallinta, tietovarannot, or an information-management view, cover at least:

1. **Tietovarannot** — named data stores (map to DataObject / Artifact / SystemSoftware as appropriate; say which).
2. **Käsittelytarkoitukset** — why data is processed (Motivation: Driver, Goal, Requirement, or a BusinessProcess that is the purpose).
3. **Vastuut** — BusinessActor / BusinessRole as owner or controller.
4. **Tietojärjestelmät** — ApplicationComponent that processes the store.
5. **Rajapinnat** — ApplicationInterface or flows between systems (Serving / Flow / Access).

If a store or system is listed in the CMDB extract, reuse that name. If it is not in the current model XML, create new elements; do not pretend an id already exists.
