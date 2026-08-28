package com.kaio.runtracker.service;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlanoTreinoRegrasDeterministicasValidatorTest {
    private final PlanoTreinoRegrasDeterministicasValidator validator =
            new PlanoTreinoRegrasDeterministicasValidator(Clock.fixed(
                    Instant.parse("2026-07-20T15:00:00Z"),
                    ZoneId.of("America/Sao_Paulo")));

    @Test
    void rejeitaPassadoHojeESeteDiasEAceitaQuatorzeDias() {
        for (LocalDate data : List.of(
                LocalDate.of(2026, 7, 19),
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 27))) {
            assertThrows(IllegalArgumentException.class,
                    () -> validator.normalizarEValidarSolicitacaoPublica(requestProva(data)));
        }
        assertDoesNotThrow(() -> validator.normalizarEValidarSolicitacaoPublica(
                requestProva(LocalDate.of(2026, 8, 3))));
    }

    @Test
    void distanciaDaProvaUsaDistanciaAlvoComoFontePublica() {
        GerarPlanoTreinoRequestDTO request = requestProva(LocalDate.of(2026, 8, 3));
        request.setDistanciaAlvo("10 km");
        request.setDistanciaProva("21 km");

        validator.normalizarEValidarSolicitacaoPublica(request);

        assertEquals("10 km", request.getDistanciaProva());
    }

    @Test
    void antecipaBloqueioDeterministicoDeMaratona() {
        GerarPlanoTreinoRequestDTO request = requestProva(LocalDate.of(2026, 8, 3));
        request.setObjetivo("Melhorar tempo na Maratona");
        request.setDistanciaAlvo("42 km");
        request.setIdade(17);
        request.setExperienciaCorrida("Mais de 3 anos");
        request.setVolumeSemanalAtual("40-60 km");
        request.setDiasDisponiveis(List.of("terça-feira", "quinta-feira", "sábado", "domingo"));

        assertThrows(IllegalArgumentException.class,
                () -> validator.normalizarEValidarSolicitacaoPublica(request));
    }

    @Test
    void novoV1AceitaQuatroCincoSeisSemanasESempreDesativaProva() {
        for (int duracao : List.of(4, 5, 6)) {
            GerarPlanoTreinoRequestDTO request = requestProva(LocalDate.of(2026, 8, 3));
            request.setDuracaoSemanas(duracao);
            request.setDistanciaProva("21 km");
            request.setObjetivoProva("Completar a prova");
            request.setImportanciaProva("Prova importante");

            validator.prepararNovaSolicitacaoPublicaV1(request);

            assertEquals(false, request.getPossuiProva());
            assertEquals(null, request.getDataProva());
            assertEquals(null, request.getDistanciaProva());
            assertEquals(null, request.getObjetivoProva());
            assertEquals(null, request.getImportanciaProva());
            assertEquals(duracao, request.getDuracaoSemanas());
        }
    }

    @Test
    void novoV1RejeitaDuracaoForaDeQuatroACincoOuSeisSemanas() {
        GerarPlanoTreinoRequestDTO request = requestProva(LocalDate.of(2026, 8, 3));
        request.setDuracaoSemanas(3);

        assertThrows(IllegalArgumentException.class,
                () -> validator.prepararNovaSolicitacaoPublicaV1(request));
    }

    private GerarPlanoTreinoRequestDTO requestProva(LocalDate data) {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setObjetivo("Melhorar tempo nos 10 km");
        request.setDistanciaAlvo("10 km");
        request.setPossuiProva(true);
        request.setDataProva(data);
        request.setDiasDisponiveis(List.of("terça-feira", "quinta-feira", "sábado"));
        request.setIdade(30);
        request.setExperienciaCorrida("1 a 3 anos");
        request.setVolumeSemanalAtual("20-40 km");
        return request;
    }
}
