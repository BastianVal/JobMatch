package mx.jobmatch.catalog.adapters.web;

import mx.jobmatch.catalog.application.CatalogQueryPort;
import mx.jobmatch.catalog.domain.CatalogEntry;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Profile("api")
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    private final CatalogQueryPort catalog;
    public CatalogController(CatalogQueryPort catalog) { this.catalog = catalog; }

    @GetMapping("/roles")
    List<CatalogEntry> roles(@RequestParam(defaultValue = "") String q,
                             @RequestParam(defaultValue = "20") int limit) {
        return catalog.roles(q, limit);
    }

    @GetMapping("/skills")
    List<CatalogEntry> skills(@RequestParam(defaultValue = "") String q,
                              @RequestParam(defaultValue = "20") int limit) {
        return catalog.skills(q, limit);
    }
}
