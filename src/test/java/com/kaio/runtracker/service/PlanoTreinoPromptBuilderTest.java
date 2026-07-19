package com.kaio.runtracker.service;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanoTreinoPromptBuilderTest {

    private final PlanoTreinoPromptBuilder promptBuilder =
            new PlanoTreinoPromptBuilder();

    @Test
    void promptExigePlanoFactivelEAvisoQuandoPrazoForInsuficiente() {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setObjetivo("Maratona em 3 horas");
        request.setExperienciaCorrida("Mais de 3 anos");
        request.setVolumeSemanalAtual("20-40 km");
        request.setRitmoConfortavel("5:30-6:00 min/km");
        request.setDistanciaAlvo("42 km");
        request.setDiasDisponiveis(List.of(
                "segunda-feira", "terca-feira", "sexta-feira", "sabado"));
        request.setDiaLongao("sabado");
        request.setPossuiProva(false);
        request.setPossuiLesao(false);

        String prompt = promptBuilder.criarPrompt(request, 4);

        assertTrue(prompt.contains("Criterios de um plano factivel"));
        assertTrue(prompt.contains("75% a 85%"));
        assertTrue(prompt.contains("nao necessariamente como a preparacao completa"));
        assertTrue(prompt.contains("ciclo de construcao de base"));
        assertTrue(prompt.contains("Somente para maratona"));
        assertTrue(prompt.contains("\"alerta\": \"\""));
    }

    @Test
    void promptDeDezKmUsaFaixaCompletaESemAlerta() {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setObjetivo("Melhorar tempo nos 10 km");
        request.setExperienciaCorrida("1 a 3 anos");
        request.setVolumeSemanalAtual("20-40 km");
        request.setRitmoConfortavel("5:30-6:00 min/km");
        request.setDistanciaAlvo("10 km");
        request.setDiasDisponiveis(List.of("terca-feira", "quinta-feira", "domingo"));
        request.setDiaLongao("domingo");
        request.setPossuiProva(false);
        request.setPossuiLesao(false);

        String prompt = promptBuilder.criarPrompt(request, 4);

        assertTrue(prompt.contains("Em objetivos de 10 km, use a faixa completa"));
        assertTrue(prompt.contains("nao assuma automaticamente o menor valor"));
        assertTrue(prompt.contains("inclusive 5 km, 10 km e meia maratona, retorne alerta como string vazia"));
    }

    @Test
    void systemPromptProibePromessaIrrealDeResultado() {
        String systemPrompt = promptBuilder.criarSystemPrompt();

        assertTrue(systemPrompt.contains("Nunca prometa"));
        assertTrue(systemPrompt.contains("construcao de base"));
    }
}
