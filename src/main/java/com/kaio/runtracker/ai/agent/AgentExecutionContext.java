package com.kaio.runtracker.ai.agent;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;

import java.time.LocalDate;

public record AgentExecutionContext(
        GerarPlanoTreinoRequestDTO request,
        int duracaoSemanas,
        LocalDate dataInicio,
        String identificadorTecnico) {
}
