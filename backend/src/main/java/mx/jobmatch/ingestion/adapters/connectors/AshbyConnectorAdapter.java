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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Public Ashby Job Posting API connector. The registry, not a member, owns board names. */
@Component
public final class AshbyConnectorAdapter implements ConnectorPort {
    private final ObjectMapper json;
    private final HttpClient http;
    @Autowired public AshbyConnectorAdapter(ObjectMapper json) { this(json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()); }
    AshbyConnectorAdapter(ObjectMapper json, HttpClient http) { this.json = json; this.http = http; }
    @Override public String sourceKey() { return "ASHBY"; }

    @Override public ConnectorPage fetch(ConnectorQuery query) {
        String board = boardKey(query);
        try {
            URI uri = URI.create("https://api.ashbyhq.com/posting-api/job-board/" + board + "?includeCompensation=true");
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429 || response.statusCode() >= 500) throw new ConnectorFailure("ASHBY_UNAVAILABLE", true);
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new ConnectorFailure("ASHBY_REJECTED_REQUEST", false);
            return parse(response.body(), query, json);
        } catch (ConnectorFailure failure) { throw failure;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new ConnectorFailure("ASHBY_INTERRUPTED", true);
        } catch (Exception failure) { throw new ConnectorFailure("ASHBY_INVALID_RESPONSE", true); }
    }

    static ConnectorPage parse(String body, ConnectorQuery query, ObjectMapper json) throws Exception {
        String board = boardKey(query);
        JsonNode jobs = json.readTree(body).path("jobs");
        if (!jobs.isArray()) throw new ConnectorFailure("ASHBY_INVALID_RESPONSE", true);
        List<RawPosting> postings = new ArrayList<>();
        for (JsonNode job : jobs) {
            if (job.path("isListed").isBoolean() && !job.path("isListed").asBoolean()) continue;
            String id = AtsConnectorSupport.text(job, "id"), title = AtsConnectorSupport.text(job, "title");
            String url = first(AtsConnectorSupport.text(job, "jobUrl"), AtsConnectorSupport.text(job, "applyUrl"));
            if (AtsConnectorSupport.blank(id) || AtsConnectorSupport.blank(title) || AtsConnectorSupport.blank(url)) continue;
            AtsConnectorSupport.Location location = mexicanLocation(job);
            if (!"MX".equals(location.countryCode())) continue;
            String description = first(AtsConnectorSupport.htmlToText(AtsConnectorSupport.text(job, "descriptionHtml")), title);
            String fullText = (title + " " + description + " " + AtsConnectorSupport.value(location.city())).toLowerCase(Locale.ROOT);
            postings.add(new RawPosting("ashby:" + board + ":" + id, url, title,
                    query.employerName() == null ? board : query.employerName(), description,
                    location.countryCode(), location.state(), location.city(),
                    job.path("isRemote").asBoolean(false) || fullText.contains("remote") ? "REMOTE" : "ONSITE",
                    employment(job), null, null, null, null,
                    AtsConnectorSupport.instant(AtsConnectorSupport.text(job, "publishedAt")), json.writeValueAsString(job)));
        }
        return new ConnectorPage(postings, null, true);
    }
    private static AtsConnectorSupport.Location mexicanLocation(JsonNode job) {
        List<JsonNode> locations = new ArrayList<>(); locations.add(job);
        JsonNode secondary = job.path("secondaryLocations"); if (secondary.isArray()) secondary.forEach(locations::add);
        for (JsonNode location : locations) {
            JsonNode address = location.path("address").path("postalAddress");
            String raw = first(AtsConnectorSupport.text(location, "location"), AtsConnectorSupport.text(address, "addressLocality"));
            AtsConnectorSupport.Location parsed = AtsConnectorSupport.location(raw, AtsConnectorSupport.text(address, "addressCountry"));
            if ("MX".equals(parsed.countryCode())) return parsed;
        }
        return AtsConnectorSupport.location(null, null);
    }
    private static String employment(JsonNode job) {
        String type = AtsConnectorSupport.text(job, "employmentType"); if (type == null) return "OTHER";
        String normalized = type.toLowerCase(Locale.ROOT);
        if (normalized.contains("full")) return "FULL_TIME";
        if (normalized.contains("part")) return "PART_TIME";
        if (normalized.contains("contract")) return "CONTRACT";
        if (normalized.contains("intern")) return "INTERNSHIP";
        return "OTHER";
    }
    private static String boardKey(ConnectorQuery query) {
        String board = query.boardKey();
        if (AtsConnectorSupport.blank(board) || !board.matches("[a-zA-Z0-9_-]{1,200}")) throw new ConnectorFailure("ASHBY_BOARD_NOT_CONFIGURED", false);
        return board;
    }
    private static String first(String... values) { for (String value : values) if (!AtsConnectorSupport.blank(value)) return value; return null; }
}
