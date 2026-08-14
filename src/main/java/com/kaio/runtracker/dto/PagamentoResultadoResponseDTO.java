package com.kaio.runtracker.dto;

import com.kaio.runtracker.entity.GeracaoPlanoStatus;
import com.kaio.runtracker.entity.PagamentoStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PagamentoResultadoResponseDTO(
        Long pagamentoId,
        PagamentoStatus pagamentoStatus,
        GeracaoPlanoStatus geracaoStatus,
        Long planoId,
        String mensagem,
        BigDecimal valor,
        String pixCopiaCola,
        String qrCodeBase64,
        String ticketUrl,
        LocalDateTime dataExpiracao) {
}
