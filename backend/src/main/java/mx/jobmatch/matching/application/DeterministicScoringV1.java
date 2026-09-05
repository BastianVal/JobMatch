package mx.jobmatch.matching.application;

import mx.jobmatch.matching.domain.JobFacts;
import mx.jobmatch.matching.domain.MatchEvaluation;
import mx.jobmatch.profile.domain.ProfessionalProfile;
import mx.jobmatch.profile.domain.ProfileDraft;
import mx.jobmatch.profile.domain.TrajectoryType;
import mx.jobmatch.vacancies.domain.JobDetail;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class DeterministicScoringV1 {
    public static final String VERSION="score-1";

    public MatchEvaluation evaluate(ProfessionalProfile profile,JobDetail job,JobFacts facts){
        var reasons=new ArrayList<MatchEvaluation.Reason>();
        var components=new LinkedHashMap<String,BigDecimal>();
        ProfileDraft data=profile.data();
        double role=role(data,facts,reasons);components.put("ROLE_RESPONSIBILITIES",decimal(role));
        Technical technical=technical(data,facts,reasons);components.put("TECHNOLOGIES_KNOWLEDGE",decimal(technical.points()));
        Experience experience=experience(data,job,facts,reasons);components.put("SENIORITY_EXPERIENCE",decimal(experience.points()));
        double projects=projects(data,facts,reasons);components.put("RELEVANT_PROJECTS",decimal(projects));
        double solidity=solidity(data,job,reasons);components.put("EXPERIENCE_TYPE",decimal(solidity));
        double preferences=preferences(data,job,reasons);components.put("PREFERENCES_QUALITY",decimal(preferences));
        double raw=role+technical.points()+experience.points()+projects+solidity+preferences;
        if(technical.mandatoryGap()||experience.belowRequiredCoverage())raw=Math.min(raw,64);
        if(juniorMismatch(data.seniority(),job.seniority(),job.title())&&!experience.requiredSatisfied())raw=Math.min(raw,44);
        BigDecimal score=decimal(raw);
        return new MatchEvaluation(UUID.randomUUID(),job.id(),profile.version(),job.version(),profile.catalogVersion(),VERSION,
                score,classification(score.doubleValue()),Map.copyOf(components),List.copyOf(reasons),false);
    }

    private double role(ProfileDraft profile,JobFacts facts,List<MatchEvaluation.Reason> reasons){
        if(facts.roleFamilyId()==null||profile.targetRoles().isEmpty()){
            reason(reasons,"ROLE_RESPONSIBILITIES","CONSIDERATION",null,map("roleFamilyId",facts.roleFamilyId()),Map.of(),12.5,
                    "No hay suficiente información normalizada para comparar el rol.");return 12.5;
        }
        var target=profile.targetRoles().stream().filter(role->role.roleFamilyId().equals(facts.roleFamilyId())).findFirst();
        if(target.isPresent()){
            reason(reasons,"ROLE_RESPONSIBILITIES","MATCH",null,map("roleFamilyId",facts.roleFamilyId()),
                    map("targetRoleId",target.get().id(),"priority",target.get().priority()),25,"La vacante coincide con un rol objetivo.");return 25;
        }
        reason(reasons,"ROLE_RESPONSIBILITIES","GAP",null,map("roleFamilyId",facts.roleFamilyId()),
                map("targetRoleIds",profile.targetRoles().stream().map(ProfileDraft.TargetRole::roleFamilyId).toList()),0,
                "La familia de la vacante no coincide con los roles objetivo.");return 0;
    }

    private Technical technical(ProfileDraft profile,JobFacts facts,List<MatchEvaluation.Reason> reasons){
        Map<UUID,ProfileDraft.Skill> owned=new HashMap<>();
        profile.skills().stream().filter(ProfileDraft.Skill::matchEligible).filter(skill->skill.catalogSkillId()!=null)
                .forEach(skill->owned.put(skill.catalogSkillId(),skill));
        double total=0;boolean mandatoryGap=false;
        total+=skillGroup("REQUIRED",17.5,facts.skills(),owned,reasons);
        total+=skillGroup("DESIRED",5.0,facts.skills(),owned,reasons);
        total+=skillGroup("RESPONSIBILITY",2.5,facts.skills(),owned,reasons);
        for(var required:facts.skills())if(required.priority().equals("REQUIRED")&&!owned.containsKey(required.skillId()))mandatoryGap=true;
        Map<String,ProfileDraft.Language> languages=new HashMap<>();profile.languages().forEach(language->languages.put(language.code(),language));
        for(var required:facts.languages()){
            var evidence=languages.get(required.code());boolean match=evidence!=null;
            if(required.mandatory()&&!match)mandatoryGap=true;
            reason(reasons,"TECHNOLOGIES_KNOWLEDGE",match?"MATCH":required.mandatory()?"GAP":"CONSIDERATION",required.requirementId(),
                    map("language",required.code(),"mandatory",required.mandatory(),"text",required.evidenceText()),
                    match?map("languageId",evidence.id(),"proficiency",evidence.proficiency()):Map.of(),0,
                    match?"El perfil contiene el idioma solicitado.":"No hay evidencia del idioma mencionado.");
        }
        return new Technical(total,mandatoryGap);
    }

    private double skillGroup(String priority,double maximum,List<JobFacts.SkillRequirement> requirements,
                              Map<UUID,ProfileDraft.Skill> owned,List<MatchEvaluation.Reason> reasons){
        var group=requirements.stream().filter(item->item.priority().equals(priority)).toList();
        if(group.isEmpty()){
            reason(reasons,"TECHNOLOGIES_KNOWLEDGE","CONSIDERATION",null,map("priority",priority),Map.of(),maximum/2,
                    "La vacante no declara habilidades de esta prioridad.");return maximum/2;
        }
        double each=maximum/group.size(),points=0;
        for(var requirement:group){
            var evidence=owned.get(requirement.skillId());boolean match=evidence!=null;
            if(match)points+=each;
            reason(reasons,"TECHNOLOGIES_KNOWLEDGE",match?"MATCH":"GAP",requirement.requirementId(),
                    map("skillId",requirement.skillId(),"skill",requirement.skill(),"priority",priority,"text",requirement.evidenceText()),
                    match?map("profileSkillId",evidence.id(),"professionalMonths",evidence.professionalMonths(),
                            "weightedPracticalMonths",evidence.weightedPracticalMonths(),"trajectoryIds",evidence.evidenceTrajectoryIds()):Map.of(),
                    match?each:0,match?"Existe evidencia concreta de la habilidad.":"No existe evidencia de la habilidad en el perfil.");
        }
        return points;
    }

    private Experience experience(ProfileDraft profile,JobDetail job,JobFacts facts,List<MatchEvaluation.Reason> reasons){
        int professional=months(profile.trajectory(),true,reference(job));
        double seniority;
        if(job.seniority()==null||profile.seniority()==null){seniority=5;
            reason(reasons,"SENIORITY_EXPERIENCE","CONSIDERATION",null,map("jobSeniority",job.seniority()),
                    map("profileSeniority",profile.seniority()),5,"Falta seniority normalizado en uno de los lados.");
        }else if(level(profile.seniority())>=level(job.seniority())){seniority=10;
            reason(reasons,"SENIORITY_EXPERIENCE","MATCH",null,map("jobSeniority",job.seniority()),map("profileSeniority",profile.seniority()),10,
                    "El seniority del perfil cubre el solicitado.");
        }else{seniority=0;reason(reasons,"SENIORITY_EXPERIENCE","GAP",null,map("jobSeniority",job.seniority()),
                    map("profileSeniority",profile.seniority()),0,"El seniority declarado está por debajo del solicitado.");}
        if(facts.professionalMonths()==null){reason(reasons,"SENIORITY_EXPERIENCE","CONSIDERATION",null,
                map("professionalMonthsRequired",null),map("professionalMonths",professional),5,
                "La vacante no declara una duración de experiencia profesional.");return new Experience(seniority+5,false,false);}
        double coverage=facts.professionalMonths()==0?1:(double)professional/facts.professionalMonths();
        double duration=Math.min(10,10*coverage);boolean below=coverage<0.70;boolean satisfied=coverage>=1;
        reason(reasons,"SENIORITY_EXPERIENCE",satisfied?"MATCH":"GAP",null,map("professionalMonthsRequired",facts.professionalMonths()),
                map("professionalMonths",professional,"coverage",decimal(coverage)),duration,
                satisfied?"La experiencia profesional cubre el mínimo.":"La experiencia profesional no cubre el mínimo declarado.");
        return new Experience(seniority+duration,below,satisfied);
    }

    private double projects(ProfileDraft profile,JobFacts facts,List<MatchEvaluation.Reason> reasons){
        Set<UUID> evidence=new HashSet<>();
        Set<UUID> wanted=new HashSet<>();facts.skills().forEach(skill->wanted.add(skill.skillId()));
        profile.skills().stream().filter(skill->wanted.contains(skill.catalogSkillId())).forEach(skill->evidence.addAll(skill.evidenceTrajectoryIds()));
        long relevant=profile.trajectory().stream().filter(item->item.type()!=TrajectoryType.EMPLOYMENT).filter(item->evidence.contains(item.id())).count();
        if(profile.trajectory().stream().noneMatch(item->item.type()!=TrajectoryType.EMPLOYMENT)){
            reason(reasons,"RELEVANT_PROJECTS","CONSIDERATION",null,map("requiredSkills",wanted),Map.of(),7.5,
                    "El perfil no contiene contextos de proyecto comparables.");return 7.5;
        }
        double points=Math.min(15,relevant*7.5);
        reason(reasons,"RELEVANT_PROJECTS",relevant>0?"MATCH":"GAP",null,map("requiredSkills",wanted),map("trajectoryIds",evidence),points,
                relevant>0?"Hay proyectos con evidencia de tecnologías relevantes.":"No hay proyectos con evidencia relevante.");return points;
    }

    private double solidity(ProfileDraft profile,JobDetail job,List<MatchEvaluation.Reason> reasons){
        int professional=months(profile.trajectory(),true,reference(job));double points=professional>=24?10:professional>=12?7:professional>0?4:0;
        reason(reasons,"EXPERIENCE_TYPE",professional>0?"MATCH":"GAP",null,map("measure","formal-employment-months"),
                map("professionalMonths",professional),points,professional>0?"Hay experiencia en empleo formal.":"No hay experiencia de empleo formal.");return points;
    }

    private double preferences(ProfileDraft profile,JobDetail job,List<MatchEvaluation.Reason> reasons){
        double points=0;var preference=profile.preferences();
        if(preference==null){reason(reasons,"PREFERENCES_QUALITY","CONSIDERATION",null,map("jobPublishedAt",job.publishedAt()),Map.of(),2.5,
                "El perfil no define preferencias estrictas.");return 2.5;}
        boolean remote=preference.remoteMode()==null||preference.remoteMode().equals("ANY")||preference.remoteMode().equals(job.remoteMode());
        boolean employment=preference.employmentType()==null||preference.employmentType().equals("ANY")||preference.employmentType().equals(job.employmentType());
        boolean salary=preference.minimumMonthlySalary()==null||(job.salaryMaxMonthly()!=null&&job.salaryMaxMonthly().compareTo(preference.minimumMonthlySalary())>=0);
        points+=(remote?1.5:0)+(employment?1.5:0)+(salary?1:0)+(job.status().equals("ACTIVE")?1:0);
        reason(reasons,"PREFERENCES_QUALITY",remote&&employment&&salary?"MATCH":"GAP",null,
                map("remoteMode",job.remoteMode(),"employmentType",job.employmentType(),"salaryMax",job.salaryMaxMonthly()),
                map("remoteMode",preference.remoteMode(),"employmentType",preference.employmentType(),"minimumSalary",preference.minimumMonthlySalary()),
                points,remote&&employment&&salary?"La vacante respeta las preferencias definidas.":"Alguna preferencia estricta no se cumple.");return points;
    }

    private static int months(List<ProfileDraft.TrajectoryItem> trajectory,boolean professionalOnly,YearMonth reference){
        Set<YearMonth> months=new HashSet<>();
        for(var item:trajectory){if(professionalOnly&&!item.type().professional())continue;YearMonth cursor=YearMonth.of(item.startYear(),item.startMonth());
            YearMonth end=item.current()?reference:YearMonth.of(item.endYear(),item.endMonth());while(!cursor.isAfter(end)){months.add(cursor);cursor=cursor.plusMonths(1);}}
        return months.size();
    }
    private static YearMonth reference(JobDetail job){return YearMonth.from(job.publishedAt().atZone(ZoneOffset.UTC));}
    private static boolean juniorMismatch(String profile,String job,String title){return "JUNIOR".equals(profile)
            && (Set.of("SENIOR","LEAD","MANAGER","DIRECTOR").contains(job)||JobFactExtractor.normalize(title).contains("architect"));}
    private static int level(String value){return switch(value){case "INTERN"->0;case "JUNIOR"->1;case "MID"->2;case "SENIOR"->3;case "LEAD"->4;case "MANAGER"->5;case "DIRECTOR"->6;default->0;};}
    private static String classification(double score){return score>=85?"EXCELLENT":score>=70?"STRONG":score>=50?"POSSIBLE":"LOW";}
    private static BigDecimal decimal(double value){return BigDecimal.valueOf(value).setScale(2,RoundingMode.HALF_UP);}
    private static Map<String,Object> map(Object...values){Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)result.put((String)values[i],values[i+1]);return result;}
    private static void reason(List<MatchEvaluation.Reason> reasons,String component,String type,UUID requirementId,
                               Map<String,Object> requirement,Map<String,Object> evidence,double points,String explanation){
        reasons.add(new MatchEvaluation.Reason(UUID.randomUUID(),component,type,requirementId,requirement,evidence,decimal(points),explanation));
    }
    private record Technical(double points,boolean mandatoryGap){}
    private record Experience(double points,boolean belowRequiredCoverage,boolean requiredSatisfied){}
}
