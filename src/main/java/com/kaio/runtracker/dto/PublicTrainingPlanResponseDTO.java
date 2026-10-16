package com.kaio.runtracker.dto;

import java.time.LocalDate;
import java.util.List;

public record PublicTrainingPlanResponseDTO(
        String nome,
        String objetivo,
        String nivel,
        String descricao,
        LocalDate dataInicioPlano,
        List<String> diasDisponiveis) {
}
