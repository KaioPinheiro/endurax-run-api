package com.kaio.runtracker.ai.prompt;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptObjetivoFactoryTest {

    private final PromptObjetivoFactory factory = new PromptObjetivoFactory();

    @Test
    void objetivosGeraisUsamPromptBase() {
        for (String objetivo : new String[]{
                "Melhorar condicionamento", "Emagrecer", "Primeiros 5 km"}) {
            assertEquals(
                    factory.criarPromptBase(),
                    factory.criarPrompt(request(objetivo, "5 km")));
        }
    }

    @Test
    void dezKmRecebeSomenteRegrasDeDezKmENaoDeMaratona() {
        String prompt = factory.criarPrompt(request("Primeiros 10 km", "10 km"));

        assertTrue(prompt.contains("Em objetivos de 10 km"));
        assertTrue(prompt.contains("retorne alerta como string vazia"));
        assertFalse(prompt.contains("viabilidade obrigatoria para maratona"));
    }

    @Test
    void primeiraMaratonaRecebeRegrasDeViabilidade() {
        String prompt = factory.criarPrompt(
                request("Primeira Maratona", "42 km"));

        assertTrue(prompt.contains("Avaliacao de viabilidade obrigatoria para maratona"));
        assertTrue(prompt.contains("ciclo de construcao de base"));
        assertFalse(prompt.contains("Em objetivos de 10 km"));
    }

    @Test
    void sub30CincoKmPossuiMetodoIndependenteSemInventarNovaRegra() {
        String prompt = factory.criarPrompt(
                request("Sub 30 nos 5 km", "5 km"));

        assertEquals(factory.criarPromptSub30CincoKm(), prompt);
        assertEquals(factory.criarPromptBase(), prompt);
    }

    private GerarPlanoTreinoRequestDTO request(
            String objetivo,
            String distancia) {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setObjetivo(objetivo);
        request.setDistanciaAlvo(distancia);
        return request;
    }
}
