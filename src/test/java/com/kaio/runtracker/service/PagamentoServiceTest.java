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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import java.time.LocalDate;

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
        properties.setExpiracaoPixMinutos(15);
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
        assertTrue(response.acessoToken() != null && !response.acessoToken().isBlank());
        assertEquals(PagamentoStatus.PENDING, response.status());
        assertEquals(new BigDecimal("12.90"), response.valor());
        assertEquals("QR-CODE", response.pixCopiaCola());
        assertEquals("BASE64", response.qrCodeBase64());
        assertEquals(LocalDateTime.of(2026, 7, 20, 12, 15), response.dataExpiracao());
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
        SolicitacaoPlano solicitacao = solicitacaoValida(false, null);
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
    void bloqueiaPixQuandoSolicitacaoEnvelheceu() {
        SolicitacaoPlano solicitacao = solicitacaoValida(true, LocalDate.of(2026, 8, 2));
        when(solicitacaoPlanoRepository.findById(7L)).thenReturn(Optional.of(solicitacao));

        PagamentoException exception = assertThrows(
                PagamentoException.class,
                () -> service.criarPix("cliente@email.com", 7L));

        assertTrue(exception.getMessage().contains("14 dias"));
        verify(client, never()).criarOrderPix(any(), any(), any(), any());
    }

    @Test
    void bloqueiaPixQuandoDistanciaDaProvaPersistidaDivergeDaMeta() throws Exception {
        SolicitacaoPlano solicitacao = solicitacaoValida(true, LocalDate.of(2026, 8, 10));
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        GerarPlanoTreinoRequestDTO formulario = mapper.readValue(
                solicitacao.getDadosFormularioJson(), GerarPlanoTreinoRequestDTO.class);
        formulario.setDistanciaProva("5 km");
        solicitacao.setDadosFormularioJson(mapper.writeValueAsString(formulario));
        when(solicitacaoPlanoRepository.findById(7L)).thenReturn(Optional.of(solicitacao));

        PagamentoException exception = assertThrows(
                PagamentoException.class,
                () -> service.criarPix("cliente@email.com", 7L));

        assertTrue(exception.getMessage().contains("distância da prova"));
        verify(client, never()).criarOrderPix(any(), any(), any(), any());
    }

    @Test
    void recriarPixDaMesmaSolicitacaoEEmailRetornaMesmoTokenSemNovaCobranca() {
        Pagamento pagamento = pagamentoPendente();
        pagamento.setEmailPagador("cliente@email.com");
        when(repository.findBySolicitacaoPlanoId(7L)).thenReturn(Optional.of(pagamento));

        CriarPagamentoPixResponseDTO response = service.criarPix("cliente@email.com", 7L);

        assertEquals("EXT123", response.acessoToken());
        verify(client, never()).criarOrderPix(any(), any(), any(), any());
    }

    @Test
    void tokenInvalidoNaoVazaExistenciaDoPagamento() {
        when(repository.findPublicByExternalReference("invalido")).thenReturn(Optional.empty());

        PagamentoException exception = assertThrows(
                PagamentoException.class,
                () -> service.consultarResultadoPorToken("invalido"));

        assertEquals(404, exception.getStatus().value());
        assertEquals("Pagamento não encontrado.", exception.getMessage());
    }

    @Test
    void cancelaPagamentoPendenteSomenteDepoisDaConfirmacaoRemota() {
        Pagamento pagamento = pagamentoPendenteComSolicitacao();
        when(repository.findByExternalReference("EXT123")).thenReturn(Optional.of(pagamento));
        when(client.cancelarOrder(eq("ORD123"), any())).thenReturn(orderCancelada());

        service.cancelarPorToken("EXT123");

        assertEquals(PagamentoStatus.CANCELLED, pagamento.getStatus());
        assertEquals(SolicitacaoPlanoStatus.CANCELLED, pagamento.getSolicitacaoPlano().getStatus());
        verify(repository).save(pagamento);
    }

    @Test
    void pagamentoAprovadoNaoEhCancelado() {
        Pagamento pagamento = pagamentoPendenteComSolicitacao();
        pagamento.setStatus(PagamentoStatus.APPROVED);
        when(repository.findByExternalReference("EXT123")).thenReturn(Optional.of(pagamento));

        PagamentoException exception = assertThrows(
                PagamentoException.class, () -> service.cancelarPorToken("EXT123"));

        assertEquals(409, exception.getStatus().value());
        assertEquals(PagamentoStatus.APPROVED, pagamento.getStatus());
        verify(client, never()).cancelarOrder(any(), any());
    }

    @Test
    void cancelamentoRepetidoEhIdempotente() {
        Pagamento pagamento = pagamentoPendenteComSolicitacao();
        pagamento.setStatus(PagamentoStatus.CANCELLED);
        pagamento.getSolicitacaoPlano().setStatus(SolicitacaoPlanoStatus.CANCELLED);
        when(repository.findByExternalReference("EXT123")).thenReturn(Optional.of(pagamento));

        service.cancelarPorToken("EXT123");

        verify(client, never()).cancelarOrder(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void falhaRemotaNaoMarcaPagamentoComoCancelado() {
        Pagamento pagamento = pagamentoPendenteComSolicitacao();
        PagamentoException falha = new PagamentoException(
                org.springframework.http.HttpStatus.BAD_GATEWAY, "Falha no cancelamento");
        when(repository.findByExternalReference("EXT123")).thenReturn(Optional.of(pagamento));
        when(client.cancelarOrder(eq("ORD123"), any())).thenThrow(falha);
        when(client.consultarOrder("ORD123")).thenReturn(orderPendente("QR-CODE", "BASE64"));

        assertThrows(PagamentoException.class, () -> service.cancelarPorToken("EXT123"));

        assertEquals(PagamentoStatus.PENDING, pagamento.getStatus());
        assertEquals(SolicitacaoPlanoStatus.PAYMENT_PENDING, pagamento.getSolicitacaoPlano().getStatus());
        verify(repository, never()).save(any());
    }

    @Test
    void aprovacaoDetectadaNaCorridaVenceOCancelamento() {
        Pagamento pagamento = pagamentoPendenteComSolicitacao();
        PagamentoException rejeicao = new PagamentoException(
                org.springframework.http.HttpStatus.BAD_GATEWAY, "Order já processada");
        when(repository.findByExternalReference("EXT123")).thenReturn(Optional.of(pagamento));
        when(client.cancelarOrder(eq("ORD123"), any())).thenThrow(rejeicao);
        when(client.consultarOrder("ORD123")).thenReturn(orderAprovada());

        PagamentoException exception = assertThrows(
                PagamentoException.class, () -> service.cancelarPorToken("EXT123"));

        assertEquals(409, exception.getStatus().value());
        assertEquals(PagamentoStatus.APPROVED, pagamento.getStatus());
        assertEquals(SolicitacaoPlanoStatus.PAYMENT_PENDING, pagamento.getSolicitacaoPlano().getStatus());
        verify(repository).save(pagamento);
    }

    @Test
    void webhookAtrasadoNaoRessuscitaCancelamento() {
        Pagamento pagamento = pagamentoPendenteComSolicitacao();
        pagamento.setStatus(PagamentoStatus.CANCELLED);
        pagamento.getSolicitacaoPlano().setStatus(SolicitacaoPlanoStatus.CANCELLED);
        when(client.consultarOrder("ORD123")).thenReturn(orderPendente("QR-CODE", "BASE64"));
        when(repository.findByExternalReference("EXT123")).thenReturn(Optional.of(pagamento));

        Long pagamentoParaGerar = service.processarWebhookOrder("ORD123");

        assertNull(pagamentoParaGerar);
        assertEquals(PagamentoStatus.CANCELLED, pagamento.getStatus());
        assertEquals(SolicitacaoPlanoStatus.CANCELLED, pagamento.getSolicitacaoPlano().getStatus());
        verify(repository, never()).save(any());
    }

    @Test
    void reconciliacaoNaoRessuscitaCancelamento() {
        Pagamento pagamento = pagamentoPendenteComSolicitacao();
        pagamento.setStatus(PagamentoStatus.CANCELLED);
        pagamento.getSolicitacaoPlano().setStatus(SolicitacaoPlanoStatus.CANCELLED);
        when(repository.findPublicByExternalReference("EXT123")).thenReturn(Optional.of(pagamento));

        PagamentoStatusResponseDTO status = service.consultarStatusPorToken("EXT123");

        assertEquals(PagamentoStatus.CANCELLED, status.status());
        verify(client, never()).consultarOrder(any());
        verify(repository, never()).save(any());
    }

    @Test
    void resultadoPorTokenForneceMesmoTokenParaRecuperarPlano() {
        Pagamento pagamento = pagamentoPendente();
        pagamento.setStatus(PagamentoStatus.APPROVED);
        pagamento.setGeracaoStatus(GeracaoPlanoStatus.COMPLETED);
        pagamento.setTrainingPlan(new TrainingPlan());
        when(repository.findPublicByExternalReference("EXT123")).thenReturn(Optional.of(pagamento));

        var resultado = service.consultarResultadoPorToken("EXT123");

        assertEquals("EXT123", resultado.planoToken());
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

    private SolicitacaoPlano solicitacaoValida(boolean possuiProva, LocalDate dataProva) {
        try {
            GerarPlanoTreinoRequestDTO formulario = new GerarPlanoTreinoRequestDTO();
            formulario.setObjetivo("Melhorar tempo nos 10 km");
            formulario.setDistanciaAlvo("10 km");
            formulario.setPossuiProva(possuiProva);
            formulario.setDataProva(dataProva);
            formulario.setDistanciaProva(possuiProva ? "10 km" : null);
            formulario.setDiasDisponiveis(List.of("terça-feira", "quinta-feira", "sábado"));
            formulario.setIdade(30);
            formulario.setExperienciaCorrida("1 a 3 anos");
            formulario.setVolumeSemanalAtual("20-40 km");
            SolicitacaoPlano solicitacao = new SolicitacaoPlano();
            solicitacao.setId(7L);
            solicitacao.setEmail("cliente@email.com");
            solicitacao.setStatus(SolicitacaoPlanoStatus.PENDING);
            solicitacao.setDadosFormularioJson(
                    new ObjectMapper().findAndRegisterModules().writeValueAsString(formulario));
            return solicitacao;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
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
    void webhookDuplicadoNaoAlteraPagamentoAprovadoNemRepeteGeracaoConcluida() {
        Pagamento pagamento = pagamentoPendente();
        pagamento.setStatus(PagamentoStatus.APPROVED);
        pagamento.setStatusDetail("accredited");
        pagamento.setPagoEm(LocalDateTime.of(2026, 7, 20, 11, 45));
        pagamento.setGeracaoStatus(GeracaoPlanoStatus.COMPLETED);
        TrainingPlan plano = new TrainingPlan();
        plano.setId(10L);
        pagamento.setTrainingPlan(plano);
        when(client.consultarOrder("ORD123")).thenReturn(orderAprovada());
        when(repository.findByExternalReference("EXT123")).thenReturn(Optional.of(pagamento));

        Long resultado = service.processarWebhookOrder("ORD123");

        assertEquals(LocalDateTime.of(2026, 7, 20, 11, 45), pagamento.getPagoEm());
        assertNull(resultado);
        verify(repository, never()).save(any());
    }

    @Test
    void webhookDepoisDaReconciliacaoGaranteGeracaoSemRegravarEstadoFinanceiro() {
        Pagamento pagamento = pagamentoPendente();
        pagamento.setStatus(PagamentoStatus.APPROVED);
        pagamento.setStatusDetail("accredited");
        pagamento.setPagoEm(LocalDateTime.of(2026, 7, 20, 11, 45));
        pagamento.setGeracaoStatus(GeracaoPlanoStatus.PENDING);
        when(client.consultarOrder("ORD123")).thenReturn(orderAprovada());
        when(repository.findByExternalReference("EXT123")).thenReturn(Optional.of(pagamento));

        Long resultado = service.processarWebhookOrder("ORD123");

        assertEquals(1L, resultado);
        assertEquals(PagamentoStatus.APPROVED, pagamento.getStatus());
        assertEquals(LocalDateTime.of(2026, 7, 20, 11, 45), pagamento.getPagoEm());
        verify(repository, never()).save(any());
    }

    @Test
    void webhookNaoRepeteGeracaoQuandoJaEstaProcessando() {
        Pagamento pagamento = pagamentoPendente();
        pagamento.setStatus(PagamentoStatus.APPROVED);
        pagamento.setStatusDetail("accredited");
        pagamento.setGeracaoStatus(GeracaoPlanoStatus.PROCESSING);
        when(client.consultarOrder("ORD123")).thenReturn(orderAprovada());
        when(repository.findByExternalReference("EXT123")).thenReturn(Optional.of(pagamento));

        assertNull(service.processarWebhookOrder("ORD123"));
        verify(repository, never()).save(any());
    }

    @Test
    void reconciliacaoDetectaAprovacaoESinalizaGeracao() {
        Pagamento pagamento = pagamentoPendente();
        when(repository.findById(1L)).thenReturn(Optional.of(pagamento));
        when(client.consultarOrder("ORD123")).thenReturn(orderAprovada());
        when(repository.save(pagamento)).thenReturn(pagamento);

        PagamentoStatusResponseDTO status = service.consultarStatus(1L);

        assertEquals(PagamentoStatus.APPROVED, status.status());
        assertEquals(GeracaoPlanoStatus.PENDING, pagamento.getGeracaoStatus());
        assertEquals(1L, service.pagamentoPendenteDeGeracao(1L));
    }

    @Test
    void reconciliacaoNaoSinalizaGeracaoQuandoJaFoiIniciada() {
        Pagamento pagamento = pagamentoPendente();
        pagamento.setStatus(PagamentoStatus.APPROVED);
        pagamento.setGeracaoStatus(GeracaoPlanoStatus.PROCESSING);
        when(repository.findById(1L)).thenReturn(Optional.of(pagamento));

        assertNull(service.pagamentoPendenteDeGeracao(1L));
    }

    @Test
    void pagamentoAprovadoNaoRegrideParaExpiradoNaReconciliacao() {
        Pagamento pagamento = pagamentoPendente();
        pagamento.setStatus(PagamentoStatus.APPROVED);
        pagamento.setStatusDetail("accredited");
        pagamento.setPagoEm(LocalDateTime.of(2026, 7, 20, 11, 45));
        pagamento.setDataExpiracao(LocalDateTime.of(2026, 7, 20, 11, 0));
        when(repository.findById(1L)).thenReturn(Optional.of(pagamento));

        PagamentoStatusResponseDTO status = service.consultarStatus(1L);

        assertEquals(PagamentoStatus.APPROVED, status.status());
        assertTrue(status.pago());
        assertFalse(status.expirado());
        verify(client, never()).consultarOrder(any());
        verify(repository, never()).save(any());
    }

    @Test
    void resultadoPersistidoEhLidoMesmoComMercadoPagoIndisponivel() {
        Pagamento pagamento = pagamentoPendente();
        pagamento.setStatus(PagamentoStatus.APPROVED);
        pagamento.setGeracaoStatus(GeracaoPlanoStatus.COMPLETED);
        TrainingPlan plano = new TrainingPlan();
        plano.setId(10L);
        pagamento.setTrainingPlan(plano);
        when(repository.findById(1L)).thenReturn(Optional.of(pagamento));
        when(client.consultarOrder(any())).thenThrow(new PagamentoException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Provedor indisponível"));

        var resultado = service.consultarResultado(1L);

        assertEquals(GeracaoPlanoStatus.COMPLETED, resultado.geracaoStatus());
        assertEquals("EXT123", resultado.planoToken());
        verify(client, never()).consultarOrder(any());
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

        assertEquals(1L, resultado.pagamentoId());
        assertEquals(PagamentoStatus.PENDING, resultado.pagamentoStatus());
        assertEquals(GeracaoPlanoStatus.PENDING, resultado.geracaoStatus());
        assertEquals(null, resultado.planoToken());
        assertEquals(new BigDecimal("12.90"), resultado.valor());
        assertEquals("QR-CODE", resultado.pixCopiaCola());
        assertEquals("BASE64", resultado.qrCodeBase64());
        assertEquals(LocalDateTime.of(2026, 7, 20, 13, 0), resultado.dataExpiracao());
    }

    @Test
    void recuperaPagamentoPelaSolicitacaoComDadosDoPix() {
        Pagamento pagamento = pagamentoPendente();
        when(repository.findBySolicitacaoPlanoId(7L)).thenReturn(Optional.of(pagamento));

        var resultado = service.consultarPorSolicitacao(7L);

        assertEquals(1L, resultado.pagamentoId());
        assertEquals("QR-CODE", resultado.pixCopiaCola());
        assertEquals("BASE64", resultado.qrCodeBase64());
        assertEquals(new BigDecimal("12.90"), resultado.valor());
    }

    @Test
    void consultaResultadoProcessando() {
        Pagamento pagamento = pagamentoPendente();
        pagamento.setStatus(PagamentoStatus.APPROVED);
        pagamento.setGeracaoStatus(GeracaoPlanoStatus.PROCESSING);
        when(repository.findById(1L)).thenReturn(Optional.of(pagamento));

        var resultado = service.consultarResultado(1L);

        assertEquals(GeracaoPlanoStatus.PROCESSING, resultado.geracaoStatus());
        assertEquals(null, resultado.planoToken());
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
        assertEquals("EXT123", resultado.planoToken());
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

    private MercadoPagoOrderResponse orderCancelada() {
        return order("canceled", "canceled_transaction", "QR-CODE", "BASE64");
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
        pagamento.setExternalReference("EXT123");
        pagamento.setStatus(PagamentoStatus.PENDING);
        pagamento.setStatusDetail("waiting_transfer");
        pagamento.setValor(new BigDecimal("12.90"));
        pagamento.setPixCopiaCola("QR-CODE");
        pagamento.setQrCodeBase64("BASE64");
        pagamento.setTicketUrl("https://ticket");
        pagamento.setDataExpiracao(LocalDateTime.of(2026, 7, 20, 13, 0));
        return pagamento;
    }

    private Pagamento pagamentoPendenteComSolicitacao() {
        Pagamento pagamento = pagamentoPendente();
        SolicitacaoPlano solicitacao = new SolicitacaoPlano();
        solicitacao.setStatus(SolicitacaoPlanoStatus.PAYMENT_PENDING);
        pagamento.setSolicitacaoPlano(solicitacao);
        return pagamento;
    }
}
