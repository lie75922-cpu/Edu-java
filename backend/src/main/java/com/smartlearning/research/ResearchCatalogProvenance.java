package com.smartlearning.research;

public record ResearchCatalogProvenance(
        String sourceType,
        String sourceLabel,
        String sourceUri,
        String sourceFileName,
        String sourceSha256,
        String acquiredAt,
        String transformation
) {
}
