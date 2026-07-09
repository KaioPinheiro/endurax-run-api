package com.kaio.runtracker.repository;

import com.kaio.runtracker.entity.Treino;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TreinoRepository extends JpaRepository<Treino, Long> {

    List<Treino> findByUserEmail(String email);

    Optional<Treino> findByIdAndUserEmail(Long id, String email);
}
