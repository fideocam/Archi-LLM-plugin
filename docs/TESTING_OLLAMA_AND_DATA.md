# Testing Against Ollama and Test Data

This document describes the **current test data**, whether you **should test against real Ollama**, and **what kind of test data** to use if you add integration tests.

---

## 1. Current test data (no real Ollama)

| Test class | What it uses | Purpose |
|------------|----------------|--------|
| **OllamaClientTest** | In-process **mock HTTP server** (`HttpServer` on random port). Fixed JSON response bodies (e.g. `{"message":{"content":"{\"elements\":[],\"relationships\":[]}"}}`). Client is pointed at `localhost:<mockPort>`. | Asserts request path (`/api/chat`, `/api/generate`), request body shape (system/user roles), response parsing (extract content, unescape), and error handling (404, 500). **No real Ollama.** |
| **ArchiMateLLMResultParserTest** | **Hardcoded JSON strings** (e.g. `GOOD_JSON` with 2 elements, 1 relationship, ids like `id-a1b2c3d4e5f67890...`). Also markdown-wrapped JSON, empty input, `"error":"..."` responses. | Parsing logic: elements, relationships, diagram, remove* arrays, error field. |
| **ArchiMateSchemaValidatorTest** | **Programmatically built** `ArchiMateLLMResult` (e.g. `ElementSpec` BusinessActor/BusinessRole, `RelationshipSpec` AssignmentRelationship) with valid and invalid types/ids. | Validates that the validator accepts valid ArchiMate types and rejects invalid ones (wrong type, missing id, bad relationship). Uses Archi’s `IArchimatePackage` when on classpath. |
| **ArchiMateImportFlowTest** | **Fixed JSON** (`GOOD_LLM_RESPONSE` with 2 elements, 1 relationship). Parsed → `ArchiMateSchemaValidator.validate()` → `ArchiMateLLMImporter.importIntoModel()` into a **reflection-created** in-memory model. | End-to-end: parse → validate → import. No LLM; only checks that valid JSON flows through and lands in the model. |
| **ArchiMateAnalysisUseCaseTest** | **Minimal model XML** (short snippet: `<model>…<archimateModel><folder name=\"Business\"/>…</archimateModel></model>`). **Plain-text strings** as “analysis response” (e.g. “This solution supports Order Management…”). | Asserts prompt content (routing, view vs element), user message format, and that **plain-text responses** are not parsed as import (parser returns empty elements/relationships). |
| **SelectionInPromptTest** | **Short strings**: selection context (e.g. `Folder \"Business\"`, `Element BusinessActor \"Customer\"`), `"<model/>"` or minimal XML, and prompt text. | Asserts that `UserMessageBuilder.buildUserMessage()` includes selection context, delimiter, and prompt. |
| **ModelContextToXmlTest** | Likely in-memory model (when Archi on classpath) or skipped. | Serialization of model to XML for the payload. |

**Summary:** No test calls a real LLM. All use stubs, fixed JSON/XML, or in-memory objects. “Analysis” is tested only by feeding plain text into the **parser** and asserting no import data; “XML validation” in the suite is validation of **parsed JSON** (ArchiMate types), not validation of Open Exchange XML returned by the LLM.

---

## 2. Should you test against Ollama?

**Short answer:** Optional but useful. Keep the current **unit tests** (fast, deterministic, no Ollama). Add **optional integration tests** that run only when Ollama is available and hit the real API for sanity-checking the full pipeline.

**Why unit tests stay as they are**

- Fast, no network, no flakiness.
- They already verify: client request/response shape, parser, validator, import flow, prompt content, and “analysis response → no import”.

**Why add optional integration tests against Ollama**

- **Real prompt adherence:** You see whether the model actually returns JSON for CHANGES and plain text for ANALYSIS (and valid Open Exchange XML for “return the model”).
- **Regression:** Prompt or API changes can be checked with a real model (e.g. “add a BusinessActor” → response parses as JSON and passes validator).
- **Analysis and “XML validation” in the broad sense:**
  - **Analysis:** Send a known model XML + “describe this view” (or “what is in the model”). Assert: response is **not** valid CHANGES JSON (or parsed result has empty elements/relationships), and optionally that the response is non-empty text. That checks the model stays in ANALYSIS mode.
  - **Export/XML:** Send “return the model as XML” (or “export the model”) with a small model. Assert: response is **well-formed XML** and optionally that it matches the expected Open Exchange structure (root `model`, `archimateModel`, folders, views). That checks the model returns export XML you can validate (and optionally schema-validate).

**Caveats**

- Requires Ollama installed and running (or skip when unavailable).
- Non-deterministic: same prompt can yield different replies. Assert **shape** (is JSON? is XML? does it parse? does it validate?) rather than exact content.
- Slower and not suitable for every CI run unless you have a dedicated runner with Ollama. Use an env flag or Maven profile (e.g. `-Dollama.integration=true`) to enable these tests.

**Recommendation**

- **Do not** replace unit tests with Ollama tests.
- **Do** add a small set of **optional** integration tests that:
  - Run only when Ollama is reachable (e.g. `OllamaClient.checkConnection()` or env `OLLAMA_INTEGRATION=1`).
  - Use **fixed, small test data** (see below).
  - Assert: CHANGES prompt → response parses as JSON and passes schema validator; ANALYSIS prompt → response is plain text (no import); EXPORT prompt → response is valid XML (and optionally schema-valid).

---

## 3. What to test against Ollama (analysis and XML validation)

| Scenario | Prompt / intent | What to assert |
|----------|------------------|----------------|
| **CHANGES (add)** | User message: small model XML + “Add a BusinessActor named TestUser”. | Response parses as JSON; `elements` has at least one element; `ArchiMateSchemaValidator.validate(parsed)` returns no errors (or only acceptable warnings). Optionally: import into in-memory model and assert element count. |
| **ANALYSIS** | User message: same small model XML + “Describe this model” or “What is in this view?”. | Response is **not** valid CHANGES JSON (e.g. parser returns empty `elements`/`relationships`), or response is plain text (no JSON block). Ensures the model doesn’t reply with JSON when we want analysis. |
| **EXPORT / XML** | User message: same small model XML + “Return the model as Open Exchange XML” or “Export the model”. | Response is **well-formed XML** (parse with a simple XML parser). Optionally: root is `model`, contains `archimateModel`, and has expected namespaces; optionally validate against ArchiMate 3.2 diagram/model schema. |

So: **analysis** = “describe/view” → assert no JSON / no import; **XML validation** = “return/export model” → assert valid XML (and optionally schema). The existing **schema validator** in the codebase validates **parsed JSON** (element/relationship types); it does not validate Open Exchange XML. For export, you’d add a separate check (e.g. DOM/SAX parse + optional schema validation).

---

## 4. What test data to use for Ollama integration tests

**Model XML (user message payload)**

- Use a **small but realistic** Open Exchange snippet so the prompt is meaningful and fits in context:
  - e.g. one or two folders, a few elements (BusinessActor, BusinessProcess, ApplicationComponent), one diagram with a couple of nodes, and 1–2 relationships.
- Keep it **fixed** (same string in the test) so the only variable is the model’s reply. Size: a few hundred to ~1–2k characters so it stays within typical limits and is easy to maintain.
- Example: “Minimal model with one BusinessActor, one BusinessRole, one AssignmentRelationship, and one view with one node.” That’s enough for “describe this view” and “add an element” and “return the model”.

**Prompts**

- **CHANGES:** e.g. “Add a BusinessActor named IntegrationTestUser.” (simple, one element).
- **ANALYSIS:** e.g. “Describe this model in one sentence.” or “What elements are in the first view?”
- **EXPORT:** e.g. “Return the model as Open Exchange XML.”

**Assertions**

- **CHANGES:** `ArchiMateLLMResultParser.parse(response)` returns non-empty elements or relationships; `ArchiMateSchemaValidator.validate(parsed)` has no errors (or document allowed warnings).
- **ANALYSIS:** `ArchiMateLLMResultParser.parse(response)` yields empty import data (no elements/relationships/diagram/remove*), or the response string does not contain a JSON object with `"elements"`.
- **EXPORT:** Parse response as XML (e.g. `DocumentBuilder` or similar); assert root element and optionally structure; optionally run schema validation.

**Where to put the data**

- Small model XML: a **constant** in the integration test class (or a small `.xml` file in `src/test/resources`). Same for the three prompt strings.
- No need for a large or production model; the goal is to exercise the pipeline with a real LLM, not to stress-test with huge payloads.

---

## 5. Implementation: OllamaIntegrationTest

The project includes **OllamaIntegrationTest** (in `archigpt-tests`), which implements the above:

- **Skip when Ollama not reachable:** `@Before` calls `OllamaClient.checkConnection()`; if false, all tests in the class are skipped via `Assume.assumeTrue`.
- **CHANGES:** User message = fixed `MODEL_XML` + “Add a BusinessActor named IntegrationTestUser.” Asserts: response parses as JSON, at least one element or relationship; when Archi is on classpath, `ArchiMateSchemaValidator.validate(parsed)` returns no errors.
- **ANALYSIS:** User message = same model XML + “Describe this model in one sentence.” Asserts: parsed result has empty elements/relationships/diagram (i.e. response is plain text, not CHANGES JSON).
- **EXPORT:** User message = same model XML + “Return the model as Open Exchange XML.” Asserts: response contains XML; after extracting/unescaping (markdown, `&lt;`/`\u003c`), it parses as well-formed XML with root `model`.

Test data: a minimal Open Exchange snippet (`MODEL_XML`: two folders, one view) and three fixed prompts. Run with Ollama started (e.g. `ollama serve`) to execute these tests; otherwise they are skipped.

---

## 6. Summary

| Question | Answer |
|----------|--------|
| **Do current tests use real Ollama?** | Unit tests (OllamaClientTest) use a mock server. **OllamaIntegrationTest** calls real Ollama when reachable. |
| **What test data is used?** | Mock responses; hardcoded JSON (parser, import flow); programmatic `ArchiMateLLMResult` (validator); minimal model XML and plain text (analysis use case); short selection and prompt strings. Integration tests use fixed `MODEL_XML` and three prompts. |
| **Should you test against Ollama?** | Optional. **OllamaIntegrationTest** is included; it is skipped when Ollama is not reachable. |
| **Analysis and XML validation?** | Yes: ANALYSIS = “describe” → assert no import JSON; EXPORT = “return model as XML” → assert well-formed XML. |
| **What test data for Ollama tests?** | Small, fixed Open Exchange model XML (see `MODEL_XML` in OllamaIntegrationTest); fixed prompts for add, describe, export; assert on **shape** (parses, validates, is XML) not exact content. |
