package com.kaio.runtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.entity.GeracaoPlanoStatus;
import com.kaio.runtracker.entity.Pagamento;
import com.kaio.runtracker.entity.PagamentoStatus;
import com.kaio.runtracker.entity.SolicitacaoPlano;
import com.kaio.runtracker.entity.SolicitacaoPlanoStatus;
import com.kaio.runtracker.entity.TrainingPlan;
import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import com.kaio.runtracker.repository.PagamentoRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeracaoPlanoTransacaoServiceTest {

    @Test
    void falhaMantemPagamentoAprovadoEDefineGeracaoFailed() {
        PagamentoRepository pagamentoRepository = mock(PagamentoRepository.class);
        TrainingPlanService trainingPlanService = mock(TrainingPlanService.class);
        GeracaoPlanoTransacaoService service = new GeracaoPlanoTransacaoService(
                pagamentoRepository, trainingPlanService, new ObjectMapper());
        Pagamento pagamento = new Pagamento();
        pagamento.setId(1L);
        pagamento.setStatus(PagamentoStatus.APPROVED);
        pagamento.setGeracaoStatus(GeracaoPlanoStatus.PROCESSING);
        SolicitacaoPlano solicitacao = new SolicitacaoPlano();
        solicitacao.setStatus(SolicitacaoPlanoStatus.PROCESSING);
        pagamento.setSolicitacaoPlano(solicitacao);
        when(pagamentoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pagamento));

        service.falhar(1L);

        assertEquals(PagamentoStatus.APPROVED, pagamento.getStatus());
        assertEquals(GeracaoPlanoStatus.FAILED, pagamento.getGeracaoStatus());
        assertEquals(SolicitacaoPlanoStatus.FAILED, solicitacao.getStatus());
        verify(pagamentoRepository).save(pagamento);
    }

    @Test
    void concluiEVinculaUmUnicoPlanoAoPagamento() throws Exception {
        PagamentoRepository pagamentoRepository = mock(PagamentoRepository.class);
        TrainingPlanService trainingPlanService = mock(TrainingPlanService.class);
        GeracaoPlanoTransacaoService service = new GeracaoPlanoTransacaoService(
                pagamentoRepository, trainingPlanService, new ObjectMapper());
        Pagamento pagamento = pagamentoAprovadoComSolicitacao();
        pagamento.setGeracaoStatus(GeracaoPlanoStatus.PROCESSING);
        when(pagamentoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pagamento));
        TrainingPlan plano = new TrainingPlan();
        plano.setId(10L);
        GerarPlanoTreinoRequestDTO formulario = new GerarPlanoTreinoRequestDTO();
        PlanoTreinoIAResponseDTO resposta = new PlanoTreinoIAResponseDTO();
        var contexto = new GeracaoPlanoTransacaoService.GeracaoContexto(1L, formulario);
        when(trainingPlanService.salvarPlanoGerado(formulario, resposta)).thenReturn(plano);

        Long planoId = service.concluir(contexto, resposta);

        assertEquals(10L, planoId);
        assertEquals(plano, pagamento.getTrainingPlan());
        assertTrue(pagamento.isPlanoGerado());
        assertEquals(GeracaoPlanoStatus.COMPLETED, pagamento.getGeracaoStatus());
        assertEquals(SolicitacaoPlanoStatus.COMPLETED, pagamento.getSolicitacaoPlano().getStatus());
        verify(pagamentoRepository).save(pagamento);
    }

    @Test
    void reservaPermiteNovaTentativaDepoisDeFailed() throws Exception {
        PagamentoRepository pagamentoRepository = mock(PagamentoRepository.class);
        TrainingPlanService trainingPlanService = mock(TrainingPlanService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        GeracaoPlanoTransacaoService service = new GeracaoPlanoTransacaoService(
                pagamentoRepository, trainingPlanService, objectMapper);
        Pagamento pagamento = pagamentoAprovadoComSolicitacao();
        pagamento.setGeracaoStatus(GeracaoPlanoStatus.FAILED);
        pagamento.getSolicitacaoPlano().setDadosFormularioJson(
                objectMapper.writeValueAsString(new GerarPlanoTreinoRequestDTO()));
        when(pagamentoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pagamento));

        var reserva = service.reservar(1L);

        assertTrue(reserva.isPresent());
        assertEquals(GeracaoPlanoStatus.PROCESSING, pagamento.getGeracaoStatus());
        assertEquals(SolicitacaoPlanoStatus.PROCESSING, pagamento.getSolicitacaoPlano().getStatus());
    }

    private Pagamento pagamentoAprovadoComSolicitacao() {
        Pagamento pagamento = new Pagamento();
        pagamento.setId(1L);
        pagamento.setStatus(PagamentoStatus.APPROVED);
        SolicitacaoPlano solicitacao = new SolicitacaoPlano();
        solicitacao.setStatus(SolicitacaoPlanoStatus.PROCESSING);
        pagamento.setSolicitacaoPlano(solicitacao);
        return pagamento;
    }
}
