package com.kaio.runtracker.dto;

import com.kaio.runtracker.entity.PagamentoStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CriarPagamentoPixResponseDTO(
        Long pagamentoId,
        PagamentoStatus status,
        BigDecimal valor,
        String pixCopiaCola,
        String qrCodeBase64,
        String ticketUrl,
        LocalDateTime dataExpiracao) {
}
