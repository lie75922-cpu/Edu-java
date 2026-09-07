# DATA-0 Bad Cases and Boundaries

1. The local archive is a USTC mirror with no recorded direct source URI; it is not PRIMARY.
2. DataShop Files access exposed a login-required marker during this audit.
3. The actual metadata column is prerequisites, not the singular spelling in public documentation.
4. 2 metadata external IDs are duplicated.
5. 1 in-row duplicate prerequisite tokens are retained.
6. 2 prerequisite self loops are retained; no cycle is auto-deleted.
7. Relationship annotation scores are not converted into graph edges without verified semantics.
8. High-cardinality log columns may be reported as lower bounds under an explicit memory control.
9. Docker daemon was unavailable during environment inspection.
10. Content digests and repository HEAD identifiers are prohibited by the governing repository instruction.
