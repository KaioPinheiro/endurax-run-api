package com.kaio.runtracker.dto;

import com.kaio.runtracker.entity.GeracaoPlanoStatus;
import com.kaio.runtracker.entity.PagamentoStatus;

public record PagamentoResultadoResponseDTO(
        PagamentoStatus pagamentoStatus,
        GeracaoPlanoStatus geracaoStatus,
        Long planoId,
        String mensagem) {
}
