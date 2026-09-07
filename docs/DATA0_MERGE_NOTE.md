# DATA-0 Merge Note

DATA-0 has been independently reviewed after Codex execution.

Merge is permitted only when the pull-request CI passes all existing repository gates:

- Java backend Maven tests;
- Python/model-service and data-pipeline tests;
- Vue production build;
- Docker Compose configuration validation.

The merge records DATA-0 as `PASSED WITH CONDITIONS` and does not claim that:

- official DataShop source files were directly acquired;
- raw Junyi prerequisite relations are production graph edges;
- any cognitive-diagnosis model has been trained successfully;
- V0.2 product functionality is implemented.

Post-merge work is split into two controlled tracks: `MODEL-0` and `V0.2`, both governed by the corresponding documents in `docs/`.
