package com.kaio.runtracker.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GerarPlanoTreinoRequestDTOTest {

    @Test
    void performanceExigeTemposEImprovement() {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setObjetivo("Melhorar tempo nos 5 km");
        assertFalse(request.isTemposPerformanceValidos());

        request.setTempoAtual("31:20");
        request.setTempoDesejado("31:20");
        assertFalse(request.isTemposPerformanceValidos());

        request.setTempoDesejado("29:30");
        assertTrue(request.isTemposPerformanceValidos());
    }

    @Test
    void objetivosRemovidosNaoSaoAceitosEmNovasSolicitacoes() {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setObjetivo("Outro");
        assertFalse(request.isObjetivoValido());
        request.setObjetivo("Sub 30 nos 5 km");
        assertFalse(request.isObjetivoValido());
        request.setObjetivo("Começar a correr");
        assertTrue(request.isObjetivoValido());
    }
}
