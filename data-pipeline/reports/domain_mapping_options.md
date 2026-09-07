# Domain Mapping Options — Evidence, Not a Schema Freeze

| Option | Evidence support | Model implications | Platform implications | DATA-0 conclusion |
| --- | --- | --- | --- | --- |
| A. Exercise equals KnowledgePoint | Metadata carries prerequisite text at Exercise granularity, but duplicate IDs and raw graph cycles exist. | Direct but conflates capability unit and concept. | Cannot be a complete Question bank. | Not safe to freeze. |
| B. Topic equals KnowledgePoint, Exercise maps to Topic | Topic and Area hierarchy exists for most metadata rows. | A provisional Exercise-to-Topic matrix can support NCDM-style adapters. | More explainable aggregation. | Candidate only; Topic semantics require approval. |
| C. ExerciseUnit plus KnowledgePoint plus Topic and Area | Preserves raw Exercise, keeps a future conceptual layer explicit, and retains hierarchy evidence. | Supports adapters without asserting a final concept source. | Aligns with the data-domain boundary ADR. | Recommended comparison baseline, not a V0.2 decision. |

No KnowledgePoint identifier is fabricated in this audit. The Owner and architect
must choose domain meaning after reviewing the evidence.
