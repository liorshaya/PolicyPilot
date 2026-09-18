## What this pull request ships

<!-- One task row of docs/07-work-plan.md; name the day and the row. -->

## Definition of Done (docs/06-test-strategy.md)

A line that cannot be satisfied says why here rather than being skipped.

- [ ] The tests for the task were listed before the code was written, from the specification tables, and each listed test exists.
- [ ] Every new public method of `engine`, `rules`, the validators and the security filters has unit tests for its normal path, its boundaries and its failure path.
- [ ] Every new endpoint has a contract test (status codes, envelope, authentication) and an integration test through the service layer.
- [ ] Every new use of the model has a recorded test with at least one malformed and one adversarial recording.
- [ ] Every new security control from Document 5 has its named test.
- [ ] Coverage gates for the touched packages pass (`./mvnw verify`), and the mutation score of `engine` and `rules` has not dropped.
- [ ] The Python reference implementation and its fixtures were updated when the DSL or the engine semantics changed, and both implementations still agree on the conformance suite.
- [ ] The fixtures the task adds are synthetic, deterministic and committed; no test depends on the network, the clock, the locale or test order.
- [ ] CI is green, including the security gates.
- [ ] The traceability matrix row for the requirement names the new tests.

## Only when a prompt changed

- [ ] New recordings for its inputs
- [ ] The evaluation report on both providers
- [ ] The red-team result
- [ ] The CHANGELOG entry of the prompt version
