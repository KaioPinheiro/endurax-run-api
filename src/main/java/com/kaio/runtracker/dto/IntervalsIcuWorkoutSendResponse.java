package com.kaio.runtracker.dto;

import java.time.LocalDate;

public record IntervalsIcuWorkoutSendResponse(
        Long eventId,
        String externalId,
        String name,
        LocalDate scheduledDate,
        boolean alreadySent) {
}
