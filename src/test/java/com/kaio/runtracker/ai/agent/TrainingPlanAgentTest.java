package com.kaio.runtracker.ai.agent;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrainingPlanAgentTest {
    private final TrainingPlanGenerator generator = mock(TrainingPlanGenerator.class);
    private final TrainingPlanReviewer reviewer = mock(TrainingPlanReviewer.class);
    private final TrainingPlanValidator validator = mock(TrainingPlanValidator.class);
    private final AgentExecutionContext context = new AgentExecutionContext(
            new GerarPlanoTreinoRequestDTO(), 4, LocalDate.of(2026, 1, 5), "teste-1");
    private final Logger logger = (Logger) LoggerFactory.getLogger(TrainingPlanAgent.class);
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void capturarLogs() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void limparLogs() {
        logger.detachAppender(appender);
    }

    @Test
    void aprovaNaPrimeiraTentativa() {
        PlanoTreinoIAResponseDTO plano = new PlanoTreinoIAResponseDTO();
        when(generator.generate(context)).thenReturn(plano);
        when(validator.validate(plano, context)).thenReturn(ValidationResult.valid());
        when(reviewer.review(plano, context)).thenReturn(ReviewResult.approved());

        AgentExecutionResult resultado = agent(2).execute(context);

        assertSame(plano, resultado.plano());
        assertEquals(0, resultado.correcoesRealizadas());
        verify(generator, never()).correct(any(), any(), any(), any());
    }

    @Test
    void erroEncontradoSolicitaCorrecao() {
        PlanoTreinoIAResponseDTO original = new PlanoTreinoIAResponseDTO();
        PlanoTreinoIAResponseDTO corrigido = new PlanoTreinoIAResponseDTO();
        ValidationResult invalido = new ValidationResult(List.of("erro"), List.of());
        when(generator.generate(context)).thenReturn(original);
        when(validator.validate(original, context)).thenReturn(invalido);
        when(reviewer.review(original, context)).thenReturn(ReviewResult.approved());
        when(generator.correct(original, context, invalido, ReviewResult.approved()))
                .thenReturn(corrigido);
        when(validator.validate(corrigido, context)).thenReturn(ValidationResult.valid());
        when(reviewer.review(corrigido, context)).thenReturn(ReviewResult.approved());

        agent(2).execute(context);

        verify(generator).correct(original, context, invalido, ReviewResult.approved());
    }

    @Test
    void aprovaAposCorrecao() {
        PlanoTreinoIAResponseDTO original = new PlanoTreinoIAResponseDTO();
        PlanoTreinoIAResponseDTO corrigido = new PlanoTreinoIAResponseDTO();
        ValidationResult invalido = new ValidationResult(List.of("erro"), List.of());
        when(generator.generate(context)).thenReturn(original);
        when(validator.validate(original, context)).thenReturn(invalido);
        when(reviewer.review(original, context)).thenReturn(ReviewResult.approved());
        when(generator.correct(any(), any(), any(), any())).thenReturn(corrigido);
        when(validator.validate(corrigido, context)).thenReturn(ValidationResult.valid());
        when(reviewer.review(corrigido, context)).thenReturn(ReviewResult.approved());

        AgentExecutionResult resultado = agent(2).execute(context);

        assertSame(corrigido, resultado.plano());
        assertEquals(1, resultado.correcoesRealizadas());
    }

    @Test
    void falhaAposOLimiteConfigurado() {
        PlanoTreinoIAResponseDTO plano = new PlanoTreinoIAResponseDTO();
        ValidationResult invalido = new ValidationResult(List.of("erro persistente"), List.of());
        when(generator.generate(context)).thenReturn(plano);
        when(validator.validate(any(), any())).thenReturn(invalido);
        when(reviewer.review(any(), any())).thenReturn(ReviewResult.approved());
        when(generator.correct(any(), any(), any(), any())).thenReturn(plano);

        assertThrows(PlanoTreinoReprovadoException.class, () -> agent(2).execute(context));
        verify(generator, times(2)).correct(any(), any(), any(), any());
    }

    @Test
    void rejeitaEstruturaInvalidaDoGerador() {
        ValidationResult estruturaInvalida =
                new ValidationResult(List.of("Global: plano ausente"), List.of());
        when(generator.generate(context)).thenReturn(null);
        when(validator.validate(null, context)).thenReturn(estruturaInvalida);
        when(reviewer.review(null, context)).thenReturn(ReviewResult.approved());

        PlanoTreinoReprovadoException exception = assertThrows(
                PlanoTreinoReprovadoException.class,
                () -> agent(0).execute(context));

        assertTrue(exception.getErrors().contains("Global: plano ausente"));
    }

    @Test
    void revisorPodeReprovarPlanoDeterministicamenteValido() {
        PlanoTreinoIAResponseDTO plano = new PlanoTreinoIAResponseDTO();
        ReviewResult reprovado =
                new ReviewResult(false, List.of("progressão incoerente"), List.of(), "Reprovado");
        when(generator.generate(context)).thenReturn(plano);
        when(validator.validate(plano, context)).thenReturn(ValidationResult.valid());
        when(reviewer.review(plano, context)).thenReturn(reprovado);

        assertThrows(PlanoTreinoReprovadoException.class, () -> agent(0).execute(context));
    }

    @Test
    void reuneMultiplosErrosParaCorrecao() {
        PlanoTreinoIAResponseDTO plano = new PlanoTreinoIAResponseDTO();
        ValidationResult validacao = new ValidationResult(
                List.of("erro semanal", "erro global"), List.of("aviso java"));
        ReviewResult revisao = new ReviewResult(
                false, List.of("erro revisão"), List.of("aviso revisão"), "Reprovado");
        when(generator.generate(context)).thenReturn(plano);
        when(validator.validate(any(), any()))
                .thenReturn(validacao)
                .thenReturn(ValidationResult.valid());
        when(reviewer.review(any(), any()))
                .thenReturn(revisao)
                .thenReturn(ReviewResult.approved());
        when(generator.correct(any(), any(), any(), any())).thenReturn(plano);

        agent(1).execute(context);

        ArgumentCaptor<ValidationResult> validacaoCaptor =
                ArgumentCaptor.forClass(ValidationResult.class);
        ArgumentCaptor<ReviewResult> revisaoCaptor =
                ArgumentCaptor.forClass(ReviewResult.class);
        verify(generator).correct(
                any(), any(), validacaoCaptor.capture(), revisaoCaptor.capture());
        assertEquals(2, validacaoCaptor.getValue().getErrors().size());
        assertEquals(1, revisaoCaptor.getValue().errors().size());
    }

    @Test
    void registraApontamentosIndividualmenteComTentativaLimiteESanitizacaoSemAlterarResultado() {
        PlanoTreinoIAResponseDTO original = new PlanoTreinoIAResponseDTO();
        PlanoTreinoIAResponseDTO corrigido = new PlanoTreinoIAResponseDTO();
        corrigido.setResumo("PLANO_COMPLETO_NAO_DEVE_APARECER");
        ValidationResult invalido = new ValidationResult(List.of("forçar correção"), List.of());
        List<String> erros = new ArrayList<>(List.of(
                "Linha 1\nLinha 2 cliente@example.com 550e8400-e29b-41d4-a716-446655440000 "
                        + "Bearer credencial-secreta eyJhbGciOiJIUzI1NiJ9.cGF5bG9hZA.assinatura "
                        + "sk-12345678901234567890 token=segredo123",
                "x".repeat(350)));
        for (int indice = 3; indice <= 12; indice++) erros.add("erro-" + indice);
        ReviewResult reprovado = new ReviewResult(
                false,
                erros,
                List.of("warning um", "warning dois"),
                "SUMMARY_NAO_DEVE_APARECER");
        when(generator.generate(context)).thenReturn(original);
        when(validator.validate(original, context)).thenReturn(invalido);
        when(reviewer.review(original, context)).thenReturn(ReviewResult.approved());
        when(generator.correct(original, context, invalido, ReviewResult.approved()))
                .thenReturn(corrigido);
        when(validator.validate(corrigido, context)).thenReturn(ValidationResult.valid());
        when(reviewer.review(corrigido, context)).thenReturn(reprovado);

        PlanoTreinoReprovadoException exception = assertThrows(
                PlanoTreinoReprovadoException.class,
                () -> agent(1).execute(context));

        assertThat(exception.getErrors()).containsExactlyElementsOf(erros);
        String log = logCompleto();
        assertThat(log).contains(
                "id=teste-1, tentativa=1, severidade=ERROR, indice=1",
                "Linha 1 Linha 2 [REDACTED_EMAIL] [REDACTED_UUID]",
                "Bearer [REDACTED] [REDACTED_JWT] [REDACTED_API_KEY] [REDACTED_CREDENTIAL]",
                "severidade=ERROR, indice=2",
                "... [truncado]",
                "severidade=WARNING, indice=1, mensagem=warning um",
                "severidade=WARNING, indice=2, mensagem=warning dois",
                "severidade=ERROR, omitidos=2");
        assertThat(log).doesNotContain(
                "cliente@example.com",
                "550e8400-e29b-41d4-a716-446655440000",
                "credencial-secreta",
                "eyJhbGciOiJIUzI1NiJ9.cGF5bG9hZA.assinatura",
                "sk-12345678901234567890",
                "segredo123",
                "PLANO_COMPLETO_NAO_DEVE_APARECER",
                "SUMMARY_NAO_DEVE_APARECER");
    }

    private String logCompleto() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (acumulado, mensagem) -> acumulado + mensagem + "\n");
    }

    private TrainingPlanAgent agent(int tentativas) {
        return new TrainingPlanAgent(generator, reviewer, validator, tentativas);
    }
}
