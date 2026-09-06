ALTER TABLE catalog.role_family
    ADD COLUMN selectable boolean NOT NULL DEFAULT true;

-- Version 2 expands the role taxonomy without changing the public IDs referenced by
-- existing profiles, skills or vacancies. The four original broad families remain
-- resolvable for historical data, but are not offered for new profile selections.
INSERT INTO catalog.catalog_version(public_id, version_no, status)
VALUES ('10000000-0000-0000-0000-000000000002', 2, 'PUBLISHED');

UPDATE catalog.role_family
SET catalog_version_id=(SELECT id FROM catalog.catalog_version WHERE version_no=2),
    selectable=false;

UPDATE catalog.skill
SET catalog_version_id=(SELECT id FROM catalog.catalog_version WHERE version_no=2);

UPDATE catalog.catalog_version SET status='RETIRED' WHERE version_no=1;

DELETE FROM catalog.role_alias;

INSERT INTO catalog.role_family(public_id, catalog_version_id, slug, display_name, selectable)
SELECT seed.public_id::uuid, version.id, seed.slug, seed.display_name, true
FROM catalog.catalog_version version
CROSS JOIN (VALUES
    ('11100000-0000-0000-0000-000000000001','java-developer','Desarrollador Java'),
    ('11100000-0000-0000-0000-000000000002','python-developer','Desarrollador Python'),
    ('11100000-0000-0000-0000-000000000003','dotnet-developer','Desarrollador .NET'),
    ('11100000-0000-0000-0000-000000000004','nodejs-developer','Desarrollador Node.js'),
    ('11100000-0000-0000-0000-000000000005','go-developer','Desarrollador Go'),
    ('11100000-0000-0000-0000-000000000006','php-developer','Desarrollador PHP'),
    ('11100000-0000-0000-0000-000000000007','frontend-developer','Desarrollador Frontend'),
    ('11100000-0000-0000-0000-000000000008','react-developer','Desarrollador React'),
    ('11100000-0000-0000-0000-000000000009','angular-developer','Desarrollador Angular'),
    ('11100000-0000-0000-0000-000000000010','vuejs-developer','Desarrollador Vue.js'),
    ('11100000-0000-0000-0000-000000000011','backend-developer','Desarrollador Backend'),
    ('11100000-0000-0000-0000-000000000012','fullstack-developer','Desarrollador Full Stack'),
    ('11100000-0000-0000-0000-000000000013','mobile-developer','Desarrollador Móvil'),
    ('11100000-0000-0000-0000-000000000014','ios-developer','Desarrollador iOS'),
    ('11100000-0000-0000-0000-000000000015','android-developer','Desarrollador Android'),
    ('11100000-0000-0000-0000-000000000016','flutter-developer','Desarrollador Flutter'),
    ('11100000-0000-0000-0000-000000000017','react-native-developer','Desarrollador React Native'),
    ('11100000-0000-0000-0000-000000000018','data-scientist','Científico de Datos'),
    ('11100000-0000-0000-0000-000000000019','data-analyst','Analista de Datos'),
    ('11100000-0000-0000-0000-000000000020','data-engineer','Ingeniero de Datos'),
    ('11100000-0000-0000-0000-000000000021','machine-learning-engineer','Ingeniero de Machine Learning'),
    ('11100000-0000-0000-0000-000000000022','ai-developer','Desarrollador de Inteligencia Artificial'),
    ('11100000-0000-0000-0000-000000000023','business-intelligence-engineer','Ingeniero de Business Intelligence'),
    ('11100000-0000-0000-0000-000000000024','devops-engineer','Ingeniero DevOps'),
    ('11100000-0000-0000-0000-000000000025','cloud-engineer','Ingeniero Cloud'),
    ('11100000-0000-0000-0000-000000000026','cloud-architect','Arquitecto Cloud'),
    ('11100000-0000-0000-0000-000000000027','sre-engineer','Ingeniero SRE'),
    ('11100000-0000-0000-0000-000000000028','system-administrator','Administrador de Sistemas'),
    ('11100000-0000-0000-0000-000000000029','database-administrator','Administrador de Bases de Datos'),
    ('11100000-0000-0000-0000-000000000030','qa-engineer','Ingeniero QA'),
    ('11100000-0000-0000-0000-000000000031','qa-automation-engineer','Ingeniero QA Automation'),
    ('11100000-0000-0000-0000-000000000032','software-tester','Tester de Software'),
    ('11100000-0000-0000-0000-000000000033','cybersecurity-engineer','Ingeniero de Ciberseguridad'),
    ('11100000-0000-0000-0000-000000000034','soc-analyst','Analista SOC'),
    ('11100000-0000-0000-0000-000000000035','ethical-hacker','Ethical Hacker'),
    ('11100000-0000-0000-0000-000000000036','penetration-tester','Penetration Tester'),
    ('11100000-0000-0000-0000-000000000037','ux-ui-designer','Diseñador UX/UI'),
    ('11100000-0000-0000-0000-000000000038','product-owner','Product Owner'),
    ('11100000-0000-0000-0000-000000000039','scrum-master','Scrum Master'),
    ('11100000-0000-0000-0000-000000000040','tech-lead','Líder Técnico'),
    ('11100000-0000-0000-0000-000000000041','software-architect','Arquitecto de Software'),
    ('11100000-0000-0000-0000-000000000042','engineering-manager','Engineering Manager')
) seed(public_id, slug, display_name)
WHERE version.version_no=2;

INSERT INTO catalog.role_alias(role_family_id, alias)
SELECT role.id, seed.alias
FROM catalog.role_family role
JOIN (VALUES
    ('java-developer','Desarrollador Java'),('java-developer','Desarrollador Java Jr'),('java-developer','Desarrollador Java Sr'),('java-developer','Java Developer'),('java-developer','Java Developer Jr'),('java-developer','Java Developer Sr'),
    ('python-developer','Desarrollador Python'),('python-developer','Desarrollador Python Jr'),('python-developer','Desarrollador Python Sr'),('python-developer','Python Developer'),('python-developer','Python Developer Jr'),('python-developer','Python Developer Sr'),
    ('dotnet-developer','Desarrollador .NET'),('dotnet-developer','Desarrollador .NET Jr'),('dotnet-developer','Desarrollador .NET Sr'),('dotnet-developer','.NET Developer'),('dotnet-developer','.NET Developer Jr'),('dotnet-developer','.NET Developer Sr'),
    ('nodejs-developer','Desarrollador Node.js'),('nodejs-developer','Desarrollador Node.js Jr'),('nodejs-developer','Desarrollador Node.js Sr'),('nodejs-developer','Node.js Developer'),('nodejs-developer','Node.js Developer Jr'),('nodejs-developer','Node.js Developer Sr'),
    ('go-developer','Desarrollador Go'),('go-developer','Go Developer'),
    ('php-developer','Desarrollador PHP'),('php-developer','PHP Developer'),
    ('frontend-developer','Desarrollador Frontend'),('frontend-developer','Desarrollador Frontend Jr'),('frontend-developer','Desarrollador Frontend Sr'),('frontend-developer','Frontend Developer'),('frontend-developer','Frontend Developer Jr'),('frontend-developer','Frontend Developer Sr'),
    ('react-developer','Desarrollador React'),('react-developer','React Developer'),
    ('angular-developer','Desarrollador Angular'),('angular-developer','Angular Developer'),
    ('vuejs-developer','Desarrollador Vue.js'),('vuejs-developer','Vue.js Developer'),
    ('backend-developer','Desarrollador Backend'),('backend-developer','Desarrollador Backend Jr'),('backend-developer','Desarrollador Backend Sr'),('backend-developer','Backend Developer'),('backend-developer','Backend Developer Jr'),('backend-developer','Backend Developer Sr'),
    ('fullstack-developer','Desarrollador Full Stack'),('fullstack-developer','Desarrollador Full Stack Jr'),('fullstack-developer','Desarrollador Full Stack Sr'),('fullstack-developer','Full Stack Developer'),('fullstack-developer','Full Stack Developer Jr'),('fullstack-developer','Full Stack Developer Sr'),
    ('mobile-developer','Desarrollador Móvil'),('mobile-developer','Desarrollador Móvil Jr'),('mobile-developer','Desarrollador Móvil Sr'),('mobile-developer','Mobile Developer'),('mobile-developer','Mobile Developer Jr'),('mobile-developer','Mobile Developer Sr'),
    ('ios-developer','Desarrollador iOS'),('ios-developer','Desarrollador iOS Jr'),('ios-developer','Desarrollador iOS Sr'),('ios-developer','iOS Developer'),('ios-developer','iOS Developer Jr'),('ios-developer','iOS Developer Sr'),
    ('android-developer','Desarrollador Android'),('android-developer','Desarrollador Android Jr'),('android-developer','Desarrollador Android Sr'),('android-developer','Android Developer'),('android-developer','Android Developer Jr'),('android-developer','Android Developer Sr'),
    ('flutter-developer','Desarrollador Flutter'),('flutter-developer','Flutter Developer'),
    ('react-native-developer','Desarrollador React Native'),('react-native-developer','React Native Developer'),
    ('data-scientist','Científico de Datos'),('data-scientist','Científico de Datos Jr'),('data-scientist','Científico de Datos Sr'),('data-scientist','Data Scientist'),('data-scientist','Data Scientist Jr'),('data-scientist','Data Scientist Sr'),
    ('data-analyst','Analista de Datos'),('data-analyst','Analista de Datos Jr'),('data-analyst','Analista de Datos Sr'),('data-analyst','Data Analyst'),('data-analyst','Data Analyst Jr'),('data-analyst','Data Analyst Sr'),
    ('data-engineer','Ingeniero de Datos'),('data-engineer','Ingeniero de Datos Jr'),('data-engineer','Ingeniero de Datos Sr'),('data-engineer','Data Engineer'),('data-engineer','Data Engineer Jr'),('data-engineer','Data Engineer Sr'),
    ('machine-learning-engineer','Ingeniero de Machine Learning'),('machine-learning-engineer','Ingeniero de Machine Learning Jr'),('machine-learning-engineer','Ingeniero de Machine Learning Sr'),('machine-learning-engineer','Machine Learning Engineer'),('machine-learning-engineer','Machine Learning Engineer Jr'),('machine-learning-engineer','Machine Learning Engineer Sr'),
    ('ai-developer','Desarrollador de Inteligencia Artificial'),('ai-developer','AI Developer'),
    ('business-intelligence-engineer','Ingeniero de Business Intelligence'),('business-intelligence-engineer','BI Developer'),
    ('devops-engineer','Ingeniero DevOps'),('devops-engineer','Ingeniero DevOps Jr'),('devops-engineer','Ingeniero DevOps Sr'),('devops-engineer','DevOps Engineer'),('devops-engineer','DevOps Engineer Jr'),('devops-engineer','DevOps Engineer Sr'),
    ('cloud-engineer','Ingeniero Cloud'),('cloud-engineer','Ingeniero Cloud Jr'),('cloud-engineer','Ingeniero Cloud Sr'),('cloud-engineer','Cloud Engineer'),('cloud-engineer','Cloud Engineer Jr'),('cloud-engineer','Cloud Engineer Sr'),
    ('cloud-architect','Arquitecto Cloud'),('cloud-architect','Cloud Architect'),
    ('sre-engineer','Ingeniero SRE'),('sre-engineer','Site Reliability Engineer (SRE)'),('sre-engineer','Site Reliability Engineer Jr'),('sre-engineer','Site Reliability Engineer Sr'),
    ('system-administrator','Administrador de Sistemas'),('system-administrator','System Administrator (SysAdmin)'),
    ('database-administrator','Administrador de Bases de Datos'),('database-administrator','Database Administrator (DBA)'),('database-administrator','Database Administrator Jr'),('database-administrator','Database Administrator Sr'),
    ('qa-engineer','Ingeniero QA'),('qa-engineer','Ingeniero QA Jr'),('qa-engineer','Ingeniero QA Sr'),('qa-engineer','QA Engineer'),('qa-engineer','QA Engineer Jr'),('qa-engineer','QA Engineer Sr'),
    ('qa-automation-engineer','Ingeniero QA Automation'),('qa-automation-engineer','QA Automation Engineer'),('qa-automation-engineer','QA Automation Engineer Jr'),('qa-automation-engineer','QA Automation Engineer Sr'),
    ('software-tester','Tester de Software'),('software-tester','Software Tester'),
    ('cybersecurity-engineer','Ingeniero de Ciberseguridad'),('cybersecurity-engineer','Ingeniero de Ciberseguridad Jr'),('cybersecurity-engineer','Ingeniero de Ciberseguridad Sr'),('cybersecurity-engineer','Cybersecurity Engineer'),('cybersecurity-engineer','Cybersecurity Engineer Jr'),('cybersecurity-engineer','Cybersecurity Engineer Sr'),
    ('soc-analyst','Analista SOC'),('soc-analyst','SOC Analyst'),
    ('ethical-hacker','Ethical Hacker'),
    ('penetration-tester','Penetration Tester'),
    ('ux-ui-designer','Diseñador UX/UI'),('ux-ui-designer','Diseñador UX/UI Jr'),('ux-ui-designer','Diseñador UX/UI Sr'),('ux-ui-designer','UX/UI Designer'),('ux-ui-designer','UX/UI Designer Jr'),('ux-ui-designer','UX/UI Designer Sr'),
    ('product-owner','Product Owner'),('product-owner','Product Owner Jr'),('product-owner','Product Owner Sr'),
    ('scrum-master','Scrum Master'),('scrum-master','Scrum Master Jr'),('scrum-master','Scrum Master Sr'),
    ('tech-lead','Líder Técnico'),('tech-lead','Tech Lead'),
    ('software-architect','Arquitecto de Software'),('software-architect','Software Architect'),
    ('engineering-manager','Engineering Manager')
) seed(slug, alias) ON seed.slug=role.slug
JOIN catalog.catalog_version version ON version.id=role.catalog_version_id AND version.version_no=2;

CREATE UNIQUE INDEX role_alias_global_unique_idx ON catalog.role_alias(alias);
