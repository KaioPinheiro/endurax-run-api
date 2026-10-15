package com.kaio.runtracker.dto;

import java.time.LocalDateTime;

public record IntervalsIcuConnectionStatusDTO(
        boolean connected,
        String athleteName,
        String scope,
        LocalDateTime connectedAt) {
}
