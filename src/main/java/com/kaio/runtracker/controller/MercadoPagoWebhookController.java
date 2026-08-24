package com.kaio.runtracker.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.kaio.runtracker.service.PagamentoService;
import com.kaio.runtracker.service.GeracaoPlanoAssincronaService;
import com.kaio.runtracker.security.MercadoPagoWebhookSignatureValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/mercado-pago")
public class MercadoPagoWebhookController {
    private static final Logger logger = LoggerFactory.getLogger(MercadoPagoWebhookController.class);

    private final PagamentoService pagamentoService;
    private final GeracaoPlanoAssincronaService geracaoAssincronaService;
    private final MercadoPagoWebhookSignatureValidator signatureValidator;

    public MercadoPagoWebhookController(
            PagamentoService pagamentoService,
            GeracaoPlanoAssincronaService geracaoAssincronaService,
            MercadoPagoWebhookSignatureValidator signatureValidator) {
        this.pagamentoService = pagamentoService;
        this.geracaoAssincronaService = geracaoAssincronaService;
        this.signatureValidator = signatureValidator;
    }

    @PostMapping
    public ResponseEntity<Void> receber(
            @RequestHeader(name = "x-signature", required = false) String assinatura,
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            @RequestParam(name = "data.id", required = false) String dataIdAssinado,
            @RequestBody JsonNode notificacao) {
        String tipo = texto(notificacao, "type");
        String acao = texto(notificacao, "action");
        String orderId = notificacao.path("data").path("id").asText(null);

        if (!signatureValidator.validar(assinatura, requestId, dataIdAssinado)
                || !StringUtils.hasText(orderId)
                || !orderId.equals(dataIdAssinado)) {
            logger.warn("Webhook Mercado Pago rejeitado: assinatura ou identificador inválido");
            return ResponseEntity.status(401).build();
        }
        logger.info("Webhook Mercado Pago recebido: type={}, action={}, orderId={}", tipo, acao, orderId);

        if (!"order".equalsIgnoreCase(tipo)) {
            logger.info("Webhook Mercado Pago ignorado: recurso não é Order, type={}", tipo);
            return ResponseEntity.ok().build();
        }
        if (!StringUtils.hasText(orderId)) {
            logger.warn("Webhook Mercado Pago ignorado: notificação de Order sem data.id");
            return ResponseEntity.ok().build();
        }

        Long pagamentoAprovadoId = pagamentoService.processarWebhookOrder(orderId);
        if (pagamentoAprovadoId != null) {
            geracaoAssincronaService.iniciar(pagamentoAprovadoId);
        }
        return ResponseEntity.ok().build();
    }

    private String texto(JsonNode node, String campo) {
        return node.path(campo).asText(null);
    }
}
