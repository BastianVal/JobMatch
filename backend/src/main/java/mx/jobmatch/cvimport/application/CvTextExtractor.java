package mx.jobmatch.cvimport.application;

import mx.jobmatch.cvimport.domain.ImportProposal;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class CvTextExtractor {
    public static final String VERSION = "tika-3.3.2-rules-2";
    private static final int MAX_TEXT_CHARACTERS = 200_000;
    private static final Pattern EXPERIENCE = Pattern.compile(
            "(?i)^(?:EXPERIENCE|EXPERIENCIA)\\s*:\\s*(.+?)\\s*\\|\\s*(.+?)\\s*\\|\\s*(\\d{4}-\\d{2})\\s*\\|\\s*(\\d{4}-\\d{2}|PRESENT|CURRENT|ACTUAL)$");
    private static final Pattern EDUCATION = Pattern.compile(
            "(?i)^(?:EDUCATION|EDUCACION|EDUCACIÓN)\\s*:\\s*(.+?)\\s*\\|\\s*(.+?)\\s*\\|\\s*(\\d{4})\\s*\\|\\s*(\\d{4})$");
    private static final Pattern CERTIFICATION = Pattern.compile(
            "(?i)^(?:CERTIFICATION|CERTIFICACION|CERTIFICACIÓN)\\s*:\\s*(.+?)\\s*\\|\\s*(.+?)\\s*\\|\\s*(\\d{4})$");
    private static final Pattern LANGUAGE = Pattern.compile(
            "(?i)^(?:LANGUAGE|IDIOMA)\\s*:\\s*([a-z]{2,10})\\s*\\|\\s*(.+?)\\s*\\|\\s*(BASIC|CONVERSATIONAL|PROFESSIONAL|FLUENT|NATIVE)$");
    private static final Pattern DATE_RANGE = Pattern.compile(
            "(?i)(?:([a-z]+)\\.?\\s+)?((?:19|20)\\d{2})\\s*(?:-|–|—|a|al|hasta|to)\\s*(?:([a-z]+)\\.?\\s+)?((?:19|20)\\d{2}|presente|actual|present|current)");
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})(?!\\d)");
    private static final Pattern NATURAL_LANGUAGE = Pattern.compile(
            "(?i)^(espanol|spanish|ingles|english|frances|french|aleman|german|portugues|portuguese|italiano|italian)\\s*(?::|\\||-|–)\\s*(.+)$");
    private final CvCatalogPort catalog;

    public CvTextExtractor(CvCatalogPort catalog) { this.catalog = catalog; }

    public Extraction extract(Path file, String mediaType) {
        String text = parse(file, mediaType);
        String normalized = normalize(text);
        if (normalized.isBlank()) throw new ExtractionFailure("NO_TEXT_CONTENT");
        List<ImportProposal> proposals = new ArrayList<>();
        List<String> lines=new ArrayList<>();
        for (String rawLine : text.replace('\r', '\n').split("\\n+")) {
            String line = rawLine.strip().replaceAll("\\s+", " ");
            if(line.isBlank())continue;
            lines.add(line);
            addExperience(line, proposals);
            addEducation(line, proposals);
            addCertification(line, proposals);
            addLanguage(line, proposals);
        }
        addNaturalSections(lines,proposals);
        List<String> trajectoryEvidence = proposals.stream().filter(proposal -> proposal.type().equals("TRAJECTORY"))
                .map(proposal -> proposal.payload().get("id").toString()).toList();
        for (var skill : catalog.findSkillsIn(normalized)) {
            Map<String,Object> payload = map("id", UUID.randomUUID().toString(), "catalogSkillId", skill.id().toString(),
                    "name", skill.name(), "proficiency", null, "matchEligible", true,
                    "evidenceTrajectoryIds", trajectoryEvidence);
            proposals.add(proposal("SKILL", payload, "skill|" + skill.id()));
        }
        if (proposals.isEmpty()) throw new ExtractionFailure("NO_IMPORT_CANDIDATES");
        return new Extraction(sha256(normalized), List.copyOf(proposals));
    }

    private void addNaturalSections(List<String> lines,List<ImportProposal> proposals){
        Map<Section,List<String>> sections=new EnumMap<>(Section.class);
        Section current=null;
        for(String line:lines){
            Section heading=section(line);
            if(heading!=null){current=heading;sections.computeIfAbsent(current,ignored->new ArrayList<>());continue;}
            if(current!=null&&!structured(line))sections.get(current).add(line);
        }
        var identities=new HashSet<String>();
        for(var proposal:proposals)identities.add(identity(proposal.type(),proposal.payload()));
        addTrajectory(sections.getOrDefault(Section.EXPERIENCE,List.of()),"EMPLOYMENT",proposals,identities);
        addTrajectory(sections.getOrDefault(Section.INTERNSHIP,List.of()),"INTERNSHIP",proposals,identities);
        addTrajectory(sections.getOrDefault(Section.SOCIAL_SERVICE,List.of()),"TECHNICAL_SOCIAL_SERVICE",proposals,identities);
        addTrajectory(sections.getOrDefault(Section.VOLUNTEERING,List.of()),"TECHNICAL_VOLUNTEERING",proposals,identities);
        addTrajectory(sections.getOrDefault(Section.PROJECTS,List.of()),"PERSONAL_PROJECT",proposals,identities);
        addNaturalEducation(sections.getOrDefault(Section.EDUCATION,List.of()),proposals,identities);
        addNaturalCertifications(sections.getOrDefault(Section.CERTIFICATIONS,List.of()),proposals,identities);
        addNaturalLanguages(sections.getOrDefault(Section.LANGUAGES,List.of()),proposals,identities);
    }

    private void addTrajectory(List<String> lines,String defaultType,List<ImportProposal> proposals,HashSet<String> identities){
        for(int index=0;index<lines.size();index++){
            String normalized=normalize(lines.get(index));var matcher=DATE_RANGE.matcher(normalized);if(!matcher.find())continue;
            DateSpan dates=dates(matcher);String prefix=cleanPrefix(normalized.substring(0,matcher.start()));
            String title="",organization=null;
            if(!prefix.isBlank()){
                String[] parts=prefix.split("\\s*(?:\\||•)\\s*|\\s+(?:at|en)\\s+",2);title=human(parts[0]);if(parts.length>1)organization=human(parts[1]);
                else if(index>0&&!hasDate(lines.get(index-1)))organization=human(lines.get(index-1));
            }else{
                if(index>0)organization=human(lines.get(index-1));
                if(index>1)title=human(lines.get(index-2));else if(index>0){title=organization;organization=null;}
            }
            if(title.isBlank())continue;
            String combined=normalize(title+" "+(organization==null?"":organization));String type=trajectoryType(defaultType,combined);
            String description=index+1<lines.size()&&!hasDate(lines.get(index+1))?human(lines.get(index+1)):null;
            Map<String,Object> payload=map("id",UUID.randomUUID().toString(),"type",type,"title",title,
                    "organization",blankToNull(organization),"description",blankToNull(description),"startYear",dates.startYear(),
                    "startMonth",dates.startMonth(),"endYear",dates.endYear(),"endMonth",dates.endMonth(),"current",dates.current());
            addUnique("TRAJECTORY",payload,proposals,identities);
        }
    }

    private void addNaturalEducation(List<String> lines,List<ImportProposal> proposals,HashSet<String> identities){
        for(int index=0;index<lines.size();index++){
            String normalized=normalize(lines.get(index));var matcher=DATE_RANGE.matcher(normalized);if(!matcher.find())continue;
            DateSpan dates=dates(matcher);String prefix=cleanPrefix(normalized.substring(0,matcher.start()));
            String degree="",institution="";
            if(!prefix.isBlank()){
                String[] parts=prefix.split("\\s*(?:\\||•)\\s*",2);degree=human(parts[0]);institution=parts.length>1?human(parts[1]):index>0?human(lines.get(index-1)):"";
            }else if(index>1){degree=human(lines.get(index-2));institution=human(lines.get(index-1));}
            else if(index>0)degree=human(lines.get(index-1));
            if(degree.isBlank()||institution.isBlank())continue;
            Map<String,Object> payload=map("id",UUID.randomUUID().toString(),"degree",degree,"institution",institution,
                    "fieldOfStudy",null,"startYear",dates.startYear(),"endYear",dates.current()?null:dates.endYear());
            addUnique("EDUCATION",payload,proposals,identities);
        }
    }

    private void addNaturalCertifications(List<String> lines,List<ImportProposal> proposals,HashSet<String> identities){
        for(int index=0;index<lines.size();index++){
            var matcher=YEAR.matcher(lines.get(index));if(!matcher.find())continue;
            String before=cleanPrefix(lines.get(index).substring(0,matcher.start()));String name="",issuer=null;
            String[] parts=before.split("\\s*(?:\\||•)\\s*",2);
            if(parts.length>1){name=human(parts[0]);issuer=human(parts[1]);}
            else if(index>0){name=human(lines.get(index-1));issuer=human(before);}else name=human(before);
            if(name.isBlank())continue;
            Map<String,Object> payload=map("id",UUID.randomUUID().toString(),"name",name,"issuer",blankToNull(issuer),
                    "issuedYear",Integer.parseInt(matcher.group(1)),"credentialId",null,"credentialUrl",null);
            addUnique("CERTIFICATION",payload,proposals,identities);
        }
    }

    private void addNaturalLanguages(List<String> lines,List<ImportProposal> proposals,HashSet<String> identities){
        for(String line:lines){var matcher=NATURAL_LANGUAGE.matcher(normalize(line));if(!matcher.matches())continue;
            String name=human(matcher.group(1));String code=languageCode(matcher.group(1));
            Map<String,Object> payload=map("id",UUID.randomUUID().toString(),"code",code,"name",name,
                    "proficiency",proficiency(matcher.group(2)));
            addUnique("LANGUAGE",payload,proposals,identities);
        }
    }

    private static void addUnique(String type,Map<String,Object> payload,List<ImportProposal> proposals,HashSet<String> identities){
        String identity=identity(type,payload);if(identities.add(identity))proposals.add(proposal(type,payload,identity));
    }
    private static String identity(String type,Map<String,Object> payload){
        return switch(type){
            case "TRAJECTORY"->"trajectory|"+payload.get("type")+"|"+normalize(String.valueOf(payload.get("title")))+"|"+normalize(String.valueOf(payload.get("organization")))+"|"+payload.get("startYear");
            case "EDUCATION"->"education|"+normalize(String.valueOf(payload.get("degree")))+"|"+normalize(String.valueOf(payload.get("institution")))+"|"+payload.get("endYear");
            case "CERTIFICATION"->"certification|"+normalize(String.valueOf(payload.get("name")))+"|"+normalize(String.valueOf(payload.get("issuer")));
            case "LANGUAGE"->"language|"+payload.get("code");default->type+"|"+payload;
        };
    }
    private static Section section(String line){
        String value=normalize(line).replaceAll("[:：]$","").strip();
        return switch(value){
            case "experiencia","experiencia laboral","experiencia profesional","experience","work experience","professional experience","employment history"->Section.EXPERIENCE;
            case "pasantias","pasantia","practicas","practicas profesionales","internships","internship"->Section.INTERNSHIP;
            case "servicio social","social service"->Section.SOCIAL_SERVICE;
            case "voluntariado","volunteer experience","volunteering"->Section.VOLUNTEERING;
            case "proyectos","proyectos relevantes","proyectos personales","projects","personal projects"->Section.PROJECTS;
            case "educacion","formacion academica","estudios","education","academic background"->Section.EDUCATION;
            case "certificaciones","certificados","cursos y certificaciones","certifications","certificates"->Section.CERTIFICATIONS;
            case "idiomas","languages"->Section.LANGUAGES;
            case "habilidades","competencias","skills","technical skills"->Section.OTHER;
            default->null;
        };
    }
    private static boolean structured(String line){return EXPERIENCE.matcher(line).matches()||EDUCATION.matcher(line).matches()||CERTIFICATION.matcher(line).matches()||LANGUAGE.matcher(line).matches();}
    private static boolean hasDate(String value){return DATE_RANGE.matcher(normalize(value)).find();}
    private static DateSpan dates(java.util.regex.Matcher matcher){
        int startYear=Integer.parseInt(matcher.group(2)),startMonth=month(matcher.group(1));String end=matcher.group(4);
        boolean current=!Character.isDigit(end.charAt(0));return new DateSpan(startYear,startMonth,current?null:Integer.parseInt(end),current?null:month(matcher.group(3)),current);
    }
    private static int month(String value){if(value==null)return 1;String v=normalize(value);String[] keys={"ene","feb","mar","abr","may","jun","jul","ago","sep","oct","nov","dic"};String[] english={"jan","feb","mar","apr","may","jun","jul","aug","sep","oct","nov","dec"};for(int i=0;i<12;i++)if(v.startsWith(keys[i])||v.startsWith(english[i]))return i+1;return 1;}
    private static String trajectoryType(String fallback,String value){if(value.contains("pasant")||value.contains("intern")||value.contains("practic"))return "INTERNSHIP";if(value.contains("servicio social"))return "TECHNICAL_SOCIAL_SERVICE";if(value.contains("voluntar"))return "TECHNICAL_VOLUNTEERING";if(value.contains("open source"))return "OPEN_SOURCE";if(value.contains("academ")||value.contains("universit"))return "ACADEMIC_PROJECT";return fallback;}
    private static String languageCode(String value){return switch(normalize(value)){case "espanol","spanish"->"es";case "ingles","english"->"en";case "frances","french"->"fr";case "aleman","german"->"de";case "portugues","portuguese"->"pt";case "italiano","italian"->"it";default->normalize(value).substring(0,Math.min(2,normalize(value).length()));};}
    private static String proficiency(String value){String v=normalize(value);if(v.contains("nativ")||v.contains("materna"))return "NATIVE";if(v.contains("c1")||v.contains("c2")||v.contains("fluid")||v.contains("fluent"))return "FLUENT";if(v.contains("b2")||v.contains("avanzad")||v.contains("professional"))return "PROFESSIONAL";if(v.contains("b1")||v.contains("intermedi")||v.contains("conversational"))return "CONVERSATIONAL";return "BASIC";}
    private static String cleanPrefix(String value){return human(value.replaceAll("[|•,:;\\-]+$","").strip());}
    private static String human(String value){if(value==null)return "";String cleaned=value.strip().replaceAll("^[•·▪*-]+\\s*","").replaceAll("\\s+"," ");if(cleaned.isBlank())return "";return Character.toUpperCase(cleaned.charAt(0))+cleaned.substring(1);}
    private static String blankToNull(String value){return value==null||value.isBlank()?null:value;}
    private enum Section{EXPERIENCE,INTERNSHIP,SOCIAL_SERVICE,VOLUNTEERING,PROJECTS,EDUCATION,CERTIFICATIONS,LANGUAGES,OTHER}
    private record DateSpan(int startYear,int startMonth,Integer endYear,Integer endMonth,boolean current){}

    private String parse(Path file, String mediaType) {
        try (InputStream input = Files.newInputStream(file)) {
            Metadata metadata = new Metadata();
            metadata.set(HttpHeaders.CONTENT_TYPE, mediaType);
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, mediaType.equals("application/pdf") ? "cv.pdf" : "cv.docx");
            BodyContentHandler handler = new BodyContentHandler(MAX_TEXT_CHARACTERS);
            new AutoDetectParser().parse(input, handler, metadata, new ParseContext());
            return handler.toString();
        } catch (Exception failure) { throw new ExtractionFailure("CV_PARSE_FAILED"); }
    }

    private void addExperience(String line, List<ImportProposal> proposals) {
        var matcher = EXPERIENCE.matcher(line);
        if (!matcher.matches()) return;
        YearMonth start = YearMonth.parse(matcher.group(3));
        String endValue = matcher.group(4).toUpperCase(Locale.ROOT);
        boolean current = !Character.isDigit(endValue.charAt(0));
        YearMonth end = current ? null : YearMonth.parse(endValue);
        Map<String,Object> payload = map("id", UUID.randomUUID().toString(), "type", "EMPLOYMENT",
                "title", matcher.group(1), "organization", matcher.group(2), "description", null,
                "startYear", start.getYear(), "startMonth", start.getMonthValue(),
                "endYear", end == null ? null : end.getYear(), "endMonth", end == null ? null : end.getMonthValue(),
                "current", current);
        proposals.add(proposal("TRAJECTORY", payload, "trajectory|employment|" + matcher.group(1) + "|"
                + matcher.group(2) + "|" + start));
    }

    private void addEducation(String line, List<ImportProposal> proposals) {
        var matcher = EDUCATION.matcher(line);
        if (!matcher.matches()) return;
        Map<String,Object> payload = map("id", UUID.randomUUID().toString(), "degree", matcher.group(1),
                "institution", matcher.group(2), "fieldOfStudy", null,
                "startYear", Integer.parseInt(matcher.group(3)), "endYear", Integer.parseInt(matcher.group(4)));
        proposals.add(proposal("EDUCATION", payload, "education|" + matcher.group(2) + "|" + matcher.group(1)
                + "|" + matcher.group(4)));
    }

    private void addCertification(String line, List<ImportProposal> proposals) {
        var matcher = CERTIFICATION.matcher(line);
        if (!matcher.matches()) return;
        Map<String,Object> payload = map("id", UUID.randomUUID().toString(), "name", matcher.group(1),
                "issuer", matcher.group(2), "issuedYear", Integer.parseInt(matcher.group(3)),
                "credentialId", null, "credentialUrl", null);
        proposals.add(proposal("CERTIFICATION", payload, "certification|" + matcher.group(1) + "|"
                + matcher.group(2) + "|" + matcher.group(3)));
    }

    private void addLanguage(String line, List<ImportProposal> proposals) {
        var matcher = LANGUAGE.matcher(line);
        if (!matcher.matches()) return;
        Map<String,Object> payload = map("id", UUID.randomUUID().toString(), "code", matcher.group(1).toLowerCase(Locale.ROOT),
                "name", matcher.group(2), "proficiency", matcher.group(3).toUpperCase(Locale.ROOT));
        proposals.add(proposal("LANGUAGE", payload, "language|" + matcher.group(1)));
    }

    private static ImportProposal proposal(String type, Map<String,Object> payload, String identity) {
        return new ImportProposal(type, payload, sha256(normalize(identity)));
    }
    private static Map<String,Object> map(Object... values) {
        Map<String,Object> result = new LinkedHashMap<>();
        for (int i=0; i<values.length; i+=2) result.put((String) values[i], values[i+1]);
        return result;
    }
    public static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
    public static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    public record Extraction(String textHash, List<ImportProposal> proposals) {}
    public static class ExtractionFailure extends RuntimeException {
        private final String safeCode;
        public ExtractionFailure(String safeCode) { super(safeCode); this.safeCode=safeCode; }
        public String safeCode() { return safeCode; }
    }
}
