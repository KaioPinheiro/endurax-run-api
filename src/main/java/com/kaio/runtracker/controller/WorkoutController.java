package com.kaio.runtracker.controller;

import com.kaio.runtracker.dto.WorkoutRequestDTO;
import com.kaio.runtracker.dto.WorkoutResponseDTO;
import com.kaio.runtracker.service.WorkoutService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/workouts")
@CrossOrigin(origins = "http://localhost:5173")
public class WorkoutController {

    private final WorkoutService workoutService;

    public WorkoutController(WorkoutService workoutService) {
        this.workoutService = workoutService;
    }

    @GetMapping
    public ResponseEntity<List<WorkoutResponseDTO>> listarWorkouts() {
        return ResponseEntity.ok(workoutService.listarTodos());
    }

    @GetMapping("/pendentes")
    public ResponseEntity<List<WorkoutResponseDTO>> listarPendentes() {
        return ResponseEntity.ok(workoutService.listarPendentes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkoutResponseDTO> buscarPorId(@PathVariable Long id) {

        WorkoutResponseDTO workout = workoutService.buscarPorId(id);

        if (workout == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(workout);
    }

    @PostMapping
    public ResponseEntity<WorkoutResponseDTO> salvarWorkout(
            @Valid @RequestBody WorkoutRequestDTO dto) {

        WorkoutResponseDTO workoutSalvo = workoutService.salvar(dto);

        return ResponseEntity.status(201).body(workoutSalvo);
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkoutResponseDTO> atualizarWorkout(
            @PathVariable Long id,
            @Valid @RequestBody WorkoutRequestDTO dto) {

        WorkoutResponseDTO workoutAtualizado =
                workoutService.atualizar(id, dto);

        if (workoutAtualizado == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(workoutAtualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarWorkout(@PathVariable Long id) {

        WorkoutResponseDTO workout =
                workoutService.buscarPorId(id);

        if (workout == null) {
            return ResponseEntity.notFound().build();
        }

        workoutService.deletar(id);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/concluir")
    public ResponseEntity<Void> concluirWorkout(@PathVariable Long id) {

        boolean concluido = workoutService.concluirWorkout(id);

        if (!concluido) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.status(201).build();
    }
}