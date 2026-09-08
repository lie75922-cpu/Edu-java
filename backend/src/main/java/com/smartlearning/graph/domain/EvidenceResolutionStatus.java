package com.smartlearning.graph.domain;

public enum EvidenceResolutionStatus {
    RESOLVED,
    UNRESOLVED_IDENTITY,
    UNMAPPED_SOURCE,
    UNMAPPED_TARGET,
    AMBIGUOUS_SOURCE_MAPPING,
    AMBIGUOUS_TARGET_MAPPING,
    REJECTED_SELF_LOOP
}
