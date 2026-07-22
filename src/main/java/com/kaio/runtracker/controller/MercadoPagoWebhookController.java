package com.kaio.runtracker.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.kaio.runtracker.service.PagamentoService;
import com.kaio.runtracker.service.GeracaoPlanoAssincronaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/mercado-pago")
public class MercadoPagoWebhookController {
    private static final Logger logger = LoggerFactory.getLogger(MercadoPagoWebhookController.class);

    private final PagamentoService pagamentoService;
    private final GeracaoPlanoAssincronaService geracaoAssincronaService;

    public MercadoPagoWebhookController(
            PagamentoService pagamentoService,
            GeracaoPlanoAssincronaService geracaoAssincronaService) {
        this.pagamentoService = pagamentoService;
        this.geracaoAssincronaService = geracaoAssincronaService;
    }

    @PostMapping
    public ResponseEntity<Void> receber(@RequestBody JsonNode notificacao) {
        String tipo = texto(notificacao, "type");
        String acao = texto(notificacao, "action");
        String orderId = notificacao.path("data").path("id").asText(null);
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
