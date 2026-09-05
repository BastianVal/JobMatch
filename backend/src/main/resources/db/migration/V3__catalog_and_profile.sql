CREATE TABLE catalog.catalog_version (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    version_no integer NOT NULL UNIQUE CHECK (version_no > 0),
    status varchar(20) NOT NULL CHECK (status IN ('PUBLISHED','RETIRED')),
    published_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE catalog.role_family (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    catalog_version_id bigint NOT NULL REFERENCES catalog.catalog_version(id),
    slug varchar(100) NOT NULL,
    display_name varchar(150) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    UNIQUE (catalog_version_id, slug)
);

CREATE TABLE catalog.role_alias (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role_family_id bigint NOT NULL REFERENCES catalog.role_family(id) ON DELETE CASCADE,
    alias citext NOT NULL,
    UNIQUE (role_family_id, alias)
);
CREATE INDEX role_alias_lookup_idx ON catalog.role_alias(alias);

CREATE TABLE catalog.skill (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    catalog_version_id bigint NOT NULL REFERENCES catalog.catalog_version(id),
    slug varchar(120) NOT NULL,
    display_name varchar(150) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    UNIQUE (catalog_version_id, slug)
);

CREATE TABLE catalog.skill_alias (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    skill_id bigint NOT NULL REFERENCES catalog.skill(id) ON DELETE CASCADE,
    alias citext NOT NULL,
    UNIQUE (skill_id, alias)
);
CREATE INDEX skill_alias_lookup_idx ON catalog.skill_alias(alias);

INSERT INTO catalog.catalog_version(public_id, version_no, status)
VALUES ('10000000-0000-0000-0000-000000000001', 1, 'PUBLISHED');

INSERT INTO catalog.role_family(public_id, catalog_version_id, slug, display_name)
SELECT seed.public_id::uuid, version.id, seed.slug, seed.display_name
FROM catalog.catalog_version version
CROSS JOIN (VALUES
    ('11000000-0000-0000-0000-000000000001','software-development','Desarrollo de software'),
    ('11000000-0000-0000-0000-000000000002','data-analysis','Análisis de datos'),
    ('11000000-0000-0000-0000-000000000003','devops-platform','DevOps y plataforma'),
    ('11000000-0000-0000-0000-000000000004','product-management','Gestión de producto')
) seed(public_id, slug, display_name)
WHERE version.version_no=1;

INSERT INTO catalog.role_alias(role_family_id, alias)
SELECT role.id, alias.alias
FROM catalog.role_family role
JOIN (VALUES
    ('software-development','desarrollador'), ('software-development','software engineer'),
    ('data-analysis','analista de datos'), ('data-analysis','data analyst'),
    ('devops-platform','devops'), ('devops-platform','platform engineer'),
    ('product-management','product manager'), ('product-management','gerente de producto')
) alias(slug, alias) ON alias.slug=role.slug;

INSERT INTO catalog.skill(public_id, catalog_version_id, slug, display_name)
SELECT seed.public_id::uuid, version.id, seed.slug, seed.display_name
FROM catalog.catalog_version version
CROSS JOIN (VALUES
    ('12000000-0000-0000-0000-000000000001','java','Java'),
    ('12000000-0000-0000-0000-000000000002','spring-boot','Spring Boot'),
    ('12000000-0000-0000-0000-000000000003','postgresql','PostgreSQL'),
    ('12000000-0000-0000-0000-000000000004','react','React'),
    ('12000000-0000-0000-0000-000000000005','typescript','TypeScript'),
    ('12000000-0000-0000-0000-000000000006','python','Python'),
    ('12000000-0000-0000-0000-000000000007','docker','Docker'),
    ('12000000-0000-0000-0000-000000000008','git','Git')
) seed(public_id, slug, display_name)
WHERE version.version_no=1;

INSERT INTO catalog.skill_alias(skill_id, alias)
SELECT skill.id, alias.alias
FROM catalog.skill skill
JOIN (VALUES
    ('java','java'), ('spring-boot','spring'), ('spring-boot','spring boot'),
    ('postgresql','postgres'), ('postgresql','postgresql'), ('react','react.js'),
    ('typescript','ts'), ('python','python'), ('docker','containers'), ('git','git')
) alias(slug, alias) ON alias.slug=skill.slug;

CREATE TABLE profile.professional_profile (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    account_id bigint NOT NULL UNIQUE REFERENCES iam.account(id) ON DELETE CASCADE,
    headline varchar(160),
    summary varchar(3000),
    location varchar(160),
    seniority varchar(30) CHECK (seniority IS NULL OR seniority IN ('INTERN','JUNIOR','MID','SENIOR','LEAD','MANAGER','DIRECTOR')),
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE profile.target_role (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    profile_id bigint NOT NULL REFERENCES profile.professional_profile(id) ON DELETE CASCADE,
    role_family_id bigint NOT NULL REFERENCES catalog.role_family(id),
    priority smallint NOT NULL CHECK (priority BETWEEN 1 AND 10),
    UNIQUE (profile_id, role_family_id),
    UNIQUE (profile_id, priority)
);

CREATE TABLE profile.profile_preference (
    profile_id bigint PRIMARY KEY REFERENCES profile.professional_profile(id) ON DELETE CASCADE,
    remote_mode varchar(20) CHECK (remote_mode IS NULL OR remote_mode IN ('REMOTE','HYBRID','ONSITE','ANY')),
    employment_type varchar(20) CHECK (employment_type IS NULL OR employment_type IN ('FULL_TIME','PART_TIME','CONTRACT','INTERNSHIP','ANY')),
    minimum_monthly_salary numeric(12,2) CHECK (minimum_monthly_salary IS NULL OR minimum_monthly_salary >= 0),
    currency char(3) NOT NULL DEFAULT 'MXN',
    willing_to_relocate boolean NOT NULL DEFAULT false
);

CREATE TABLE profile.excluded_employer (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    profile_id bigint NOT NULL REFERENCES profile.professional_profile(id) ON DELETE CASCADE,
    employer_name varchar(200) NOT NULL,
    employer_name_normalized citext NOT NULL,
    UNIQUE (profile_id, employer_name_normalized)
);

CREATE TABLE profile.trajectory_item (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    profile_id bigint NOT NULL REFERENCES profile.professional_profile(id) ON DELETE CASCADE,
    item_type varchar(40) NOT NULL CHECK (item_type IN ('EMPLOYMENT','INTERNSHIP','TECHNICAL_SOCIAL_SERVICE','TECHNICAL_VOLUNTEERING','PERSONAL_PROJECT','OPEN_SOURCE','ACADEMIC_PROJECT','STUDY')),
    title varchar(180) NOT NULL,
    organization varchar(180),
    description varchar(3000),
    start_year smallint NOT NULL CHECK (start_year BETWEEN 1950 AND 2200),
    start_month smallint NOT NULL CHECK (start_month BETWEEN 1 AND 12),
    end_year smallint CHECK (end_year BETWEEN 1950 AND 2200),
    end_month smallint CHECK (end_month BETWEEN 1 AND 12),
    is_current boolean NOT NULL DEFAULT false,
    CHECK ((is_current AND end_year IS NULL AND end_month IS NULL) OR
           (NOT is_current AND end_year IS NOT NULL AND end_month IS NOT NULL)),
    CHECK (end_year IS NULL OR (end_year * 12 + end_month) >= (start_year * 12 + start_month))
);
CREATE INDEX trajectory_profile_date_idx ON profile.trajectory_item(profile_id, start_year DESC, start_month DESC);

CREATE TABLE profile.education (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    profile_id bigint NOT NULL REFERENCES profile.professional_profile(id) ON DELETE CASCADE,
    institution varchar(200) NOT NULL,
    degree varchar(200) NOT NULL,
    field_of_study varchar(200),
    start_year smallint,
    end_year smallint,
    CHECK (start_year IS NULL OR start_year BETWEEN 1950 AND 2200),
    CHECK (end_year IS NULL OR end_year BETWEEN 1950 AND 2200),
    CHECK (start_year IS NULL OR end_year IS NULL OR end_year >= start_year)
);

CREATE TABLE profile.course_certification (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    profile_id bigint NOT NULL REFERENCES profile.professional_profile(id) ON DELETE CASCADE,
    name varchar(220) NOT NULL,
    issuer varchar(200),
    issued_year smallint CHECK (issued_year IS NULL OR issued_year BETWEEN 1950 AND 2200),
    credential_id varchar(200),
    credential_url varchar(1000)
);

CREATE TABLE profile.language (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    profile_id bigint NOT NULL REFERENCES profile.professional_profile(id) ON DELETE CASCADE,
    language_code varchar(10) NOT NULL,
    display_name varchar(100) NOT NULL,
    proficiency varchar(20) NOT NULL CHECK (proficiency IN ('BASIC','CONVERSATIONAL','PROFESSIONAL','FLUENT','NATIVE')),
    UNIQUE (profile_id, language_code)
);

CREATE TABLE profile.profile_skill (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    profile_id bigint NOT NULL REFERENCES profile.professional_profile(id) ON DELETE CASCADE,
    skill_id bigint REFERENCES catalog.skill(id),
    custom_name varchar(150),
    match_eligible boolean NOT NULL,
    proficiency varchar(20) CHECK (proficiency IS NULL OR proficiency IN ('BEGINNER','INTERMEDIATE','ADVANCED','EXPERT')),
    CHECK ((skill_id IS NOT NULL AND custom_name IS NULL AND match_eligible) OR
           (skill_id IS NULL AND custom_name IS NOT NULL AND NOT match_eligible)),
    UNIQUE (profile_id, skill_id)
);

CREATE UNIQUE INDEX profile_custom_skill_unique_idx
ON profile.profile_skill(profile_id, lower(custom_name)) WHERE custom_name IS NOT NULL;

CREATE TABLE profile.skill_evidence (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id uuid NOT NULL UNIQUE,
    profile_skill_id bigint NOT NULL REFERENCES profile.profile_skill(id) ON DELETE CASCADE,
    trajectory_item_id bigint NOT NULL REFERENCES profile.trajectory_item(id) ON DELETE CASCADE,
    context_type varchar(40) NOT NULL,
    context_weight numeric(4,3) NOT NULL CHECK (context_weight BETWEEN 0 AND 1),
    source varchar(20) NOT NULL CHECK (source IN ('MANUAL','CV_IMPORT')),
    profile_version bigint NOT NULL,
    UNIQUE (profile_skill_id, trajectory_item_id)
);

CREATE TABLE profile.skill_duration_projection (
    profile_skill_id bigint PRIMARY KEY REFERENCES profile.profile_skill(id) ON DELETE CASCADE,
    professional_months integer NOT NULL CHECK (professional_months >= 0),
    weighted_practical_months numeric(10,2) NOT NULL CHECK (weighted_practical_months >= 0),
    profile_version bigint NOT NULL,
    calculated_at timestamptz NOT NULL DEFAULT now()
);
