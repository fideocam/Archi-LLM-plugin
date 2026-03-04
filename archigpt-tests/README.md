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
- **ArchiMateLLMResultParserTest**: Good JSON (elements + relationships), markdown-wrapped JSON, faulty input (empty, null, non-JSON, missing fields, error field).
- **ArchiMateSchemaValidatorTest**: Valid data (no errors), invalid element types, missing ids, invalid relationship types, source/target not found. *Skipped when Archi model is not on classpath.*
- **ArchiMateImportFlowTest**: Parse → validate → import into an in-memory model. *Skipped when Archi model is not on classpath.*

Validator and import tests require the Archi model (`IArchimatePackage`, `IArchimateFactory`) on the classpath; they are skipped automatically when absent.
