package mx.jobmatch.profile.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SkillDurationCalculator {
    private SkillDurationCalculator() {}

    public static Projection calculate(Collection<ProfileDraft.TrajectoryItem> evidence, YearMonth currentMonth) {
        Set<YearMonth> professional = new HashSet<>();
        Map<YearMonth, BigDecimal> practical = new HashMap<>();
        for (var item : evidence) {
            YearMonth start = YearMonth.of(item.startYear(), item.startMonth());
            YearMonth end = item.current() ? currentMonth : YearMonth.of(item.endYear(), item.endMonth());
            if (end.isBefore(start) || end.isAfter(currentMonth)) throw new IllegalArgumentException("Invalid trajectory dates");
            for (YearMonth month = start; !month.isAfter(end); month = month.plusMonths(1)) {
                if (item.type().professional()) professional.add(month);
                practical.merge(month, item.type().weight(), BigDecimal::max);
            }
        }
        BigDecimal weighted = practical.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        return new Projection(professional.size(), weighted);
    }

    public record Projection(int professionalMonths, BigDecimal weightedPracticalMonths) {}
}
