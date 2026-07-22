package com.kaio.runtracker.dto;

import com.kaio.runtracker.entity.PagamentoStatus;

public record PagamentoStatusResponseDTO(
        PagamentoStatus status,
        String statusDetail,
        boolean pago,
        boolean expirado) {
}
