package com.kaio.runtracker.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record IntervalsIcuWorkoutSendRequest(
        @NotNull @Min(1) Integer weekNumber,
        @NotBlank String dayOfWeek,
        @NotNull LocalDate scheduledDate) {
}
