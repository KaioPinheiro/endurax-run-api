package com.kaio.runtracker.repository;

import com.kaio.runtracker.entity.IntervalsIcuConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IntervalsIcuConnectionRepository extends JpaRepository<IntervalsIcuConnection, Long> {
    Optional<IntervalsIcuConnection> findByPagamentoId(Long pagamentoId);
}
