package mx.jobmatch.ingestion.adapters.connectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.jobmatch.ingestion.application.ConnectorFailure;
import mx.jobmatch.ingestion.application.ConnectorPort;
import mx.jobmatch.ingestion.domain.ConnectorPage;
import mx.jobmatch.ingestion.domain.ConnectorQuery;
import mx.jobmatch.ingestion.domain.RawPosting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Public Greenhouse Job Board API adapter. Board keys only originate in the managed registry. */
@Component
public final class GreenhouseConnectorAdapter implements ConnectorPort {
    private static final String API_HOST = "boards-api.greenhouse.io";
    private final ObjectMapper json;
    private final HttpClient http;

    @Autowired
    public GreenhouseConnectorAdapter(ObjectMapper json) {
        this(json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    GreenhouseConnectorAdapter(ObjectMapper json, HttpClient http) {
        this.json = json;
        this.http = http;
    }

    @Override public String sourceKey() { return "GREENHOUSE"; }

    @Override
    public ConnectorPage fetch(ConnectorQuery query) {
        String board = boardKey(query);
        try {
            URI uri = URI.create("https://" + API_HOST + "/v1/boards/" + board + "/jobs?content=true");
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json").GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429 || response.statusCode() >= 500)
                throw new ConnectorFailure("GREENHOUSE_UNAVAILABLE", true);
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new ConnectorFailure("GREENHOUSE_REJECTED_REQUEST", false);
            return parse(response.body(), query, json);
        } catch (ConnectorFailure failure) {
            throw failure;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ConnectorFailure("GREENHOUSE_INTERRUPTED", true);
        } catch (Exception failure) {
            throw new ConnectorFailure("GREENHOUSE_INVALID_RESPONSE", true);
        }
    }

    static ConnectorPage parse(String body, ConnectorQuery query, ObjectMapper json) throws Exception {
        String board = boardKey(query);
        JsonNode jobs = json.readTree(body).path("jobs");
        if (!jobs.isArray()) throw new ConnectorFailure("GREENHOUSE_INVALID_RESPONSE", true);
        List<RawPosting> postings = new ArrayList<>();
        for (JsonNode job : jobs) {
            String id = text(job, "id");
            String url = text(job, "absolute_url");
            String title = text(job, "title");
            if (blank(id) || blank(url) || blank(title)) continue;
            String description = htmlToText(text(job, "content"));
            if (blank(description)) description = title;
            Location location = location(job.path("location").path("name").asText(null), query.countryCode());
            String fullText = (title + " " + description + " " + value(location.city())).toLowerCase(Locale.ROOT);
            postings.add(new RawPosting("greenhouse:" + board + ":" + id, url, title,
                    query.employerName() == null ? board : query.employerName(), description,
                    location.countryCode(), location.state(), location.city(),
                    fullText.contains("remote") ? "REMOTE" : "ONSITE", "FULL_TIME", null,
                    null, null, null, published(job), json.writeValueAsString(job)));
        }
        return new ConnectorPage(postings, null, true);
    }

    private static String boardKey(ConnectorQuery query) {
        String board = query.boardKey();
        if (blank(board) || !board.matches("[a-zA-Z0-9_-]{1,200}"))
            throw new ConnectorFailure("GREENHOUSE_BOARD_NOT_CONFIGURED", false);
        return board;
    }
    private static Instant published(JsonNode job) {
        String updated = text(job, "updated_at");
        try { return updated == null ? Instant.now() : Instant.parse(updated); }
        catch (Exception invalid) { return Instant.now(); }
    }
    private static Location location(String raw, String configuredCountry) {
        if (blank(raw)) return new Location(configuredCountry == null ? "MX" : configuredCountry, null, null);
        String[] parts = raw.split("\\s*,\\s*");
        String last = parts[parts.length - 1].strip().toLowerCase(Locale.ROOT);
        String country = COUNTRIES.get(last);
        int cityEnd = country == null ? parts.length : parts.length - 1;
        String city = cityEnd == 0 ? raw : parts[0].strip();
        String state = cityEnd > 1 ? parts[1].strip() : null;
        return new Location(country == null ? (configuredCountry == null ? "MX" : configuredCountry) : country, state, city);
    }
    private static String htmlToText(String html) {
        if (html == null) return null;
        return html.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&nbsp;", " ").replace("&amp;", "&")
                .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?is)<[^>]+>", " ").replace("&nbsp;", " ")
                .replaceAll("\\s+", " ").trim();
    }
    private static String text(JsonNode node, String name) {
        JsonNode value = node.path(name);
        return value.isValueNode() && !value.isNull() ? value.asText() : null;
    }
    private static String value(String value) { return value == null ? "" : value; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private record Location(String countryCode, String state, String city) {}
    private static final Map<String, String> COUNTRIES = Map.ofEntries(
            Map.entry("argentina", "AR"), Map.entry("méxico", "MX"), Map.entry("mexico", "MX"),
            Map.entry("united states", "US"), Map.entry("usa", "US"), Map.entry("canada", "CA"),
            Map.entry("brazil", "BR"), Map.entry("brasil", "BR"), Map.entry("colombia", "CO"),
            Map.entry("chile", "CL"), Map.entry("spain", "ES"), Map.entry("españa", "ES"),
            Map.entry("united kingdom", "GB"), Map.entry("uk", "GB"), Map.entry("india", "IN"),
            Map.entry("germany", "DE"), Map.entry("france", "FR"), Map.entry("australia", "AU"));
}
