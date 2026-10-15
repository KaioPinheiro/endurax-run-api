package com.kaio.runtracker.fixture;

import com.kaio.runtracker.entity.GeracaoPlanoStatus;
import com.kaio.runtracker.entity.Pagamento;
import com.kaio.runtracker.entity.PagamentoStatus;
import com.kaio.runtracker.entity.TrainingPlan;
import com.kaio.runtracker.repository.PagamentoRepository;
import com.kaio.runtracker.repository.TrainingPlanRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public final class IntervalsIcuOAuthFixture {
    private final TrainingPlanRepository trainingPlanRepository;
    private final PagamentoRepository pagamentoRepository;

    public IntervalsIcuOAuthFixture(
            TrainingPlanRepository trainingPlanRepository,
            PagamentoRepository pagamentoRepository) {
        this.trainingPlanRepository = trainingPlanRepository;
        this.pagamentoRepository = pagamentoRepository;
    }

    public Scenario approvedCompletedPlan() {
        String suffix = UUID.randomUUID().toString();

        TrainingPlan plan = new TrainingPlan();
        plan.setNome("Plano ficticio para OAuth local");
        plan.setObjetivo("Validar conexao com Intervals.icu");
        plan.setNivel("TESTE");
        plan.setDescricao("Fixture isolada; nao representa um plano real.");
        trainingPlanRepository.saveAndFlush(plan);

        Pagamento payment = new Pagamento();
        payment.setOrderExternalId("oauth-fixture-order-" + suffix);
        payment.setExternalReference("oauth-fixture-plan-" + suffix);
        payment.setIdempotencyKey("oauth-fixture-idempotency-" + suffix);
        payment.setStatus(PagamentoStatus.APPROVED);
        payment.setStatusDetail("fixture-local");
        payment.setValor(new BigDecimal("12.90"));
        payment.setEmailPagador("oauth-fixture@example.invalid");
        payment.setPixCopiaCola("fixture-sem-pagamento-real");
        payment.setQrCodeBase64("fixture-sem-qrcode-real");
        payment.setDataExpiracao(LocalDateTime.now().plusHours(1));
        payment.setPagoEm(LocalDateTime.now());
        payment.setPlanoGerado(true);
        payment.setGeracaoStatus(GeracaoPlanoStatus.COMPLETED);
        payment.setTrainingPlan(plan);
        pagamentoRepository.saveAndFlush(payment);

        return new Scenario(payment, plan, payment.getExternalReference());
    }

    public record Scenario(Pagamento payment, TrainingPlan plan, String planAccessToken) {}
}
