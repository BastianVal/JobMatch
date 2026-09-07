-- The previous candidate query capped all target roles together. Rebuild the
-- derived projection after changing the cap to 2,000 candidates per role.
DELETE FROM matching.recommendation_generation;
