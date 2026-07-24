package com.kaio.runtracker.ai.agent;

import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;

public interface TrainingPlanGenerator {
    PlanoTreinoIAResponseDTO generate(AgentExecutionContext context);

    PlanoTreinoIAResponseDTO correct(
            PlanoTreinoIAResponseDTO plano,
            AgentExecutionContext context,
            ValidationResult validacao,
            ReviewResult revisao);
}
