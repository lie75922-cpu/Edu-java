# MODEL-1 Bad Cases and Boundaries

| Candidate | False positives | False negatives | Final-test rows on Exercises unseen in train/history |
| --- | ---: | ---: | ---: |
| C40_TOPIC | 40,913 | 9,562 | 385 |
| C_FINE_EXERCISE | 41,193 | 11,557 | 385 |

## Fine diagnosis exceptions retained

| Check | Value |
| --- | --- |
| Missing train-covered fine ModelConcept IDs | 57, 73, 75, 120, 143, 193, 240, 252, 255, 291, 311, 323, 388, 425, 426, 428, 448, 476, 477, 514, 596 |
| Constant fine ModelConcept IDs | none |
| NaN / infinite mastery values | 0 / 0 |

No bad case is removed by changing frozen external membership, chronology,
Topic semantics, seed, configuration, or final-test membership. No RCD, ORCDF,
or GEAR-CD run occurs unless the separately recorded Concept Gate passes.
