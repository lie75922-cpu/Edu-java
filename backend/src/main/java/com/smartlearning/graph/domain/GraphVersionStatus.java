package com.smartlearning.graph.domain;

/** States of an immutable published-graph snapshot and its controlled draft lifecycle. */
public enum GraphVersionStatus {
    DRAFT,
    VALIDATING,
    READY,
    PUBLISHING,
    PUBLISHED,
    ARCHIVED,
    VALIDATION_FAILED,
    PROJECTION_FAILED
}
