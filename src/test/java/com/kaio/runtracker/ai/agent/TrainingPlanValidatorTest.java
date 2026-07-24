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
        plano.setAlerta("Prazo insuficiente para um ciclo completo; "
                + "o período posterior será usado para recuperação e continuidade.");
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
