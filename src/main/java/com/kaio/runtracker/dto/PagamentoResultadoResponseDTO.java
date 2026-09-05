package com.kaio.runtracker.dto;

import com.kaio.runtracker.entity.GeracaoPlanoStatus;
import com.kaio.runtracker.entity.PagamentoStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PagamentoResultadoResponseDTO(
        Long pagamentoId,
        PagamentoStatus pagamentoStatus,
        GeracaoPlanoStatus geracaoStatus,
        String planoToken,
        String mensagem,
        BigDecimal valor,
        String pixCopiaCola,
        String qrCodeBase64,
        String ticketUrl,
        OffsetDateTime dataExpiracao) {
}
