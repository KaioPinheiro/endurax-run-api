package com.kaio.runtracker.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IntervalsIcuEventResponse(
        Long id,
        String name,
        @JsonProperty("external_id") String externalId) {
}
