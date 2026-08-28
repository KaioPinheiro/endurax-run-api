package com.kaio.runtracker.ai.prompt;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.service.PlanoTreinoPromptBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.time.LocalDate;

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
        return criarPrompt(request, duracaoSemanas, planoJson, errosJava, avisosJava,
                errosRevisao, avisosRevisao, LocalDate.now());
    }

    public String criarPrompt(
            GerarPlanoTreinoRequestDTO request,
            int duracaoSemanas,
            String planoJson,
            List<String> errosJava,
            List<String> avisosJava,
            List<String> errosRevisao,
            List<String> avisosRevisao,
            LocalDate dataInicio) {
        return """
                Corrija o plano abaixo e retorne somente o JSON completo no mesmo contrato.

                Princípio obrigatório da correção:
                - Corrija somente os problemas apontados. Não reconstrua livremente o plano
                  quando uma alteração localizada for suficiente.
                - Preserve integralmente semanas, dias, treinos e demais partes válidas que
                  não precisam mudar. Faça a menor alteração necessária.
                - Avisos orientam melhorias, mas não justificam reconstruir partes válidas.

                Invariantes estruturais que toda correção deve preservar:
                - Mantenha exatamente %d semanas e nunca ultrapasse 6.
                - Em TODAS as semanas, mantenha cada dia de corrida selecionado exatamente
                  uma vez: %s. Nenhum desses dias pode desaparecer ou virar descanso.
                - EXCECAO: se a prova estiver dentro do ciclo e ocorrer em dia nao selecionado,
                  preserve a substituicao de exatamente um treino normal por essa prova. Nao
                  force o dia substituido a voltar a ser corrida e nao adicione sessao extra.
                - Não crie corrida em dia não selecionado e não duplique dias, salvo a
                  competição na exceção explicitamente descrita acima.
                - Mantenha em cada semana exatamente a quantidade de treinos de corrida
                  correspondente à quantidade de dias selecionados.
                - Preserve a ordem das semanas, a estrutura esperada e o contrato JSON.
                - Preserve objetivo, duração do ciclo, contexto de prova quando aplicável
                  e restrições ou lesão informadas.

                Como corrigir sem destruir a estrutura:
                - Se houver intensidade, pace ou carga excessiva em um dia selecionado,
                  reduza intensidade, volume, pace ou repetições, troque o tipo ou transforme
                  o treino em leve; NÃO remova o dia de corrida.
                - Use no máximo um intervalado e dois treinos intensos por semana.
                - Não coloque treinos intensos em dias consecutivos.
                - Inclua treino leve ou regenerativo quando necessário.
                %s
                - Respeite o volume atual e a progressão; reduza antes da prova somente quando ela estiver dentro deste ciclo.
                - Trate volume semanal atual como referência da carga habitual e maior distância já
                  corrida como referência da base da maior sessão; nenhum deles é teto permanente.
                  Permita evolução coerente acima dessas referências, mas corrija saltos desproporcionais.
                - Quando distância-alvo for menor ou igual à maior distância já corrida, não force
                  crescimento contínuo do longão. O Dia do longão preenchido define o dia, não obriga
                  que a distância dessa sessão aumente em todas as semanas.
                - Se um apontamento mencionar volume, longão, carga, experiência ou maior distância,
                  identifique as sessões responsáveis e faça a menor alteração necessária.
                - Após uma prova próxima, programe somente recuperação e retorno progressivo; não use treinos preparatórios como se a prova ainda não tivesse ocorrido.
                - Se o atleta não corre 5 km direto, mantenha corrida/caminhada conservadora em todas as sessões, sem treinos intensos.
                - Não invente dados e não altere o contrato JSON.

                Antes de responder, faça uma conferência final silenciosa:
                - todos os dias selecionados continuam presentes como corrida em cada semana,
                  salvo exatamente o treino substituído pela prova fora dos dias selecionados;
                - nenhuma semana perdeu treino e a quantidade de corridas continua correta;
                - não foi criada corrida em dia indevido nem dia duplicado, salvo a prova permitida;
                - quantidade, ordem e estrutura das semanas continuam válidas;
                - os problemas apontados foram tratados sem criar novos erros estruturais.
                - some distanciaKm das sessões de corrida de cada semana e confira o total contra o
                  volume atual como referência, sem transformá-lo em teto rígido;
                - identifique a maior sessão de cada semana e confronte-a com a maior distância já
                  corrida, considerando progressão, experiência, recuperação, objetivo, distância-alvo
                  e duração do ciclo;
                - se uma sessão for alterada, mantenha coerentes distanciaKm, duracaoEstimada,
                  descrição, tipo, título, ritmos e repetições aplicáveis.

                Contexto: objetivo=%s; corre5KmDireto=%s; tempo5Km=%s;
                maiorDistancia=%s; experiência=%s; volume=%s; distância=%s;
                tempoDesejado=%s; paceAlvoMeta=%s; ritmoConfortavelAtual=%s;
                possuiProva=%s; dataProva=%s; possuiLesão=%s.

                O paceAlvoMeta e o pace matematico da meta. Diferencie-o do ritmo
                confortavel atual e dos demais ritmos de treino. Se mencionar ritmo
                da distancia-alvo referindo-se a meta, mantenha-o coerente com paceAlvoMeta.

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
                orientacaoLongaoCorrecao(request),
                valor(request.getObjetivo()),
                respostaSimNao(request.getCorre5KmSemCaminhar()),
                valor(request.getTempo5Km()),
                valor(request.getMaiorDistanciaCorrida()),
                valor(request.getExperienciaCorrida()),
                valor(request.getVolumeSemanalAtual()),
                valor(request.getDistanciaAlvo()),
                valor(request.getTempoDesejado()),
                PaceAlvoCalculator.calcular(request).orElse("Nao se aplica"),
                valor(request.getRitmoConfortavel()),
                Boolean.TRUE.equals(request.getPossuiProva()) ? "Sim" : "Não",
                request.getDataProva() == null ? "Não informada" : request.getDataProva(),
                Boolean.TRUE.equals(request.getPossuiLesao()) ? "Sim" : "Não",
                errosJava,
                avisosJava,
                errosRevisao,
                avisosRevisao,
                planoJson,
                generationPromptBuilder.orientacaoCiclo(request, duracaoSemanas, dataInicio));
    }

    private String orientacaoLongaoCorrecao(GerarPlanoTreinoRequestDTO request) {
        String regra = generationPromptBuilder.orientacaoLongaoObrigatorio(request);
        if (!StringUtils.hasText(regra)) {
            return "";
        }
        return regra + " Se ja existir treino esportivamente equivalente no dia escolhido "
                + "com tipo incorreto, prefira alterar somente o tipo para \"Longão\", "
                + "preservando o treino e a estrutura valida existente.";
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
