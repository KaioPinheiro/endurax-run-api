package com.kaio.runtracker.ai.agent;

import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;

public interface TrainingPlanReviewer {
    ReviewResult review(
            PlanoTreinoIAResponseDTO plano,
            AgentExecutionContext context);
}
