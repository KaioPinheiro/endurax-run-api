package com.kaio.runtracker.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record IntervalsIcuEventRequest(
        String category,
        @JsonProperty("start_date_local") String startDateLocal,
        String type,
        String name,
        String description,
        @JsonProperty("external_id") String externalId) {
}
