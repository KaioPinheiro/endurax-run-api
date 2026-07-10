package com.kaio.runtracker.controller;


import com.kaio.runtracker.dto.TrainingPlanRequestDTO;
import com.kaio.runtracker.dto.TrainingPlanResponseDTO;
import com.kaio.runtracker.service.TrainingPlanService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/training-plans")
public class TrainingPlanController {

    private final TrainingPlanService trainingPlanService;

    public TrainingPlanController(TrainingPlanService trainingPlanService) {
        this.trainingPlanService = trainingPlanService;
    }

    @GetMapping
    public ResponseEntity<List<TrainingPlanResponseDTO>> listarPlanos() {
        return ResponseEntity.ok(trainingPlanService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TrainingPlanResponseDTO> buscarPorId(@PathVariable Long id) {

        TrainingPlanResponseDTO plano = trainingPlanService.buscarPorId(id);

        if (plano == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(plano);
    }

    @PostMapping
    public ResponseEntity<TrainingPlanResponseDTO> salvarPlano(
            @Valid @RequestBody TrainingPlanRequestDTO dto) {

        TrainingPlanResponseDTO planoSalvo =
                trainingPlanService.salvar(dto);

        return ResponseEntity.status(201).body(planoSalvo);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TrainingPlanResponseDTO> atualizarPlano(
            @PathVariable Long id,
            @Valid @RequestBody TrainingPlanRequestDTO dto) {

        TrainingPlanResponseDTO planoAtualizado =
                trainingPlanService.atualizar(id, dto);

        if (planoAtualizado == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(planoAtualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarPlano(@PathVariable Long id) {

        TrainingPlanResponseDTO plano =
                trainingPlanService.buscarPorId(id);

        if (plano == null) {
            return ResponseEntity.notFound().build();
        }

        trainingPlanService.deletar(id);

        return ResponseEntity.noContent().build();
    }
}
