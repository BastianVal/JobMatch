package mx.jobmatch.ingestion.domain;

import java.util.List;

public record ConnectorPage(List<RawPosting> postings, String nextCursor, boolean completeResponse) {
    public ConnectorPage { postings = List.copyOf(postings); }
}
