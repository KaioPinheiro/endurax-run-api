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
    void aceitaAliasesEmInglesSemAlterarContratoFinal() {
        String resposta = """
                {
                  "title": "Training plan",
                  "summary": "Summary",
                  "durationWeeks": 1,
                  "objectivePlan": "Objective",
                  "weeks": [
                    {
                      "weekNumber": 1,
                      "title": "Week 1",
                      "focus": "Base",
                      "workouts": [
                        {
                          "dayOfWeek": "segunda-feira",
                          "title": "Easy run",
                          "type": "Corrida continua",
                          "description": "Treino leve",
                          "distanceKm": "5 km",
                          "estimatedDuration": "30 min",
                          "suggestedPace": "6:00 min/km",
                          "notes": "Manter confortavel"
                        }
                      ]
                    }
                  ]
                }
                """;

        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(resposta, 1);

        assertEquals("Training plan", plano.getTitulo());
        assertEquals(1, plano.getSemanas().get(0).getNumeroSemana());
        assertEquals("Week 1", plano.getSemanas().get(0).getTitulo());
        assertEquals("Easy run", plano.getSemanas().get(0).getTreinos().get(0).getTitulo());
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
    void respostaComTreinoGenericoRejeitaPlano() {
        String treinoGenerico = """
                {
                  "diaSemana": "segunda-feira",
                  "titulo": "Treino de velocidade",
                  "tipo": "Intervalado",
                  "descricao": "Intensificar os intervalos",
                  "distanciaKm": "8 km",
                  "duracaoEstimada": "40 min",
                  "paceSugerido": "4:30 min/km",
                  "observacoes": "Aumentar o esforço"
                }
                """;

        GerarTreinoIAException exception = assertThrows(
                GerarTreinoIAException.class,
                () -> parser.parsePlanoTreino(
                        planoJson(semanaJson(1, treinoGenerico)),
                        1,
                        List.of("segunda-feira")
                )
        );

        assertEquals(
                "A IA retornou treino sem aquecimento, bloco principal e desaquecimento detalhados com pace. Tente gerar novamente.",
                exception.getMessage()
        );
    }

    @Test
    void respostaSemPaceNoAquecimentoEDesaquecimentoRejeitaPlano() {
        String treinoSemPaceNasExtremidades = """
                {
                  "diaSemana": "segunda-feira",
                  "titulo": "Treino intervalado",
                  "tipo": "Intervalado",
                  "descricao": "Aquecimento: 10 min de trote leve | Principal: 6 x 800 m a 4:30 min/km | Desaquecimento: 8 min de trote leve",
                  "distanciaKm": "8 km",
                  "duracaoEstimada": "50 min",
                  "paceSugerido": "4:30 min/km",
                  "observacoes": "Controle o esforço"
                }
                """;

        assertThrows(
                GerarTreinoIAException.class,
                () -> parser.parsePlanoTreino(
                        planoJson(semanaJson(1, treinoSemPaceNasExtremidades)),
                        1,
                        List.of("segunda-feira")
                )
        );
    }

    @Test
    void comProvaAceitaCompeticaoEmDiaNaoSelecionado() {
        String treinos = treinoJson("segunda-feira", "Segunda")
                + ","
                + treinoJson("quarta-feira", "Quarta")
                + ","
                + treinoJson("sexta-feira", "Sexta")
                + ","
                + descansoJson("sabado")
                + ","
                + competicaoJson("domingo");

        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, treinos)),
                1,
                List.of("segunda-feira", "quarta-feira", "sexta-feira", "sabado"),
                true
        );

        assertEquals(7, plano.getSemanas().get(0).getTreinos().size());
        assertEquals("Dia da Prova", plano.getSemanas().get(0).getTreinos().get(6).getTitulo());
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

    @Test
    void mantemQuatroCorridasVariadasComLongaoNoDiaEscolhido() {
        String treinos = treinoJson("segunda-feira", "Rodagem leve")
                + ","
                + treinoJson("terca-feira", "Treino de ritmo")
                + ","
                + treinoJson("sexta-feira", "Regenerativo")
                + ","
                + treinoJson("sabado", "Longão");

        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, treinos)),
                1,
                List.of("segunda-feira", "terca-feira", "sexta-feira", "sabado"),
                false,
                "Sábado"
        );

        long corridas = plano.getSemanas().get(0).getTreinos().stream()
                .filter(treino -> !"Descanso".equals(treino.getTipo()))
                .count();
        assertEquals(4, corridas);
        assertEquals("Longão", plano.getSemanas().get(0).getTreinos().get(5).getTitulo());
    }

    @Test
    void respostaComEducativosRejeitaPlano() {
        String treinoComEducativos = treinoJson("segunda-feira", "Rodagem leve")
                .replace(
                        "Aquecimento: 10 min de trote leve a 6:20 min/km",
                        "Aquecimento: 10 min de trote leve a 6:20 min/km + 3 educativos"
                );

        assertThrows(
                GerarTreinoIAException.class,
                () -> parser.parsePlanoTreino(
                        planoJson(semanaJson(1, treinoComEducativos)),
                        1,
                        List.of("segunda-feira")
                )
        );
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
                  "descricao": "Aquecimento: 10 min de trote leve a 6:20 min/km | Principal: 20 min a 6:00 min/km | Desaquecimento: 5 min de caminhada a 6:40 min/km",
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

    private String competicaoJson(String diaSemana) {
        return """
                {
                  "diaSemana": "%s",
                  "titulo": "Dia da Prova",
                  "tipo": "Competicao",
                  "descricao": "Prova alvo",
                  "distanciaKm": "42 km",
                  "duracaoEstimada": "3h",
                  "paceSugerido": "4:15 min/km",
                  "observacoes": "Competicao principal"
                }
                """.formatted(diaSemana);
    }
}
