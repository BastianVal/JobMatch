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

/** Public Lever Postings API connector. It only accepts managed board keys. */
@Component
public final class LeverConnectorAdapter implements ConnectorPort {
    private final ObjectMapper json;
    private final HttpClient http;

    @Autowired
    public LeverConnectorAdapter(ObjectMapper json) {
        this(json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }
    LeverConnectorAdapter(ObjectMapper json, HttpClient http) { this.json = json; this.http = http; }
    @Override public String sourceKey() { return "LEVER"; }

    @Override public ConnectorPage fetch(ConnectorQuery query) {
        String board = boardKey(query);
        try {
            URI uri = URI.create("https://api.lever.co/v0/postings/" + board + "?mode=json");
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429 || response.statusCode() >= 500) throw new ConnectorFailure("LEVER_UNAVAILABLE", true);
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new ConnectorFailure("LEVER_REJECTED_REQUEST", false);
            return parse(response.body(), query, json);
        } catch (ConnectorFailure failure) { throw failure;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ConnectorFailure("LEVER_INTERRUPTED", true);
        } catch (Exception failure) { throw new ConnectorFailure("LEVER_INVALID_RESPONSE", true); }
    }

    static ConnectorPage parse(String body, ConnectorQuery query, ObjectMapper json) throws Exception {
        String board = boardKey(query);
        JsonNode jobs = json.readTree(body);
        if (!jobs.isArray()) throw new ConnectorFailure("LEVER_INVALID_RESPONSE", true);
        List<RawPosting> postings = new ArrayList<>();
        for (JsonNode job : jobs) {
            String id = AtsConnectorSupport.text(job, "id");
            String title = AtsConnectorSupport.text(job, "text");
            String url = first(AtsConnectorSupport.text(job, "hostedUrl"), AtsConnectorSupport.text(job, "applyUrl"));
            if (AtsConnectorSupport.blank(id) || AtsConnectorSupport.blank(title) || AtsConnectorSupport.blank(url)) continue;
            JsonNode categories = job.path("categories");
            AtsConnectorSupport.Location location = mexicanLocation(categories);
            if (!"MX".equals(location.countryCode())) continue;
            String description = first(AtsConnectorSupport.text(job, "descriptionPlain"),
                    AtsConnectorSupport.htmlToText(AtsConnectorSupport.text(job, "description")), title);
            String additional = AtsConnectorSupport.text(job, "additionalPlain");
            if (!AtsConnectorSupport.blank(additional)) description = description + "\n\n" + additional;
            String fullText = (title + " " + description + " " + AtsConnectorSupport.value(location.city())).toLowerCase(Locale.ROOT);
            postings.add(new RawPosting("lever:" + board + ":" + id, url, title,
                    query.employerName() == null ? board : query.employerName(), description,
                    location.countryCode(), location.state(), location.city(),
                    fullText.contains("remote") ? "REMOTE" : "ONSITE", employment(categories), null,
                    null, null, null, published(job), json.writeValueAsString(job)));
        }
        return new ConnectorPage(postings, null, true);
    }

    private static AtsConnectorSupport.Location mexicanLocation(JsonNode categories) {
        List<String> candidates = new ArrayList<>();
        String main = AtsConnectorSupport.text(categories, "location");
        if (!AtsConnectorSupport.blank(main)) candidates.add(main);
        JsonNode all = categories.path("allLocations");
        if (all.isArray()) all.forEach(item -> candidates.add(item.asText()));
        return candidates.stream().map(value -> AtsConnectorSupport.location(value, null))
                .filter(location -> "MX".equals(location.countryCode())).findFirst()
                .orElseGet(() -> AtsConnectorSupport.location(main, null));
    }
    private static String employment(JsonNode categories) {
        String commitment = AtsConnectorSupport.text(categories, "commitment");
        if (commitment == null) return "OTHER";
        String normalized = commitment.toLowerCase(Locale.ROOT);
        if (normalized.contains("full")) return "FULL_TIME";
        if (normalized.contains("part")) return "PART_TIME";
        if (normalized.contains("contract")) return "CONTRACT";
        if (normalized.contains("intern")) return "INTERNSHIP";
        return "OTHER";
    }
    private static Instant published(JsonNode job) {
        JsonNode created = job.path("createdAt");
        return created.canConvertToLong() ? Instant.ofEpochMilli(created.asLong()) : Instant.now();
    }
    private static String boardKey(ConnectorQuery query) {
        String board = query.boardKey();
        if (AtsConnectorSupport.blank(board) || !board.matches("[a-zA-Z0-9_-]{1,200}")) throw new ConnectorFailure("LEVER_BOARD_NOT_CONFIGURED", false);
        return board;
    }
    private static String first(String... values) {
        for (String value : values) if (!AtsConnectorSupport.blank(value)) return value;
        return null;
    }
}
