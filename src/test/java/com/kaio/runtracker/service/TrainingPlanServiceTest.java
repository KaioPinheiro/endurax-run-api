package com.kaio.runtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kaio.runtracker.entity.Pagamento;
import com.kaio.runtracker.entity.SolicitacaoPlano;
import com.kaio.runtracker.entity.TrainingPlan;
import com.kaio.runtracker.repository.PagamentoRepository;
import com.kaio.runtracker.repository.TrainingPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrainingPlanServiceTest {
    private PagamentoRepository pagamentoRepository;
    private TrainingPlanService service;

    @BeforeEach
    void configurar() {
        pagamentoRepository = mock(PagamentoRepository.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new TrainingPlanService(
                mock(TrainingPlanRepository.class), pagamentoRepository, objectMapper);
    }

    @Test
    void recuperaAgendamentoDoFormularioPersistidoSemAlterarPlano() {
        Pagamento pagamento = pagamentoComPlano();
        SolicitacaoPlano solicitacao = new SolicitacaoPlano();
        solicitacao.setDadosFormularioJson("""
                {"dataInicioPlano":"2026-10-21",
                 "diasDisponiveis":["segunda-feira","quarta-feira"]}
                """);
        pagamento.setSolicitacaoPlano(solicitacao);
        when(pagamentoRepository.findByValidPlanAccessToken("token"))
                .thenReturn(Optional.of(pagamento));

        var resposta = service.buscarPlanoPagoPorToken("token");

        assertEquals(LocalDate.of(2026, 10, 21), resposta.dataInicioPlano());
        assertEquals(List.of("segunda-feira", "quarta-feira"), resposta.diasDisponiveis());
        assertEquals("{}", resposta.descricao());
    }

    @Test
    void planoAntigoContinuaDisponivelSemDadosDeAgendamento() {
        Pagamento pagamento = pagamentoComPlano();
        SolicitacaoPlano solicitacao = new SolicitacaoPlano();
        solicitacao.setDadosFormularioJson("{\"diasDisponiveis\":[\"segunda-feira\"]}");
        pagamento.setSolicitacaoPlano(solicitacao);
        when(pagamentoRepository.findByValidPlanAccessToken("antigo"))
                .thenReturn(Optional.of(pagamento));

        var resposta = service.buscarPlanoPagoPorToken("antigo");

        assertNull(resposta.dataInicioPlano());
        assertEquals(List.of("segunda-feira"), resposta.diasDisponiveis());
    }

    @Test
    void formularioLegadoInvalidoNaoImpedeAbrirPlano() {
        Pagamento pagamento = pagamentoComPlano();
        SolicitacaoPlano solicitacao = new SolicitacaoPlano();
        solicitacao.setDadosFormularioJson("não é json");
        pagamento.setSolicitacaoPlano(solicitacao);
        when(pagamentoRepository.findByValidPlanAccessToken("legado"))
                .thenReturn(Optional.of(pagamento));

        var resposta = service.buscarPlanoPagoPorToken("legado");

        assertNull(resposta.dataInicioPlano());
        assertNull(resposta.diasDisponiveis());
    }

    private Pagamento pagamentoComPlano() {
        TrainingPlan plano = new TrainingPlan();
        plano.setNome("Plano");
        plano.setObjetivo("5 km");
        plano.setNivel("iniciante");
        plano.setDescricao("{}");
        Pagamento pagamento = new Pagamento();
        pagamento.setTrainingPlan(plano);
        return pagamento;
    }
}
