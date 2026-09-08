package mx.jobmatch.ingestion.domain;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

public final class PostingNormalizer {
    private static final Set<String> TRACKING_PARAMETERS = Set.of("utm_source", "utm_medium", "utm_campaign", "ref");
    private PostingNormalizer() {}

    public static NormalizedPosting normalize(String sourceKey, RawPosting raw) {
        require(raw.externalId(), "externalId");
        require(raw.url(), "url");
        require(raw.title(), "title");
        require(raw.employer(), "employer");
        require(raw.description(), "description");
        if (raw.publishedAt() == null) throw new IllegalArgumentException("MISSING_PUBLISHED_AT");
        String url = normalizedUrl(raw.url());
        String title = clean(raw.title());
        String employer = clean(raw.employer());
        String city = clean(raw.city());
        String state = clean(raw.state());
        String country = raw.countryCode() == null ? "MX" : raw.countryCode().strip().toUpperCase(Locale.ROOT);
        if (country.length() != 2) throw new IllegalArgumentException("INVALID_COUNTRY");
        String titleKey = key(title);
        String employerKey = key(employer);
        String cityKey = key(city == null ? state : city);
        String dateBucket = DateTimeFormatter.ofPattern("uuuu-MM").withZone(ZoneOffset.UTC).format(raw.publishedAt());
        String identity = sha256(employerKey + "|" + titleKey + "|" + cityKey + "|" + dateBucket);
        return new NormalizedPosting(sourceKey, clean(raw.externalId()), url, sha256(url), title, titleKey,
                employer, employerKey, cleanDescription(raw.description()), country, state, key(state), city, key(city),
                upper(raw.remoteMode(), "ONSITE"), upper(raw.employmentType(), "FULL_TIME"),
                upper(raw.seniority(), null), raw.salaryMinMonthly(), raw.salaryMaxMonthly(),
                upper(raw.currency(), raw.salaryMinMonthly() == null ? null : "MXN"), raw.publishedAt(),
                identity, raw.payload() == null ? "{}" : raw.payload(), sha256(raw.payload() == null ? "{}" : raw.payload()));
    }

    public static String key(String value) {
        if (value == null) return "";
        return Normalizer.normalize(clean(value), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+#.]+", " ").trim();
    }

    public static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    private static String normalizedUrl(String input) {
        try {
            URI uri = URI.create(input.strip());
            String query = uri.getRawQuery();
            if (query != null) query = java.util.Arrays.stream(query.split("&"))
                    .filter(part -> !TRACKING_PARAMETERS.contains(part.split("=", 2)[0].toLowerCase(Locale.ROOT)))
                    .sorted().collect(java.util.stream.Collectors.joining("&"));
            return new URI(uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT),
                    uri.getUserInfo(), uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT),
                    uri.getPort(), uri.getPath(), query == null || query.isBlank() ? null : query, null).toString();
        } catch (Exception invalid) { throw new IllegalArgumentException("INVALID_URL"); }
    }

    private static String clean(String value) {
        if (value == null) return null;
        String cleaned = Normalizer.normalize(value, Normalizer.Form.NFKC).replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
    private static String cleanDescription(String value) {
        if (value == null) return null;
        String cleaned = Normalizer.normalize(value, Normalizer.Form.NFKC).replace("\r\n", "\n")
                .replaceAll("[ \\t]+", " ").replaceAll(" *\\n *", "\n").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
    private static String upper(String value, String fallback) {
        String cleaned = clean(value);
        return cleaned == null ? fallback : cleaned.toUpperCase(Locale.ROOT);
    }
    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("MISSING_" + field.toUpperCase(Locale.ROOT));
    }
}
