package mx.jobmatch.catalog.application;

import mx.jobmatch.catalog.domain.CatalogEntry;

import java.util.List;

public interface CatalogQueryPort {
    List<CatalogEntry> roles(String query, int limit);
    List<CatalogEntry> skills(String query, int limit);
    int currentVersion();
}
