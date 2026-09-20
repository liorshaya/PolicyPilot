# Recordings

What a provider actually answered, so every test after the first live run is offline and deterministic
(Document 6, AI Layer Testing).

```
recordings/<provider>/<prompt>/<version>/<inputHash>.json        what RecordedGateway replays
recordings/<provider>/<prompt>/<version>/<inputHash>.runN.json   the numbered runs behind a gate's measurement
```

The numbered files are every answer of a batch as it came. The file without a number is a copy of the first run
that validated: a gate that allows one failure in ten must not leave that one failure as the answer CI replays.

Each file holds the request (prompt name, version, model, the hash of the rendered prompts, and the prompts
themselves) and the raw response. The hash is the SHA-256 of `system + U+001F + user`, which is what the gateway
looks a recording up by: a new prompt version or a new input needs one live run to create its recording, and a
test that cannot find one fails rather than answering something else.

Recordings are made by `LiveAuthoringRecordingIT`, which is tagged `live` and never runs in CI:

```
OPENAI_API_KEY=... ./mvnw verify -Dtest=none -Dit.test=LiveAuthoringRecordingIT -Dlive.tag= \
    -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -Djacoco.skip=true
```

Hand-written adversarial answers live beside the real ones with a `"synthetic": true` marker (Document 6).
