package com.kaio.runtracker.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import com.kaio.runtracker.service.OpenAIService;
import com.kaio.runtracker.ai.prompt.PlanoTreinoReviewPromptBuilder;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiTrainingPlanReviewerTest {
    @Test
    void converteRespostaEstruturadaSemChamadaReal() {
        OpenAIService openAIService = mock(OpenAIService.class);
        when(openAIService.enviarPromptRevisaoPlano(anyString(), anyString(), eq(4)))
                .thenReturn("""
                        {
                          "valid": false,
                          "errors": ["volume excessivo"],
                          "warnings": ["revisar pace"],
                          "summary": "Necessita correção"
                        }
                        """);
        ObjectMapper objectMapper = new ObjectMapper();
        OpenAiTrainingPlanReviewer reviewer =
                new OpenAiTrainingPlanReviewer(
                        openAIService,
                        objectMapper,
                        new PlanoTreinoReviewPromptBuilder(objectMapper));
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setDiasDisponiveis(List.of("terça-feira"));
        AgentExecutionContext context = new AgentExecutionContext(
                request, 4, LocalDate.of(2026, 1, 5), "teste");

        ReviewResult resultado = reviewer.review(new PlanoTreinoIAResponseDTO(), context);

        assertFalse(resultado.valid());
        assertEquals(List.of("volume excessivo"), resultado.errors());
        assertEquals(List.of("revisar pace"), resultado.warnings());
    }
}
