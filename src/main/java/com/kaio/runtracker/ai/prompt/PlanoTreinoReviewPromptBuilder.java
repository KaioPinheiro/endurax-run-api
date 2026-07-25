package com.kaio.runtracker.ai.prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.ai.agent.AgentExecutionContext;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class PlanoTreinoReviewPromptBuilder {

    private final ObjectMapper objectMapper;

    public PlanoTreinoReviewPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String criarSystemPrompt() {
        return """
                Você é o revisor técnico de um plano completo de corrida.
                Não altere o plano. Analise cada semana e o ciclo global.
                Retorne somente JSON: {"valid":boolean,"errors":[],"warnings":[],"summary":""}.
                Um erro torna valid=false. Warnings não reprovam sozinhos.
                """;
    }

    public String criarPrompt(
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
