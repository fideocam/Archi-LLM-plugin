# ArchiGPT Tests

Unit tests for the ArchiGPT plugin: Ollama client, ArchiMate JSON parsing, validation, and import.

## Running tests

From the repo root, build and install the plugin first, then run tests:

```bash
# Install the plugin (requires Archi target with -P with-archi if building from scratch)
mvn install -pl com.archimatetool.archigpt -am -P with-archi

# Run tests
mvn test -pl archigpt-tests
```

Or run both in one go (plugin must build successfully):

```bash
mvn install -P with-archi
```

## What is tested

- **OllamaClientTest**: Calls a mock HTTP server (no real Ollama). Covers chat API with system prompt, response parsing, and error handling (5xx, 404, malformed response).
- **OllamaIntegrationTest**: Calls a **real Ollama** instance. *Skipped when Ollama is not reachable* (e.g. not running). Tests: CHANGES (“Add a BusinessActor”) → response parses as JSON and passes validator; ANALYSIS (“Describe this model”) → response is plain text (no import data); EXPORT (“Return the model as XML”) → response is well-formed XML. Start Ollama (e.g. `ollama serve`) to run these.
- **ArchiMateLLMResultParserTest**: Good JSON (elements + relationships), markdown-wrapped JSON, faulty input (empty, null, non-JSON, missing fields, error field).
- **ArchiMateSchemaValidatorTest**: Valid data (no errors), invalid element types, missing ids, invalid relationship types, source/target not found. *Skipped when Archi model is not on classpath.*
- **ArchiMateImportFlowTest**: Parse → validate → import into an in-memory model. *Skipped when Archi model is not on classpath.*
- **ArchiMateAnalysisUseCaseTest**: Enterprise-architecture analysis use cases; user message format; plain-text response not parsed as import.
- **SelectionInPromptTest**: Selection context and prompt included in user message.

Validator and import tests require the Archi model (`IArchimatePackage`, `IArchimateFactory`) on the classpath; they are skipped automatically when absent.
