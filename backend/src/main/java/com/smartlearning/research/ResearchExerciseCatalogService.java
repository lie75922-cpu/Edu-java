package com.smartlearning.research;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class ResearchExerciseCatalogService {

    private static final int REQUIRED_SCHEMA_VERSION = 1;
    private static final String REQUIRED_SOURCE = "Junyi via USTC mirror";

    private final ObjectMapper objectMapper;
    private final Path catalogPath;

    public ResearchExerciseCatalogService(
            ObjectMapper objectMapper,
            @Value("${app.research.exercises.catalog-path:D:/Code/java/data-pipeline/data/processed/junyi_catalog_v1.json}")
            String catalogPath
    ) {
        this.objectMapper = objectMapper;
        this.catalogPath = Path.of(catalogPath);
    }

    public ResearchExercisePage search(String topic, String area, String query, int page, int size) {
        if (page < 0) {
            throw new ResearchCatalogException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new ResearchCatalogException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "size must be between 1 and 100");
        }

        List<ResearchExerciseItem> filtered = loadCatalog().items().stream()
                .filter(item -> exactMatch(item.topic(), topic))
                .filter(item -> exactMatch(item.area(), area))
                .filter(item -> matchesQuery(item, query))
                .sorted(Comparator.comparingInt(ResearchExerciseItem::recordNumber))
                .toList();

        long requestedOffset = (long) page * size;
        int fromIndex = requestedOffset >= filtered.size() ? filtered.size() : (int) requestedOffset;
        int toIndex = Math.min(fromIndex + size, filtered.size());
        return new ResearchExercisePage(filtered.subList(fromIndex, toIndex), filtered.size(), page, size);
    }

    private ResearchExerciseCatalog loadCatalog() {
        if (!Files.isRegularFile(catalogPath)) {
            throw new ResearchCatalogException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "RESEARCH_CATALOG_NOT_FOUND",
                    "research exercise catalog file not found: " + catalogPath
            );
        }

        String payload;
        try {
            payload = Files.readString(catalogPath, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ResearchCatalogException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "RESEARCH_CATALOG_UNREADABLE",
                    "research exercise catalog file cannot be read"
            );
        }

        try {
            ResearchExerciseCatalog catalog = objectMapper.readValue(payload, ResearchExerciseCatalog.class);
            validateCatalog(catalog);
            return catalog;
        } catch (JacksonException ex) {
            throw new ResearchCatalogException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "RESEARCH_CATALOG_INVALID_JSON",
                    "research exercise catalog JSON is invalid"
            );
        }
    }

    private void validateCatalog(ResearchExerciseCatalog catalog) {
        if (catalog == null
                || catalog.schemaVersion() != REQUIRED_SCHEMA_VERSION
                || !REQUIRED_SOURCE.equals(catalog.source())
                || catalog.items() == null) {
            throw invalidFormat("research exercise catalog schema is invalid");
        }
        for (ResearchExerciseItem item : catalog.items()) {
            if (item == null
                    || item.recordNumber() < 1
                    || isBlank(item.externalId())
                    || item.displayName() == null
                    || item.topic() == null
                    || item.area() == null
                    || item.prerequisites() == null
                    || item.prerequisites().stream().anyMatch(Objects::isNull)) {
                throw invalidFormat("research exercise catalog item is invalid");
            }
        }
    }

    private ResearchCatalogException invalidFormat(String message) {
        return new ResearchCatalogException(HttpStatus.INTERNAL_SERVER_ERROR, "RESEARCH_CATALOG_INVALID_FORMAT", message);
    }

    private boolean exactMatch(String value, String expected) {
        return isBlank(expected) || Objects.equals(value, expected);
    }

    private boolean matchesQuery(ResearchExerciseItem item, String query) {
        if (isBlank(query)) {
            return true;
        }
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        return Stream.of(item.externalId(), item.displayName())
                .filter(Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(normalizedQuery));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
