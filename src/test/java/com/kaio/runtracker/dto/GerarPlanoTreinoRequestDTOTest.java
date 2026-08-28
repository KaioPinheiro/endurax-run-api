package com.kaio.runtracker.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void tempoCincoKmEhValidadoQuandoCampoForAplicavelEPositivo() {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setExperienciaCorrida("Menos de 6 meses");
        request.setObjetivo("Primeiros 5 km");
        request.setCorre5KmSemCaminhar(true);

        for (String valor : List.of("29:30", "1:05:30", "2:00:00")) {
            request.setTempo5Km(valor);
            assertTrue(request.isTempo5KmValido(), valor);
        }
        for (String valor : List.of(
                "12:99", "1:70:00", "2:00:01", "5555555")) {
            request.setTempo5Km(valor);
            assertFalse(request.isTempo5KmValido(), valor);
        }
    }

    @Test
    void formatoLegadoForaDoEscopoPermaneceCompativel() {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setExperienciaCorrida("Mais de 3 anos");
        request.setObjetivo("Primeira Meia Maratona");
        request.setCorre5KmSemCaminhar(true);
        request.setTempo5Km("29 minutos");

        assertTrue(request.isTempo5KmValido());
    }
}
