package com.kaio.runtracker.ai.agent;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import com.kaio.runtracker.dto.SemanaPlanoIAResponseDTO;
import com.kaio.runtracker.dto.TreinoPlanoIAResponseDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingPlanValidatorTest {
    private final TrainingPlanValidator validator = new TrainingPlanValidator();

    @Test
    void aprovaPlanoValido() {
        assertTrue(validar(plano(4), request(false, false, "10 km"), 4).isValid());
    }

    @Test
    void rejeitaPlanoSemProvaComMenosDeQuatroSemanas() {
        ValidationResult resultado = validar(plano(3), request(false, false, "10 km"), 3);
        assertErro(resultado, "pelo menos 4 semanas");
    }

    @Test
    void rejeitaPlanoComMaisDeSeisSemanas() {
        ValidationResult resultado = validar(plano(7), request(false, false, "10 km"), 7);
        assertErro(resultado, "ultrapassar 6 semanas");
    }

    @Test
    void rejeitaTreinoIntensoImediatamenteDepoisDaProva() {
        GerarPlanoTreinoRequestDTO request = request(true, false, "10 km");
        request.setDataProva(LocalDate.of(2026, 1, 24));
        PlanoTreinoIAResponseDTO plano = plano(4);
        TreinoPlanoIAResponseDTO treino =
                plano.getSemanas().get(3).getTreinos().get(2);
        treino.setTipo("Intervalado");
        treino.setTitulo("Intervalado");
        ValidationResult resultado = validar(plano, request, 4);
        assertErro(resultado, "intenso imediatamente após a prova");
    }

    @Test
    void rejeitaDoisIntervaladosNaSemana() {
        PlanoTreinoIAResponseDTO plano = plano(4);
        plano.getSemanas().get(0).getTreinos().get(2).setTipo("Intervalado");
        ValidationResult resultado = validar(plano, request(false, false, "10 km"), 4);
        assertErro(resultado, "mais de um treino intervalado");
    }

    @Test
    void rejeitaTreinosIntensosConsecutivos() {
        PlanoTreinoIAResponseDTO plano = plano(4);
        TreinoPlanoIAResponseDTO primeiro = plano.getSemanas().get(0).getTreinos().get(0);
        primeiro.setDiaSemana("quarta-feira");
        primeiro.setTipo("Ritmo");
        primeiro.setTitulo("Ritmo");
        ValidationResult resultado = validar(plano, request(false, false, "10 km"), 4);
        assertErro(resultado, "dias consecutivos");
    }

    @Test
    void rejeitaTreinoEmDiaNaoPermitido() {
        PlanoTreinoIAResponseDTO plano = plano(4);
        plano.getSemanas().get(0).getTreinos().get(0).setDiaSemana("segunda-feira");
        ValidationResult resultado = validar(plano, request(false, false, "10 km"), 4);
        assertErro(resultado, "não é um dia selecionado");
    }

    @Test
    void rejeitaQuantidadeIncorretaDeTreinos() {
        PlanoTreinoIAResponseDTO plano = plano(4);
        plano.getSemanas().get(0).getTreinos().remove(2);
        ValidationResult resultado = validar(plano, request(false, false, "10 km"), 4);
        assertErro(resultado, "quantidade de treinos");
    }

    @Test
    void rejeitaAusenciaDeTreinoLeve() {
        PlanoTreinoIAResponseDTO plano = plano(4);
        plano.getSemanas().get(0).getTreinos().get(0).setTipo("Tempo");
        ValidationResult resultado = validar(plano, request(false, false, "10 km"), 4);
        assertErro(resultado, "não possui treino leve");
    }

    @Test
    void rejeitaDistanciaInvalida() {
        PlanoTreinoIAResponseDTO plano = plano(4);
        plano.getSemanas().get(0).getTreinos().get(0).setDistanciaKm("0 km");
        ValidationResult resultado = validar(plano, request(false, false, "10 km"), 4);
        assertErro(resultado, "distância inválida");
    }

    @Test
    void rejeitaLesaoAtivaComTreinoIntenso() {
        ValidationResult resultado = validar(
                plano(4), request(false, true, "10 km"), 4);
        assertErro(resultado, "lesão ativa");
    }

    @Test
    void rejeitaTreinoIntensoParaQuemAindaNaoCorreCincoKmDireto() {
        GerarPlanoTreinoRequestDTO request = request(false, false, "5 km");
        request.setCorre5KmSemCaminhar(false);

        ValidationResult resultado = validar(plano(4), request, 4);

        assertErro(resultado, "ainda não corre 5 km direto");
        assertErro(resultado, "deve alternar trote ou corrida leve com caminhada");
    }

    @Test
    void aprovaCorridaECaminhadaParaQuemAindaNaoCorreCincoKmDireto() {
        GerarPlanoTreinoRequestDTO request = request(false, false, "5 km");
        request.setCorre5KmSemCaminhar(false);
        PlanoTreinoIAResponseDTO plano = plano(4);
        plano.getSemanas().forEach(semana ->
                semana.getTreinos().forEach(treino -> {
                    treino.setTipo("Leve");
                    treino.setTitulo("Corrida e caminhada");
                    treino.setDescricao(
                            "Aquecimento: 5 min de caminhada | "
                                    + "Principal: 6 x 2 min de trote com 2 min de caminhada | "
                                    + "Desaquecimento: 5 min de caminhada");
                }));

        ValidationResult resultado = validar(plano, request, 4);

        assertTrue(resultado.isValid(), () -> "Erros: " + resultado.getErrors());
    }

    @Test
    void rejeitaMaratonaSemLongao() {
        PlanoTreinoIAResponseDTO plano = plano(4);
        plano.getSemanas().forEach(semana -> {
            semana.getTreinos().get(2).setTipo("Rodagem moderada");
            semana.getTreinos().get(2).setTitulo("Rodagem moderada");
        });
        ValidationResult resultado = validar(
                plano, request(false, false, "Maratona 42 km"), 4);
        assertErro(resultado, "não possui longão");
    }

    @Test
    void avisaSobreAumentoBruscoDeVolume() {
        PlanoTreinoIAResponseDTO plano = plano(4);
        plano.getSemanas().get(1).getTreinos().forEach(treino -> treino.setDistanciaKm("20 km"));
        ValidationResult resultado = validar(plano, request(false, false, "10 km"), 4);
        assertAviso(resultado, "aumento brusco");
    }

    @Test
    void rejeitaAumentoDeLongaoAcimaDoMaiorEntreQuinzeMinutosEQuinzePorCento() {
        PlanoTreinoIAResponseDTO plano = plano(4);
        definirDuracaoLongao(plano, 0, "90 min");
        definirDuracaoLongao(plano, 1, "120 min");

        ValidationResult resultado = validar(plano, request(false, false, "10 km"), 4);

        assertErro(resultado, "aumentou de 90 min na semana 1 para 120 min");
        assertErro(resultado, "30 min (33.3%)");
        assertErro(resultado, "no máximo aproximadamente 105 min");
    }

    @Test
    void permiteAumentosAteOLimiteAbsolutoOuPercentual() {
        List<int[]> casos = List.of(
                new int[]{40, 50}, new int[]{50, 60}, new int[]{60, 75},
                new int[]{75, 90}, new int[]{90, 100}, new int[]{90, 105},
                new int[]{150, 165}, new int[]{150, 172});
        for (int[] caso : casos) {
            PlanoTreinoIAResponseDTO plano = plano(4);
            definirDuracaoLongao(plano, 0, caso[0] + " min");
            definirDuracaoLongao(plano, 1, caso[1] + " min");

            ValidationResult resultado = validar(
                    plano, request(false, false, "5 km"), 4);

            assertTrue(resultado.isValid(), () -> caso[0] + " -> " + caso[1]
                    + ", erros: " + resultado.getErrors());
        }
    }

    @Test
    void rejeitaAumentoDeLongaoAvancadoAcimaDeQuinzePorCento() {
        PlanoTreinoIAResponseDTO plano = plano(4);
        definirDuracaoLongao(plano, 0, "150 min");
        definirDuracaoLongao(plano, 1, "180 min");

        ValidationResult resultado = validar(plano, request(false, false, "42 km"), 4);

        assertErro(resultado, "aumentou de 150 min na semana 1 para 180 min");
        assertErro(resultado, "limite permitido a partir de 150 min é 22.5 min");
        assertErro(resultado, "no máximo aproximadamente 172 min");
    }

    @Test
    void permiteReducaoDeLongao() {
        PlanoTreinoIAResponseDTO plano = plano(4);
        definirDuracaoLongao(plano, 0, "120 min");
        definirDuracaoLongao(plano, 1, "90 min");

        ValidationResult resultado = validar(plano, request(false, false, "10 km"), 4);

        assertTrue(resultado.isValid(), () -> "Erros: " + resultado.getErrors());
    }

    @Test
    void ignoraComparacaoQuandoDuracaoDoLongaoNaoForCanonica() {
        PlanoTreinoIAResponseDTO plano = plano(4);
        definirDuracaoLongao(plano, 0, "90 minutos");
        definirDuracaoLongao(plano, 1, "120 min");

        ValidationResult resultado = validar(plano, request(false, false, "10 km"), 4);

        assertTrue(resultado.isValid(), () -> "Erros: " + resultado.getErrors());
    }

    @Test
    void ignoraComparacaoQuandoDuracaoDoLongaoEstiverAusente() {
        PlanoTreinoIAResponseDTO plano = plano(4);
        definirDuracaoLongao(plano, 0, null);
        definirDuracaoLongao(plano, 1, "120 min");

        assertTrue(validar(plano, request(false, false, "10 km"), 4).isValid());
    }

    @Test
    void primeiraSemanaNaoTemComparacaoELongaoIgualPassa() {
        PlanoTreinoIAResponseDTO plano = plano(4);
        definirDuracaoLongao(plano, 0, "300 min");
        definirDuracaoLongao(plano, 1, "300 min");

        assertTrue(validar(plano, request(false, false, "10 km"), 4).isValid());
    }

    @Test
    void semanaSemLongaoNaoCriaNovoErroParaObjetivoQueNaoOExige() {
        PlanoTreinoIAResponseDTO plano = plano(4);
        plano.getSemanas().get(1).getTreinos().get(2).setTipo("Leve");
        plano.getSemanas().get(1).getTreinos().get(2).setTitulo("Leve");
        definirDuracaoLongao(plano, 0, "40 min");
        definirDuracaoLongao(plano, 2, "120 min");

        assertTrue(validar(plano, request(false, false, "5 km"), 4).isValid());
    }

    @Test
    void regraPermaneceCompativelComMeiaEMaratona() {
        for (String objetivo : List.of("Primeira Meia Maratona 21 km", "Maratona 42 km")) {
            PlanoTreinoIAResponseDTO plano = plano(4);
            definirDuracaoLongao(plano, 0, "90 min");
            definirDuracaoLongao(plano, 1, "105 min");
            assertTrue(validar(plano, request(false, false, objetivo), 4).isValid());
        }
    }

    @Test
    void rejeitaSemanasForaDeOrdem() {
        PlanoTreinoIAResponseDTO plano = plano(4);
        plano.getSemanas().get(1).setNumeroSemana(3);
        ValidationResult resultado = validar(plano, request(false, false, "10 km"), 4);
        assertErro(resultado, "fora de ordem");
    }

    @Test
    void avisaSobreAusenciaDeReducaoAntesDaProva() {
        GerarPlanoTreinoRequestDTO request = request(true, false, "10 km");
        request.setDataProva(LocalDate.of(2026, 2, 1));
        ValidationResult resultado = validar(plano(4), request, 4);
        assertAviso(resultado, "redução de carga");
    }

    @Test
    void provaProximaGeraWarningEPermiteRecuperacaoPosterior() {
        GerarPlanoTreinoRequestDTO request = request(true, false, "10 km");
        request.setDataProva(LocalDate.of(2026, 1, 18));
        PlanoTreinoIAResponseDTO plano = plano(4);
        TreinoPlanoIAResponseDTO prova =
                plano.getSemanas().get(1).getTreinos().get(2);
        prova.setTipo("Prova");
        prova.setTitulo("Prova");
        plano.getSemanas().get(2).getTreinos().forEach(treino -> {
            treino.setTipo("Regenerativo");
            treino.setTitulo("Regenerativo");
        });

        ValidationResult resultado = validar(plano, request, 4);

        assertTrue(resultado.isValid(), () -> "Erros: " + resultado.getErrors());
        assertAviso(resultado, "menos de quatro semanas");
        assertAviso(resultado, "não contém alerta");
    }

    @Test
    void provaEmDiaNaoSelecionadoSubstituiExatamenteUmTreino() {
        GerarPlanoTreinoRequestDTO request = request(true, false, "10 km");
        request.setDataProva(LocalDate.of(2026, 1, 17));
        PlanoTreinoIAResponseDTO plano = plano(4);
        SemanaPlanoIAResponseDTO semanaProva = plano.getSemanas().get(1);
        TreinoPlanoIAResponseDTO substituido = semanaProva.getTreinos().get(2);
        substituido.setTipo("Descanso");
        substituido.setTitulo("Descanso");
        substituido.setDistanciaKm("0 km");
        TreinoPlanoIAResponseDTO prova = treino("sábado", "Prova", "10 km");
        prova.setTitulo("Prova");
        semanaProva.getTreinos().add(prova);
        plano.getSemanas().get(2).getTreinos().forEach(treino -> {
            treino.setTipo("Regenerativo");
            treino.setTitulo("Regenerativo");
        });

        ValidationResult resultado = validar(plano, request, 4);

        assertTrue(resultado.isValid(), () -> "Erros: " + resultado.getErrors());
    }

    @Test
    void provaForaDoCicloNaoExigeCompeticaoNemTaperFinal() {
        GerarPlanoTreinoRequestDTO request = request(true, false, "10 km");
        request.setDataProva(LocalDate.of(2026, 3, 1));

        ValidationResult resultado = validar(plano(4), request, 4);

        assertTrue(resultado.isValid(), () -> "Erros: " + resultado.getErrors());
        assertFalse(resultado.getWarnings().stream()
                .anyMatch(aviso -> aviso.contains("redução de carga")));
    }

    @Test
    void provaForaDoCicloRejeitaCompeticaoInventada() {
        GerarPlanoTreinoRequestDTO request = request(true, false, "10 km");
        request.setDataProva(LocalDate.of(2026, 3, 1));
        PlanoTreinoIAResponseDTO plano = plano(4);
        plano.getSemanas().get(3).getTreinos().get(2).setTipo("Prova");
        plano.getSemanas().get(3).getTreinos().get(2).setTitulo("Prova");

        assertErro(validar(plano, request, 4), "não deve aparecer");
    }

    private ValidationResult validar(
            PlanoTreinoIAResponseDTO plano,
            GerarPlanoTreinoRequestDTO request,
            int semanas) {
        return validator.validate(
                plano,
                new AgentExecutionContext(
                        request, semanas, LocalDate.of(2026, 1, 5), "teste"));
    }

    private GerarPlanoTreinoRequestDTO request(
            boolean possuiProva,
            boolean possuiLesao,
            String objetivo) {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setObjetivo(objetivo);
        request.setExperienciaCorrida("1 a 3 anos");
        request.setVolumeSemanalAtual("20-40 km");
        request.setRitmoConfortavel("6:00 min/km");
        request.setDistanciaAlvo(objetivo);
        request.setDiasDisponiveis(List.of(
                "terça-feira", "quinta-feira", "domingo"));
        request.setPossuiProva(possuiProva);
        request.setPossuiLesao(possuiLesao);
        request.setDuracaoSemanas(possuiProva ? null : 4);
        return request;
    }

    private PlanoTreinoIAResponseDTO plano(int quantidadeSemanas) {
        PlanoTreinoIAResponseDTO plano = new PlanoTreinoIAResponseDTO();
        plano.setTitulo("Plano");
        plano.setResumo("Resumo");
        plano.setDuracaoSemanas(quantidadeSemanas);
        plano.setObjetivoPlano("Objetivo");
        List<SemanaPlanoIAResponseDTO> semanas = new ArrayList<>();
        for (int numero = 1; numero <= quantidadeSemanas; numero++) {
            SemanaPlanoIAResponseDTO semana = new SemanaPlanoIAResponseDTO();
            semana.setNumeroSemana(numero);
            semana.setTitulo("Semana " + numero);
            semana.setFoco("Evolução");
            semana.setTreinos(new ArrayList<>(List.of(
                    treino("terça-feira", "Leve", "5 km"),
                    treino("quinta-feira", "Intervalado", "6 km"),
                    treino("domingo", "Longão", "10 km"))));
            semanas.add(semana);
        }
        plano.setSemanas(semanas);
        return plano;
    }

    private TreinoPlanoIAResponseDTO treino(String dia, String tipo, String distancia) {
        TreinoPlanoIAResponseDTO treino = new TreinoPlanoIAResponseDTO();
        treino.setDiaSemana(dia);
        treino.setTitulo(tipo);
        treino.setTipo(tipo);
        treino.setDescricao("Aquecimento | Principal | Desaquecimento");
        treino.setDistanciaKm(distancia);
        treino.setDuracaoEstimada("40 minutos");
        treino.setPaceSugerido("6:00 min/km");
        treino.setObservacoes("Controle o esforço.");
        return treino;
    }

    private void definirDuracaoLongao(
            PlanoTreinoIAResponseDTO plano, int indiceSemana, String duracao) {
        plano.getSemanas().get(indiceSemana).getTreinos().stream()
                .filter(treino -> "Longão".equals(treino.getTipo()))
                .findFirst()
                .orElseThrow()
                .setDuracaoEstimada(duracao);
    }

    private void assertErro(ValidationResult resultado, String trecho) {
        assertFalse(resultado.isValid());
        assertTrue(resultado.getErrors().stream().anyMatch(erro -> erro.contains(trecho)),
                () -> "Erros encontrados: " + resultado.getErrors());
    }

    private void assertAviso(ValidationResult resultado, String trecho) {
        assertTrue(resultado.getWarnings().stream().anyMatch(aviso -> aviso.contains(trecho)),
                () -> "Avisos encontrados: " + resultado.getWarnings());
    }
}
