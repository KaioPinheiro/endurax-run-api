package com.kaio.runtracker.ai.prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.ai.agent.AgentExecutionContext;
import com.kaio.runtracker.ai.CapacidadeCincoKm;
import com.kaio.runtracker.ai.agent.PlanoTreinoCalendario;
import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PlanoTreinoReviewPromptBuilder {

    private final ObjectMapper objectMapper;

    public PlanoTreinoReviewPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String criarSystemPrompt() {
        return """
                Você é o revisor técnico de um plano completo de corrida.
                Não altere o plano. Analise cada semana e o ciclo global.
                Retorne somente JSON: {"valid":boolean,"errors":[],"warnings":[],"summary":""}.
                Use ERROR somente para problema sério que impeça a entrega: risco relevante
                de segurança, carga ou progressão claramente incompatível, intensidade ou
                recuperação perigosamente inadequada, ou incoerência grave com o contexto.
                Use WARNING para preocupação, incerteza ou melhoria recomendável que ainda
                seja plausível e não torne necessariamente o plano inválido.
                Um erro torna valid=false. Warnings não reprovam sozinhos. Não transforme
                dado opcional ausente em erro e não invente limites absolutos não informados.
                Um pace mais rápido que o ritmo confortável NÃO é, isoladamente, motivo para
                ERROR: ritmo, intervalado, velocidade, VO2 e progressivo podem legitimamente
                ocorrer acima do ritmo confortável. Para usar ERROR por intensidade, demonstre
                risco grave pelo contexto completo: duração do estímulo, volume intenso total,
                repetições, recuperação, frequência semanal, progressão de carga, proximidade
                entre sessões intensas, experiência, volume atual, maior distância e restrições.
                Se a preocupação for apenas pace acima do confortável ou que o treino pode ser
                exigente, use WARNING. Carga completa claramente perigosa continua sendo ERROR.
                """;
    }

    public String criarPrompt(
            PlanoTreinoIAResponseDTO plano,
            AgentExecutionContext context) throws JsonProcessingException {
        GerarPlanoTreinoRequestDTO request = context.request();
        return """
                Revise o plano completo considerando o contexto informado.

                Interpretação obrigatória do contexto:
                - Objetivo ou distância desejada NÃO significa que existe prova marcada.
                - Se possuiProva=false, não exija data de prova, não avalie proximidade,
                  não exija taper, não suponha competição no último treino e avalie estas
                  semanas como ciclo de desenvolvimento para o objetivo informado.
                - Somente se possuiProva=true considere data, proximidade, posicionamento
                  da prova, recuperação relacionada ao evento e taper quando aplicável.
                - Se a prova estiver dentro do ciclo e ocorrer em dia não selecionado, ela
                  substitui exatamente um treino normal; isso não é erro e não aumenta o total semanal.
                - Se a prova estiver fora do ciclo de 6 semanas, não exija competição,
                  taper final ou recuperação pós-prova neste bloco de preparação.
                - duracaoSemanas representa somente o ciclo solicitado ao Endurax, não
                  necessariamente toda a preparação para alcançar o objetivo final.
                - Não reprove apenas porque o objetivo completo poderia exigir mais semanas.
                  Avalie coerência interna, segurança e progressão dentro do ciclo solicitado.
                - Dados opcionais ausentes não constituem erro por si próprios.
                - Avalie relativamente à capacidade demonstrada: iniciantes pedem adaptação
                  conservadora; atletas com base podem receber estímulos progressivos; cargas
                  avançadas só são adequadas quando histórico e capacidade as sustentarem.
                - A validação Java já cobre regras estruturais determinísticas. Complemente-a
                  com julgamento contextual sem criar versões subjetivas conflitantes.

                Em cada semana verifique quantidade e dias dos treinos, duplicidades,
                distribuição de intensidade, recuperação, treino leve, longão,
                compatibilidade com nível e objetivo, distância, pace, duração,
                excesso de carga e clareza.

                Globalmente verifique quantidade e continuidade das semanas,
                progressão de volume e intensidade, aumentos bruscos, repetições,
                recuperação e adequação dos estímulos ao objetivo. Apenas quando possuiProva=true
                e o contexto temporal indicar que a prova está dentro do ciclo, verifique redução antes dela, semana/data da prova e
                coerência do período posterior ao evento. Mesmo com prova próxima, não reprove
                apenas pela proximidade; avalie se preparação curta, alerta, recuperação e
                retorno progressivo são coerentes com o contexto real.
                %s

                Contexto do atleta e do ciclo:
                duracaoSemanas=%d
                objetivo=%s
                experiencia=%s
                diasDisponiveis=%s
                diaLongao=%s
                volumeSemanalAtual=%s
                maiorDistanciaRealizada=%s
                corre5KmDireto=%s
                tempoAtual=%s
                tempoAtual5Km=%s
                tempoDesejado=%s
                paceAlvoMeta=%s
                ritmoConfortavelAtual=%s
                distanciaAlvo=%s
                possuiProva=%s
                dataProva=%s
                distanciaProva=%s
                possuiLesao=%s
                restricaoOuObservacao=%s
                contextoTemporalProva=%s

                Plano:
                %s
                """.formatted(
                orientacaoCapacidadeCincoKm(request),
                context.duracaoSemanas(),
                valor(request.getObjetivo()),
                valor(request.getExperienciaCorrida()),
                request.getDiasDisponiveis(),
                valor(request.getDiaLongao()),
                valor(request.getVolumeSemanalAtual()),
                valor(request.getMaiorDistanciaCorrida()),
                respostaSimNao(CapacidadeCincoKm.respostaAplicavel(request)),
                valor(request.getTempoAtual()),
                CapacidadeCincoKm.ehAplicavel(request)
                        ? valor(request.getTempo5Km()) : "Não informado",
                valor(request.getTempoDesejado()),
                paceAlvo(request),
                valor(request.getRitmoConfortavel()),
                valor(request.getDistanciaAlvo()),
                Boolean.TRUE.equals(request.getPossuiProva()) ? "Sim" : "Não",
                Boolean.TRUE.equals(request.getPossuiProva()) && request.getDataProva() != null
                        ? request.getDataProva() : "Não se aplica",
                Boolean.TRUE.equals(request.getPossuiProva())
                        ? valor(request.getDistanciaProva()) : "Não se aplica",
                Boolean.TRUE.equals(request.getPossuiLesao()) ? "Sim" : "Não",
                valor(request.getObservacoes()),
                contextoTemporal(context),
                objectMapper.writeValueAsString(plano));
    }

    private String valor(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : "Não informado";
    }

    private String paceAlvo(GerarPlanoTreinoRequestDTO request) {
        if (!request.ehObjetivoPerformance()) return "Não se aplica";
        return PaceAlvoCalculator.calcular(request).orElse("Não se aplica");
    }

    private String respostaSimNao(Boolean valor) {
        if (valor == null) {
            return "Não informado";
        }
        return valor ? "Sim" : "Não";
    }

    private String orientacaoCapacidadeCincoKm(GerarPlanoTreinoRequestDTO request) {
        if (!CapacidadeCincoKm.ehAplicavel(request)
                || !Boolean.FALSE.equals(request.getCorre5KmSemCaminhar())) {
            return "";
        }
        return """
                Se o atleta ainda não corre 5 km direto sem caminhar, confirme que
                todas as sessões de corrida alternam trote ou corrida leve com caminhada e que
                não existem tiros, ritmo forte ou outro treino intenso.
                """.strip();
    }

    private String contextoTemporal(AgentExecutionContext context) {
        if (!Boolean.TRUE.equals(context.request().getPossuiProva())) {
            return "Não se aplica";
        }
        PlanoTreinoCalendario.ContextoProva calendario = PlanoTreinoCalendario.contexto(
                context.request(), context.duracaoSemanas(), context.dataInicio());
        if (!calendario.provaDentroDoCiclo()) {
            return "Prova fora do ciclo; gerar somente preparação direcionada";
        }
        return "inicioCiclo=" + calendario.dataInicio()
                + ", inicioSemana1=" + calendario.inicioSemana1()
                + ", dataProva=" + calendario.dataProva()
                + ", diaProva=" + calendario.diaSemanaProva()
                + ", semanaProva=" + calendario.numeroSemanaProva();
    }
}
