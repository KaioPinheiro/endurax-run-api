package com.kaio.runtracker.controller;

import com.kaio.runtracker.dto.IntervalsIcuConnectionStatusDTO;
import com.kaio.runtracker.dto.IntervalsIcuWorkoutSendRequest;
import com.kaio.runtracker.dto.IntervalsIcuWorkoutSendResponse;
import com.kaio.runtracker.service.IntervalsIcuOAuthService;
import com.kaio.runtracker.service.IntervalsIcuWorkoutService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/integrations/intervals-icu")
public class IntervalsIcuOAuthController {
    private final IntervalsIcuOAuthService service;
    private final IntervalsIcuWorkoutService workoutService;

    public IntervalsIcuOAuthController(
            IntervalsIcuOAuthService service,
            IntervalsIcuWorkoutService workoutService) {
        this.service = service;
        this.workoutService = workoutService;
    }

    @GetMapping("/plans/{planAccessToken}/authorize")
    public ResponseEntity<Void> authorize(@PathVariable String planAccessToken) {
        URI location = service.beginAuthorization(planAccessToken);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, location.toString()).build();
    }

    @GetMapping("/callback")
    public IntervalsIcuConnectionStatusDTO callback(
            @RequestParam(required = false) String code,
            @RequestParam String state,
            @RequestParam(required = false) String error) {
        return service.completeAuthorization(code, state, error);
    }

    @GetMapping("/plans/{planAccessToken}/status")
    public IntervalsIcuConnectionStatusDTO status(@PathVariable String planAccessToken) {
        return service.connectionStatus(planAccessToken);
    }

    @PostMapping("/plans/{planAccessToken}/workouts")
    public IntervalsIcuWorkoutSendResponse sendWorkout(
            @PathVariable String planAccessToken,
            @Valid @RequestBody IntervalsIcuWorkoutSendRequest request) {
        return workoutService.sendOne(planAccessToken, request);
    }
}
