package com.kaio.runtracker.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IntervalsIcuTokenResponse(
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("access_token") String accessToken,
        String scope,
        Athlete athlete) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Athlete(String id, String name) {}
}
