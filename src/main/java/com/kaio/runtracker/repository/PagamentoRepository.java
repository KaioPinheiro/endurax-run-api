package com.kaio.runtracker.repository;

import com.kaio.runtracker.entity.Pagamento;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {
    Optional<Pagamento> findBySolicitacaoPlanoId(Long solicitacaoPlanoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Pagamento> findByExternalReference(String externalReference);

    @org.springframework.data.jpa.repository.Query(
            "select p from Pagamento p where p.externalReference = :externalReference")
    Optional<Pagamento> findPublicByExternalReference(String externalReference);

    @org.springframework.data.jpa.repository.Query("select p from Pagamento p "
            + "where p.externalReference = :token "
            + "and p.status = com.kaio.runtracker.entity.PagamentoStatus.APPROVED "
            + "and p.geracaoStatus = com.kaio.runtracker.entity.GeracaoPlanoStatus.COMPLETED "
            + "and p.trainingPlan is not null")
    Optional<Pagamento> findByValidPlanAccessToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Pagamento p "
            + "where p.externalReference = :token "
            + "and p.status = com.kaio.runtracker.entity.PagamentoStatus.APPROVED "
            + "and p.geracaoStatus = com.kaio.runtracker.entity.GeracaoPlanoStatus.COMPLETED "
            + "and p.trainingPlan is not null")
    Optional<Pagamento> findByValidPlanAccessTokenForUpdate(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Pagamento p where p.id = :id")
    Optional<Pagamento> findByIdForUpdate(Long id);
}
