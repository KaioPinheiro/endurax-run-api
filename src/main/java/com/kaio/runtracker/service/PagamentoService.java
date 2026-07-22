package com.kaio.runtracker.service;

import com.kaio.runtracker.client.MercadoPagoOrderResponse;
import com.kaio.runtracker.client.MercadoPagoOrdersClient;
import com.kaio.runtracker.config.MercadoPagoProperties;
import com.kaio.runtracker.dto.CriarPagamentoPixResponseDTO;
import com.kaio.runtracker.dto.PagamentoStatusResponseDTO;
import com.kaio.runtracker.dto.PagamentoResultadoResponseDTO;
import com.kaio.runtracker.entity.GeracaoPlanoStatus;
import com.kaio.runtracker.entity.Pagamento;
import com.kaio.runtracker.entity.PagamentoStatus;
import com.kaio.runtracker.entity.SolicitacaoPlano;
import com.kaio.runtracker.entity.SolicitacaoPlanoStatus;
import com.kaio.runtracker.exception.PagamentoException;
import com.kaio.runtracker.repository.PagamentoRepository;
import com.kaio.runtracker.repository.SolicitacaoPlanoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class PagamentoService {
    private static final Logger logger = LoggerFactory.getLogger(PagamentoService.class);

    private final PagamentoRepository repository;
    private final MercadoPagoOrdersClient mercadoPagoClient;
    private final MercadoPagoProperties properties;
    private final SolicitacaoPlanoRepository solicitacaoPlanoRepository;
    private final Clock clock;

    @Autowired
    public PagamentoService(
            PagamentoRepository repository,
            MercadoPagoOrdersClient mercadoPagoClient,
            MercadoPagoProperties properties,
            SolicitacaoPlanoRepository solicitacaoPlanoRepository) {
        this(repository, mercadoPagoClient, properties, solicitacaoPlanoRepository, Clock.systemDefaultZone());
    }

    PagamentoService(
            PagamentoRepository repository,
            MercadoPagoOrdersClient mercadoPagoClient,
            MercadoPagoProperties properties,
            SolicitacaoPlanoRepository solicitacaoPlanoRepository,
            Clock clock) {
        this.repository = repository;
        this.mercadoPagoClient = mercadoPagoClient;
        this.properties = properties;
        this.solicitacaoPlanoRepository = solicitacaoPlanoRepository;
        this.clock = clock;
    }

    public CriarPagamentoPixResponseDTO criarPix(String email) {
        return criarPix(email, null);
    }

    public CriarPagamentoPixResponseDTO criarPix(String email, Long solicitacaoPlanoId) {
        String emailNormalizado = email.trim().toLowerCase(Locale.ROOT);
        SolicitacaoPlano solicitacao = buscarSolicitacao(solicitacaoPlanoId, emailNormalizado);
        String externalReference = UUID.randomUUID().toString();
        String idempotencyKey = UUID.randomUUID().toString();
        OffsetDateTime expiracaoRequest = OffsetDateTime.now(clock)
                .plusMinutes(properties.getExpiracaoPixMinutos());

        logger.info("Criando cobrança Pix: externalReference={}, valor={}",
                externalReference, properties.getValorPlano());

        MercadoPagoOrderResponse order = mercadoPagoClient.criarOrderPix(
                emailNormalizado,
                externalReference,
                idempotencyKey,
                properties.getValorPlano()
        );
        validarOrderCriada(order);

        MercadoPagoOrderResponse.Payment payment = order.primeiroPagamento();
        MercadoPagoOrderResponse.PaymentMethod metodo = payment.paymentMethod();
        LocalDateTime expiracao = expiracaoRequest.toLocalDateTime();

        Pagamento pagamento = new Pagamento();
        pagamento.setOrderExternalId(order.id());
        pagamento.setExternalReference(externalReference);
        pagamento.setIdempotencyKey(idempotencyKey);
        pagamento.setStatus(mapearStatus(statusRemoto(order), statusDetailRemoto(order)));
        pagamento.setStatusDetail(statusDetailRemoto(order));
        pagamento.setValor(properties.getValorPlano());
        pagamento.setEmailPagador(emailNormalizado);
        pagamento.setPixCopiaCola(metodo.qrCode());
        pagamento.setQrCodeBase64(metodo.qrCodeBase64());
        pagamento.setTicketUrl(metodo.ticketUrl());
        pagamento.setDataExpiracao(expiracao);
        pagamento.setPlanoGerado(false);
        pagamento.setGeracaoStatus(GeracaoPlanoStatus.PENDING);
        pagamento.setSolicitacaoPlano(solicitacao);

        Pagamento salvo = repository.save(pagamento);
        if (solicitacao != null) {
            solicitacao.setStatus(SolicitacaoPlanoStatus.PAYMENT_PENDING);
            solicitacaoPlanoRepository.save(solicitacao);
        }
        logger.info("Cobrança Pix criada: pagamentoId={}, orderId={}, status={}, expiraEm={}",
                salvo.getId(), salvo.getOrderExternalId(), salvo.getStatus(), salvo.getDataExpiracao());
        return respostaCriacao(salvo);
    }

    public PagamentoStatusResponseDTO consultarStatus(Long id) {
        Pagamento pagamento = repository.findById(id).orElseThrow(() ->
                new PagamentoException(HttpStatus.NOT_FOUND, "Pagamento não encontrado."));

        MercadoPagoOrderResponse order = mercadoPagoClient.consultarOrder(pagamento.getOrderExternalId());
        if (!StringUtils.hasText(order.id())) {
            throw new PagamentoException(HttpStatus.BAD_GATEWAY, "O Mercado Pago retornou uma order inválida.");
        }

        PagamentoStatus novoStatus = mapearStatus(statusRemoto(order), statusDetailRemoto(order));
        if (novoStatus != PagamentoStatus.APPROVED
                && LocalDateTime.now(clock).isAfter(pagamento.getDataExpiracao())) {
            novoStatus = PagamentoStatus.EXPIRED;
        }
        pagamento.setStatus(novoStatus);
        pagamento.setStatusDetail(statusDetailRemoto(order));
        if (novoStatus == PagamentoStatus.APPROVED && pagamento.getPagoEm() == null) {
            pagamento.setPagoEm(LocalDateTime.now(clock));
        }
        Pagamento atualizado = repository.save(pagamento);
        logger.info("Status Pix atualizado: pagamentoId={}, orderId={}, status={}, statusDetail={}",
                atualizado.getId(), atualizado.getOrderExternalId(), atualizado.getStatus(), atualizado.getStatusDetail());
        return respostaStatus(atualizado);
    }

    @Transactional
    public Long processarWebhookOrder(String orderId) {
        logger.info("Webhook Mercado Pago: consultando Order, orderId={}", orderId);
        MercadoPagoOrderResponse order = mercadoPagoClient.consultarOrder(orderId);
        if (!StringUtils.hasText(order.id()) || !StringUtils.hasText(order.externalReference())) {
            throw new PagamentoException(HttpStatus.BAD_GATEWAY,
                    "O Mercado Pago retornou uma Order inválida para o webhook.");
        }

        MercadoPagoOrderResponse.Payment payment = order.primeiroPagamento();
        String statusRemoto = statusRemoto(order);
        String statusDetailRemoto = statusDetailRemoto(order);
        String paymentId = payment != null ? payment.id() : null;
        logger.info("Webhook Mercado Pago: Order consultada, orderId={}, externalReference={}, "
                        + "status={}, statusDetail={}, paymentId={}",
                order.id(), order.externalReference(), statusRemoto, statusDetailRemoto, paymentId);

        Pagamento pagamento = repository.findByExternalReference(order.externalReference()).orElse(null);
        if (pagamento == null) {
            logger.warn("Webhook Mercado Pago: pagamento não encontrado, orderId={}, externalReference={}",
                    order.id(), order.externalReference());
            return null;
        }
        logger.info("Webhook Mercado Pago: pagamento localizado, pagamentoId={}, orderId={}",
                pagamento.getId(), order.id());

        if (pagamento.getStatus() == PagamentoStatus.APPROVED) {
            logger.info("Webhook Mercado Pago ignorado por duplicidade: pagamentoId={}, orderId={}, status={}",
                    pagamento.getId(), order.id(), pagamento.getStatus());
            return null;
        }

        PagamentoStatus statusAnterior = pagamento.getStatus();
        String detalheAnterior = pagamento.getStatusDetail();
        PagamentoStatus novoStatus = mapearStatus(statusRemoto, statusDetailRemoto);
        if (novoStatus == statusAnterior && Objects.equals(statusDetailRemoto, detalheAnterior)) {
            logger.info("Webhook Mercado Pago ignorado sem mudança de status: pagamentoId={}, orderId={}, status={}",
                    pagamento.getId(), order.id(), statusAnterior);
            return null;
        }

        pagamento.setStatus(novoStatus);
        pagamento.setStatusDetail(statusDetailRemoto);
        if (novoStatus == PagamentoStatus.APPROVED && pagamento.getPagoEm() == null) {
            pagamento.setPagoEm(LocalDateTime.now(clock));
        }
        pagamento.setAtualizadoEm(LocalDateTime.now(clock));
        repository.save(pagamento);
        logger.info("Webhook Mercado Pago: status atualizado, pagamentoId={}, orderId={}, statusAnterior={}, novoStatus={}",
                pagamento.getId(), order.id(), statusAnterior, novoStatus);
        boolean transicaoAprovada = (statusAnterior == PagamentoStatus.PENDING
                || statusAnterior == PagamentoStatus.PROCESSING)
                && novoStatus == PagamentoStatus.APPROVED;
        if (transicaoAprovada) {
            logger.info("Pagamento aprovado; geração automática liberada: pagamentoId={}", pagamento.getId());
            return pagamento.getId();
        }
        return null;
    }

    public PagamentoResultadoResponseDTO consultarResultado(Long pagamentoId) {
        Pagamento pagamento = repository.findById(pagamentoId).orElseThrow(() ->
                new PagamentoException(HttpStatus.NOT_FOUND, "Pagamento não encontrado."));
        Long planoId = pagamento.getTrainingPlan() != null ? pagamento.getTrainingPlan().getId() : null;
        String mensagem = pagamento.getGeracaoStatus() == GeracaoPlanoStatus.FAILED
                ? "Não foi possível gerar o plano neste momento."
                : null;
        return new PagamentoResultadoResponseDTO(
                pagamento.getStatus(), pagamento.getGeracaoStatus(), planoId, mensagem);
    }

    private SolicitacaoPlano buscarSolicitacao(Long solicitacaoPlanoId, String emailNormalizado) {
        if (solicitacaoPlanoId == null) {
            return null;
        }
        SolicitacaoPlano solicitacao = solicitacaoPlanoRepository.findById(solicitacaoPlanoId)
                .orElseThrow(() -> new PagamentoException(HttpStatus.NOT_FOUND,
                        "Solicitação de plano não encontrada."));
        if (!emailNormalizado.equals(solicitacao.getEmail())) {
            throw new PagamentoException(HttpStatus.BAD_REQUEST,
                    "O e-mail não corresponde à solicitação do plano.");
        }
        if (solicitacao.getStatus() != SolicitacaoPlanoStatus.PENDING) {
            throw new PagamentoException(HttpStatus.CONFLICT,
                    "A solicitação do plano já está vinculada a um pagamento.");
        }
        return solicitacao;
    }

    private void validarOrderCriada(MercadoPagoOrderResponse order) {
        if (!StringUtils.hasText(order.id())) {
            throw new PagamentoException(HttpStatus.BAD_GATEWAY, "O Mercado Pago retornou uma order sem identificador.");
        }
        MercadoPagoOrderResponse.Payment payment = order.primeiroPagamento();
        if (payment == null || payment.paymentMethod() == null) {
            throw new PagamentoException(HttpStatus.BAD_GATEWAY, "O Mercado Pago retornou uma resposta Pix inválida.");
        }
        if (!StringUtils.hasText(payment.paymentMethod().qrCode())) {
            throw new PagamentoException(HttpStatus.BAD_GATEWAY, "O Mercado Pago não retornou o Pix Copia e Cola.");
        }
        if (!StringUtils.hasText(payment.paymentMethod().qrCodeBase64())) {
            throw new PagamentoException(HttpStatus.BAD_GATEWAY, "O Mercado Pago não retornou o QR Code Pix.");
        }
    }

    private String statusRemoto(MercadoPagoOrderResponse order) {
        MercadoPagoOrderResponse.Payment payment = order.primeiroPagamento();
        return payment != null && StringUtils.hasText(payment.status()) ? payment.status() : order.status();
    }

    private String statusDetailRemoto(MercadoPagoOrderResponse order) {
        MercadoPagoOrderResponse.Payment payment = order.primeiroPagamento();
        return payment != null && StringUtils.hasText(payment.statusDetail())
                ? payment.statusDetail() : order.statusDetail();
    }

    private PagamentoStatus mapearStatus(String status, String statusDetail) {
        if (!StringUtils.hasText(status)) return PagamentoStatus.PROCESSING;

        String statusNormalizado = status.toLowerCase(Locale.ROOT);
        String detalheNormalizado = StringUtils.hasText(statusDetail)
                ? statusDetail.toLowerCase(Locale.ROOT)
                : "";

        if ("action_required".equals(statusNormalizado)
                && "waiting_transfer".equals(detalheNormalizado)) {
            return PagamentoStatus.PENDING;
        }
        if ("action_required".equals(statusNormalizado)) {
            return PagamentoStatus.PROCESSING;
        }
        if ("processed".equals(statusNormalizado) && "accredited".equals(detalheNormalizado)) {
            return PagamentoStatus.APPROVED;
        }
        if ("processing".equals(statusNormalizado)
                || "in_process".equals(detalheNormalizado)
                || "processed".equals(statusNormalizado)) {
            return PagamentoStatus.PROCESSING;
        }
        if ("canceled".equals(statusNormalizado) || "cancelled".equals(statusNormalizado)) {
            return PagamentoStatus.CANCELLED;
        }
        if ("expired".equals(statusNormalizado)) {
            return PagamentoStatus.EXPIRED;
        }
        if ("failed".equals(statusNormalizado) || "rejected".equals(statusNormalizado)) {
            return PagamentoStatus.REJECTED;
        }
        return PagamentoStatus.PROCESSING;
    }

    private CriarPagamentoPixResponseDTO respostaCriacao(Pagamento p) {
        return new CriarPagamentoPixResponseDTO(p.getId(), p.getStatus(), p.getValor(),
                p.getPixCopiaCola(), p.getQrCodeBase64(), p.getTicketUrl(), p.getDataExpiracao());
    }

    private PagamentoStatusResponseDTO respostaStatus(Pagamento p) {
        return new PagamentoStatusResponseDTO(p.getStatus(), p.getStatusDetail(),
                p.getStatus() == PagamentoStatus.APPROVED,
                p.getStatus() == PagamentoStatus.EXPIRED);
    }
}
