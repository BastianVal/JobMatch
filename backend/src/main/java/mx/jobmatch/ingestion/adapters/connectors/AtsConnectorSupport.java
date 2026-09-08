package mx.jobmatch.ingestion.adapters.connectors;

import com.fasterxml.jackson.databind.JsonNode;

import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Small, conservative normalizers shared by public ATS connectors. */
final class AtsConnectorSupport {
    private static final Map<String, String> COUNTRIES = Map.ofEntries(
            Map.entry("argentina", "AR"), Map.entry("mexico", "MX"),
            Map.entry("united states", "US"), Map.entry("usa", "US"), Map.entry("canada", "CA"),
            Map.entry("brazil", "BR"), Map.entry("brasil", "BR"), Map.entry("colombia", "CO"),
            Map.entry("chile", "CL"), Map.entry("spain", "ES"), Map.entry("united kingdom", "GB"),
            Map.entry("uk", "GB"), Map.entry("india", "IN"), Map.entry("germany", "DE"),
            Map.entry("france", "FR"), Map.entry("australia", "AU"));
    private static final List<String> MEXICAN_LOCATION_TERMS = List.of(
            "mexico city", "ciudad de mexico", "guadalajara", "jalisco", "monterrey", "nuevo leon",
            "queretaro", "puebla", "tijuana", "baja california", "merida", "yucatan", "leon", "guanajuato");

    private AtsConnectorSupport() {}

    /**
     * A configured board must not convert an unknown city into Mexico. Only an explicit country
     * or a well-known Mexican city/state is accepted. This deliberately favors false negatives
     * over presenting a foreign vacancy as Mexican.
     */
    static Location location(String raw, String explicitCountry) {
        if (blank(raw) && blank(explicitCountry)) return new Location(null, null, null);
        String normalizedRaw = normalize(raw);
        String country = countryCode(explicitCountry);
        if (country == null) country = detectedCountry(normalizedRaw);
        if (country == null && isMexicanLocation(normalizedRaw)) country = "MX";
        String[] parts = blank(raw) ? new String[0] : raw.split("\\s*,\\s*");
        String city = parts.length == 0 ? null : parts[0].strip();
        String state = parts.length > 1 ? parts[1].strip() : null;
        return new Location(country, state, city);
    }

    static String htmlToText(String html) {
        if (html == null) return null;
        String decoded = html;
        for (int attempt = 0; attempt < 3; attempt++) {
            String next = decodeEntities(decoded);
            if (next.equals(decoded)) break;
            decoded = next;
        }
        return decoded.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?is)<h[1-6][^>]*>", "\n\n")
                .replaceAll("(?is)</h[1-6]>", "\n")
                .replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)<li[^>]*>", "\n• ")
                .replaceAll("(?is)</(p|div|ul|ol)>", "\n")
                .replaceAll("(?is)<[^>]+>", " ").replaceAll("[ \\t]+", " ")
                .replaceAll("(?m)^[ \\t]+|[ \\t]+$", "")
                .replaceAll("\\n(?:[ \\t]*\\n)+", "\n\n")
                .replaceAll("\\n{3,}", "\n\n").trim();
    }

    static String text(JsonNode node, String name) {
        JsonNode value = node.path(name);
        return value.isValueNode() && !value.isNull() ? value.asText() : null;
    }

    static Instant instant(String value) {
        try { return value == null ? Instant.now() : Instant.parse(value); }
        catch (Exception invalid) { return Instant.now(); }
    }

    static boolean blank(String value) { return value == null || value.isBlank(); }
    static String value(String value) { return value == null ? "" : value; }

    private static String countryCode(String value) {
        if (blank(value)) return null;
        String normalized = normalize(value);
        if (normalized.matches("[a-z]{2}")) return normalized.toUpperCase(Locale.ROOT);
        return COUNTRIES.get(normalized);
    }
    private static String detectedCountry(String normalized) {
        String padded = " " + normalized.replaceAll("[^a-z]+", " ").strip() + " ";
        return COUNTRIES.entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getKey().length(), left.getKey().length()))
                .filter(entry -> padded.contains(" " + entry.getKey() + " "))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }
    private static boolean isMexicanLocation(String normalized) {
        return MEXICAN_LOCATION_TERMS.stream().anyMatch(normalized::contains);
    }
    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
    private static String decodeEntities(String value) {
        return value.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&nbsp;", " ").replace("&amp;", "&");
    }

    record Location(String countryCode, String state, String city) {}
}
