package com.kaio.runtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlanoTreinoRespostaParserTest {

    private final PlanoTreinoRespostaParser parser =
            new PlanoTreinoRespostaParser(new ObjectMapper());

    @Test
    void respostaComSemanaFaltanteRejeitaPlano() {
        GerarTreinoIAException exception = assertThrows(
                GerarTreinoIAException.class,
                () -> parser.parsePlanoTreino(
                        planoJson(semanaJson(1, todosOsDiasJson())),
                        2
                )
        );

        assertEquals("502 BAD_GATEWAY", exception.getStatus().toString());
        assertEquals(
                "A IA retornou um plano com semanas faltantes. Tente gerar novamente.",
                exception.getMessage()
        );
    }

    @Test
    void respostaComSemanaExtraDescartaSemanaExcedente() {
        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(
                        semanaJson(1, todosOsDiasJson())
                                + ","
                                + semanaJson(2, todosOsDiasJson())
                ),
                1
        );

        assertEquals(1, plano.getSemanas().size());
        assertEquals(1, plano.getSemanas().get(0).getNumeroSemana());
    }

    @Test
    void respostaComCincoSemanasQuandoEsperavaQuatroRemoveSemanaExtra() {
        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(
                        semanaJson(1, todosOsDiasJson())
                                + ","
                                + semanaJson(2, todosOsDiasJson())
                                + ","
                                + semanaJson(3, todosOsDiasJson())
                                + ","
                                + semanaJson(4, todosOsDiasJson())
                                + ","
                                + semanaJson(5, todosOsDiasJson())
                ),
                4
        );

        assertEquals(4, plano.getDuracaoSemanas());
        assertEquals(4, plano.getSemanas().size());
        assertEquals(4, plano.getSemanas().get(3).getNumeroSemana());
    }

    @Test
    void respostaComDiaDuplicadoMantemPrimeiroDiaValido() {
        String treinos = treinoJson("segunda-feira", "Primeiro")
                + ","
                + treinoJson("segunda-feira", "Duplicado")
                + ","
                + treinoJson("terca-feira", "Terca")
                + ","
                + treinoJson("quarta-feira", "Quarta")
                + ","
                + treinoJson("quinta-feira", "Quinta")
                + ","
                + treinoJson("sexta-feira", "Sexta")
                + ","
                + treinoJson("sabado", "Sabado")
                + ","
                + treinoJson("domingo", "Domingo");

        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, treinos)),
                1
        );

        assertEquals(7, plano.getSemanas().get(0).getTreinos().size());
        assertEquals("Primeiro", plano.getSemanas().get(0).getTreinos().get(0).getTitulo());
    }

    @Test
    void respostaComDiaAusentePreencheComoDescanso() {
        String treinos = treinoJson("segunda-feira", "Segunda")
                + ","
                + treinoJson("terca-feira", "Terca")
                + ","
                + treinoJson("quarta-feira", "Quarta")
                + ","
                + treinoJson("quinta-feira", "Quinta")
                + ","
                + treinoJson("sexta-feira", "Sexta")
                + ","
                + treinoJson("sabado", "Sabado");

        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, treinos)),
                1
        );

        assertEquals(7, plano.getSemanas().get(0).getTreinos().size());
        assertEquals("domingo", plano.getSemanas().get(0).getTreinos().get(6).getDiaSemana());
        assertEquals("Descanso", plano.getSemanas().get(0).getTreinos().get(6).getTipo());
    }

    @Test
    void respostaComDiaDisponivelSemCorridaRejeitaPlano() {
        String treinos = treinoJson("segunda-feira", "Segunda")
                + ","
                + descansoJson("quarta-feira");

        GerarTreinoIAException exception = assertThrows(
                GerarTreinoIAException.class,
                () -> parser.parsePlanoTreino(
                        planoJson(semanaJson(1, treinos)),
                        1,
                        List.of("segunda-feira", "quarta-feira")
                )
        );

        assertEquals("502 BAD_GATEWAY", exception.getStatus().toString());
        assertEquals(
                "A IA retornou menos treinos de corrida do que os dias escolhidos. Tente gerar novamente.",
                exception.getMessage()
        );
    }

    @Test
    void respostaComCorridaEmDiaNaoSelecionadoRejeitaPlano() {
        String treinos = treinoJson("segunda-feira", "Segunda")
                + ","
                + treinoJson("domingo", "Domingo");

        GerarTreinoIAException exception = assertThrows(
                GerarTreinoIAException.class,
                () -> parser.parsePlanoTreino(
                        planoJson(semanaJson(1, treinos)),
                        1,
                        List.of("segunda-feira")
                )
        );

        assertEquals("502 BAD_GATEWAY", exception.getStatus().toString());
        assertEquals(
                "A IA retornou corrida em dia nao selecionado. Tente gerar novamente.",
                exception.getMessage()
        );
    }

    @Test
    void aceitaDiasDisponiveisComAbreviacoes() {
        String treinos = treinoJson("segunda-feira", "Segunda")
                + ","
                + treinoJson("quarta-feira", "Quarta")
                + ","
                + treinoJson("sexta-feira", "Sexta")
                + ","
                + treinoJson("sábado", "Sabado");

        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, treinos)),
                1,
                List.of("SEG", "QUA", "SEX", "SÁB")
        );

        assertEquals(7, plano.getSemanas().get(0).getTreinos().size());
        assertEquals("Sexta", plano.getSemanas().get(0).getTreinos().get(4).getTitulo());
    }

    private String planoJson(String semanas) {
        return """
                {
                  "titulo": "Plano completo",
                  "resumo": "Resumo",
                  "duracaoSemanas": 1,
                  "objetivoPlano": "Objetivo",
                  "semanas": [%s]
                }
                """.formatted(semanas);
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

    private String todosOsDiasJson() {
        return treinoJson("segunda-feira", "Segunda")
                + ","
                + treinoJson("terca-feira", "Terca")
                + ","
                + treinoJson("quarta-feira", "Quarta")
                + ","
                + treinoJson("quinta-feira", "Quinta")
                + ","
                + treinoJson("sexta-feira", "Sexta")
                + ","
                + treinoJson("sabado", "Sabado")
                + ","
                + treinoJson("domingo", "Domingo");
    }

    private String treinoJson(String diaSemana, String titulo) {
        return """
                {
                  "diaSemana": "%s",
                  "titulo": "%s",
                  "tipo": "Corrida continua",
                  "descricao": "Treino leve",
                  "distanciaKm": "5 km",
                  "duracaoEstimada": "30 min",
                  "paceSugerido": "6:00 min/km",
                  "observacoes": "Manter confortavel"
                }
                """.formatted(diaSemana, titulo);
    }

    private String descansoJson(String diaSemana) {
        return """
                {
                  "diaSemana": "%s",
                  "titulo": "Descanso",
                  "tipo": "Descanso",
                  "descricao": "Recuperacao",
                  "distanciaKm": "0 km",
                  "duracaoEstimada": "Livre",
                  "paceSugerido": "Nao se aplica",
                  "observacoes": "Recuperar"
                }
                """.formatted(diaSemana);
    }
}
