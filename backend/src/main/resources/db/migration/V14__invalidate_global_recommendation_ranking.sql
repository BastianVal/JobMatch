-- Recommendations are derived data. Force regeneration after replacing the
-- global-score ordering with balanced ordering across target roles.
DELETE FROM matching.recommendation_generation;
