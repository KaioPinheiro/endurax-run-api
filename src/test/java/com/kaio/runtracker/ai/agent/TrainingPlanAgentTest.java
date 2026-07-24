package com.kaio.runtracker.ai.agent;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

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

    private TrainingPlanAgent agent(int tentativas) {
        return new TrainingPlanAgent(generator, reviewer, validator, tentativas);
    }
}
