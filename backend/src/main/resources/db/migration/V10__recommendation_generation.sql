CREATE TABLE matching.recommendation_generation (
    profile_id bigint PRIMARY KEY REFERENCES profile.professional_profile(id) ON DELETE CASCADE,
    profile_version bigint NOT NULL,
    catalog_version integer NOT NULL,
    generated_at timestamptz NOT NULL DEFAULT now()
);
