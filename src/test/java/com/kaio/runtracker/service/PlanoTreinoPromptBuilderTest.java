package com.kaio.runtracker.service;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.ai.prompt.PromptObjetivoFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
        request.setTempoAtual("52:10");
        request.setTempoDesejado("49:30");
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
        assertTrue(prompt.contains("Distancia da meta de performance: 10 km"));
        assertTrue(prompt.contains("Tempo atual na distancia alvo: 52:10"));
        assertTrue(prompt.contains("Tempo desejado na distancia alvo: 49:30"));
        assertTrue(prompt.contains("Pace matematico necessario para atingir a meta (PACE-ALVO DA META): 4:57 min/km"));
        assertTrue(prompt.contains("RITMO CONFORTAVEL ATUAL"));
        assertTrue(prompt.contains("os ritmos de treino nao precisam ser iguais"));
    }

    @Test
    void objetivoNaoPerformanceNaoRecebePaceAlvo() {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setObjetivo("Melhorar condicionamento");
        request.setTempoDesejado("40:00");
        request.setExperienciaCorrida("1 a 3 anos");
        request.setVolumeSemanalAtual("20-40 km");
        request.setRitmoConfortavel("5:30-6:00 min/km");
        request.setDistanciaAlvo("10 km");
        request.setDiasDisponiveis(List.of("terca-feira"));
        request.setPossuiProva(false);

        String prompt = promptBuilder.criarPrompt(request, 4);

        assertTrue(prompt.contains("PACE-ALVO DA META): Nao se aplica"));
        assertFalse(prompt.contains("PACE-ALVO DA META): 4:00 min/km"));
        assertTrue(prompt.contains("duracaoSemanas somente como o ciclo solicitado"));
        assertTrue(prompt.contains("nao precisam concluir toda a preparacao"));
        assertTrue(prompt.contains("Nao force o longao a alcancar a distancia-alvo"));
        assertTrue(prompt.contains("aumento maximo = maior entre 15 min e 15%"));
        assertTrue(prompt.contains("nao force crescimento monotonico"));
    }

    @Test
    void systemPromptProibePromessaIrrealDeResultado() {
        String systemPrompt = promptBuilder.criarSystemPrompt();

        assertTrue(systemPrompt.contains("Nunca prometa"));
    }

    @Test
    void nuncaCorreuUsaRegrasPropriasSemAtivarBooleanoDeCincoKm() {
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

        assertTrue(prompt.contains("Corre 5 km direto sem caminhar: Nao informado"));
        assertTrue(prompt.contains("nao se aplica a este perfil e objetivo"));
        assertFalse(prompt.contains("Defina internamente o nivel real do corredor principalmente"));
        assertTrue(prompt.contains("alternando caminhada e blocos curtos de trote ou corrida leve"));
        assertTrue(prompt.contains("Trechos um pouco mais rapidos so podem aparecer"));
        assertTrue(prompt.contains("avalie explicitamente idade e presenca de lesao"));
        assertTrue(prompt.contains("todas as sessoes devem usar caminhada no Aquecimento"));
        assertTrue(prompt.contains("o Principal deve ser dividido em passos distintos"));
        assertTrue(prompt.contains("3 x (2 min de trote leve + 3 min de caminhada)"));
        assertTrue(prompt.contains("atletas mais velhos, lesionados, com dor"));
        assertTrue(prompt.contains("Desaquecimento deve ser sempre caminhada"));
        assertTrue(prompt.contains("para N esforcos existem N - 1 recuperacoes"));
        assertTrue(prompt.contains("duracao prevista desse esforco em minutos"));
        assertTrue(prompt.contains("Nao use horas, segundos, faixas de duracao"));
        assertTrue(prompt.contains("quilometros podem aparecer apenas como informacao complementar"));
        assertTrue(prompt.contains("Use somente esta sintaxe para repeticoes"));
        assertTrue(prompt.contains("nao use apenas \"trote entre repeticoes\""));
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
        request.setDiaLongao("domingo");
        request.setPossuiProva(false);
        request.setPossuiLesao(false);

        String prompt = promptBuilder.criarPrompt(request, 4);

        assertTrue(prompt.contains("Corre 5 km direto sem caminhar: Nao informado"));
        assertTrue(prompt.contains("Tempo atual nos 5 km: Nao informado"));
        assertTrue(prompt.contains("Maior distancia ja percorrida: 14 km"));
        assertFalse(prompt.contains("use o tempo dos 5 km para estimar de forma prudente"));
        assertTrue(prompt.contains("TODA semana deve conter exatamente um treino"));
        assertTrue(prompt.contains("tipo=\"Longão\""));
        assertTrue(prompt.contains("Dia do longao informado"));
    }

    @Test
    void maratonaExigeTipoLongaoEmTodaSemanaSemCriarSessaoExtra() {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setObjetivo("Melhorar tempo na Maratona");
        request.setDistanciaAlvo("42 km");
        request.setDiasDisponiveis(List.of("terça-feira", "quinta-feira", "domingo"));
        request.setDiaLongao("domingo");

        String prompt = promptBuilder.criarPrompt(request, 6);

        assertTrue(prompt.contains("TODA semana deve conter exatamente um treino"));
        assertTrue(prompt.contains("tipo=\"Longão\""));
        assertTrue(prompt.contains("Nao crie sessao extra nem aumente volume ou intensidade"));
    }

    @Test
    void cincoKmNaoRecebeObrigacaoDeLongaoSemanal() {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setObjetivo("Primeiros 5 km");
        request.setDistanciaAlvo("5 km");
        request.setDiasDisponiveis(List.of("terça-feira", "quinta-feira", "domingo"));
        request.setDiaLongao("domingo");

        String prompt = promptBuilder.criarPrompt(request, 4);

        assertFalse(prompt.contains("TODA semana deve conter exatamente um treino"));
        assertFalse(prompt.contains("tipo=\"Longão\""));
    }

    @Test
    void cincoKmComBaseMaiorQueMetaRecebeOrientacaoContextualSemTetoRigido() {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setObjetivo("Primeiros 5 km");
        request.setDistanciaAlvo("5 km");
        request.setCorre5KmSemCaminhar(true);
        request.setTempo5Km("29:00");
        request.setMaiorDistanciaCorrida("8 km");
        request.setVolumeSemanalAtual("10-20 km");
        request.setExperienciaCorrida("6 meses a 1 ano");
        request.setDiasDisponiveis(List.of("terça-feira", "quinta-feira", "domingo"));
        request.setDiaLongao("domingo");

        String prompt = promptBuilder.criarPrompt(request, 5);

        assertTrue(prompt.contains("maior distancia ja corrida como referencia"));
        assertTrue(prompt.contains("base atual, nao como teto permanente"));
        assertTrue(prompt.contains("E permitido evoluir acima dela"));
        assertTrue(prompt.contains("exige que o longao continue crescendo"));
        assertTrue(prompt.contains("Mesmo com Dia do longao preenchido"));
        assertTrue(prompt.contains("some distanciaKm de todas as sessoes de corrida"));
        assertTrue(prompt.contains("volume semanal atual como referencia de carga habitual"));
        assertTrue(prompt.contains("maior sessao individual"));
        assertTrue(prompt.contains("experiencia, objetivo, distancia-alvo, recuperacao e duracao do ciclo"));
        assertTrue(prompt.contains("Corre 5 km direto sem caminhar: Sim"));
        assertTrue(prompt.contains("Defina internamente o nivel real do corredor"));
    }

    @Test
    void respostaFalsaForaDoEscopoNaoAtivaRegrasDeIniciante() {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setObjetivo("Emagrecer");
        request.setExperienciaCorrida("Mais de 3 anos");
        request.setCorre5KmSemCaminhar(false);
        request.setTempo5Km("22:00");
        request.setDistanciaAlvo("5 km");
        request.setDiasDisponiveis(List.of("terÃ§a-feira"));

        String prompt = promptBuilder.criarPrompt(request, 4);

        assertTrue(prompt.contains("Corre 5 km direto sem caminhar: Nao informado"));
        assertTrue(prompt.contains("Tempo atual nos 5 km: Nao informado"));
        assertFalse(prompt.contains("sem tiros, intervalados intensos"));
    }

    @Test
    void provaDentroDoCicloRecebeAncorasTemporaisDeterministicas() {
        GerarPlanoTreinoRequestDTO request = provaBase(LocalDate.of(2026, 8, 11));

        String prompt = promptBuilder.criarPrompt(request, 4, LocalDate.of(2026, 7, 22));

        assertTrue(prompt.contains("Segunda-feira que inicia a semana 1: 2026-07-20"));
        assertTrue(prompt.contains("Data da prova: 2026-08-11"));
        assertTrue(prompt.contains("Numero esperado da semana da prova: 4"));
        assertTrue(prompt.contains("substituir exatamente um treino normal"));
        assertTrue(prompt.contains("use tipo \"Prova\" e um titulo que contenha \"Prova\""));
    }

    @Test
    void provaForaDoCicloNaoPedeCompeticaoTaperOuRecuperacao() {
        GerarPlanoTreinoRequestDTO request = provaBase(LocalDate.of(2026, 10, 4));

        String prompt = promptBuilder.criarPrompt(request, 6, LocalDate.of(2026, 7, 22));

        assertTrue(prompt.contains("prova esta alem da janela maxima"));
        assertTrue(prompt.contains("Nao insira a competicao neste ciclo"));
        assertTrue(prompt.contains("nao exija taper final"));
        assertTrue(prompt.contains("nao prescreva recuperacao pos-prova"));
    }

    private GerarPlanoTreinoRequestDTO provaBase(LocalDate dataProva) {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setObjetivo("Melhorar tempo nos 10 km");
        request.setDistanciaAlvo("10 km");
        request.setDistanciaProva("10 km");
        request.setPossuiProva(true);
        request.setDataProva(dataProva);
        request.setDiasDisponiveis(List.of("quinta-feira", "domingo"));
        return request;
    }
}
