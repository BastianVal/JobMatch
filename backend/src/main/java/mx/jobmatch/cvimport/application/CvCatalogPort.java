package mx.jobmatch.cvimport.application;

import java.util.List;
import java.util.UUID;

public interface CvCatalogPort {
    List<SkillMatch> findSkillsIn(String normalizedText);
    record SkillMatch(UUID id, String name) {}
}
