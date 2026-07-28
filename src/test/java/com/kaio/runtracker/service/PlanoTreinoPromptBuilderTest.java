package com.kaio.runtracker.service;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.ai.prompt.PromptObjetivoFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanoTreinoPromptBuilderTest {

    private final PlanoTreinoPromptBuilder promptBuilder =
            new PlanoTreinoPromptBuilder(new PromptObjetivoFactory());

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
    }

    @Test
    void inicianteSemCincoKmRecebeOrientacaoDeCorridaECaminhada() {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setObjetivo("Primeiros 5 km");
        request.setCorre5KmSemCaminhar(false);
        request.setExperienciaCorrida("Nunca corri");
        request.setVolumeSemanalAtual("Menos de 10 km");
        request.setRitmoConfortavel("Caminhada / trote leve");
        request.setDistanciaAlvo("5 km");
        request.setDiasDisponiveis(List.of(
                "terça-feira", "quinta-feira", "domingo"));
        request.setPossuiProva(false);
        request.setPossuiLesao(false);

        String prompt = promptBuilder.criarPrompt(request, 4);

        assertTrue(prompt.contains("Corre 5 km direto sem caminhar: Nao"));
        assertTrue(prompt.contains("Defina internamente o nivel real do corredor"));
        assertTrue(prompt.contains("alternando caminhada e blocos curtos de trote ou corrida leve"));
        assertTrue(prompt.contains("sem tiros, intervalados intensos"));
        assertTrue(prompt.contains("Trechos um pouco mais rapidos so podem aparecer"));
        assertTrue(prompt.contains("avalie explicitamente idade e presenca de lesao"));
        assertTrue(prompt.contains("todas as sessoes devem usar caminhada no Aquecimento"));
        assertTrue(prompt.contains("o Principal deve ser dividido em passos distintos"));
        assertTrue(prompt.contains("3 x (2 min de trote leve + 3 min de caminhada)"));
        assertTrue(prompt.contains("atletas mais velhos, lesionados, com dor"));
        assertTrue(prompt.contains("Desaquecimento deve ser sempre caminhada"));
    }

    @Test
    void meiaMaratonaIncluiMaiorDistanciaNoContexto() {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setObjetivo("Primeira Meia Maratona");
        request.setCorre5KmSemCaminhar(true);
        request.setTempo5Km("29 minutos");
        request.setMaiorDistanciaCorrida("14 km");
        request.setExperienciaCorrida("1 a 3 anos");
        request.setVolumeSemanalAtual("20-40 km");
        request.setRitmoConfortavel("6:00-6:30 min/km");
        request.setDistanciaAlvo("21 km");
        request.setDiasDisponiveis(List.of(
                "terça-feira", "quinta-feira", "domingo"));
        request.setPossuiProva(false);
        request.setPossuiLesao(false);

        String prompt = promptBuilder.criarPrompt(request, 4);

        assertTrue(prompt.contains("Tempo atual nos 5 km: 29 minutos"));
        assertTrue(prompt.contains("Maior distancia ja percorrida: 14 km"));
        assertTrue(prompt.contains("use o tempo dos 5 km para estimar de forma prudente"));
    }
}
