# Company metamodel: templates, specializations, and comparison

This note records how ArchiGPT should eventually **compare a working model to a company-specific metamodel**, and where that metamodel should live in Archi. It is a design note, not an implemented feature.

Related current behaviour: EA tools in `com.archimatetool.archigpt/skills/ea/` already look for **echo gaps** — a related element or relationship that this model already shows in one place, missing for peers or on an overlapping view. Company metamodel comparison is the same idea, except the “present here” side is a **company reference**, not another part of the same file.

---

## Why this matters

A lot of modelling in Archi is a **service diagram** or **tech architecture for one system**, not a full enterprise inventory. In that setting, “what is missing” is more useful than “what is already drawn” — but only if “missing” means **missing relative to a pattern the organisation already uses**, not missing relative to the whole ArchiMate language.

Examples:

- This view has three Business Services; two have a serving Application Service, the third does not.
- Application A is realized by a Node; Application B on the same view is not, even though the company pattern always shows a host.
- View “Payments — business” shows Serving to an application; view “Payments — application” contains the same application but not the business service.

Those are **echo gaps**. A **company metamodel** is a named, maintained set of such patterns (and company types) so the comparison is against the standard, not only against whatever happens to already exist in this file.

If the working model never uses a pattern, comparison should **stop** and say the pattern is not in play. It should not score the file against a full EA checklist.

---

## Two different jobs

| Job | What it is | Archi mechanism |
|-----|------------|-----------------|
| **Publish / seed** | How a modeller gets the company standard into a **new** project | Archi **template** (`.architemplate`) |
| **Compare** | What ArchiGPT (or a human) checks the **current** work against | A **living reference model** (`.archimate`), optionally the same content that was saved as the template |

A template is the right place to **author and share** the metamodel. It is the wrong thing to **read at analysis time** as the only source of truth.

---

## Archi templates

Archi templates are re-usable models saved as `*.architemplate` (`File → Save As Template…`). `File → New → Model from Template` **copies** that archive into a new `.archimate`. The copy can include:

- Folder structure
- Specializations (company types)
- Example views and unnamed pattern fragments
- Properties and documentation on those examples

After creation, the working model is **detached**. Updating the company template does not update existing files. ArchiGPT therefore cannot treat “whatever was in the template when this model was created” as the current standard.

**Use the template for:** onboarding, consistent folders, shipping specializations and example pattern views to every new model.

**Do not use the template file as:** the runtime input for “compare to metamodel” unless you also keep a current reference (see below).

---

## Specializations (profiles)

Archi’s **Specializations Manager** (`Tools` menu, and the `…` control next to Specialization in Properties) implements ArchiMate **language customization**: a new type based on an existing element or relationship, optionally with an image. Specializations are stored **in the model**.

They are the right vocabulary for company types, for example:

- Business Service specialized as “Customer Journey”
- Application Component specialized as “System of Record”
- Serving specialized as “API contract”

They do **not** encode structural rules such as “every Customer Journey must be served by an Application Service.” Those rules belong in **pattern views** (or equivalent fragments) in the reference model.

Specializations should be **defined in the reference model, saved into the template**, and thus copied into new projects. Existing models can pick up new specializations later via Archi’s **model import** (profiles can be imported from another model), not via the template snapshot.

---

## Living reference for comparison

Comparison needs the **current** company metamodel. Practical options, in order:

1. **Reference `.archimate` (preferred)**  
   Maintain one Archi model that *is* the metamodel: specializations plus a small set of pattern views (unnamed or generically named examples). Save that same model as the `.architemplate` when you publish. ArchiGPT would compare the open working model to this file, or to a second model open in Archi.

2. **Pattern views copied into every working model**  
   A reserved folder (for example `Metamodel` or `Reference patterns`) copied from the template. Convenient (everything is in one file) but **drifts** unless someone re-imports when the standard changes.

3. **Collaboration / shared repository copy of the same reference model**  
   Same as (1), with a shared location so the team is not depending on a local disk path.

Do **not** put the company metamodel only in ArchiGPT skill Markdown. Skills in this plugin are **product defaults**. The company standard should stay in Archi so architects can see, edit, and review it like any other model.

---

## What to put in the reference model

Keep it small and aligned with how people actually model:

- **Specializations** used in the company
- A few **pattern views**, not a full landscape, for example:
  - Business diagram for a service (service, serving application, delivering process/role)
  - Tech architecture for a system (component, interface or application service, data access, host node)
  - Optional: motivation link, assignment, integration slice — only if the company actually uses them
- **Unnamed or generic** example elements so comparison is about *structure*, not about copying “Customer” and “CRM” into every project
- Short documentation on the view stating the convention in one paragraph (what related element must appear when this pattern is in use)

The reference is a **library of conventions**, not a second enterprise architecture.

---

## How this relates to current ArchiGPT tools

| Today | Later, with a company metamodel |
|------|----------------------------------|
| **Tidy** | Catalog hygiene (duplicates, relationships not on any view). Unchanged. |
| **View** | Echo gaps **on this canvas** vs what this view already does. Unchanged as the default. |
| **Pattern** | Instantiate a built-in fragment. Could later offer “instantiate from company pattern view.” |
| **EA** | Echo gaps vs **peers and other views in this file**. A new tool would echo gaps vs **the reference model**. |

A future tool (name TBD), report-only:

1. Load working-model XML (as now) and reference-model XML (new).
2. Detect which reference pattern views the current selection or open view **already resembles** (same types and some of the same relationship kinds).
3. Report only related elements/relationships the reference pattern has that this slice lacks.
4. If nothing in the working model resembles a reference pattern, say so and **stop**. Do not apply every reference view as a mandatory checklist.

That matches the EA rule already in the skills: do not invent a full EA; only flag incompleteness relative to a pattern that is in play.

---

## Recommended practice

1. **Author** the company metamodel as a normal Archi model (specializations + pattern views).
2. **Publish** it with `File → Save As Template…` and share the `.architemplate` so new work starts from the standard.
3. **Keep** the `.archimate` (or a Collaboration copy) as the living reference; bump the template when the standard changes.
4. **Compare** working models to that living reference (human or, later, ArchiGPT), not to a historical template copy inside each project file.
5. Treat **Specializations** as types, **pattern views** as structural rules.

---

## Later plugin work (not done)

When implementing comparison in ArchiGPT, likely pieces:

- Settings (or Tools): path or picker for the reference model; optionally “use the other open model.”
- Serialize the reference with the same XML dump as the working model (`ModelContextToXml`).
- A report-only skill that states the echo-gap rules against the reference, with a hard stop if no pattern is in play.
- Do not auto-import from the reference unless the user picks a Pattern-style “Add …” action.

Out of scope until then: encoding company rules only in plugin skills, scoring every model against the full ArchiMate metamodel, or treating `.architemplate` as a live linked library.

---

## References

- Archi help: Templates (`File → Save As Template…`, New Model from Template); Specializations Manager
- ArchiMate 3.2: [Language Customization Mechanisms](https://pubs.opengroup.org/architecture/archimate32-doc/#_Toc112155065)
- This repo: `com.archimatetool.archigpt/skills/ea/` (echo-gap tools), `docs/Skills.md` (how tools are injected)
