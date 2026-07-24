package com.kaio.runtracker.ai.agent;

import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TrainingPlanAgent {
    private static final Logger logger = LoggerFactory.getLogger(TrainingPlanAgent.class);

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
}
