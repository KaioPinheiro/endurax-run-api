package com.kaio.runtracker.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.cors.CorsConfiguration;

class SecurityConfigTest {

    @Test
    void producaoPermiteSomenteOriginsOficiais() {
        List<String> origins = originsPermitidas("prod");

        assertTrue(origins.contains("https://enduraxrun.com.br"));
        assertTrue(origins.contains("https://www.enduraxrun.com.br"));
        assertFalse(origins.contains("http://localhost:5173"));
        assertFalse(origins.contains("https://origem-aleatoria.example"));
    }

    @Test
    void desenvolvimentoPermiteLocalhostEOriginsOficiais() {
        List<String> origins = originsPermitidas("dev");

        assertTrue(origins.contains("http://localhost:5173"));
        assertTrue(origins.contains("https://enduraxrun.com.br"));
        assertTrue(origins.contains("https://www.enduraxrun.com.br"));
        assertFalse(origins.contains("https://origem-aleatoria.example"));
    }

    private List<String> originsPermitidas(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/teste");
        CorsConfiguration configuration = new SecurityConfig(environment)
                .corsConfigurationSource()
                .getCorsConfiguration(request);
        return configuration.getAllowedOrigins();
    }
}
