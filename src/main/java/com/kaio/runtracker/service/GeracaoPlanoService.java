package com.kaio.runtracker.service;

import com.kaio.runtracker.ai.agent.AgentExecutionContext;
import com.kaio.runtracker.ai.agent.AgentExecutionResult;
import com.kaio.runtracker.ai.agent.PlanoTreinoDuracaoCalculator;
import com.kaio.runtracker.ai.agent.TrainingPlanAgent;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class GeracaoPlanoService {
    private static final Logger logger = LoggerFactory.getLogger(GeracaoPlanoService.class);

    private final GeracaoPlanoTransacaoService transacaoService;
    private final TrainingPlanAgent trainingPlanAgent;
    private final PlanoTreinoDuracaoCalculator duracaoCalculator;

    public GeracaoPlanoService(
            GeracaoPlanoTransacaoService transacaoService,
            TrainingPlanAgent trainingPlanAgent,
            PlanoTreinoDuracaoCalculator duracaoCalculator) {
        this.transacaoService = transacaoService;
        this.trainingPlanAgent = trainingPlanAgent;
        this.duracaoCalculator = duracaoCalculator;
    }

    public void gerar(Long pagamentoId) {
        long inicioTotal = System.nanoTime();
        Optional<GeracaoPlanoTransacaoService.GeracaoContexto> reserva =
                transacaoService.reservar(pagamentoId);
        if (reserva.isEmpty()) {
            logger.info("Geração de plano ignorada por duplicidade ou estado incompatível: pagamentoId={}",
                    pagamentoId);
            return;
        }

        GeracaoPlanoTransacaoService.GeracaoContexto contexto = reserva.get();
        String solicitacaoPlanoId = String.valueOf(contexto.solicitacaoPlanoId());
        MDC.put("solicitacaoPlanoId", solicitacaoPlanoId);
        MDC.put("etapa", "GENERATION");
        try {
            int duracaoSemanas = duracaoCalculator.calcular(contexto.formulario());
            logger.info(
                    "solicitacaoPlanoId={} etapa=PIPELINE status=STARTED semanas={} diasDisponiveis={}",
                    contexto.solicitacaoPlanoId(), duracaoSemanas,
                    contexto.formulario().getDiasDisponiveis() == null
                            ? 0 : contexto.formulario().getDiasDisponiveis().size());
            AgentExecutionContext agentContext = new AgentExecutionContext(
                    contexto.formulario(),
                    duracaoSemanas,
                    duracaoCalculator.hoje(),
                    solicitacaoPlanoId);
            AgentExecutionResult resultado = trainingPlanAgent.execute(agentContext);
            PlanoTreinoIAResponseDTO plano = resultado.plano();
            MDC.put("etapa", "PERSISTENCE");
            Long planoId = transacaoService.concluir(contexto, plano);
            if (planoId == null) {
                logger.error(
                        "solicitacaoPlanoId={} etapa=PERSISTENCE status=FAILED motivo=plano_nao_persistido duracaoMs={}",
                        contexto.solicitacaoPlanoId(), tempoMs(inicioTotal));
            } else {
                logger.info(
                        "solicitacaoPlanoId={} etapa=PERSISTENCE status=SUCCESS planoId={} semanas={}",
                        contexto.solicitacaoPlanoId(), planoId,
                        plano != null && plano.getSemanas() != null ? plano.getSemanas().size() : 0);
                logger.info("solicitacaoPlanoId={} etapa=PIPELINE status=SUCCESS duracaoMs={}",
                        contexto.solicitacaoPlanoId(), tempoMs(inicioTotal));
            }
        } catch (Exception exception) {
            transacaoService.falhar(pagamentoId);
            logger.error(
                    "solicitacaoPlanoId={} etapa={} status=FAILED tipoErro={}",
                    contexto.solicitacaoPlanoId(), MDC.get("etapa"),
                    exception.getClass().getSimpleName(), exception);
        } finally {
            MDC.remove("etapa");
            MDC.remove("solicitacaoPlanoId");
        }
    }

    private long tempoMs(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }
}
