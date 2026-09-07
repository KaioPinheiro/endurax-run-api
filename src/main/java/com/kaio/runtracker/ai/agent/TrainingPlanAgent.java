package com.kaio.runtracker.ai.agent;

import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class TrainingPlanAgent {
    private static final Logger logger = LoggerFactory.getLogger(TrainingPlanAgent.class);
    private static final int MAX_APONTAMENTOS_LOG = 10;
    private static final int MAX_MENSAGEM_LOG = 300;
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern UUID = Pattern.compile(
            "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[^\\s,;]+");
    private static final Pattern JWT = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b");
    private static final Pattern API_KEY = Pattern.compile(
            "(?i)\\b(?:sk|APP_USR|TEST)[-_][A-Za-z0-9._-]{10,}\\b");
    private static final Pattern CREDENCIAL_NOMEADA = Pattern.compile(
            "(?i)\\b(?:api[_-]?key|access[_-]?token|refresh[_-]?token|secret|token)"
                    + "\\s*[:=]\\s*[\\\"']?[^\\s,;\\\"']+");

    private final TrainingPlanGenerator generator;
    private final TrainingPlanReviewer reviewer;
    private final TrainingPlanValidator validator;
    private final int maxCorrectionAttempts;

    public TrainingPlanAgent(
            TrainingPlanGenerator generator,
            TrainingPlanReviewer reviewer,
            TrainingPlanValidator validator,
            @Value("${endurax.ai.agent.max-correction-attempts:2}")
            int maxCorrectionAttempts) {
        if (maxCorrectionAttempts < 0) {
            throw new IllegalArgumentException(
                    "O limite de tentativas de correção não pode ser negativo.");
        }
        this.generator = generator;
        this.reviewer = reviewer;
        this.validator = validator;
        this.maxCorrectionAttempts = maxCorrectionAttempts;
    }

    public AgentExecutionResult execute(AgentExecutionContext context) {
        MDC.put("etapa", "GENERATION");
        long inicioEtapa = System.nanoTime();
        PlanoTreinoIAResponseDTO plano = generator.generate(context);
        logger.info("solicitacaoPlanoId={} etapa=GENERATION status=SUCCESS tentativa=0 duracaoMs={}",
                context.identificadorTecnico(), tempoMs(inicioEtapa));

        for (int tentativa = 0; tentativa <= maxCorrectionAttempts; tentativa++) {
            MDC.put("etapa", "VALIDATION");
            ValidationResult validacao = validator.validate(plano, context);
            if (validacao.isValid()) {
                logger.info(
                        "solicitacaoPlanoId={} etapa=VALIDATION status=SUCCESS tentativa={} avisos={}",
                        context.identificadorTecnico(), tentativa, validacao.getWarnings().size());
            } else {
                logger.warn(
                        "solicitacaoPlanoId={} etapa=VALIDATION status=REJECTED tentativa={} erros={} errosSemanais={} errosGlobais={} avisos={}",
                        context.identificadorTecnico(), tentativa, validacao.getErrors().size(),
                        quantidadeComPrefixo(validacao, "Semana"),
                        quantidadeComPrefixo(validacao, "Global"), validacao.getWarnings().size());
            }
            registrarErrosValidacao(context.identificadorTecnico(), tentativa, validacao.getErrors());

            MDC.put("etapa", "REVIEW");
            inicioEtapa = System.nanoTime();
            ReviewResult revisao = reviewer.review(plano, context);
            String statusRevisao = revisao.valid() ? "SUCCESS" : "REJECTED";
            if (revisao.valid()) {
                logger.info("solicitacaoPlanoId={} etapa=REVIEW status={} tentativa={} erros={} avisos={} duracaoMs={}",
                        context.identificadorTecnico(), statusRevisao, tentativa,
                        revisao.errors().size(), revisao.warnings().size(), tempoMs(inicioEtapa));
            } else {
                logger.warn("solicitacaoPlanoId={} etapa=REVIEW status={} tentativa={} erros={} avisos={} duracaoMs={}",
                        context.identificadorTecnico(), statusRevisao, tentativa,
                        revisao.errors().size(), revisao.warnings().size(), tempoMs(inicioEtapa));
            }
            registrarApontamentos(
                    context.identificadorTecnico(), tentativa, "ERROR", revisao.errors());
            registrarApontamentos(
                    context.identificadorTecnico(), tentativa, "WARNING", revisao.warnings());

            if (validacao.isValid() && revisao.valid()) {
                return new AgentExecutionResult(plano, tentativa, validacao, revisao);
            }
            if (tentativa == maxCorrectionAttempts) {
                logger.warn(
                        "solicitacaoPlanoId={} etapa=PIPELINE status=REJECTED correcoes={} errosJava={} errosRevisao={}",
                        context.identificadorTecnico(), tentativa,
                        validacao.getErrors().size(), revisao.errors().size());
                throw new PlanoTreinoReprovadoException(
                        validacao.getErrors(), revisao.errors());
            }

            MDC.put("etapa", "CORRECTION");
            inicioEtapa = System.nanoTime();
            logger.info("solicitacaoPlanoId={} etapa=CORRECTION status=STARTED tentativa={}/{} errosJava={} errosReviewer={}",
                    context.identificadorTecnico(), tentativa + 1, maxCorrectionAttempts,
                    validacao.getErrors().size(), revisao.errors().size());
            plano = generator.correct(plano, context, validacao, revisao);
            logger.info("solicitacaoPlanoId={} etapa=CORRECTION status=SUCCESS tentativa={}/{} duracaoMs={}",
                    context.identificadorTecnico(), tentativa + 1, maxCorrectionAttempts,
                    tempoMs(inicioEtapa));
        }
        throw new IllegalStateException("Fluxo inesperado do agente de plano.");
    }

    private long tempoMs(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }

    private void registrarErrosValidacao(
            String solicitacaoPlanoId,
            int tentativa,
            List<String> erros) {
        int quantidadeLogada = Math.min(erros.size(), MAX_APONTAMENTOS_LOG);
        for (int indice = 0; indice < quantidadeLogada; indice++) {
            logger.warn(
                    "solicitacaoPlanoId={} etapa=VALIDATION status=DETAIL tentativa={} indice={} motivo={}",
                    solicitacaoPlanoId, tentativa, indice + 1,
                    sanitizarApontamento(erros.get(indice)));
        }
        if (erros.size() > quantidadeLogada) {
            logger.warn(
                    "solicitacaoPlanoId={} etapa=VALIDATION status=DETAIL_OMITTED tentativa={} omitidos={}",
                    solicitacaoPlanoId, tentativa, erros.size() - quantidadeLogada);
        }
    }

    private long quantidadeComPrefixo(ValidationResult resultado, String prefixo) {
        return resultado.getErrors().stream()
                .filter(erro -> erro.startsWith(prefixo))
                .count();
    }

    private void registrarApontamentos(
            String identificadorTecnico,
            int tentativa,
            String severidade,
            List<String> apontamentos) {
        int quantidadeLogada = Math.min(apontamentos.size(), MAX_APONTAMENTOS_LOG);
        for (int indice = 0; indice < quantidadeLogada; indice++) {
            logger.warn(
                    "solicitacaoPlanoId={} etapa=REVIEW status=DETAIL tentativa={} severidade={} indice={} mensagem={}",
                    identificadorTecnico,
                    tentativa,
                    severidade,
                    indice + 1,
                    sanitizarApontamento(apontamentos.get(indice)));
        }
        int omitidos = apontamentos.size() - quantidadeLogada;
        if (omitidos > 0) {
            logger.warn(
                    "solicitacaoPlanoId={} etapa=REVIEW status=DETAIL_OMITTED tentativa={} severidade={} omitidos={}",
                    identificadorTecnico, tentativa, severidade, omitidos);
        }
    }

    private String sanitizarApontamento(String mensagem) {
        if (mensagem == null || mensagem.isBlank()) return "<vazio>";
        String segura = mensagem.replaceAll("[\\r\\n\\t]+", " ");
        segura = EMAIL.matcher(segura).replaceAll("[REDACTED_EMAIL]");
        segura = UUID.matcher(segura).replaceAll("[REDACTED_UUID]");
        segura = BEARER.matcher(segura).replaceAll("Bearer [REDACTED]");
        segura = JWT.matcher(segura).replaceAll("[REDACTED_JWT]");
        segura = API_KEY.matcher(segura).replaceAll("[REDACTED_API_KEY]");
        segura = CREDENCIAL_NOMEADA.matcher(segura).replaceAll("[REDACTED_CREDENTIAL]");
        segura = segura.replaceAll("[\\p{Cntrl}]", "").trim();
        return segura.length() > MAX_MENSAGEM_LOG
                ? segura.substring(0, MAX_MENSAGEM_LOG) + "... [truncado]"
                : segura;
    }
}
