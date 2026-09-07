# DATA-0 Source Provenance

| ID | Type | Status | Label | License boundary | Local path |
| --- | --- | --- | --- | --- | --- |
| S1_PRIMARY_REGISTRY | PRIMARY | NOT_ACQUIRED | PSLC DataShop Junyi Academy Math Practicing Log | Junyi documentation states non-commercial use only. | N/A |
| S1_LOCAL_MIRROR | THIRD_PARTY_PROCESSED | USED_WITH_PROVENANCE_LIMIT | User-provided USTC-mirror Junyi archive and extracted CSV files | Mirror distribution does not prove primary provenance; Junyi non-commercial boundary retained. | D:\Code\java\data-pipeline\data |
| S2_RCD | REFERENCE_CODE | INSPECTED | bigdata-ustc/RCD | No root license file declared in the inspected repository. | D:\Code\java\Edu-java-submit\data-pipeline\.data\reference\RCD |
| S3_INSCD | REFERENCE_CODE | INSPECTED | ECNU-ILOG/InsCD | MIT license in inspected repository. | D:\Code\java\Edu-java-submit\data-pipeline\.data\reference\InsCD |
| S4_GEAR_CD | THIRD_PARTY_PROCESSED | INSPECTED_NO_TRAINING | Figshare GEAR-CD preprocessed archive | Figshare says CC BY 4.0; embedded Junyi documentation says non-commercial. The stricter boundary controls. | D:\Code\java\Edu-java-submit\data-pipeline\.data\reference\GEAR-CD |
| S5_NEURAL_CD | REFERENCE_CODE | INSPECTED | bigdata-ustc/Neural_Cognitive_Diagnosis-NeuralCD | No root license file declared in the inspected repository. | D:\Code\java\Edu-java-submit\data-pipeline\.data\reference\NeuralCD |
| S6_ORCDF | REFERENCE_CODE | INSPECTED | ECNU-ILOG/ORCDF | No root license file declared in the inspected repository. | D:\Code\java\Edu-java-submit\data-pipeline\.data\reference\ORCDF |

## Integrity evidence policy

The governing repository rule prohibits content digests. This audit records explicit source URIs, acquisition state, local paths, file sizes, schemas, sample records, deterministic selection settings, and local member lists. That does not meet the conflicting Issue #2 digest requirement and remains a Final Gate condition.

## Source detail

### S1_PRIMARY_REGISTRY — PSLC DataShop Junyi Academy Math Practicing Log

- URI: https://pslcdatashop.web.cmu.edu/DatasetInfo?datasetId=1275
- Acquired: Not acquired: unauthenticated Files view reported login required.
- File evidence: No local direct-download artifact.
- Purpose: Authoritative provenance target; not used as a local input.

### S1_LOCAL_MIRROR — User-provided USTC-mirror Junyi archive and extracted CSV files

- URI: USTC mirror URL was not recorded in the local acquisition handoff.
- Acquired: Local archive observed 2026-09-07; original acquisition time unverified.
- File evidence: {"metadata": {"path": "D:\\Code\\java\\data-pipeline\\data\\interim\\junyi_metadata\\junyi_Exercise_table.csv", "size_bytes": 139957, "last_modified_utc": "2018-08-19T11:51:57.377607Z"}, "problem_log": {"path": "D:\\Code\\java\\data-pipeline\\data\\interim\\junyi_original\\junyi_ProblemLog_original.csv", "size_bytes": 2668566436, "last_modified_utc": "2015-06-18T06:57:37.301787Z"}}
- Purpose: Actual schema, EDA, graph, sampling, and materialisation input.

### S2_RCD — bigdata-ustc/RCD

- URI: https://github.com/bigdata-ustc/RCD
- Acquired: Cloned for local reference during DATA-0.
- File evidence: Junyi graph pairs and JSON files inspected locally.
- Purpose: RCD graph semantics and model-input comparison.

### S3_INSCD — ECNU-ILOG/InsCD

- URI: https://github.com/ECNU-ILOG/InsCD
- Acquired: Cloned for local reference during DATA-0.
- File evidence: Junyi734 datahub adapter inspected locally.
- Purpose: NCDM, RCD, and ORCDF interface comparison.

### S4_GEAR_CD — Figshare GEAR-CD preprocessed archive

- URI: https://figshare.com/articles/journal_contribution/The_preprocessed_dataset_and_code_for_GEAR-CD/29231882
- Acquired: Downloaded for local schema inspection during DATA-0.
- File evidence: Junyi processed archive exposes RCD-like JSON and graph pair files.
- Purpose: GEAR-CD input and cost-readiness audit only; no training.

### S5_NEURAL_CD — bigdata-ustc/Neural_Cognitive_Diagnosis-NeuralCD

- URI: https://github.com/bigdata-ustc/Neural_Cognitive_Diagnosis-NeuralCD
- Acquired: Cloned for local reference during DATA-0.
- File evidence: Response data-loader contract inspected locally.
- Purpose: NCDM baseline input comparison.

### S6_ORCDF — ECNU-ILOG/ORCDF

- URI: https://github.com/ECNU-ILOG/ORCDF
- Acquired: Cloned for local reference during DATA-0.
- File evidence: Junyi response and Q-matrix configuration inspected locally.
- Purpose: ORCDF response and Q-matrix readiness comparison.
