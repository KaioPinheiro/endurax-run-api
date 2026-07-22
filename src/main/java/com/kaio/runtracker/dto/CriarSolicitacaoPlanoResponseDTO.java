package com.kaio.runtracker.dto;

import com.kaio.runtracker.entity.SolicitacaoPlanoStatus;

public record CriarSolicitacaoPlanoResponseDTO(
        Long solicitacaoPlanoId,
        SolicitacaoPlanoStatus status) {
}
