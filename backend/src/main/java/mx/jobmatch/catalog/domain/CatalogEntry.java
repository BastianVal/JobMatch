package mx.jobmatch.catalog.domain;

import java.util.List;
import java.util.UUID;

public record CatalogEntry(UUID id, String slug, String name, List<String> aliases, int catalogVersion) {}
