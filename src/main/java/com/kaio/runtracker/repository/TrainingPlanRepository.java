package com.kaio.runtracker.repository;

import com.kaio.runtracker.entity.TrainingPlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, Long> {
}