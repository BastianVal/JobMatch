package mx.jobmatch.cvimport.domain;

import java.util.Map;

public record ImportProposal(String type, Map<String, Object> payload, String fingerprint) {}
