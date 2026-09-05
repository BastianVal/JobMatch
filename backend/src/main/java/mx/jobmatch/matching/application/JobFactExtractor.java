package mx.jobmatch.matching.application;

import mx.jobmatch.matching.domain.JobFacts;
import mx.jobmatch.vacancies.domain.JobDetail;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class JobFactExtractor {
    public static final String VERSION="facts-1";
    private static final Pattern EXPERIENCE=Pattern.compile("(?i)(\\d{1,2})\\s*(?:anos|years?)");
    private static final List<Language> LANGUAGES=List.of(new Language("en","Inglés",List.of("ingles","english")),
            new Language("es","Español",List.of("espanol","spanish")));

    public JobFacts extract(JobDetail job,List<MatchingRepository.CatalogSkill> catalog){
        String document=normalize(job.title()+". "+job.description());
        var skills=new LinkedHashMap<UUID,JobFacts.SkillRequirement>();
        for(var requirement:job.skillRequirements())skills.put(requirement.skillId(),new JobFacts.SkillRequirement(
                requirement.id(),requirement.skillId(),requirement.skill(),requirement.priority(),requirement.evidenceText()));
        for(var skill:catalog){
            if(skills.containsKey(skill.id()))continue;
            for(String alias:skill.aliases()){
                String normalized=normalize(alias);
                if(containsTerm(document,normalized)){
                    String sentence=sentence(document,normalized);
                    String priority=priority(sentence);
                    UUID requirement=id(job.id(),job.version(),"skill",skill.id().toString(),priority);
                    skills.putIfAbsent(skill.id(),new JobFacts.SkillRequirement(requirement,skill.id(),skill.name(),priority,sentence));
                    break;
                }
            }
        }
        var languages=new ArrayList<JobFacts.LanguageRequirement>();
        for(var language:LANGUAGES){
            for(String alias:language.aliases())if(containsTerm(document,alias)){
                String sentence=sentence(document,alias);
                boolean mandatory=mandatory(sentence);
                languages.add(new JobFacts.LanguageRequirement(id(job.id(),job.version(),"language",language.code(),Boolean.toString(mandatory)),
                        language.code(),language.name(),mandatory,sentence));
                break;
            }
        }
        var matcher=EXPERIENCE.matcher(document);
        Integer months=matcher.find()?Integer.parseInt(matcher.group(1))*12:null;
        List<String> responsibilities=document.isBlank()?List.of():List.of(document.length()>500?document.substring(0,500):document);
        return new JobFacts(job.roleFamilyId(),job.seniority(),months,List.copyOf(skills.values()),languages,responsibilities);
    }

    private static String priority(String sentence){
        if(mandatory(sentence))return "REQUIRED";
        if(sentence.matches(".*(responsab|constru|desarroll|implement|automatiz|operaci).*"))return "RESPONSIBILITY";
        return "DESIRED";
    }
    private static boolean mandatory(String sentence){return sentence.matches(".*(obligatori|indispensable|requerid|required|must have).*");}
    private static String sentence(String text,String term){
        for(String sentence:text.split("[.!;\\n]+"))if(containsTerm(sentence,term))return sentence.strip();
        return term;
    }
    private static boolean containsTerm(String text,String term){return (" "+text+" ").contains(" "+term+" ");}
    public static String normalize(String value){return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFD)
            .replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+#.]+"," ").replaceAll("\\s+"," ").strip();}
    private static UUID id(UUID job,long version,String...parts){return UUID.nameUUIDFromBytes((job+"|"+version+"|"+String.join("|",parts)).getBytes(StandardCharsets.UTF_8));}
    private record Language(String code,String name,List<String> aliases){}
}
