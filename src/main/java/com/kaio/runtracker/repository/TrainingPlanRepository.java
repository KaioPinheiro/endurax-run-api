package com.kaio.runtracker.repository;

import com.kaio.runtracker.entity.TrainingPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, Long> {
    @Query("select p.trainingPlan from Pagamento p "
            + "where p.externalReference = :token "
            + "and p.status = com.kaio.runtracker.entity.PagamentoStatus.APPROVED "
            + "and p.geracaoStatus = com.kaio.runtracker.entity.GeracaoPlanoStatus.COMPLETED")
    Optional<TrainingPlan> findPlanoPagoByToken(String token);
}
