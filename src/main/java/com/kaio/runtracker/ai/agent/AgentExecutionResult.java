package com.kaio.runtracker.ai.agent;

import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;

public record AgentExecutionResult(
        PlanoTreinoIAResponseDTO plano,
        int correcoesRealizadas,
        ValidationResult validacao,
        ReviewResult revisao) {
}
