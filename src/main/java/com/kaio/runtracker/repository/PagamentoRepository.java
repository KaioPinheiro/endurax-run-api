package com.kaio.runtracker.repository;

import com.kaio.runtracker.entity.Pagamento;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Pagamento> findByExternalReference(String externalReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Pagamento p where p.id = :id")
    Optional<Pagamento> findByIdForUpdate(Long id);
}
