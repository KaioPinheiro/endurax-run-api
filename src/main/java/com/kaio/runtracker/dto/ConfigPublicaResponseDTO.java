package com.kaio.runtracker.dto;

import com.kaio.runtracker.config.FluxoPlanoModo;

import java.math.BigDecimal;

public record ConfigPublicaResponseDTO(
        FluxoPlanoModo modoFluxoPlano,
        boolean pagamentoObrigatorio,
        boolean ambientePagamentoTeste,
        BigDecimal valorPlano) {
}
