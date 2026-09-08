package com.kaio.runtracker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import com.kaio.runtracker.entity.GeracaoPlanoStatus;
import com.kaio.runtracker.entity.Pagamento;
import com.kaio.runtracker.entity.PagamentoStatus;
import com.kaio.runtracker.entity.SolicitacaoPlanoStatus;
import com.kaio.runtracker.entity.TrainingPlan;
import com.kaio.runtracker.repository.PagamentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class GeracaoPlanoTransacaoService {
    private static final Logger logger = LoggerFactory.getLogger(GeracaoPlanoTransacaoService.class);
    private static final String MENSAGEM_FALHA = "Não foi possível gerar o plano neste momento.";

    private final PagamentoRepository pagamentoRepository;
    private final TrainingPlanService trainingPlanService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final long processingStaleTimeoutMinutes;

    @Autowired
    public GeracaoPlanoTransacaoService(
            PagamentoRepository pagamentoRepository,
            TrainingPlanService trainingPlanService,
            ObjectMapper objectMapper,
            @Value("${endurax.ai.agent.processing-stale-timeout-minutes:30}")
            long processingStaleTimeoutMinutes) {
        this(pagamentoRepository, trainingPlanService, objectMapper,
                Clock.systemDefaultZone(), processingStaleTimeoutMinutes);
    }

    GeracaoPlanoTransacaoService(
            PagamentoRepository pagamentoRepository,
            TrainingPlanService trainingPlanService,
            ObjectMapper objectMapper) {
        this(pagamentoRepository, trainingPlanService, objectMapper,
                Clock.systemDefaultZone(), 30);
    }

    GeracaoPlanoTransacaoService(
            PagamentoRepository pagamentoRepository,
            TrainingPlanService trainingPlanService,
            ObjectMapper objectMapper,
            Clock clock,
            long processingStaleTimeoutMinutes) {
        this.pagamentoRepository = pagamentoRepository;
        this.trainingPlanService = trainingPlanService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.processingStaleTimeoutMinutes = processingStaleTimeoutMinutes;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<GeracaoContexto> reservar(Long pagamentoId) {
        Pagamento pagamento = pagamentoRepository.findByIdForUpdate(pagamentoId).orElse(null);
        if (pagamento == null
                || pagamento.getStatus() != PagamentoStatus.APPROVED
                || (pagamento.getSolicitacaoPlano() != null
                    && pagamento.getSolicitacaoPlano().getStatus() == SolicitacaoPlanoStatus.CANCELLED)
                || pagamento.getTrainingPlan() != null
                || processamentoRecente(pagamento)
                || pagamento.getGeracaoStatus() == GeracaoPlanoStatus.COMPLETED) {
            return Optional.empty();
        }
        if (pagamento.getSolicitacaoPlano() == null) {
            pagamento.setGeracaoStatus(GeracaoPlanoStatus.FAILED);
            pagamento.setGeracaoMensagem(MENSAGEM_FALHA);
            pagamentoRepository.save(pagamento);
            return Optional.empty();
        }

        try {
            GerarPlanoTreinoRequestDTO formulario = objectMapper.readValue(
                    pagamento.getSolicitacaoPlano().getDadosFormularioJson(),
                    GerarPlanoTreinoRequestDTO.class);
            pagamento.setGeracaoStatus(GeracaoPlanoStatus.PROCESSING);
            pagamento.setGeracaoMensagem(null);
            pagamento.setAtualizadoEm(LocalDateTime.now(clock));
            pagamento.getSolicitacaoPlano().setStatus(SolicitacaoPlanoStatus.PROCESSING);
            pagamentoRepository.save(pagamento);
            return Optional.of(new GeracaoContexto(
                    pagamentoId, pagamento.getSolicitacaoPlano().getId(), formulario));
        } catch (JsonProcessingException exception) {
            pagamento.setGeracaoStatus(GeracaoPlanoStatus.FAILED);
            pagamento.setGeracaoMensagem(MENSAGEM_FALHA);
            pagamento.getSolicitacaoPlano().setStatus(SolicitacaoPlanoStatus.FAILED);
            pagamentoRepository.save(pagamento);
            logger.error(
                    "solicitacaoPlanoId={} etapa=RESERVATION status=FAILED motivo=formulario_invalido",
                    pagamento.getSolicitacaoPlano().getId(), exception);
            return Optional.empty();
        }
    }

    private boolean processamentoRecente(Pagamento pagamento) {
        if (pagamento.getGeracaoStatus() != GeracaoPlanoStatus.PROCESSING) return false;
        LocalDateTime atualizadoEm = pagamento.getAtualizadoEm();
        if (atualizadoEm == null) return true;
        LocalDateTime limite = LocalDateTime.now(clock).minusMinutes(processingStaleTimeoutMinutes);
        return atualizadoEm.isAfter(limite);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long concluir(GeracaoContexto contexto, PlanoTreinoIAResponseDTO planoGerado) {
        Pagamento pagamento = pagamentoRepository.findByIdForUpdate(contexto.pagamentoId()).orElseThrow();
        if (pagamento.getTrainingPlan() != null) {
            return pagamento.getTrainingPlan().getId();
        }
        if (pagamento.getGeracaoStatus() != GeracaoPlanoStatus.PROCESSING) {
            return null;
        }

        TrainingPlan plano = trainingPlanService.salvarPlanoGerado(contexto.formulario(), planoGerado);
        pagamento.setTrainingPlan(plano);
        pagamento.setPlanoGerado(true);
        pagamento.setGeracaoStatus(GeracaoPlanoStatus.COMPLETED);
        pagamento.setGeracaoMensagem(null);
        pagamento.getSolicitacaoPlano().setStatus(SolicitacaoPlanoStatus.COMPLETED);
        pagamentoRepository.save(pagamento);
        return plano.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void falhar(Long pagamentoId) {
        Pagamento pagamento = pagamentoRepository.findByIdForUpdate(pagamentoId).orElse(null);
        if (pagamento == null || pagamento.getTrainingPlan() != null) {
            return;
        }
        pagamento.setGeracaoStatus(GeracaoPlanoStatus.FAILED);
        pagamento.setGeracaoMensagem(MENSAGEM_FALHA);
        if (pagamento.getSolicitacaoPlano() != null) {
            pagamento.getSolicitacaoPlano().setStatus(SolicitacaoPlanoStatus.FAILED);
        }
        pagamentoRepository.save(pagamento);
    }

    public record GeracaoContexto(
            Long pagamentoId,
            Long solicitacaoPlanoId,
            GerarPlanoTreinoRequestDTO formulario) {
    }
}
