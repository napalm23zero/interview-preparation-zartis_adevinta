# Generation evaluation

The 24 cases in `ai/gateway/evaluation-cases.json` cover eight complete inputs, six incomplete inputs, four adversarial or conflicting inputs, three boundary inputs and three simulated provider failures. They were authored after the current prompt was implemented; they are not a pre-tuning baseline.

Run `node --test ai/gateway/server.test.js` for deterministic preservation, field validation, instruction separation and stubbed failure checks. The deterministic provider echoes the supplied facts, including instruction-like text, and is labelled as demo output. These checks do not prove that a language model follows its instructions.

For a local-model review, record the gateway version, exact model identifier, runtime settings, date, per-case response and latency. A human should inspect each response for valid structure, consistency with the facts, invented claims or guarantees, usefulness and edit effort. Mark unavailable usage data as unknown. Do not treat a model's own rating as the sole review. The seller must review every suggestion before publication.

No local-model quality evaluation is claimed by this document. The earlier single live sample added unsupported adjectives; the present contract rejects malformed output but cannot establish factual accuracy or prevent prompt injection.

`ai/gateway/evaluate-local.js` prepares a reproducible run of the 21 content cases through an explicitly configured Ollama gateway. It only accepts a loopback gateway, verifies the mode and records model identity, gateway file hash, inputs, responses, latency and pending human-review status as JSON on standard output. Its three provider-failure cases remain covered by stubbed tests. Redirect the report to a new, reviewable file outside the repository; do not overwrite an existing report. No live run is performed by the test suite.
