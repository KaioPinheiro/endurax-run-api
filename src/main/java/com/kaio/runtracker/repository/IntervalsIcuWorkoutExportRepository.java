package com.kaio.runtracker.repository;

import com.kaio.runtracker.entity.IntervalsIcuWorkoutExport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IntervalsIcuWorkoutExportRepository
        extends JpaRepository<IntervalsIcuWorkoutExport, Long> {
    Optional<IntervalsIcuWorkoutExport> findByPagamentoIdAndWorkoutKey(
            Long pagamentoId, String workoutKey);
}
