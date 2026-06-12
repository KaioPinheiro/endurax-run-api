package com.kaio.runtracker.repository;

import com.kaio.runtracker.entity.Workout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkoutRepository extends JpaRepository<Workout, Long> {

    List<Workout> findByTrainingPlanId(Long trainingPlanId);

    List<Workout> findByStatus(String status);
}