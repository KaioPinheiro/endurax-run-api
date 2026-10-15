package com.kaio.runtracker.controller;

import com.kaio.runtracker.dto.IntervalsIcuConnectionStatusDTO;
import com.kaio.runtracker.dto.IntervalsIcuWorkoutSendRequest;
import com.kaio.runtracker.dto.IntervalsIcuWorkoutSendResponse;
import com.kaio.runtracker.service.IntervalsIcuOAuthService;
import com.kaio.runtracker.service.IntervalsIcuWorkoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.LocalDate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

class IntervalsIcuOAuthControllerTest {
    private IntervalsIcuOAuthService service;
    private IntervalsIcuWorkoutService workoutService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(IntervalsIcuOAuthService.class);
        workoutService = mock(IntervalsIcuWorkoutService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new IntervalsIcuOAuthController(service, workoutService)).build();
    }

    @Test
    void authorizeRedirecionaParaUrlMontadaNoBackend() throws Exception {
        when(service.beginAuthorization("plan-token"))
                .thenReturn(URI.create("https://intervals.icu/oauth/authorize?state=abc"));

        mockMvc.perform(get("/api/integrations/intervals-icu/plans/plan-token/authorize"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "https://intervals.icu/oauth/authorize?state=abc"));
    }

    @Test
    void callbackNaoExpoeAccessToken() throws Exception {
        when(service.completeAuthorization("code", "state", null))
                .thenReturn(new IntervalsIcuConnectionStatusDTO(true, "Atleta",
                        "CALENDAR:WRITE", LocalDateTime.of(2026, 10, 10, 12, 0)));

        mockMvc.perform(get("/api/integrations/intervals-icu/callback")
                        .param("code", "code").param("state", "state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.athleteName").value("Atleta"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        verify(service).completeAuthorization("code", "state", null);
    }

    @Test
    void enviaSomenteOTreinoSolicitado() throws Exception {
        var request = new IntervalsIcuWorkoutSendRequest(
                2, "quinta-feira", LocalDate.of(2026, 10, 15));
        when(workoutService.sendOne("plan-token", request)).thenReturn(
                new IntervalsIcuWorkoutSendResponse(123L, "endurax-20-w2-quinta-feira",
                        "Treino de ritmo", request.scheduledDate(), false));

        mockMvc.perform(post("/api/integrations/intervals-icu/plans/plan-token/workouts")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"weekNumber":2,"dayOfWeek":"quinta-feira",
                                 "scheduledDate":"2026-10-15"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(123))
                .andExpect(jsonPath("$.name").value("Treino de ritmo"))
                .andExpect(jsonPath("$.alreadySent").value(false))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        verify(workoutService).sendOne("plan-token", request);
    }
}
