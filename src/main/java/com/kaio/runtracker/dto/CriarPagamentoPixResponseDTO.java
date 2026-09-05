package com.kaio.runtracker.dto;

import com.kaio.runtracker.entity.PagamentoStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CriarPagamentoPixResponseDTO(
        Long pagamentoId,
        String acessoToken,
        PagamentoStatus status,
        BigDecimal valor,
        String pixCopiaCola,
        String qrCodeBase64,
        String ticketUrl,
        OffsetDateTime dataExpiracao) {
}
