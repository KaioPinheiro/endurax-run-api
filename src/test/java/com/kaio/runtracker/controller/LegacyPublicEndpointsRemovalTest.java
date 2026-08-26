package com.kaio.runtracker.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LegacyPublicEndpointsRemovalTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping mappings;

    @Test
    void usersNoLongerHasPublicMappings() throws Exception {
        mockMvc.perform(get("/users")).andExpect(status().isNotFound());
        mockMvc.perform(get("/users/1")).andExpect(status().isNotFound());
        mockMvc.perform(get("/users/1/workouts")).andExpect(status().isNotFound());
        mockMvc.perform(post("/users").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/users/1").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/users/1")).andExpect(status().isNotFound());
    }

    @Test
    void workoutsNoLongerHasPublicMappings() throws Exception {
        mockMvc.perform(get("/workouts")).andExpect(status().isNotFound());
        mockMvc.perform(get("/workouts/pendentes")).andExpect(status().isNotFound());
        mockMvc.perform(get("/workouts/1")).andExpect(status().isNotFound());
        mockMvc.perform(post("/workouts").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/workouts/1/concluir")).andExpect(status().isNotFound());
        mockMvc.perform(put("/workouts/1").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/workouts/1")).andExpect(status().isNotFound());
    }

    @Test
    void treinosNoLongerHasPublicMappings() throws Exception {
        mockMvc.perform(get("/treinos")).andExpect(status().isNotFound());
        mockMvc.perform(get("/treinos/1")).andExpect(status().isNotFound());
        mockMvc.perform(post("/treinos").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/treinos/1").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/treinos/1")).andExpect(status().isNotFound());
    }

    @Test
    void loginNoLongerHasPublicMapping() throws Exception {
        mockMvc.perform(post("/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void currentV1MappingsRemainRegistered() {
        assertMapping(HttpMethod.POST, "/api/solicitacoes-plano");
        assertMapping(HttpMethod.POST, "/api/pagamentos/pix");
        assertMapping(HttpMethod.POST, "/api/webhooks/mercado-pago");
        assertMapping(HttpMethod.GET, "/api/pagamentos/public/{token}/status");
        assertMapping(HttpMethod.GET, "/api/pagamentos/public/{token}/resultado");
        assertMapping(HttpMethod.POST,
                "/api/pagamentos/public/{token}/geracao/tentar-novamente");
        assertMapping(HttpMethod.GET, "/training-plans/public/{token}");
        assertMapping(HttpMethod.GET, "/api/config/publica");
    }

    private void assertMapping(HttpMethod method, String path) {
        Map<RequestMappingInfo, ?> registered = mappings.getHandlerMethods();
        assertThat(registered.keySet()).anySatisfy(mapping -> {
            assertThat(mapping.getPatternValues()).contains(path);
            assertThat(mapping.getMethodsCondition().getMethods())
                    .contains(org.springframework.web.bind.annotation.RequestMethod.valueOf(method.name()));
        });
    }
}
