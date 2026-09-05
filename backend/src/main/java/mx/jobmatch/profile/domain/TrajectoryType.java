package mx.jobmatch.profile.domain;

import java.math.BigDecimal;

public enum TrajectoryType {
    EMPLOYMENT("1.00"),
    INTERNSHIP("0.70"),
    TECHNICAL_SOCIAL_SERVICE("0.60"),
    TECHNICAL_VOLUNTEERING("0.55"),
    PERSONAL_PROJECT("0.50"),
    OPEN_SOURCE("0.50"),
    ACADEMIC_PROJECT("0.35"),
    STUDY("0.10");

    private final BigDecimal weight;
    TrajectoryType(String weight) { this.weight = new BigDecimal(weight); }
    public BigDecimal weight() { return weight; }
    public boolean professional() { return this == EMPLOYMENT; }
}
