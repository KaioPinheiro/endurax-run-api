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
                "Melhorar condicionamento", "Primeiros 5 km"}) {
            assertEquals(
                    factory.criarPromptBase(),
                    factory.criarPrompt(request(objetivo, "5 km")));
        }
    }

    @Test
    void emagrecerRecebeOrientacaoEspecificaEBaseSemAfetarOutrosObjetivos() {
        String prompt = factory.criarPrompt(request("Emagrecer", "Sem distância alvo definida"));

        assertTrue(prompt.contains("ciclo de corrida sustentavel"));
        assertTrue(prompt.contains("regularidade"));
        assertTrue(prompt.contains("intensidade confortavel"));
        assertTrue(prompt.contains("capacidade atual do atleta"));
        assertTrue(prompt.contains("Nao aumente volume ou intensidade apenas para elevar gasto energetico"));
        assertTrue(prompt.contains("Nao prescreva dieta, calorias ou deficit calorico"));
        assertTrue(prompt.contains("nao prometa perda de peso"));
        assertTrue(prompt.contains(factory.criarPromptBase().strip()));
        assertFalse(prompt.contains("viabilidade obrigatoria para maratona"));
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
    void performanceCincoKmUsaPromptParametrizadoBase() {
        String prompt = factory.criarPrompt(
                request("Melhorar tempo nos 5 km", "5 km"));

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
