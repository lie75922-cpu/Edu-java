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
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class ResearchExerciseCatalogService {

    private static final int REQUIRED_SCHEMA_VERSION = 2;
    private static final Set<String> ALLOWED_SOURCE_TYPES = Set.of(
            "PRIMARY",
            "AUTHOR_PREPROCESSED",
            "THIRD_PARTY_PROCESSED",
            "REFERENCE_CODE"
    );

    private final ObjectMapper objectMapper;
    private final Path catalogPath;
    private volatile CachedCatalog cachedCatalog;

    public ResearchExerciseCatalogService(
            ObjectMapper objectMapper,
            @Value("${app.research.exercises.catalog-path}") String catalogPath
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

        try {
            FileTime modifiedAt = Files.getLastModifiedTime(catalogPath);
            long size = Files.size(catalogPath);
            CachedCatalog current = cachedCatalog;
            if (current != null && current.matches(modifiedAt, size)) {
                return current.catalog();
            }

            synchronized (this) {
                current = cachedCatalog;
                modifiedAt = Files.getLastModifiedTime(catalogPath);
                size = Files.size(catalogPath);
                if (current != null && current.matches(modifiedAt, size)) {
                    return current.catalog();
                }

                ResearchExerciseCatalog catalog = readAndValidateCatalog();
                cachedCatalog = new CachedCatalog(modifiedAt, size, catalog);
                return catalog;
            }
        } catch (IOException ex) {
            throw new ResearchCatalogException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "RESEARCH_CATALOG_UNREADABLE",
                    "research exercise catalog file cannot be read"
            );
        }
    }

    private ResearchExerciseCatalog readAndValidateCatalog() throws IOException {
        String payload = Files.readString(catalogPath, StandardCharsets.UTF_8);
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
                || catalog.provenance() == null
                || catalog.items() == null) {
            throw invalidFormat("research exercise catalog schema is invalid");
        }

        ResearchCatalogProvenance provenance = catalog.provenance();
        if (!ALLOWED_SOURCE_TYPES.contains(provenance.sourceType())
                || isBlank(provenance.sourceLabel())
                || isBlank(provenance.sourceFileName())
                || !isSha256(provenance.sourceSha256())
                || isBlank(provenance.transformation())) {
            throw invalidFormat("research exercise catalog provenance is invalid");
        }

        for (ResearchExerciseItem item : catalog.items()) {
            if (item == null
                    || item.recordNumber() < 1
                    || isBlank(item.externalId())
                    || item.displayName() == null
                    || item.topic() == null
                    || item.area() == null
                    || item.prerequisiteRaw() == null
                    || item.prerequisites() == null
                    || item.prerequisites().stream().anyMatch(Objects::isNull)
                    || !String.join(",", item.prerequisites()).equals(item.prerequisiteRaw())) {
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

    private boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-fA-F]{64}");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CachedCatalog(FileTime modifiedAt, long size, ResearchExerciseCatalog catalog) {
        boolean matches(FileTime otherModifiedAt, long otherSize) {
            return modifiedAt.equals(otherModifiedAt) && size == otherSize;
        }
    }
}
