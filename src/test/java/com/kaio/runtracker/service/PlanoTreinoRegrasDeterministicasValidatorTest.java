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

    @Test
    void menosDeSeisMesesAceitaSomenteObjetivosAteDezKm() {
        List<String> permitidos = List.of(
                "Melhorar condicionamento",
                "Emagrecer",
                "Primeiros 5 km",
                "Primeiros 10 km",
                "Melhorar tempo nos 5 km",
                "Melhorar tempo nos 10 km");

        for (String objetivo : permitidos) {
            GerarPlanoTreinoRequestDTO request = requestV1(objetivo, "Menos de 6 meses");
            assertDoesNotThrow(() -> validator.prepararNovaSolicitacaoPublicaV1(request), objetivo);
        }
    }

    @Test
    void comecarACorrerEhAceitoSomenteParaQuemNuncaCorreuOuEstaParado() {
        for (String experiencia : List.of("Nunca corri", "Estou parado(a)")) {
            GerarPlanoTreinoRequestDTO request = requestV1(
                    "Começar a correr", experiencia);
            assertDoesNotThrow(
                    () -> validator.prepararNovaSolicitacaoPublicaV1(request),
                    experiencia);
        }

        for (String experiencia : List.of(
                "Menos de 6 meses",
                "6 meses a 1 ano",
                "1 a 3 anos",
                "Mais de 3 anos")) {
            GerarPlanoTreinoRequestDTO request = requestV1(
                    "Começar a correr", experiencia);
            IllegalArgumentException erro = assertThrows(
                    IllegalArgumentException.class,
                    () -> validator.prepararNovaSolicitacaoPublicaV1(request),
                    experiencia);
            assertEquals(
                    "Escolha um objetivo compatível com sua experiência na corrida.",
                    erro.getMessage());
        }
    }

    @Test
    void menosDeSeisMesesRejeitaObjetivosDeMeiaEMaratona() {
        List<String> bloqueados = List.of(
                "Primeira Meia Maratona",
                "Primeira Maratona",
                "Melhorar tempo na Meia Maratona",
                "Melhorar tempo na Maratona");

        for (String objetivo : bloqueados) {
            GerarPlanoTreinoRequestDTO request = requestV1(objetivo, "Menos de 6 meses");
            IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                    () -> validator.prepararNovaSolicitacaoPublicaV1(request), objetivo);
            assertEquals("Escolha um objetivo compatível com sua experiência na corrida.",
                    erro.getMessage());
        }
    }

    @Test
    void solicitacaoPersistidaIncompativelTambemEhRejeitadaAntesDoPix() {
        GerarPlanoTreinoRequestDTO request = requestV1(
                "Primeira Meia Maratona", "Menos de 6 meses");

        assertThrows(IllegalArgumentException.class,
                () -> validator.validarSolicitacaoPersistidaAntesDoPix(request));
    }

    @Test
    void solicitacaoPersistidaComComecarACorrerEExperienciaAtivaEhRejeitadaAntesDoPix() {
        GerarPlanoTreinoRequestDTO request = requestV1(
                "Começar a correr", "6 meses a 1 ano");

        assertThrows(IllegalArgumentException.class,
                () -> validator.validarSolicitacaoPersistidaAntesDoPix(request));
    }

    @Test
    void experienciaMaiorMantemObjetivosAtuais() {
        for (String objetivo : List.of("Primeira Meia Maratona", "Primeira Maratona")) {
            GerarPlanoTreinoRequestDTO request = requestV1(objetivo, "1 a 3 anos");
            if (objetivo.equals("Primeira Maratona")) {
                request.setDistanciaAlvo("42 km");
                request.setVolumeSemanalAtual("40-60 km");
                request.setDiasDisponiveis(List.of(
                        "segunda-feira", "terça-feira", "quinta-feira", "domingo"));
            }
            assertDoesNotThrow(() -> validator.prepararNovaSolicitacaoPublicaV1(request), objetivo);
        }
    }

    private GerarPlanoTreinoRequestDTO requestV1(String objetivo, String experiencia) {
        GerarPlanoTreinoRequestDTO request = requestProva(LocalDate.of(2026, 8, 3));
        request.setObjetivo(objetivo);
        request.setExperienciaCorrida(experiencia);
        request.setPossuiProva(false);
        request.setDuracaoSemanas(4);
        return request;
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
