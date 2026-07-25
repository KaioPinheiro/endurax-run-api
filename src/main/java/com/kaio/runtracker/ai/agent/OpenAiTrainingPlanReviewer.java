package com.kaio.runtracker.ai.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.ai.prompt.PlanoTreinoReviewPromptBuilder;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import com.kaio.runtracker.service.GerarTreinoIAException;
import com.kaio.runtracker.service.OpenAIService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OpenAiTrainingPlanReviewer implements TrainingPlanReviewer {
    private final OpenAIService openAIService;
    private final ObjectMapper objectMapper;
    private final PlanoTreinoReviewPromptBuilder promptBuilder;

    public OpenAiTrainingPlanReviewer(
            OpenAIService openAIService,
            ObjectMapper objectMapper,
            PlanoTreinoReviewPromptBuilder promptBuilder) {
        this.openAIService = openAIService;
        this.objectMapper = objectMapper;
        this.promptBuilder = promptBuilder;
    }

    @Override
    public ReviewResult review(
            PlanoTreinoIAResponseDTO plano,
            AgentExecutionContext context) {
        try {
            String resposta = openAIService.enviarPromptRevisaoPlano(
                    promptBuilder.criarSystemPrompt(),
                    promptBuilder.criarPrompt(plano, context),
                    context.duracaoSemanas());
            ReviewResult revisao = objectMapper.readValue(resposta, ReviewResult.class);
            if (revisao.valid() && !revisao.errors().isEmpty()) {
                return new ReviewResult(
                        false, revisao.errors(), revisao.warnings(), revisao.summary());
            }
            return revisao;
        } catch (JsonProcessingException exception) {
            throw new GerarTreinoIAException(
                    HttpStatus.BAD_GATEWAY,
                    "A revisão do plano retornou uma estrutura inválida.",
                    exception);
        }
    }

}
