# Retrieval, first live pass

Work Plan day 8: one live embedding pass over the 30 questions of `fixtures/eval/questions.json`, each
asked of its own policy, published with its expected rule set and embedded by `bge-m3`.
Written by `LiveRetrievalRecordingIT`; the vectors are in `fixtures/eval/recordings/ollama/embedding/`.
Recall at 8 counts the question's `expectedChunks` among the chunks kept; a refusal question is right
to be stopped by the Threshold, but day 9's answer prompt may also refuse it.

| Question | Language | Refusal | Stopped | Best cosine | Recall at 8 | Missed | Kept |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Q-01 | he | no | no | 0.553 | 1/2 | r:R-330 | p:9, r:R-900, r:R-320, p:6, p:7, r:R-420, r:R-100, r:R-110 |
| Q-02 | he | no | no | 0.537 | 2/2 |  | r:R-330, p:7, p:9, r:R-900, p:6, r:R-100, r:R-320, r:R-110 |
| Q-03 | he | no | no | 0.741 | 2/2 |  | r:R-130, p:2, r:R-116, p:8, p:6, p:5, r:R-010, p:3 |
| Q-04 | he | yes | no | 0.572 | n/a |  | p:5, r:R-120, p:2, p:6, r:R-010, r:R-200, p:1, p:8 |
| Q-05 | en | no | no | 0.673 | 2/2 |  | r:R-220, r:R-330, r:R-200, p:7, r:R-020, p:9, p:3, r:R-320 |
| Q-06 | he | no | no | 0.703 | 3/3 |  | p:6, r:R-320, r:R-020, r:R-200, p:2, r:R-410, r:R-120, p:5 |
| Q-07 | he | no | no | 0.351 | 1/2 | p:6 | r:R-320, r:R-330, r:R-420, r:R-140, p:9, r:R-900, r:R-160, r:R-220 |
| Q-08 | he | no | no | 0.471 | n/a |  | p:7, r:R-220, r:R-100, r:R-140, r:R-120, r:R-200, p:9, r:R-150 |
| Q-09 | en | no | no | 0.653 | 2/2 |  | r:R-130, r:R-116, r:R-150, r:R-160, r:R-010, p:8, p:2, p:1 |
| Q-10 | he | yes | no | 0.647 | n/a |  | r:R-120, p:2, p:6, p:8, p:5, r:R-115, r:R-020, r:R-010 |
| Q-11 | he | no | no | 0.663 | 2/2 |  | r:R-170, p:4, r:R-410, p:6, r:R-020, r:R-420, p:3, r:R-150 |
| Q-12 | he | no | no | 0.654 | 2/2 |  | r:R-420, r:R-410, r:R-020, r:R-320, r:R-010, p:6, p:4, r:R-150 |
| Q-13 | en | no | no | 0.710 | 2/2 |  | r:R-100, r:R-115, r:R-110, r:R-116, r:R-410, p:1, p:8, p:3 |
| Q-14 | en | no | no | 0.471 | 3/3 |  | r:R-420, r:R-320, r:R-330, r:R-310, p:9, p:3, r:R-020, r:R-150 |
| Q-15 | en | yes | no | 0.589 | n/a |  | r:R-200, r:R-900, p:2, r:R-020, p:3, p:1, r:R-320, p:7 |
| Q-16 | he | no | no | 0.633 | 2/2 |  | p:3, p:1, r:R-150, p:9, r:R-140, r:R-900, r:R-160, p:7 |
| Q-17 | he | no | no | 0.715 | 2/2 |  | p:8, r:R-330, r:R-130, r:R-150, p:10, r:R-160, p:3, p:2 |
| Q-18 | he | no | no | 0.677 | 2/2 |  | r:R-180, p:5, r:R-170, r:R-220, p:8, p:4, p:3, r:R-150 |
| Q-19 | he | no | no | 0.655 | 2/2 |  | r:R-010, r:R-120, p:2, p:3, r:R-900, p:7, r:R-110, r:R-410 |
| Q-20 | he | no | no | 0.593 | 2/2 |  | p:7, r:R-310, p:5, r:R-110, r:R-900, p:1, p:2, r:R-410 |
| Q-21 | he | no | no | 0.775 | 2/2 |  | r:R-020, p:4, r:R-120, p:2, r:R-040, r:R-050, p:3, p:1 |
| Q-22 | he | yes | no | 0.565 | n/a |  | p:1, r:R-030, r:R-040, r:R-050, p:7, p:2, p:3, p:8 |
| Q-23 | he | no | no | 0.688 | 2/2 |  | p:2, r:R-030, p:6, p:1, p:4, r:R-100, p:7, r:R-310 |
| Q-24 | he | no | no | 0.717 | 2/2 |  | p:5, r:R-010, r:R-020, r:R-300, p:1, r:R-130, r:R-120, r:R-100 |
| Q-25 | he | no | no | 0.668 | 2/2 |  | p:3, r:R-040, r:R-120, r:R-020, r:R-030, p:7, r:R-110, p:4 |
| Q-26 | he | yes | no | 0.605 | n/a |  | p:1, p:3, p:6, r:R-100, p:2, r:R-010, r:R-110, r:R-900 |
| Q-27 | en | no | no | 0.794 | 2/2 |  | p:2, r:R-020, r:R-030, r:R-010, p:1, p:7, p:4, r:R-410 |
| Q-28 | en | no | no | 0.684 | 2/2 |  | r:R-100, p:6, p:1, p:7, r:R-300, r:R-020, p:5, p:4 |
| Q-29 | en | no | no | 0.628 | 1/2 | r:R-100 | p:7, p:1, p:2, r:R-110, r:R-120, p:3, r:R-300, p:4 |
| Q-30 | en | yes | no | 0.559 | n/a |  | p:1, p:5, p:3, p:7, r:R-900, p:2, r:R-100, r:R-110 |

Overall recall at 8: 45 of 48 expected chunks.
