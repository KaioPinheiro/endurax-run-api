package com.kaio.runtracker.ai.prompt;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaceAlvoCalculatorTest {

    @Test
    void calculaPacesDosObjetivosDePerformance() {
        assertEquals("4:00 min/km", calcular("Melhorar tempo nos 5 km", "20:00"));
        assertEquals("4:00 min/km", calcular("Melhorar tempo nos 10 km", "40:00"));
        assertEquals("4:16 min/km", calcular("Melhorar tempo na Meia Maratona", "1:30:00"));
        assertEquals("4:16 min/km", calcular("Melhorar tempo na Maratona", "3:00:00"));
    }

    @Test
    void ignoraObjetivoNaoPerformanceETemposInvalidos() {
        GerarPlanoTreinoRequestDTO naoPerformance = request("Melhorar condicionamento", "40:00");
        assertTrue(PaceAlvoCalculator.calcular(naoPerformance).isEmpty());
        assertTrue(PaceAlvoCalculator.calcular(request(
                "Melhorar tempo nos 10 km", "invalido")).isEmpty());
        assertTrue(PaceAlvoCalculator.calcular(request(
                "Melhorar tempo na Maratona", "3:99:00")).isEmpty());
        assertTrue(PaceAlvoCalculator.calcular(null).isEmpty());
    }

    private String calcular(String objetivo, String tempo) {
        return PaceAlvoCalculator.calcular(request(objetivo, tempo)).orElseThrow();
    }

    private GerarPlanoTreinoRequestDTO request(String objetivo, String tempo) {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setObjetivo(objetivo);
        request.setTempoDesejado(tempo);
        return request;
    }
}
