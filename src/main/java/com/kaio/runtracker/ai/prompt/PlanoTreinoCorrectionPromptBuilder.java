package com.kaio.runtracker.ai.prompt;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.service.PlanoTreinoPromptBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class PlanoTreinoCorrectionPromptBuilder {

    private final PlanoTreinoPromptBuilder generationPromptBuilder;

    public PlanoTreinoCorrectionPromptBuilder(
            PlanoTreinoPromptBuilder generationPromptBuilder) {
        this.generationPromptBuilder = generationPromptBuilder;
    }

    public String criarPrompt(
            GerarPlanoTreinoRequestDTO request,
            int duracaoSemanas,
            String planoJson,
            List<String> errosJava,
            List<String> avisosJava,
            List<String> errosRevisao,
            List<String> avisosRevisao) {
        return """
                Corrija o plano abaixo e retorne somente o JSON completo no mesmo contrato.

                Regras:
                - Mantenha exatamente %d semanas e nunca ultrapasse 6.
                - Preserve semanas e treinos válidos; altere somente o necessário.
                - Use somente os dias selecionados: %s.
                - Mantenha a quantidade semanal de treinos igual à quantidade de dias selecionados.
                - Use no máximo um intervalado e dois treinos intensos por semana.
                - Não coloque treinos intensos em dias consecutivos.
                - Inclua treino leve ou regenerativo e longão quando necessário.
                - Respeite o volume atual, a progressão e a redução antes da prova.
                - Após uma prova próxima, programe somente recuperação e retorno progressivo; não use treinos preparatórios como se a prova ainda não tivesse ocorrido.
                - Se o atleta não corre 5 km direto, mantenha corrida/caminhada conservadora em todas as sessões, sem treinos intensos.
                - Não invente dados e não altere o contrato JSON.

                Contexto: objetivo=%s; corre5KmDireto=%s; tempo5Km=%s;
                maiorDistancia=%s; experiência=%s; volume=%s; distância=%s;
                possuiProva=%s; dataProva=%s; possuiLesão=%s.

                Erros determinísticos: %s
                Avisos determinísticos: %s
                Erros da revisão: %s
                Avisos da revisão: %s

                Plano a corrigir:
                %s

                Orientação específica da prova:
                %s
                """.formatted(
                duracaoSemanas,
                request.getDiasDisponiveis(),
                valor(request.getObjetivo()),
                respostaSimNao(request.getCorre5KmSemCaminhar()),
                valor(request.getTempo5Km()),
                valor(request.getMaiorDistanciaCorrida()),
                valor(request.getExperienciaCorrida()),
                valor(request.getVolumeSemanalAtual()),
                valor(request.getDistanciaAlvo()),
                Boolean.TRUE.equals(request.getPossuiProva()) ? "Sim" : "Não",
                request.getDataProva() == null ? "Não informada" : request.getDataProva(),
                Boolean.TRUE.equals(request.getPossuiLesao()) ? "Sim" : "Não",
                errosJava,
                avisosJava,
                errosRevisao,
                avisosRevisao,
                planoJson,
                generationPromptBuilder.orientacaoCiclo(request));
    }

    private String valor(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : "Nao informado";
    }

    private String respostaSimNao(Boolean valor) {
        if (valor == null) {
            return "Nao informado";
        }
        return valor ? "Sim" : "Nao";
    }
}
