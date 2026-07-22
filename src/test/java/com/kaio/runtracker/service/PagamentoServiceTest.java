package com.kaio.runtracker.service;

import com.kaio.runtracker.client.MercadoPagoOrderResponse;
import com.kaio.runtracker.client.MercadoPagoOrdersClient;
import com.kaio.runtracker.config.MercadoPagoProperties;
import com.kaio.runtracker.dto.CriarPagamentoPixResponseDTO;
import com.kaio.runtracker.dto.PagamentoStatusResponseDTO;
import com.kaio.runtracker.entity.Pagamento;
import com.kaio.runtracker.entity.PagamentoStatus;
import com.kaio.runtracker.entity.GeracaoPlanoStatus;
import com.kaio.runtracker.entity.TrainingPlan;
import com.kaio.runtracker.entity.SolicitacaoPlano;
import com.kaio.runtracker.entity.SolicitacaoPlanoStatus;
import com.kaio.runtracker.exception.PagamentoException;
import com.kaio.runtracker.repository.PagamentoRepository;
import com.kaio.runtracker.repository.SolicitacaoPlanoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PagamentoServiceTest {
    private PagamentoRepository repository;
    private MercadoPagoOrdersClient client;
    private PagamentoService service;
    private SolicitacaoPlanoRepository solicitacaoPlanoRepository;

    @BeforeEach
    void configurar() {
        repository = mock(PagamentoRepository.class);
        client = mock(MercadoPagoOrdersClient.class);
        solicitacaoPlanoRepository = mock(SolicitacaoPlanoRepository.class);
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setAccessToken("TEST_TOKEN");
        properties.setValorPlano(new BigDecimal("12.90"));
        properties.setExpiracaoPixMinutos(30);
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-20T15:00:00Z"),
                ZoneId.of("America/Sao_Paulo")
        );
        service = new PagamentoService(repository, client, properties, solicitacaoPlanoRepository, clock);
    }

    @Test
    void criaOrderPixPersisteCobrancaERetornaQrCode() {
        when(client.criarOrderPix(eq("cliente@email.com"), any(), any(),
                eq(new BigDecimal("12.90")))).thenReturn(orderPendente("QR-CODE", "BASE64"));
        when(repository.save(any(Pagamento.class))).thenAnswer(invocation -> {
            Pagamento pagamento = invocation.getArgument(0);
            pagamento.setId(1L);
            return pagamento;
        });

        CriarPagamentoPixResponseDTO response = service.criarPix(" Cliente@Email.com ");

        assertEquals(1L, response.pagamentoId());
        assertEquals(PagamentoStatus.PENDING, response.status());
        assertEquals(new BigDecimal("12.90"), response.valor());
        assertEquals("QR-CODE", response.pixCopiaCola());
        assertEquals("BASE64", response.qrCodeBase64());
        ArgumentCaptor<Pagamento> pagamentoCaptor = ArgumentCaptor.forClass(Pagamento.class);
        verify(repository).save(pagamentoCaptor.capture());
        assertEquals("cliente@email.com", pagamentoCaptor.getValue().getEmailPagador());
    }

    @Test
    void rejeitaRespostaSemQrCodeBase64ENaoPersiste() {
        when(client.criarOrderPix(any(), any(), any(), any()))
                .thenReturn(orderPendente("QR-CODE", ""));

        PagamentoException exception = assertThrows(
                PagamentoException.class,
                () -> service.criarPix("cliente@email.com")
        );

        assertTrue(exception.getMessage().contains("QR Code"));
        verify(repository, never()).save(any());
    }

    @Test
    void criaPixVinculadoASolicitacaoPersistida() {
        SolicitacaoPlano solicitacao = new SolicitacaoPlano();
        solicitacao.setId(7L);
        solicitacao.setEmail("cliente@email.com");
        solicitacao.setStatus(SolicitacaoPlanoStatus.PENDING);
        when(solicitacaoPlanoRepository.findById(7L)).thenReturn(Optional.of(solicitacao));
        when(client.criarOrderPix(eq("cliente@email.com"), any(), any(),
                eq(new BigDecimal("12.90")))).thenReturn(orderPendente("QR-CODE", "BASE64"));
        when(repository.save(any(Pagamento.class))).thenAnswer(invocation -> {
            Pagamento pagamento = invocation.getArgument(0);
            pagamento.setId(1L);
            return pagamento;
        });

        service.criarPix("cliente@email.com", 7L);

        ArgumentCaptor<Pagamento> captor = ArgumentCaptor.forClass(Pagamento.class);
        verify(repository).save(captor.capture());
        assertEquals(7L, captor.getValue().getSolicitacaoPlano().getId());
        assertEquals(SolicitacaoPlanoStatus.PAYMENT_PENDING, solicitacao.getStatus());
        verify(solicitacaoPlanoRepository).save(solicitacao);
    }

    @Test
    void consultaOrderAtualizaPagamentoComoAprovado() {
        Pagamento pagamento = pagamentoPendente();
        when(repository.findById(1L)).thenReturn(Optional.of(pagamento));
        when(client.consultarOrder("ORD123")).thenReturn(orderAprovada());
        when(repository.save(pagamento)).thenReturn(pagamento);

        PagamentoStatusResponseDTO response = service.consultarStatus(1L);

        assertEquals(PagamentoStatus.APPROVED, response.status());
        assertTrue(response.pago());
        assertFalse(response.expirado());
        assertEquals("accredited", response.statusDetail());
        assertTrue(pagamento.getPagoEm() != null);
    }

    @Test
    void retornaNotFoundSemConsultarMercadoPago() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        PagamentoException exception = assertThrows(
                PagamentoException.class,
                () -> service.consultarStatus(99L)
        );

        assertEquals(404, exception.getStatus().value());
        verify(client, never()).consultarOrder(any());
    }

    @Test
    void consultaOrderProcessingMapeiaComoProcessing() {
        Pagamento pagamento = pagamentoPendente();
        when(repository.findById(1L)).thenReturn(Optional.of(pagamento));
        when(client.consultarOrder("ORD123"))
                .thenReturn(order("processing", "in_process", "QR-CODE", "BASE64"));
        when(repository.save(pagamento)).thenReturn(pagamento);

        PagamentoStatusResponseDTO response = service.consultarStatus(1L);

        assertEquals(PagamentoStatus.PROCESSING, response.status());
        assertFalse(response.pago());
    }

    @Test
    void statusRemotoApprovedNaoETratadoComoPagamentoConcluido() {
        Pagamento pagamento = pagamentoPendente();
        when(repository.findById(1L)).thenReturn(Optional.of(pagamento));
        when(client.consultarOrder("ORD123"))
                .thenReturn(order("approved", "accredited", "QR-CODE", "BASE64"));
        when(repository.save(pagamento)).thenReturn(pagamento);

        PagamentoStatusResponseDTO response = service.consultarStatus(1L);

        assertEquals(PagamentoStatus.PROCESSING, response.status());
        assertFalse(response.pago());
    }

    @Test
    void webhookPendenteAtualizaPagamentoLocal() {
        Pagamento pagamento = pagamentoPendente();
        pagamento.setStatus(PagamentoStatus.PROCESSING);
        pagamento.setStatusDetail("in_process");
        when(client.consultarOrder("ORD123")).thenReturn(orderPendente("QR-CODE", "BASE64"));
        when(repository.findByExternalReference("EXT123")).thenReturn(Optional.of(pagamento));
        when(repository.save(pagamento)).thenReturn(pagamento);

        service.processarWebhookOrder("ORD123");

        assertEquals(PagamentoStatus.PENDING, pagamento.getStatus());
        assertEquals("waiting_transfer", pagamento.getStatusDetail());
        assertEquals(LocalDateTime.of(2026, 7, 20, 12, 0), pagamento.getAtualizadoEm());
        verify(repository).save(pagamento);
    }

    @Test
    void webhookAprovadoRegistraPagamento() {
        Pagamento pagamento = pagamentoPendente();
        when(client.consultarOrder("ORD123")).thenReturn(orderAprovada());
        when(repository.findByExternalReference("EXT123")).thenReturn(Optional.of(pagamento));
        when(repository.save(pagamento)).thenReturn(pagamento);

        Long pagamentoAprovadoId = service.processarWebhookOrder("ORD123");

        assertEquals(1L, pagamentoAprovadoId);
        assertEquals(PagamentoStatus.APPROVED, pagamento.getStatus());
        assertEquals("accredited", pagamento.getStatusDetail());
        assertEquals(LocalDateTime.of(2026, 7, 20, 12, 0), pagamento.getPagoEm());
        verify(repository).save(pagamento);
    }

    @Test
    void webhookDuplicadoNaoAlteraPagamentoAprovado() {
        Pagamento pagamento = pagamentoPendente();
        pagamento.setStatus(PagamentoStatus.APPROVED);
        pagamento.setStatusDetail("accredited");
        pagamento.setPagoEm(LocalDateTime.of(2026, 7, 20, 11, 45));
        when(client.consultarOrder("ORD123")).thenReturn(orderAprovada());
        when(repository.findByExternalReference("EXT123")).thenReturn(Optional.of(pagamento));

        Long resultado = service.processarWebhookOrder("ORD123");

        assertEquals(LocalDateTime.of(2026, 7, 20, 11, 45), pagamento.getPagoEm());
        assertNull(resultado);
        verify(repository, never()).save(any());
    }

    @Test
    void webhookDeOrderSemPagamentoLocalTerminaSemErro() {
        when(client.consultarOrder("ORD123")).thenReturn(orderAprovada());
        when(repository.findByExternalReference("EXT123")).thenReturn(Optional.empty());

        service.processarWebhookOrder("ORD123");

        verify(repository, never()).save(any());
    }

    @Test
    void webhookPropagaErroAoConsultarMercadoPago() {
        PagamentoException falhaApi = new PagamentoException(
                org.springframework.http.HttpStatus.BAD_GATEWAY, "Falha na API");
        when(client.consultarOrder("ORD123")).thenThrow(falhaApi);

        PagamentoException exception = assertThrows(
                PagamentoException.class,
                () -> service.processarWebhookOrder("ORD123")
        );

        assertEquals(falhaApi, exception);
        verify(repository, never()).findByExternalReference(any());
        verify(repository, never()).save(any());
    }

    @Test
    void consultaResultadoPendente() {
        Pagamento pagamento = pagamentoPendente();
        pagamento.setGeracaoStatus(GeracaoPlanoStatus.PENDING);
        when(repository.findById(1L)).thenReturn(Optional.of(pagamento));

        var resultado = service.consultarResultado(1L);

        assertEquals(PagamentoStatus.PENDING, resultado.pagamentoStatus());
        assertEquals(GeracaoPlanoStatus.PENDING, resultado.geracaoStatus());
        assertEquals(null, resultado.planoId());
    }

    @Test
    void consultaResultadoProcessando() {
        Pagamento pagamento = pagamentoPendente();
        pagamento.setStatus(PagamentoStatus.APPROVED);
        pagamento.setGeracaoStatus(GeracaoPlanoStatus.PROCESSING);
        when(repository.findById(1L)).thenReturn(Optional.of(pagamento));

        var resultado = service.consultarResultado(1L);

        assertEquals(GeracaoPlanoStatus.PROCESSING, resultado.geracaoStatus());
        assertEquals(null, resultado.planoId());
    }

    @Test
    void consultaResultadoConcluido() {
        Pagamento pagamento = pagamentoPendente();
        pagamento.setStatus(PagamentoStatus.APPROVED);
        pagamento.setGeracaoStatus(GeracaoPlanoStatus.COMPLETED);
        TrainingPlan plano = new TrainingPlan();
        plano.setId(10L);
        pagamento.setTrainingPlan(plano);
        when(repository.findById(1L)).thenReturn(Optional.of(pagamento));

        var resultado = service.consultarResultado(1L);

        assertEquals(GeracaoPlanoStatus.COMPLETED, resultado.geracaoStatus());
        assertEquals(10L, resultado.planoId());
        assertEquals(null, resultado.mensagem());
    }

    @Test
    void consultaResultadoComFalhaNaoExpoeDetalhesTecnicos() {
        Pagamento pagamento = pagamentoPendente();
        pagamento.setStatus(PagamentoStatus.APPROVED);
        pagamento.setGeracaoStatus(GeracaoPlanoStatus.FAILED);
        pagamento.setGeracaoMensagem("Timeout técnico interno");
        when(repository.findById(1L)).thenReturn(Optional.of(pagamento));

        var resultado = service.consultarResultado(1L);

        assertEquals(GeracaoPlanoStatus.FAILED, resultado.geracaoStatus());
        assertEquals("Não foi possível gerar o plano neste momento.", resultado.mensagem());
    }

    private MercadoPagoOrderResponse orderPendente(String qrCode, String base64) {
        return order("action_required", "waiting_transfer", qrCode, base64);
    }

    private MercadoPagoOrderResponse orderAprovada() {
        return order("processed", "accredited", "QR-CODE", "BASE64");
    }

    private MercadoPagoOrderResponse order(String status, String detail, String qrCode, String base64) {
        MercadoPagoOrderResponse.PaymentMethod metodo = new MercadoPagoOrderResponse.PaymentMethod(
                "pix", "bank_transfer", "https://ticket", qrCode, base64);
        MercadoPagoOrderResponse.Payment payment = new MercadoPagoOrderResponse.Payment(
                "PAY123", status, detail, "2026-07-20T12:30:00-03:00", metodo);
        return new MercadoPagoOrderResponse(
                "ORD123", status, detail, "EXT123",
                new MercadoPagoOrderResponse.Transactions(List.of(payment)));
    }

    private Pagamento pagamentoPendente() {
        Pagamento pagamento = new Pagamento();
        pagamento.setId(1L);
        pagamento.setOrderExternalId("ORD123");
        pagamento.setStatus(PagamentoStatus.PENDING);
        pagamento.setStatusDetail("waiting_transfer");
        pagamento.setDataExpiracao(LocalDateTime.of(2026, 7, 20, 13, 0));
        return pagamento;
    }
}
