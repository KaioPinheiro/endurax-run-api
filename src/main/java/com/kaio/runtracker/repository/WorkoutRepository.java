package com.kaio.runtracker.repository;

import com.kaio.runtracker.entity.Workout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkoutRepository extends JpaRepository<Workout, Long> {

    List<Workout> findByTrainingPlanId(Long trainingPlanId);

    List<Workout> findByStatus(String status);

    List<Workout> findByUserEmail(String email);

    List<Workout> findByUserEmailAndStatus(String email, String status);

    Optional<Workout> findByIdAndUserEmail(Long id, String email);
}
