package com.kaio.runtracker.service;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

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
        request.setExperienciaCorrida("1 a 3 anos");
        request.setVolumeSemanalAtual("20-40 km");
        request.setRitmoConfortavel("5:30-6:00 min/km");
        request.setDistanciaAlvo("10 km");
        request.setPossuiLesao(false);
        return request;
    }
}
