package mx.jobmatch.profile.domain;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SkillDurationCalculatorTest {
    @Test
    void overlappingMonthsUseHighestWeightAndProfessionalMonthsAreNotInflated() {
        var employment = item(TrajectoryType.EMPLOYMENT, 2024, 1, 2024, 3);
        var internship = item(TrajectoryType.INTERNSHIP, 2024, 2, 2024, 4);
        var secondJob = item(TrajectoryType.EMPLOYMENT, 2024, 2, 2024, 2);

        var result = SkillDurationCalculator.calculate(List.of(employment, internship, secondJob), YearMonth.of(2026, 1));

        assertThat(result.professionalMonths()).isEqualTo(3);
        assertThat(result.weightedPracticalMonths()).isEqualByComparingTo("3.70");
    }

    @Test
    void socialServiceAndTechnicalVolunteeringKeepDifferentWeights() {
        var social = item(TrajectoryType.TECHNICAL_SOCIAL_SERVICE, 2025, 1, 2025, 1);
        var volunteer = item(TrajectoryType.TECHNICAL_VOLUNTEERING, 2025, 2, 2025, 2);

        var result = SkillDurationCalculator.calculate(List.of(social, volunteer), YearMonth.of(2026, 1));

        assertThat(result.professionalMonths()).isZero();
        assertThat(result.weightedPracticalMonths()).isEqualByComparingTo("1.15");
    }

    private ProfileDraft.TrajectoryItem item(TrajectoryType type, int startYear, int startMonth,
                                             int endYear, int endMonth) {
        return new ProfileDraft.TrajectoryItem(UUID.randomUUID(), type, "Actividad", "Organización", null,
                startYear, startMonth, endYear, endMonth, false);
    }
}
