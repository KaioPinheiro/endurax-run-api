package com.kaio.runtracker.service;

import com.kaio.runtracker.dto.WorkoutRequestDTO;
import com.kaio.runtracker.dto.WorkoutResponseDTO;
import com.kaio.runtracker.entity.TrainingPlan;
import com.kaio.runtracker.entity.Workout;
import com.kaio.runtracker.entity.Treino;
import com.kaio.runtracker.repository.TrainingPlanRepository;
import com.kaio.runtracker.repository.WorkoutRepository;
import com.kaio.runtracker.repository.TreinoRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class WorkoutService {

    private final WorkoutRepository repository;
    private final TrainingPlanRepository trainingPlanRepository;
    private final TreinoRepository treinoRepository;

    public WorkoutService(
            WorkoutRepository repository,
            TrainingPlanRepository trainingPlanRepository,
            TreinoRepository treinoRepository) {

        this.repository = repository;
        this.trainingPlanRepository = trainingPlanRepository;
        this.treinoRepository = treinoRepository;
    }

    public List<WorkoutResponseDTO> listarTodos() {
        return repository.findAll()
                .stream()
                .map(this::converterParaDTO)
                .toList();
    }

    public List<WorkoutResponseDTO> listarPendentes() {

        return repository.findByStatus("PENDENTE")
                .stream()
                .map(this::converterParaDTO)
                .toList();
    }

    public WorkoutResponseDTO buscarPorId(Long id) {
        return repository.findById(id)
                .map(this::converterParaDTO)
                .orElse(null);
    }

    public WorkoutResponseDTO salvar(WorkoutRequestDTO dto) {

        Workout workout = new Workout();

        workout.setTitulo(dto.getTitulo());
        workout.setTipo(dto.getTipo());
        workout.setDescricao(dto.getDescricao());
        workout.setDiaSemana(dto.getDiaSemana());
        workout.setDistanciaKm(dto.getDistanciaKm());
        workout.setPaceAlvo(dto.getPaceAlvo());
        workout.setObservacoes(dto.getObservacoes());

        workout.setStatus("PENDENTE");

        TrainingPlan trainingPlan =
                trainingPlanRepository.findById(dto.getTrainingPlanId())
                        .orElse(null);

        workout.setTrainingPlan(trainingPlan);

        Workout salvo = repository.save(workout);

        return converterParaDTO(salvo);
    }

    public WorkoutResponseDTO atualizar(Long id, WorkoutRequestDTO dto) {

        Workout workout = repository.findById(id).orElse(null);

        if (workout == null) {
            return null;
        }

        workout.setTitulo(dto.getTitulo());
        workout.setTipo(dto.getTipo());
        workout.setDescricao(dto.getDescricao());
        workout.setDiaSemana(dto.getDiaSemana());
        workout.setDistanciaKm(dto.getDistanciaKm());
        workout.setPaceAlvo(dto.getPaceAlvo());
        workout.setObservacoes(dto.getObservacoes());

        TrainingPlan trainingPlan =
                trainingPlanRepository.findById(dto.getTrainingPlanId())
                        .orElse(null);

        workout.setTrainingPlan(trainingPlan);

        Workout atualizado = repository.save(workout);

        return converterParaDTO(atualizado);
    }

    public void deletar(Long id) {
        repository.deleteById(id);
    }

    public boolean concluirWorkout(Long id) {

        Workout workout = repository.findById(id).orElse(null);

        if (workout == null) {
            return false;
        }

        Treino treino = new Treino();

        treino.setDataTreino(LocalDate.now());
        treino.setTipo(workout.getTipo());
        treino.setDistanciaKm(workout.getDistanciaKm());
        treino.setTempoMinutos(1);
        treino.setPaceMedio(workout.getPaceAlvo());
        treino.setObservacoes(
                "Treino concluído a partir do plano: "
                        + workout.getTitulo()
        );

        workout.setStatus("CONCLUIDO");
        repository.save(workout);

        treinoRepository.save(treino);

        return true;
    }

    private WorkoutResponseDTO converterParaDTO(Workout workout) {

        return new WorkoutResponseDTO(
                workout.getId(),
                workout.getTitulo(),
                workout.getTipo(),
                workout.getDescricao(),
                workout.getDiaSemana(),
                workout.getDistanciaKm(),
                workout.getPaceAlvo(),
                workout.getObservacoes(),
                workout.getStatus()
        );
    }
}