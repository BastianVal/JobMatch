package mx.jobmatch.cvimport.adapters.worker;

import mx.jobmatch.cvimport.application.CvImportRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Profile("worker")
@Component
public class CvImportMaintenance {
    private final CvImportRepository imports;
    public CvImportMaintenance(CvImportRepository imports){this.imports=imports;}
    @Scheduled(fixedDelay=3_600_000) public void expire(){imports.expireReady(Instant.now());}
}
