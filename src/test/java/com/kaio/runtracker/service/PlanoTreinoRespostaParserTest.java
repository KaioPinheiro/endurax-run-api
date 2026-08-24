package com.kaio.runtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                "O serviço retornou um plano com semanas faltantes. Tente gerar novamente.",
                exception.getMessage()
        );
    }

    @Test
    void respostaComSemanaExtraRejeitaPlano() {
        assertThrows(GerarTreinoIAException.class, () -> parser.parsePlanoTreino(
                planoJson(semanaJson(1, todosOsDiasJson()) + ","
                        + semanaJson(2, todosOsDiasJson())), 1));
    }

    @Test
    void respostaComCincoSemanasQuandoEsperavaQuatroRejeitaPlano() {
        assertThrows(GerarTreinoIAException.class, () -> parser.parsePlanoTreino(
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
                4));
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
                "O serviço retornou menos treinos de corrida do que os dias escolhidos. Tente gerar novamente.",
                exception.getMessage()
        );
    }

    @Test
    void aceitaCorridaAlternadaComCaminhadaParaIniciante() {
        String treinoMisto = """
                {
                  "diaSemana": "terça-feira",
                  "titulo": "Corrida e caminhada",
                  "tipo": "Caminhada e trote leve",
                  "descricao": "Aquecimento: 5 min de caminhada a 10:00 min/km | Principal: 6 x (2 min de trote leve + 2 min de caminhada de recuperação) | Desaquecimento: 5 min de caminhada a 10:00 min/km",
                  "distanciaKm": "3 km",
                  "duracaoEstimada": "32 min",
                  "paceSugerido": "Por percepção de esforço",
                  "observacoes": "Manter esforço confortável"
                }
                """;

        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, treinoMisto)),
                1,
                List.of("terça-feira")
        );

        assertEquals(
                "Corrida e caminhada",
                plano.getSemanas().get(0).getTreinos().get(1).getTitulo()
        );
    }

    @Test
    void normalizaDuracaoDiferenteDaSomaDosBlocos() {
        String treinoComDuracaoIncorreta = """
                {
                  "diaSemana": "terça-feira",
                  "titulo": "Caminhada e trote",
                  "tipo": "Leve",
                  "descricao": "Aquecimento: 5 min de caminhada a 10:00 min/km | Principal: 3 x (10 min de caminhada + 1 min de trote leve + 2 min de caminhada) | Desaquecimento: 5 min de caminhada a 10:00 min/km",
                  "distanciaKm": "3 km",
                  "duracaoEstimada": "30 min",
                  "paceSugerido": "Por percepção de esforço",
                  "observacoes": "Manter esforço confortável"
                }
                """;

        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, treinoComDuracaoIncorreta)),
                1,
                List.of("terça-feira"));

        assertEquals("49 min", plano.getSemanas().get(0).getTreinos().get(1)
                .getDuracaoEstimada());
    }

    @Test
    void normalizaDuracaoNumericaIncompativelComRepeticaoInterna() {
        String treinoComDuracaoIncorreta = """
                {
                  "diaSemana": "terça-feira",
                  "titulo": "Corrida e caminhada",
                  "tipo": "Leve",
                  "descricao": "Aquecimento: 5 min de trote leve a 8:00 min/km | Principal: 2 x (1 min de trote leve + 2 min de caminhada) | Desaquecimento: 5 min de caminhada a 9:00 min/km",
                  "distanciaKm": "3",
                  "duracaoEstimada": "40",
                  "paceSugerido": "8:00-9:00",
                  "observacoes": "Manter esforço confortável"
                }
                """;

        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, treinoComDuracaoIncorreta)),
                1,
                List.of("terça-feira"));

        assertEquals("16 min", plano.getSemanas().get(0).getTreinos().get(1)
                .getDuracaoEstimada());
    }

    @Test
    void mantemDuracaoCalculadaQuandoValorInformadoJaEstaCorreto() {
        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, treinoJson("terça-feira", "Leve"))),
                1,
                List.of("terça-feira"));

        assertEquals("35 min", plano.getSemanas().get(0).getTreinos().get(1)
                .getDuracaoEstimada());
    }

    @Test
    void calculaRecuperacaoEntreRepeticoesSomenteNMenosUmVezes() {
        String treino = treinoComDescricao(
                "6 x (3 min de esforço + 2 min de recuperação)",
                "38 min");

        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, treino)), 1, List.of("terça-feira"));
        assertEquals("38 min", plano.getSemanas().get(0).getTreinos().get(1)
                .getDuracaoEstimada());
    }

    @Test
    void calculaTirosPorDistanciaComDuracaoExplicita() {
        String treino = treinoComDescricao(
                "6 x (800 m em aproximadamente 3 min + 2 min de recuperação)",
                "38 min");

        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, treino)), 1, List.of("terça-feira"));
        assertEquals("38 min", plano.getSemanas().get(0).getTreinos().get(1)
                .getDuracaoEstimada());
    }

    @Test
    void calculaMultiplasSeriesERecuperacaoEntreSeries() {
        String treino = treinoComDescricao(
                "2 series de 3 x (2 min de esforço + 1 min de recuperação), com 3 min de recuperação entre series",
                "29 min");

        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, treino)), 1, List.of("terça-feira"));
        assertEquals("29 min", plano.getSemanas().get(0).getTreinos().get(1)
                .getDuracaoEstimada());
    }

    @Test
    void preservaDuracaoInformadaQuandoEsforcoPorDistanciaNaoPodeSerCalculado() {
        String treino = treinoComDescricao(
                "6 x (800 m a 4:30 min/km + 2 min de recuperação)",
                "20 min");

        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, treino)), 1, List.of("terça-feira"));
        assertEquals("20 min", plano.getSemanas().get(0).getTreinos().get(1)
                .getDuracaoEstimada());
    }

    @Test
    void preservaDuracaoInformadaQuandoDescricaoUsaSegundos() {
        String treino = treinoComDescricao(
                "6 x (3 min de esforço + 30 segundos de recuperação)",
                "28 min");

        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, treino)), 1, List.of("terça-feira"));
        assertEquals("28 min", plano.getSemanas().get(0).getTreinos().get(1)
                .getDuracaoEstimada());
    }

    @Test
    void calculaRodagemEDistanciaComDuracaoExplicita() {
        assertEquals("50 min", duracaoFinal("40 min leve"));
        assertEquals("58 min", duracaoFinal("8 km em 48 min"));
    }

    @Test
    void preservaDuracaoInformadaQuandoEsforcoUsaMinutosESegundos() {
        String treino = treinoComDescricao(
                "6 x (800 m em 3:30 min de esforço a 4:25-4:35 min/km"
                        + " + 2 min de recuperação)",
                "56 min")
                .replace("Aquecimento: 5 min", "Aquecimento: 15 min")
                .replace("Desaquecimento: 5 min", "Desaquecimento: 10 min");

        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, treino)), 1, List.of("terça-feira"));

        assertEquals("56 min", plano.getSemanas().get(0).getTreinos().get(1)
                .getDuracaoEstimada());
    }

    @Test
    void aceitaRodagemELongaoSomentePorDistanciaComFallback() {
        assertEquals("77 min", duracaoFinalIncalculavel("8 km", "77 min"));
        assertEquals("120 min", duracaoFinalIncalculavel("24 km progressivo", "120 min"));
        assertEquals("55 min", duracaoFinalIncalculavel("10 km em ritmo de maratona", "55 min"));
    }

    @Test
    void calculaTempoRunProgressivoELongaoEmMinutos() {
        assertEquals("60 min", duracaoFinal("20 min leve + 30 min em ritmo de prova"));
        assertEquals("70 min", duracaoFinal("30 min leve, 20 min moderado, 10 min forte"));
        assertEquals("130 min", duracaoFinal("120 min progressivo"));
        assertEquals("120 min", duracaoFinal("20 km em 110 min"));
    }

    @Test
    void aceitaDistanciaParcialSemDuracaoNoProgressivo() {
        assertEquals("125 min", duracaoFinalIncalculavel(
                "20 km em 110 min, ultimos 5 km em ritmo de prova", "125 min"));
    }

    @Test
    void usaFallbackParaTodasAsFormasDeFaixaConhecidas() {
        for (String principal : List.of(
                "10-12 min leve",
                "10–12 min leve",
                "10—12 min leve",
                "10/12 min leve",
                "entre 10 e 12 min leve")) {
            assertEquals("30 min", duracaoFinalIncalculavel(principal, "30 min"));
        }
    }

    @Test
    void usaFallbackParaHorasEMinutosDecimais() {
        assertEquals("120 min", duracaoFinalIncalculavel("2h de corrida", "120 min"));
        assertEquals("11 min", duracaoFinalIncalculavel("10,5 min leve", "11 min"));
    }

    @Test
    void preservaDuracaoQuandoAquecimentoNaoTemMinutos() {
        String aquecimentoSemMinutos = treinoComDescricao("40 min leve", "50 min")
                .replace("5 min de trote leve", "trote leve");
        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, aquecimentoSemMinutos)), 1, List.of("terça-feira"));
        assertEquals("50 min", plano.getSemanas().get(0).getTreinos().get(1)
                .getDuracaoEstimada());
    }

    @Test
    void calculaSeparadoresCanonicosNasRepeticoes() {
        assertEquals("38 min", duracaoFinal(
                "6 x (3 min de esforço com 2 min de recuperação)"));
        assertEquals("38 min", duracaoFinal(
                "6 x (3 min de esforço seguido de 2 min de recuperação)"));
        assertEquals("38 min", duracaoFinal(
                "6 x (3 min de esforço, 2 min de recuperação)"));
    }

    @Test
    void reconheceTroteECaminhadaDeRecuperacao() {
        assertEquals("38 min", duracaoFinal(
                "6 x (3 min de esforço + 2 min de trote de recuperação)"));
        assertEquals("38 min", duracaoFinal(
                "6 x (3 min de esforço + 2 min de caminhada de recuperação)"));
    }

    @Test
    void aceitaComFallbackRecuperacaoAmbiguaERepeticaoSemParenteses() {
        assertEquals("45 min", duracaoFinalIncalculavel(
                "6 x (3 min de esforço + 2 min de trote entre repeticoes)",
                "45 min"));
        assertEquals("50 min", duracaoFinalIncalculavel(
                "3 x 2 km em ritmo de prova com 3 min de trote",
                "50 min"));
        assertEquals("40 min", duracaoFinalIncalculavel(
                "6 x 3 min de esforço com 2 min de recuperação",
                "40 min"));
    }

    @Test
    void aceitaComFallbackMultiplasSeriesSemParenteses() {
        assertEquals("35 min", duracaoFinalIncalculavel(
                "2 series de 4 x 1 min de esforço com 1 min de recuperação",
                "35 min"));
    }

    @Test
    void aceitaDescricaoMultilineComMarcadoresCanonicos() {
        String treino = treinoComDescricao(
                "20 min leve\\n+ 30 min em ritmo de prova", "60 min");

        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, treino)), 1, List.of("terça-feira"));

        assertEquals("60 min", plano.getSemanas().get(0).getTreinos().get(1)
                .getDuracaoEstimada());
    }

    @Test
    void aceitaTreinoSemDistanciaEPaceSugerido() {
        String treinoSemMetricas = """
                {
                  "diaSemana": "terça-feira",
                  "titulo": "Corrida e caminhada",
                  "tipo": "Leve",
                  "descricao": "Aquecimento: 5 min de trote leve a 8:00 min/km | Principal: 20 min de corrida leve | Desaquecimento: 5 min de caminhada a 9:00 min/km",
                  "distanciaKm": "",
                  "duracaoEstimada": "30",
                  "paceSugerido": "",
                  "observacoes": "Manter esforço confortável"
                }
                """;

        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, treinoSemMetricas)), 1, List.of("terça-feira"));
        assertEquals("30 min", plano.getSemanas().get(0).getTreinos().get(1)
                .getDuracaoEstimada());
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
                "O serviço retornou corrida em dia nao selecionado. Tente gerar novamente.",
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
                "O serviço retornou treino sem aquecimento, bloco principal e desaquecimento. Tente gerar novamente.",
                exception.getMessage()
        );
    }

    @Test
    void respostaSemPaceNoAquecimentoEDesaquecimentoAceitaPlano() {
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

        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, treinoSemPaceNasExtremidades)),
                1, List.of("segunda-feira"));
        assertEquals("50 min", plano.getSemanas().get(0).getTreinos().get(0)
                .getDuracaoEstimada());
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
    void heuristicasDeVariedadeNaoRejeitamPlano() {
        String treinos = treinoJson("segunda-feira", "Treino intervalado")
                + "," + treinoJson("quarta-feira", "Treino de tiros")
                + "," + treinoJson("sabado", "Rodagem longa");

        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, treinos)), 1,
                List.of("segunda-feira", "quarta-feira", "sabado"), false, "sabado");

        assertEquals(7, plano.getSemanas().get(0).getTreinos().size());
    }

    @Test
    void duracaoIncalculavelSemDuracaoInformadaMantemPlano() {
        assertEquals("", duracaoFinalIncalculavel("24 km progressivo", ""));
    }

    @Test
    void regressaoBlocosSemMinutosPreservaDuracaoInformada() {
        String treino = treinoComDescricao("ritmo progressivo por sensacao", "60 min")
                .replace("5 min de trote leve", "trote leve")
                .replace("5 min de caminhada leve", "caminhada leve");

        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, treino)), 1, List.of("terça-feira"));

        assertEquals("60 min", plano.getSemanas().get(0).getTreinos().get(1)
                .getDuracaoEstimada());
    }

    @Test
    void regressaoBlocosSemMinutosMantemDuracaoAusente() {
        String treino = treinoComDescricao("ritmo progressivo por sensacao", "")
                .replace("5 min de trote leve", "trote leve")
                .replace("5 min de caminhada leve", "caminhada leve");

        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, treino)), 1, List.of("terça-feira"));

        assertEquals("", plano.getSemanas().get(0).getTreinos().get(1)
                .getDuracaoEstimada());
    }

    @Test
    void respostaComEducativosMantemPlano() {
        String treinoComEducativos = treinoJson("segunda-feira", "Rodagem leve")
                .replace(
                        "Aquecimento: 10 min de trote leve a 6:20 min/km",
                        "Aquecimento: 10 min de trote leve a 6:20 min/km + 3 educativos"
                );

        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, treinoComEducativos)),
                1, List.of("segunda-feira"));
        assertEquals(7, plano.getSemanas().get(0).getTreinos().size());
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
                  "duracaoEstimada": "35 min",
                  "paceSugerido": "6:00 min/km",
                  "observacoes": "Manter confortavel"
                }
                """.formatted(diaSemana, titulo);
    }

    private String treinoComDescricao(String principal, String duracao) {
        return """
                {
                  "diaSemana": "terça-feira",
                  "titulo": "Intervalado",
                  "tipo": "Intervalado",
                  "descricao": "Aquecimento: 5 min de trote leve a 6:20 min/km | Principal: %s | Desaquecimento: 5 min de caminhada a 6:40 min/km",
                  "distanciaKm": "5 km",
                  "duracaoEstimada": "%s",
                  "paceSugerido": "6:00 min/km",
                  "observacoes": "Manter controle"
                }
                """.formatted(principal, duracao);
    }

    private String duracaoFinal(String principal) {
        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, treinoComDescricao(principal, "999 min"))),
                1,
                List.of("terça-feira"));
        return plano.getSemanas().get(0).getTreinos().get(1).getDuracaoEstimada();
    }

    private String duracaoFinalIncalculavel(String principal, String duracaoInformada) {
        PlanoTreinoIAResponseDTO plano = parser.parsePlanoTreino(
                planoJson(semanaJson(1, treinoComDescricao(principal, duracaoInformada))),
                1, List.of("terça-feira"));
        return plano.getSemanas().get(0).getTreinos().get(1).getDuracaoEstimada();
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
