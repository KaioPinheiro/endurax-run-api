package com.kaio.runtracker.repository;

import com.kaio.runtracker.entity.IntervalsIcuOAuthState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface IntervalsIcuOAuthStateRepository extends JpaRepository<IntervalsIcuOAuthState, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from IntervalsIcuOAuthState s join fetch s.pagamento where s.stateHash = :stateHash")
    Optional<IntervalsIcuOAuthState> findByStateHashForUpdate(String stateHash);
}
