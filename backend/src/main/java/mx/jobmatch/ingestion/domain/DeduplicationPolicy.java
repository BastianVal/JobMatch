package mx.jobmatch.ingestion.domain;

import java.util.HashSet;
import java.util.Set;

public final class DeduplicationPolicy {
    public static final String VERSION = "dedup-v1";
    private DeduplicationPolicy() {}

    public static Evaluation evaluate(NormalizedPosting incoming, Candidate existing) {
        double title = similarity(incoming.titleNormalized(), existing.titleNormalized());
        double employer = similarity(incoming.employerNormalized(), existing.employerNormalized());
        double description = similarity(PostingNormalizer.key(incoming.description()), PostingNormalizer.key(existing.description()));
        double location = similarity(incoming.cityNormalized(), existing.cityNormalized());
        double salaryDate = salaryDate(incoming, existing);
        double score = title * .30 + employer * .25 + description * .25 + location * .10 + salaryDate * .10;
        Decision decision = score >= .92 && title >= .85 && employer >= .90
                ? Decision.AUTO_MERGED : score >= .80 ? Decision.KEPT_SEPARATE : Decision.DISTINCT;
        return new Evaluation(decision, Math.round(score * 10000d) / 10000d, title, employer);
    }

    private static double salaryDate(NormalizedPosting incoming, Candidate existing) {
        boolean salary = incoming.salaryMaxMonthly() != null && existing.salaryMaxMonthly() != null;
        double salaryScore = salary ? 1d - Math.min(1d, incoming.salaryMaxMonthly().subtract(existing.salaryMaxMonthly())
                .abs().divide(incoming.salaryMaxMonthly().max(existing.salaryMaxMonthly()), 6, java.math.RoundingMode.HALF_UP).doubleValue()) : .5;
        long days = Math.abs(java.time.Duration.between(incoming.publishedAt(), existing.publishedAt()).toDays());
        return salaryScore * .5 + Math.max(0d, 1d - days / 30d) * .5;
    }

    static double similarity(String first, String second) {
        if (first == null || second == null) return 0;
        if (first.equals(second)) return 1;
        Set<String> a = new HashSet<>(java.util.Arrays.asList(first.split(" ")));
        Set<String> b = new HashSet<>(java.util.Arrays.asList(second.split(" ")));
        Set<String> intersection = new HashSet<>(a); intersection.retainAll(b);
        Set<String> union = new HashSet<>(a); union.addAll(b);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    public enum Decision { AUTO_MERGED, KEPT_SEPARATE, DISTINCT }
    public record Evaluation(Decision decision, double score, double titleScore, double employerScore) {}
    public record Candidate(long internalId, String titleNormalized, String employerNormalized, String description,
                            String cityNormalized, java.math.BigDecimal salaryMaxMonthly, java.time.Instant publishedAt) {}
}
