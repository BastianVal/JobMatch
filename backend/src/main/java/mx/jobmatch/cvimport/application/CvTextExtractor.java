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
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class CvTextExtractor {
    public static final String VERSION = "tika-3.3.2-rules-1";
    private static final int MAX_TEXT_CHARACTERS = 200_000;
    private static final Pattern EXPERIENCE = Pattern.compile(
            "(?i)^(?:EXPERIENCE|EXPERIENCIA)\\s*:\\s*(.+?)\\s*\\|\\s*(.+?)\\s*\\|\\s*(\\d{4}-\\d{2})\\s*\\|\\s*(\\d{4}-\\d{2}|PRESENT|CURRENT|ACTUAL)$");
    private static final Pattern EDUCATION = Pattern.compile(
            "(?i)^(?:EDUCATION|EDUCACION|EDUCACIÓN)\\s*:\\s*(.+?)\\s*\\|\\s*(.+?)\\s*\\|\\s*(\\d{4})\\s*\\|\\s*(\\d{4})$");
    private static final Pattern CERTIFICATION = Pattern.compile(
            "(?i)^(?:CERTIFICATION|CERTIFICACION|CERTIFICACIÓN)\\s*:\\s*(.+?)\\s*\\|\\s*(.+?)\\s*\\|\\s*(\\d{4})$");
    private static final Pattern LANGUAGE = Pattern.compile(
            "(?i)^(?:LANGUAGE|IDIOMA)\\s*:\\s*([a-z]{2,10})\\s*\\|\\s*(.+?)\\s*\\|\\s*(BASIC|CONVERSATIONAL|PROFESSIONAL|FLUENT|NATIVE)$");
    private final CvCatalogPort catalog;

    public CvTextExtractor(CvCatalogPort catalog) { this.catalog = catalog; }

    public Extraction extract(Path file, String mediaType) {
        String text = parse(file, mediaType);
        String normalized = normalize(text);
        if (normalized.isBlank()) throw new ExtractionFailure("NO_TEXT_CONTENT");
        List<ImportProposal> proposals = new ArrayList<>();
        for (String rawLine : text.replace('\r', '\n').split("\\n+")) {
            String line = rawLine.strip().replaceAll("\\s+", " ");
            addExperience(line, proposals);
            addEducation(line, proposals);
            addCertification(line, proposals);
            addLanguage(line, proposals);
        }
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
