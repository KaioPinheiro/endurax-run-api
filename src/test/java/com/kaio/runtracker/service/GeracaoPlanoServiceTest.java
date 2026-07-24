package com.kaio.runtracker.service;

import com.kaio.runtracker.ai.agent.AgentExecutionContext;
import com.kaio.runtracker.ai.agent.AgentExecutionResult;
import com.kaio.runtracker.ai.agent.PlanoTreinoDuracaoCalculator;
import com.kaio.runtracker.ai.agent.ReviewResult;
import com.kaio.runtracker.ai.agent.TrainingPlanAgent;
import com.kaio.runtracker.ai.agent.ValidationResult;
import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeracaoPlanoServiceTest {
    private final GeracaoPlanoTransacaoService transacaoService = mock(GeracaoPlanoTransacaoService.class);
    private final TrainingPlanAgent agent = mock(TrainingPlanAgent.class);
    private final PlanoTreinoDuracaoCalculator duracaoCalculator =
            mock(PlanoTreinoDuracaoCalculator.class);
    private final GeracaoPlanoService service =
            new GeracaoPlanoService(transacaoService, agent, duracaoCalculator);

    @Test
    void pagamentoAprovadoGeraUmPlano() {
        var contexto = contexto();
        PlanoTreinoIAResponseDTO plano = new PlanoTreinoIAResponseDTO();
        when(transacaoService.reservar(1L)).thenReturn(Optional.of(contexto));
        prepararAgente(contexto, plano);
        when(transacaoService.concluir(contexto, plano)).thenReturn(10L);

        service.gerar(1L);

        verify(agent).execute(any(AgentExecutionContext.class));
        verify(transacaoService).concluir(contexto, plano);
        verify(transacaoService, never()).falhar(1L);
    }

    @Test
    void pagamentoJaProcessadoNaoDisparaNovamente() {
        when(transacaoService.reservar(1L)).thenReturn(Optional.empty());

        service.gerar(1L);

        verify(agent, never()).execute(any());
        verify(transacaoService, never()).concluir(any(), any());
    }

    @Test
    void falhaDaOpenAiMarcaGeracaoParaFalha() {
        var contexto = contexto();
        when(transacaoService.reservar(1L)).thenReturn(Optional.of(contexto));
        prepararContexto(contexto);
        doThrow(new GerarTreinoIAException(
                org.springframework.http.HttpStatus.BAD_GATEWAY, "Serviço indisponível"))
                .when(agent).execute(any(AgentExecutionContext.class));

        service.gerar(1L);

        verify(transacaoService).falhar(1L);
        verify(transacaoService, never()).concluir(any(), any());
    }

    @Test
    void novaTentativaAposFalhaPodeConcluir() {
        var contexto = contexto();
        PlanoTreinoIAResponseDTO plano = new PlanoTreinoIAResponseDTO();
        when(transacaoService.reservar(1L))
                .thenReturn(Optional.of(contexto))
                .thenReturn(Optional.of(contexto));
        prepararContexto(contexto);
        when(agent.execute(any(AgentExecutionContext.class)))
                .thenThrow(new RuntimeException("falha transitória"))
                .thenReturn(resultado(plano));
        when(transacaoService.concluir(contexto, plano)).thenReturn(10L);

        service.gerar(1L);
        service.gerar(1L);

        verify(transacaoService).falhar(1L);
        verify(agent, times(2)).execute(any(AgentExecutionContext.class));
        verify(transacaoService).concluir(contexto, plano);
    }

    @Test
    void chamadasConcorrentesReservamEGeramNoMaximoUmaVez() throws Exception {
        var contexto = contexto();
        PlanoTreinoIAResponseDTO plano = new PlanoTreinoIAResponseDTO();
        AtomicBoolean reservada = new AtomicBoolean();
        when(transacaoService.reservar(1L)).thenAnswer(invocation ->
                reservada.compareAndSet(false, true) ? Optional.of(contexto) : Optional.empty());
        prepararAgente(contexto, plano);
        when(transacaoService.concluir(contexto, plano)).thenReturn(10L);
        CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> aguardarEGerar(inicio));
        executor.submit(() -> aguardarEGerar(inicio));
        inicio.countDown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        verify(agent, times(1)).execute(any(AgentExecutionContext.class));
        verify(transacaoService, times(1)).concluir(contexto, plano);
    }

    private void aguardarEGerar(CountDownLatch inicio) {
        try {
            inicio.await();
            service.gerar(1L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private GeracaoPlanoTransacaoService.GeracaoContexto contexto() {
        return new GeracaoPlanoTransacaoService.GeracaoContexto(1L, new GerarPlanoTreinoRequestDTO());
    }

    private void prepararAgente(
            GeracaoPlanoTransacaoService.GeracaoContexto contexto,
            PlanoTreinoIAResponseDTO plano) {
        prepararContexto(contexto);
        when(agent.execute(any(AgentExecutionContext.class))).thenReturn(resultado(plano));
    }

    private void prepararContexto(
            GeracaoPlanoTransacaoService.GeracaoContexto contexto) {
        when(duracaoCalculator.calcular(contexto.formulario())).thenReturn(4);
        when(duracaoCalculator.hoje()).thenReturn(LocalDate.of(2026, 1, 5));
    }

    private AgentExecutionResult resultado(PlanoTreinoIAResponseDTO plano) {
        return new AgentExecutionResult(
                plano, 0, ValidationResult.valid(), ReviewResult.approved());
    }
}
