package mx.jobmatch.matching.adapters.worker;

import mx.jobmatch.matching.application.MatchingRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Profile("worker")
@Component
public class MatchingCacheMaintenance {
    private final MatchingRepository matches;
    public MatchingCacheMaintenance(MatchingRepository matches){this.matches=matches;}
    @Scheduled(cron="0 30 3 * * SUN",zone="UTC")
    public void removeObsoleteSnapshots(){matches.deleteObsolete();}
}
