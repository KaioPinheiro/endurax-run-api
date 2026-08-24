package com.kaio.runtracker.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kaio.runtracker.dto.PublicTrainingPlanResponseDTO;
import com.kaio.runtracker.service.TrainingPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TrainingPlanControllerTest {

    private TrainingPlanService service;
    private MockMvc mockMvc;

    @BeforeEach
    void configurar() {
        service = org.mockito.Mockito.mock(TrainingPlanService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TrainingPlanController(service)).build();
    }

    @Test
    void listagemPublicaEIdSequencialNaoPossuemEndpoint() throws Exception {
        mockMvc.perform(get("/training-plans")).andExpect(status().isNotFound());
        mockMvc.perform(get("/training-plans/1")).andExpect(status().isNotFound());
    }

    @Test
    void tokenValidoRetornaSomentePlanoAssociado() throws Exception {
        when(service.buscarPlanoPagoPorToken("token-compra-a"))
                .thenReturn(new PublicTrainingPlanResponseDTO("Plano A", "5 km", "iniciante", "{}"));

        mockMvc.perform(get("/training-plans/public/token-compra-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Plano A"))
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void tokenInvalidoOuDeOutraCompraNaoRetornaPlano() throws Exception {
        when(service.buscarPlanoPagoPorToken("token-invalido")).thenReturn(null);

        mockMvc.perform(get("/training-plans/public/token-invalido"))
                .andExpect(status().isNotFound());
    }
}
