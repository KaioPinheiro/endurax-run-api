package com.kaio.runtracker.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.service.PagamentoService;
import com.kaio.runtracker.service.GeracaoPlanoAssincronaService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MercadoPagoWebhookControllerTest {

    private final PagamentoService pagamentoService = mock(PagamentoService.class);
    private final GeracaoPlanoAssincronaService geracaoAssincronaService =
            mock(GeracaoPlanoAssincronaService.class);
    private final MercadoPagoWebhookController controller =
            new MercadoPagoWebhookController(pagamentoService, geracaoAssincronaService);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void recebeNotificacaoDeOrderEProcessaIdInformado() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {"type":"order","action":"order.updated","data":{"id":"ORD123"}}
                """);
        org.mockito.Mockito.when(pagamentoService.processarWebhookOrder("ORD123")).thenReturn(1L);

        ResponseEntity<Void> response = controller.receber(payload);

        assertEquals(200, response.getStatusCode().value());
        verify(pagamentoService).processarWebhookOrder("ORD123");
        verify(geracaoAssincronaService).iniciar(1L);
    }

    @Test
    void ignoraNotificacaoDeOutroRecursoComHttp200() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {"type":"payment","data":{"id":"PAY123"}}
                """);

        ResponseEntity<Void> response = controller.receber(payload);

        assertEquals(200, response.getStatusCode().value());
        verify(pagamentoService, never()).processarWebhookOrder("PAY123");
        verify(geracaoAssincronaService, never()).iniciar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void webhookDuplicadoNaoDisparaNovaGeracao() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {"type":"order","data":{"id":"ORD123"}}
                """);
        org.mockito.Mockito.when(pagamentoService.processarWebhookOrder("ORD123")).thenReturn(null);

        ResponseEntity<Void> response = controller.receber(payload);

        assertEquals(200, response.getStatusCode().value());
        verify(geracaoAssincronaService, never()).iniciar(org.mockito.ArgumentMatchers.any());
    }
}
