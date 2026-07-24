package com.kaio.runtracker.ai.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import com.kaio.runtracker.service.GerarTreinoIAException;
import com.kaio.runtracker.service.OpenAIService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OpenAiTrainingPlanReviewer implements TrainingPlanReviewer {
    private final OpenAIService openAIService;
    private final ObjectMapper objectMapper;

    public OpenAiTrainingPlanReviewer(
            OpenAIService openAIService,
            ObjectMapper objectMapper) {
        this.openAIService = openAIService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReviewResult review(
            PlanoTreinoIAResponseDTO plano,
            AgentExecutionContext context) {
        try {
            String resposta = openAIService.enviarPromptRevisaoPlano(
                    systemPrompt(),
                    userPrompt(plano, context),
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

    private String systemPrompt() {
        return """
                Você é o revisor técnico de um plano completo de corrida.
                Não altere o plano. Analise cada semana e o ciclo global.
                Retorne somente JSON: {"valid":boolean,"errors":[],"warnings":[],"summary":""}.
                Um erro torna valid=false. Warnings não reprovam sozinhos.
                """;
    }

    private String userPrompt(
            PlanoTreinoIAResponseDTO plano,
            AgentExecutionContext context) throws JsonProcessingException {
        return """
                Revise o plano completo considerando o contexto informado.

                Em cada semana verifique quantidade e dias dos treinos, duplicidades,
                distribuição de intensidade, recuperação, treino leve, longão,
                compatibilidade com nível e objetivo, distância, pace, duração,
                excesso de carga e clareza.

                Globalmente verifique quantidade e continuidade das semanas,
                progressão de volume e intensidade, aumentos bruscos, repetições,
                recuperação, preparação para a distância, redução antes da prova,
                semana da prova e coerência do período posterior ao evento.
                Quando a prova estiver a menos de quatro semanas, não reprove o plano
                apenas pela proximidade. Confirme que a prova está na data correta,
                que existe alerta de prazo insuficiente e que as semanas posteriores
                contêm recuperação e retorno progressivo, sem treino intenso imediato
                nem preparação como se a prova ainda não tivesse ocorrido.

                Duração definida: %d
                Dias disponíveis: %s
                Objetivo: %s
                Experiência: %s
                Volume atual: %s
                Distância alvo: %s
                Data da prova: %s
                Possui lesão: %s

                Plano:
                %s
                """.formatted(
                context.duracaoSemanas(),
                context.request().getDiasDisponiveis(),
                context.request().getObjetivo(),
                context.request().getExperienciaCorrida(),
                context.request().getVolumeSemanalAtual(),
                context.request().getDistanciaAlvo(),
                context.request().getDataProva(),
                Boolean.TRUE.equals(context.request().getPossuiLesao()) ? "Sim" : "Não",
                objectMapper.writeValueAsString(plano));
    }
}
