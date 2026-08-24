package com.kaio.runtracker.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.service.PagamentoService;
import com.kaio.runtracker.service.GeracaoPlanoAssincronaService;
import com.kaio.runtracker.security.MercadoPagoWebhookSignatureValidator;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MercadoPagoWebhookControllerTest {

    private final PagamentoService pagamentoService = mock(PagamentoService.class);
    private final GeracaoPlanoAssincronaService geracaoAssincronaService =
            mock(GeracaoPlanoAssincronaService.class);
    private final MercadoPagoWebhookSignatureValidator signatureValidator =
            mock(MercadoPagoWebhookSignatureValidator.class);
    private final MercadoPagoWebhookController controller =
            new MercadoPagoWebhookController(
                    pagamentoService, geracaoAssincronaService, signatureValidator);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void recebeNotificacaoDeOrderEProcessaIdInformado() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {"type":"order","action":"order.updated","data":{"id":"ORD123"}}
                """);
        when(signatureValidator.validar("ASSINATURA", "REQ123", "ORD123")).thenReturn(true);
        when(pagamentoService.processarWebhookOrder("ORD123")).thenReturn(1L);

        ResponseEntity<Void> response = controller.receber(
                "ASSINATURA", "REQ123", "ORD123", payload);

        assertEquals(200, response.getStatusCode().value());
        verify(pagamentoService).processarWebhookOrder("ORD123");
        verify(geracaoAssincronaService).iniciar(1L);
    }

    @Test
    void ignoraNotificacaoDeOutroRecursoComHttp200() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {"type":"payment","data":{"id":"PAY123"}}
                """);
        when(signatureValidator.validar("ASSINATURA", "REQ123", "PAY123")).thenReturn(true);

        ResponseEntity<Void> response = controller.receber(
                "ASSINATURA", "REQ123", "PAY123", payload);

        assertEquals(200, response.getStatusCode().value());
        verify(pagamentoService, never()).processarWebhookOrder("PAY123");
        verify(geracaoAssincronaService, never()).iniciar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void webhookDuplicadoNaoDisparaNovaGeracao() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {"type":"order","data":{"id":"ORD123"}}
                """);
        when(signatureValidator.validar("ASSINATURA", "REQ123", "ORD123")).thenReturn(true);
        when(pagamentoService.processarWebhookOrder("ORD123")).thenReturn(null);

        ResponseEntity<Void> response = controller.receber(
                "ASSINATURA", "REQ123", "ORD123", payload);

        assertEquals(200, response.getStatusCode().value());
        verify(geracaoAssincronaService, never()).iniciar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void assinaturaInvalidaEhRejeitadaAntesDeConsultarOrder() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {"type":"order","data":{"id":"ORD123"}}
                """);
        when(signatureValidator.validar("INVALIDA", "REQ123", "ORD123")).thenReturn(false);

        ResponseEntity<Void> response = controller.receber(
                "INVALIDA", "REQ123", "ORD123", payload);

        assertEquals(401, response.getStatusCode().value());
        verify(pagamentoService, never()).processarWebhookOrder(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void headersAusentesSaoRejeitados() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {"type":"order","data":{"id":"ORD123"}}
                """);

        assertEquals(401, controller.receber(null, "REQ123", "ORD123", payload)
                .getStatusCode().value());
        assertEquals(401, controller.receber("ASSINATURA", null, "ORD123", payload)
                .getStatusCode().value());
        verify(pagamentoService, never()).processarWebhookOrder(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void dataIdDaQueryDiferenteDoBodyEhRejeitado() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {"type":"order","data":{"id":"OUTRA_ORDER"}}
                """);
        when(signatureValidator.validar("ASSINATURA", "REQ123", "ORD123")).thenReturn(true);

        ResponseEntity<Void> response = controller.receber(
                "ASSINATURA", "REQ123", "ORD123", payload);

        assertEquals(401, response.getStatusCode().value());
        verify(pagamentoService, never()).processarWebhookOrder(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejeicaoNaoRegistraAssinatura() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(MercadoPagoWebhookController.class);
        ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        String assinatura = "ts=123,v1=assinatura-secreta";
        JsonNode payload = objectMapper.readTree("""
                {"type":"order","data":{"id":"ORD123"}}
                """);

        try {
            controller.receber(assinatura, "REQ123", "ORD123", payload);
            String logs = appender.list.stream()
                    .map(evento -> evento.getFormattedMessage())
                    .reduce("", String::concat);
            org.junit.jupiter.api.Assertions.assertFalse(logs.contains(assinatura));
            org.junit.jupiter.api.Assertions.assertFalse(logs.contains("assinatura-secreta"));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
