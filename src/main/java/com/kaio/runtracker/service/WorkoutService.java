package com.kaio.runtracker.service;

import com.kaio.runtracker.dto.WorkoutRequestDTO;
import com.kaio.runtracker.dto.WorkoutResponseDTO;
import com.kaio.runtracker.entity.TrainingPlan;
import com.kaio.runtracker.entity.Workout;
import com.kaio.runtracker.entity.Treino;
import com.kaio.runtracker.entity.User;
import com.kaio.runtracker.repository.TrainingPlanRepository;
import com.kaio.runtracker.repository.WorkoutRepository;
import com.kaio.runtracker.repository.TreinoRepository;
import com.kaio.runtracker.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class WorkoutService {

    private final WorkoutRepository repository;
    private final TrainingPlanRepository trainingPlanRepository;
    private final TreinoRepository treinoRepository;
    private final UserRepository userRepository;

    public WorkoutService(
            WorkoutRepository repository,
            TrainingPlanRepository trainingPlanRepository,
            TreinoRepository treinoRepository,
            UserRepository userRepository) {

        this.repository = repository;
        this.trainingPlanRepository = trainingPlanRepository;
        this.treinoRepository = treinoRepository;
        this.userRepository = userRepository;
    }

    public List<WorkoutResponseDTO> listarTodos() {
        User usuarioLogado = getUsuarioLogado();

        return repository.findByUserEmail(usuarioLogado.getEmail())
                .stream()
                .map(this::converterParaDTO)
                .toList();
    }

    public List<WorkoutResponseDTO> listarPendentes() {
        User usuarioLogado = getUsuarioLogado();

        return repository.findByUserEmailAndStatus(usuarioLogado.getEmail(), "PENDENTE")
                .stream()
                .map(this::converterParaDTO)
                .toList();
    }

    public WorkoutResponseDTO buscarPorId(Long id) {
        User usuarioLogado = getUsuarioLogado();

        return repository.findByIdAndUserEmail(id, usuarioLogado.getEmail())
                .map(this::converterParaDTO)
                .orElse(null);
    }

    public WorkoutResponseDTO salvar(WorkoutRequestDTO dto) {

        User usuarioLogado = getUsuarioLogado();
        Workout workout = new Workout();

        workout.setTitulo(dto.getTitulo());
        workout.setTipo(dto.getTipo());
        workout.setDescricao(dto.getDescricao());
        workout.setDiaSemana(dto.getDiaSemana());
        workout.setDataPlanejada(dto.getDataPlanejada());
        workout.setDistanciaKm(dto.getDistanciaKm());
        workout.setPaceAlvo(dto.getPaceAlvo());
        workout.setObservacoes(dto.getObservacoes());

        workout.setStatus("PENDENTE");

        TrainingPlan trainingPlan =
                trainingPlanRepository.findById(dto.getTrainingPlanId())
                        .orElse(null);

        workout.setTrainingPlan(trainingPlan);
        workout.setUser(usuarioLogado);

        Workout salvo = repository.save(workout);

        return converterParaDTO(salvo);
    }

    public WorkoutResponseDTO atualizar(Long id, WorkoutRequestDTO dto) {

        User usuarioLogado = getUsuarioLogado();
        Workout workout = repository.findByIdAndUserEmail(id, usuarioLogado.getEmail()).orElse(null);

        if (workout == null) {
            return null;
        }

        workout.setTitulo(dto.getTitulo());
        workout.setTipo(dto.getTipo());
        workout.setDescricao(dto.getDescricao());
        workout.setDiaSemana(dto.getDiaSemana());
        workout.setDataPlanejada(dto.getDataPlanejada());
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
        User usuarioLogado = getUsuarioLogado();
        repository.findByIdAndUserEmail(id, usuarioLogado.getEmail())
                .ifPresent(repository::delete);
    }

    public boolean concluirWorkout(Long id) {

        User usuarioLogado = getUsuarioLogado();
        Workout workout = repository.findByIdAndUserEmail(id, usuarioLogado.getEmail()).orElse(null);

        if (workout == null) {
            return false;
        }

        Treino treino = new Treino();

        treino.setDataTreino(
                workout.getDataPlanejada() != null
                        ? workout.getDataPlanejada()
                        : LocalDate.now()
        );
        treino.setTipo(workout.getTipo());
        treino.setDistanciaKm(workout.getDistanciaKm());
        treino.setTempoMinutos(
                calcularTempoMinutos(
                        workout.getDistanciaKm(),
                        workout.getPaceAlvo()
                )
        );
        treino.setPaceMedio(workout.getPaceAlvo());
        treino.setObservacoes(
                "Treino concluído a partir do plano: "
                        + workout.getTitulo()
        );

        treino.setUser(usuarioLogado);

        workout.setStatus("CONCLUIDO");
        repository.save(workout);

        treinoRepository.save(treino);

        return true;
    }

    private User getUsuarioLogado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED));
    }

    private int calcularTempoMinutos(Double distanciaKm, String paceAlvo) {
        if (distanciaKm == null || paceAlvo == null || paceAlvo.isBlank()) {
            return 0;
        }

        try {
            String paceLimpo = paceAlvo.replace("/km", "").trim();
            String[] partes = paceLimpo.split(":");

            int minutos = Integer.parseInt(partes[0]);
            int segundos = Integer.parseInt(partes[1]);

            double paceEmMinutos = minutos + (segundos / 60.0);

            return (int) Math.round(distanciaKm * paceEmMinutos);

        } catch (Exception e) {
            return 0;
        }
    }

    private WorkoutResponseDTO converterParaDTO(Workout workout) {

        return new WorkoutResponseDTO(
                workout.getId(),
                workout.getTitulo(),
                workout.getTipo(),
                workout.getDescricao(),
                workout.getDiaSemana(),
                workout.getDataPlanejada(),
                workout.getDistanciaKm(),
                workout.getPaceAlvo(),
                workout.getObservacoes(),
                workout.getStatus()
        );
    }
}
