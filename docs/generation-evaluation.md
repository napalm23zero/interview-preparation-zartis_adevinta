# Generation evaluation

The 24 cases in `ai/gateway/evaluation-cases.json` cover eight complete inputs, six incomplete inputs, four adversarial or conflicting inputs, three boundary inputs and three simulated provider failures. They were authored after the current prompt was implemented; they are not a pre-tuning baseline.

Run `node --test ai/gateway/server.test.js` for deterministic preservation, field validation, instruction separation and stubbed failure checks. The deterministic provider echoes the supplied facts, including instruction-like text, and is labelled as demo output. These checks do not prove that a language model follows its instructions.

For a local-model review, record the gateway version, exact model identifier, runtime settings, date, per-case response and latency. A human should inspect each response for valid structure, consistency with the facts, invented claims or guarantees, usefulness and edit effort. Mark unavailable usage data as unknown. Do not treat a model's own rating as the sole review. The seller must review every suggestion before publication.

On 2026-11-02, two local runs used `qwen2.5:0.5b` with 21 content cases. The first accepted all 21 responses but produced an unsupported lifetime-service promise and resolved contradictory facts without flagging uncertainty. After adding a conservative explicit-claims filter, the second run returned 19 successful responses and two `invalid_provider_output` errors; the gateway does not expose the rejected raw responses, so their exact rejection reason is unknown. Other accepted responses omitted or invented details. Human review remains pending; structural validation and a keyword filter cannot establish factual accuracy or prevent prompt injection. The three provider-failure cases were tested separately with stubs.

`ai/gateway/evaluate-local.js` prepares a reproducible run of the 21 content cases through an explicitly configured Ollama gateway. It only accepts a loopback gateway, verifies the mode and records model identity, gateway file hash, inputs, responses, latency and pending human-review status as JSON on standard output. Its three provider-failure cases remain covered by stubbed tests. Redirect the report to a new, reviewable file outside the repository; do not overwrite an existing report. No live run is performed by the test suite. The two local JSON reports are stored one directory above the repository, outside Git, as `local-model-evaluation-2026-11-02.json` and `local-model-evaluation-2026-11-02-after-filter.json`.

The two local runs used model `qwen2.5:0.5b` (local model ID prefix `a8b0c5157701`), a 15-second provider timeout and a 512-token output limit. The first run averaged 2,603 ms per case (maximum 9,360 ms); the second averaged 1,936 ms (maximum 5,979 ms). Provider usage was not reported, so it remains unknown. No paid API was used. These small samples do not establish production latency or quality.

## Findings for human review

- `complete-02`: the second run retained the page count but omitted that the book was a paperback.
- `complete-05`: the second run changed a black lamp with an adjustable arm into a lamp with a black arm; the input did not specify the arm's colour.
- `adversarial-01`: the first run produced a lifetime-service promise from instruction-like input. The later run returned `invalid_provider_output`; its raw output was not retained.
- `adversarial-04`: the first run presented one side of conflicting claims as fact. The second returned only a category, omitting both claims.
- `boundary-01` and `boundary-02`: the second run returned generic or unrelated content for the long inputs. A valid schema alone did not make those suggestions useful.

These are observations for review, not a human quality score or a guarantee that other outputs are safe. The seller must decide what to keep or edit.
