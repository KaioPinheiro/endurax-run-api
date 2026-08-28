package com.kaio.runtracker.ai;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapacidadeCincoKmTest {

    @Test
    void aplicaSomenteParaExperienciasEObjetivosDefinidos() {
        assertTrue(aplicavel("Estou parado(a)", "Começar a correr"));
        assertTrue(aplicavel("Menos de 6 meses", "Primeiros 5 km"));
        assertTrue(aplicavel("6 meses a 1 ano", "Melhorar condicionamento"));

        assertFalse(aplicavel("Nunca corri", "Começar a correr"));
        assertFalse(aplicavel("1 a 3 anos", "Emagrecer"));
        assertFalse(aplicavel("Mais de 3 anos", "Primeiros 5 km"));
        assertFalse(aplicavel("Menos de 6 meses", "Primeiros 10 km"));
        assertFalse(aplicavel("Menos de 6 meses", "Melhorar tempo nos 5 km"));
    }

    @Test
    void neutralizaValorLegadoForaDoEscopo() {
        GerarPlanoTreinoRequestDTO request = request(
                "Mais de 3 anos", "Emagrecer", false);

        assertNull(CapacidadeCincoKm.respostaAplicavel(request));
    }

    private boolean aplicavel(String experiencia, String objetivo) {
        return CapacidadeCincoKm.ehAplicavel(request(experiencia, objetivo, true));
    }

    private GerarPlanoTreinoRequestDTO request(
            String experiencia, String objetivo, boolean correCincoKm) {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setExperienciaCorrida(experiencia);
        request.setObjetivo(objetivo);
        request.setCorre5KmSemCaminhar(correCincoKm);
        return request;
    }
}
