package com.kaio.runtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GerarPlanoTreinoIAServiceTest {

    private final GerarPlanoTreinoIAService service =
            new GerarPlanoTreinoIAService(
                    null,
                    null,
                    null,
                    Clock.fixed(
                            Instant.parse("2026-07-14T12:00:00Z"),
                            ZoneId.of("America/Sao_Paulo")
                    )
            );

    @Test
    void semProvaUsaQuatroSemanasQuandoDuracaoNaoFoiInformada() {
        GerarPlanoTreinoRequestDTO request = requestSemProva(null);

        int duracao = service.calcularDuracaoSemanas(request);

        assertEquals(4, duracao);
    }

    @Test
    void semProvaAceitaSeisSemanasInformadas() {
        GerarPlanoTreinoRequestDTO request = requestSemProva(6);

        int duracao = service.calcularDuracaoSemanas(request);

        assertEquals(6, duracao);
    }

    @Test
    void semProvaAceitaQuatroSemanasInformadas() {
        GerarPlanoTreinoRequestDTO request = requestSemProva(4);

        int duracao = service.calcularDuracaoSemanas(request);

        assertEquals(4, duracao);
    }

    @Test
    void semProvaAceitaCincoSemanasInformadas() {
        GerarPlanoTreinoRequestDTO request = requestSemProva(5);

        int duracao = service.calcularDuracaoSemanas(request);

        assertEquals(5, duracao);
    }

    @Test
    void semProvaRejeitaTresSemanasAntesDeChamarOpenAI() {
        OpenAIService openAIService = mock(OpenAIService.class);
        GerarPlanoTreinoIAService serviceComMock =
                new GerarPlanoTreinoIAService(null, openAIService, null);
        GerarPlanoTreinoRequestDTO request = requestSemProva(3);

        GerarTreinoIAException exception = assertThrows(
                GerarTreinoIAException.class,
                () -> serviceComMock.gerarPlano(request)
        );

        assertEquals("400 BAD_REQUEST", exception.getStatus().toString());
        assertTrue(exception.getMessage().contains("4, 5 ou 6"));
        verifyNoInteractions(openAIService);
    }

    @Test
    void semProvaRejeitaSeteSemanas() {
        GerarPlanoTreinoRequestDTO request = requestSemProva(7);

        GerarTreinoIAException exception = assertThrows(
                GerarTreinoIAException.class,
                () -> service.calcularDuracaoSemanas(request)
        );

        assertEquals("400 BAD_REQUEST", exception.getStatus().toString());
    }

    @Test
    void planoDeMaratonaComTresDiasRejeitaAntesDeChamarOpenAI() {
        OpenAIService openAIService = mock(OpenAIService.class);
        GerarPlanoTreinoIAService serviceComMock =
                new GerarPlanoTreinoIAService(null, openAIService, null);
        GerarPlanoTreinoRequestDTO request = requestSemProva(4);
        request.setObjetivo("Primeira Maratona");
        request.setDistanciaAlvo("42 km");
        request.setVolumeSemanalAtual("40-60 km");
        request.setDiasDisponiveis(List.of("Segunda-feira", "Quarta-feira", "Sábado"));

        GerarTreinoIAException exception = assertThrows(
                GerarTreinoIAException.class,
                () -> serviceComMock.gerarPlano(request)
        );

        assertEquals("400 BAD_REQUEST", exception.getStatus().toString());
        assertEquals(
                "Para plano de maratona, selecione pelo menos 4 dias disponiveis para treinar.",
                exception.getMessage()
        );
        verifyNoInteractions(openAIService);
    }

    @Test
    void planoDeMaratonaComQuatroDiasAceitaValidacao() {
        GerarPlanoTreinoRequestDTO request = requestSemProva(4);
        request.setObjetivo("Maratona em 3 horas");
        request.setDistanciaAlvo("42 km");
        request.setVolumeSemanalAtual("40-60 km");
        request.setDiasDisponiveis(List.of(
                "Segunda-feira",
                "Quarta-feira",
                "Sexta-feira",
                "Sábado"
        ));

        service.validarDiasMinimosParaMaratona(request);
    }

    @Test
    void planoDeMaratonaComMenosDeDezoitoAnosRejeitaAntesDeChamarOpenAI() {
        OpenAIService openAIService = mock(OpenAIService.class);
        GerarPlanoTreinoIAService serviceComMock =
                new GerarPlanoTreinoIAService(null, openAIService, null);
        GerarPlanoTreinoRequestDTO request = requestSemProva(4);
        request.setObjetivo("Maratona em 3 horas");
        request.setDistanciaAlvo("42 km");
        request.setIdade(17);
        request.setVolumeSemanalAtual("40-60 km");
        request.setDiasDisponiveis(List.of(
                "Segunda-feira",
                "Quarta-feira",
                "Sexta-feira",
                "Sábado"
        ));

        GerarTreinoIAException exception = assertThrows(
                GerarTreinoIAException.class,
                () -> serviceComMock.gerarPlano(request)
        );

        assertEquals("400 BAD_REQUEST", exception.getStatus().toString());
        assertEquals(
                "Para plano de maratona, a idade minima e 18 anos.",
                exception.getMessage()
        );
        verifyNoInteractions(openAIService);
    }

    @Test
    void planoDeMaratonaComDezoitoAnosAceitaValidacao() {
        GerarPlanoTreinoRequestDTO request = requestSemProva(4);
        request.setObjetivo("Maratona em 3 horas");
        request.setDistanciaAlvo("42 km");
        request.setIdade(18);
        request.setVolumeSemanalAtual("40-60 km");
        request.setDiasDisponiveis(List.of(
                "Segunda-feira",
                "Quarta-feira",
                "Sexta-feira",
                "Sábado"
        ));

        service.validarIdadeMinimaParaMaratona(request);
    }

    @Test
    void planoDeMaratonaComVolumeAbaixoDeQuarentaRejeitaAntesDeChamarOpenAI() {
        OpenAIService openAIService = mock(OpenAIService.class);
        GerarPlanoTreinoIAService serviceComMock =
                new GerarPlanoTreinoIAService(null, openAIService, null);
        GerarPlanoTreinoRequestDTO request = requestSemProva(4);
        request.setObjetivo("Maratona em 3 horas");
        request.setDistanciaAlvo("42 km");
        request.setVolumeSemanalAtual("20-40 km");
        request.setDiasDisponiveis(List.of(
                "Segunda-feira",
                "Quarta-feira",
                "Sexta-feira",
                "Sábado"
        ));

        GerarTreinoIAException exception = assertThrows(
                GerarTreinoIAException.class,
                () -> serviceComMock.gerarPlano(request)
        );

        assertEquals("400 BAD_REQUEST", exception.getStatus().toString());
        assertEquals(
                "Para plano de maratona, o volume semanal atual deve ser 40-60 km, 60-80 km ou 80+ km.",
                exception.getMessage()
        );
        verifyNoInteractions(openAIService);
    }

    @Test
    void planoDeMaratonaInformadoNasObservacoesTambemAplicaBloqueiosAntesDeChamarOpenAI() {
        OpenAIService openAIService = mock(OpenAIService.class);
        GerarPlanoTreinoIAService serviceComMock =
                new GerarPlanoTreinoIAService(null, openAIService, null);
        GerarPlanoTreinoRequestDTO request = requestSemProva(4);
        request.setObjetivo("Outro objetivo");
        request.setDistanciaAlvo("Sem distancia alvo definida");
        request.setObservacoes("Quero preparar uma maratona em 3 horas.");
        request.setVolumeSemanalAtual("20-40 km");
        request.setDiasDisponiveis(List.of(
                "Segunda-feira",
                "Quarta-feira",
                "Sexta-feira",
                "Sábado"
        ));

        GerarTreinoIAException exception = assertThrows(
                GerarTreinoIAException.class,
                () -> serviceComMock.gerarPlano(request)
        );

        assertEquals("400 BAD_REQUEST", exception.getStatus().toString());
        assertEquals(
                "Para plano de maratona, o volume semanal atual deve ser 40-60 km, 60-80 km ou 80+ km.",
                exception.getMessage()
        );
        verifyNoInteractions(openAIService);
    }

    @Test
    void planoDeMaratonaComVolumeQuarentaASessentaAceitaValidacao() {
        GerarPlanoTreinoRequestDTO request = requestSemProva(4);
        request.setObjetivo("Maratona em 3 horas");
        request.setDistanciaAlvo("42 km");
        request.setVolumeSemanalAtual("40-60 km");
        request.setDiasDisponiveis(List.of(
                "Segunda-feira",
                "Quarta-feira",
                "Sexta-feira",
                "Sábado"
        ));

        service.validarVolumeSemanalParaMaratona(request);
    }

    @Test
    void planoDeMaratonaComVolumeSessentaAOitentaAceitaValidacao() {
        GerarPlanoTreinoRequestDTO request = requestSemProva(4);
        request.setObjetivo("Maratona em 3 horas");
        request.setDistanciaAlvo("42 km");
        request.setVolumeSemanalAtual("60-80 km");
        request.setDiasDisponiveis(List.of(
                "Segunda-feira",
                "Quarta-feira",
                "Sexta-feira",
                "Sábado"
        ));

        service.validarVolumeSemanalParaMaratona(request);
    }

    @Test
    void planoDeMaratonaComVolumeAcimaDeOitentaAceitaValidacao() {
        GerarPlanoTreinoRequestDTO request = requestSemProva(4);
        request.setObjetivo("Maratona em 3 horas");
        request.setDistanciaAlvo("42 km");
        request.setVolumeSemanalAtual("80+ km");
        request.setDiasDisponiveis(List.of(
                "Segunda-feira",
                "Quarta-feira",
                "Sexta-feira",
                "Sábado"
        ));

        service.validarVolumeSemanalParaMaratona(request);
    }

    @Test
    void planoDeMaratonaComExperienciaMenorQueUmATresAnosRejeitaAntesDeChamarOpenAI() {
        OpenAIService openAIService = mock(OpenAIService.class);
        GerarPlanoTreinoIAService serviceComMock =
                new GerarPlanoTreinoIAService(null, openAIService, null);
        GerarPlanoTreinoRequestDTO request = requestSemProva(4);
        request.setObjetivo("Maratona em 3 horas");
        request.setDistanciaAlvo("42 km");
        request.setVolumeSemanalAtual("40-60 km");
        request.setExperienciaCorrida("6 meses a 1 ano");
        request.setDiasDisponiveis(List.of(
                "Segunda-feira",
                "Quarta-feira",
                "Sexta-feira",
                "Sábado"
        ));

        GerarTreinoIAException exception = assertThrows(
                GerarTreinoIAException.class,
                () -> serviceComMock.gerarPlano(request)
        );

        assertEquals("400 BAD_REQUEST", exception.getStatus().toString());
        assertEquals(
                "Para plano de maratona, a experiencia na corrida deve ser a partir de 1 a 3 anos.",
                exception.getMessage()
        );
        verifyNoInteractions(openAIService);
    }

    @Test
    void planoDeMaratonaComExperienciaUmATresAnosAceitaValidacao() {
        GerarPlanoTreinoRequestDTO request = requestSemProva(4);
        request.setObjetivo("Maratona em 3 horas");
        request.setDistanciaAlvo("42 km");
        request.setVolumeSemanalAtual("40-60 km");
        request.setExperienciaCorrida("1 a 3 anos");
        request.setDiasDisponiveis(List.of(
                "Segunda-feira",
                "Quarta-feira",
                "Sexta-feira",
                "Sábado"
        ));

        service.validarExperienciaParaMaratona(request);
    }

    @Test
    void planoDeMaratonaComExperienciaMaisDeTresAnosAceitaValidacao() {
        GerarPlanoTreinoRequestDTO request = requestSemProva(4);
        request.setObjetivo("Maratona em 3 horas");
        request.setDistanciaAlvo("42 km");
        request.setVolumeSemanalAtual("40-60 km");
        request.setExperienciaCorrida("Mais de 3 anos");
        request.setDiasDisponiveis(List.of(
                "Segunda-feira",
                "Quarta-feira",
                "Sexta-feira",
                "Sábado"
        ));

        service.validarExperienciaParaMaratona(request);
    }

    @Test
    void meiaMaratonaComTresDiasNaoUsaRegraDeMaratona() {
        GerarPlanoTreinoRequestDTO request = requestSemProva(4);
        request.setObjetivo("Primeira Meia Maratona");
        request.setDistanciaAlvo("21 km");
        request.setDiasDisponiveis(List.of("Segunda-feira", "Quarta-feira", "Sábado"));

        service.validarDiasMinimosParaMaratona(request);
    }

    @Test
    void provaEmDuasSemanasGeraCicloMinimoDeQuatroSemanas() {
        GerarPlanoTreinoRequestDTO request = requestComProva(LocalDate.of(2026, 7, 28));

        int duracao = service.calcularDuracaoSemanas(request);

        assertEquals(4, duracao);
    }

    @Test
    void provaEmUmaSemanaGeraCicloMinimoDeQuatroSemanas() {
        GerarPlanoTreinoRequestDTO request = requestComProva(LocalDate.of(2026, 7, 21));

        int duracao = service.calcularDuracaoSemanas(request);

        assertEquals(4, duracao);
    }

    @Test
    void comProvaIgnoraDuracaoEnviadaManualmente() {
        GerarPlanoTreinoRequestDTO request = requestComProva(LocalDate.of(2026, 7, 28));
        request.setDuracaoSemanas(6);

        int duracao = service.calcularDuracaoSemanas(request);

        assertEquals(4, duracao);
    }

    @Test
    void provaEmTresSemanasGeraCicloMinimoDeQuatroSemanas() {
        GerarPlanoTreinoRequestDTO request = requestComProva(LocalDate.of(2026, 8, 4));

        int duracao = service.calcularDuracaoSemanas(request);

        assertEquals(4, duracao);
    }

    @Test
    void provaEmCincoSemanasGeraCincoSemanas() {
        GerarPlanoTreinoRequestDTO request = requestComProva(LocalDate.of(2026, 8, 18));

        int duracao = service.calcularDuracaoSemanas(request);

        assertEquals(5, duracao);
    }

    @Test
    void provaEmMaisDeSeisSemanasGeraCicloInicialDeSeisSemanas() {
        GerarPlanoTreinoRequestDTO request = requestComProva(LocalDate.of(2026, 9, 8));

        int duracao = service.calcularDuracaoSemanas(request);

        assertEquals(6, duracao);
    }

    @Test
    void provaNoPassadoRetornaErroDeValidacao() {
        GerarPlanoTreinoRequestDTO request = requestComProva(LocalDate.of(2026, 7, 13));

        GerarTreinoIAException exception = assertThrows(
                GerarTreinoIAException.class,
                () -> service.calcularDuracaoSemanas(request)
        );

        assertEquals("A data da prova não pode estar no passado.", exception.getMessage());
    }

    @Test
    void tentaGerarNovamenteQuandoPlanoNaoContemCorridaEmTodosOsDiasEscolhidos() {
        OpenAIService openAIService = mock(OpenAIService.class);
        GerarPlanoTreinoIAService serviceComMock =
                new GerarPlanoTreinoIAService(
                        new PlanoTreinoPromptBuilder(),
                        openAIService,
                        new PlanoTreinoRespostaParser(new ObjectMapper()),
                        Clock.fixed(
                                Instant.parse("2026-07-14T12:00:00Z"),
                                ZoneId.of("America/Sao_Paulo")
                        )
                );
        GerarPlanoTreinoRequestDTO request = requestSemProva(4);
        request.setDiasDisponiveis(List.of(
                "Segunda-feira",
                "Quarta-feira",
                "Sexta-feira",
                "Sábado"
        ));

        when(openAIService.enviarPromptPlanoTreino(anyString(), anyString(), eq(4)))
                .thenReturn(
                        planoJson(treinosSemSexta()),
                        planoJson(treinosComQuatroDias())
                );

        PlanoTreinoIAResponseDTO plano = serviceComMock.gerarPlano(request);

        assertEquals(4, plano.getSemanas().size());
        assertEquals(7, plano.getSemanas().get(0).getTreinos().size());
        assertEquals("Corrida sexta", plano.getSemanas().get(0).getTreinos().get(4).getTitulo());
        verify(openAIService, times(2))
                .enviarPromptPlanoTreino(anyString(), anyString(), eq(4));
    }

    private GerarPlanoTreinoRequestDTO requestSemProva(Integer duracaoSemanas) {
        GerarPlanoTreinoRequestDTO request = baseRequest();
        request.setPossuiProva(false);
        request.setDuracaoSemanas(duracaoSemanas);
        return request;
    }

    private GerarPlanoTreinoRequestDTO requestComProva(LocalDate dataProva) {
        GerarPlanoTreinoRequestDTO request = baseRequest();
        request.setPossuiProva(true);
        request.setDataProva(dataProva);
        request.setDistanciaProva("10 km");
        request.setImportanciaProva("Prova importante");
        return request;
    }

    private GerarPlanoTreinoRequestDTO baseRequest() {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setObjetivo("Melhorar condicionamento");
        request.setIdade(31);
        request.setExperienciaCorrida("1 a 3 anos");
        request.setVolumeSemanalAtual("20-40 km");
        request.setRitmoConfortavel("5:30-6:00 min/km");
        request.setDistanciaAlvo("10 km");
        request.setPossuiLesao(false);
        return request;
    }

    private String planoJson(String treinos) {
        return """
                {
                  "titulo": "Plano completo",
                  "resumo": "Resumo",
                  "duracaoSemanas": 4,
                  "objetivoPlano": "Objetivo",
                  "semanas": [
                    %s,
                    %s,
                    %s,
                    %s
                  ]
                }
                """.formatted(
                semanaJson(1, treinos),
                semanaJson(2, treinos),
                semanaJson(3, treinos),
                semanaJson(4, treinos)
        );
    }

    private String semanaJson(int numeroSemana, String treinos) {
        return """
                {
                  "numeroSemana": %d,
                  "titulo": "Semana %d",
                  "foco": "Base",
                  "treinos": [%s]
                }
                """.formatted(numeroSemana, numeroSemana, treinos);
    }

    private String treinosSemSexta() {
        return treinoJson("segunda-feira", "Corrida segunda")
                + ","
                + treinoJson("quarta-feira", "Corrida quarta")
                + ","
                + treinoJson("sábado", "Corrida sábado");
    }

    private String treinosComQuatroDias() {
        return treinoJson("segunda-feira", "Corrida segunda")
                + ","
                + treinoJson("quarta-feira", "Corrida quarta")
                + ","
                + treinoJson("sexta-feira", "Corrida sexta")
                + ","
                + treinoJson("sábado", "Corrida sábado");
    }

    private String treinoJson(String diaSemana, String titulo) {
        return """
                {
                  "diaSemana": "%s",
                  "titulo": "%s",
                  "tipo": "Corrida",
                  "descricao": "Treino de corrida",
                  "distanciaKm": "5 km",
                  "duracaoEstimada": "30 minutos",
                  "paceSugerido": "6:00 min/km",
                  "observacoes": "Manter confortavel"
                }
                """.formatted(diaSemana, titulo);
    }
}
