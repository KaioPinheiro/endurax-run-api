package com.kaio.runtracker.ai.agent;

import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        logger.info("Agente iniciado: id={}, semanas={}",
                context.identificadorTecnico(), context.duracaoSemanas());
        logger.info("Geração iniciada: id={}", context.identificadorTecnico());
        PlanoTreinoIAResponseDTO plano = generator.generate(context);
        logger.info("Geração concluída: id={}", context.identificadorTecnico());

        for (int tentativa = 0; tentativa <= maxCorrectionAttempts; tentativa++) {
            ValidationResult validacao = validator.validate(plano, context);
            logger.info(
                    "Validação concluída: id={}, tentativa={}, errosSemanais={}, errosGlobais={}, avisos={}",
                    context.identificadorTecnico(), tentativa,
                    quantidadeComPrefixo(validacao, "Semana"),
                    quantidadeComPrefixo(validacao, "Global"),
                    validacao.getWarnings().size());

            logger.info("Revisão iniciada: id={}, tentativa={}",
                    context.identificadorTecnico(), tentativa);
            ReviewResult revisao = reviewer.review(plano, context);
            logger.info("Revisão concluída: id={}, tentativa={}, erros={}, avisos={}",
                    context.identificadorTecnico(), tentativa,
                    revisao.errors().size(), revisao.warnings().size());
            registrarApontamentos(
                    context.identificadorTecnico(), tentativa, "ERROR", revisao.errors());
            registrarApontamentos(
                    context.identificadorTecnico(), tentativa, "WARNING", revisao.warnings());

            if (validacao.isValid() && revisao.valid()) {
                logger.info("Plano aprovado: id={}, correcoes={}",
                        context.identificadorTecnico(), tentativa);
                return new AgentExecutionResult(plano, tentativa, validacao, revisao);
            }
            if (tentativa == maxCorrectionAttempts) {
                logger.warn(
                        "Plano reprovado após limite: id={}, correcoes={}, errosJava={}, errosRevisao={}",
                        context.identificadorTecnico(), tentativa,
                        validacao.getErrors().size(), revisao.errors().size());
                throw new PlanoTreinoReprovadoException(
                        validacao.getErrors(), revisao.errors());
            }

            logger.info("Correção iniciada: id={}, tentativa={}/{}",
                    context.identificadorTecnico(), tentativa + 1, maxCorrectionAttempts);
            plano = generator.correct(plano, context, validacao, revisao);
            logger.info("Correção concluída: id={}, tentativa={}/{}",
                    context.identificadorTecnico(), tentativa + 1, maxCorrectionAttempts);
        }
        throw new IllegalStateException("Fluxo inesperado do agente de plano.");
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
                    "TrainingPlan reviewer apontamento: id={}, tentativa={}, severidade={}, indice={}, mensagem={}",
                    identificadorTecnico,
                    tentativa,
                    severidade,
                    indice + 1,
                    sanitizarApontamento(apontamentos.get(indice)));
        }
        int omitidos = apontamentos.size() - quantidadeLogada;
        if (omitidos > 0) {
            logger.warn(
                    "TrainingPlan reviewer apontamentos omitidos: id={}, tentativa={}, severidade={}, omitidos={}",
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
