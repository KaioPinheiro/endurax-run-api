package com.kaio.runtracker.service;

import com.kaio.runtracker.dto.UserRequestDTO;
import com.kaio.runtracker.dto.UserResponseDTO;
import com.kaio.runtracker.dto.WorkoutResponseDTO;
import com.kaio.runtracker.entity.TrainingPlan;
import com.kaio.runtracker.entity.User;
import com.kaio.runtracker.entity.Workout;
import com.kaio.runtracker.repository.TrainingPlanRepository;
import com.kaio.runtracker.repository.UserRepository;
import com.kaio.runtracker.repository.WorkoutRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final TrainingPlanRepository trainingPlanRepository;
    private final WorkoutRepository workoutRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       TrainingPlanRepository trainingPlanRepository,
                       WorkoutRepository workoutRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.trainingPlanRepository = trainingPlanRepository;
        this.workoutRepository = workoutRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserResponseDTO> listarTodos() {
        return userRepository.findAll()
                .stream()
                .map(this::converterParaDTO)
                .toList();
    }

    public UserResponseDTO buscarPorId(Long id) {
        return userRepository.findById(id)
                .map(this::converterParaDTO)
                .orElse(null);
    }

    public UserResponseDTO salvar(UserRequestDTO dto) {
        User user = new User();

        user.setNome(dto.getNome());
        user.setEmail(dto.getEmail());
        user.setSenha(passwordEncoder.encode(dto.getSenha()));
        user.setRole(dto.getRole());

        if (dto.getTrainingPlanId() != null) {
            TrainingPlan trainingPlan = trainingPlanRepository
                    .findById(dto.getTrainingPlanId())
                    .orElse(null);

            user.setTrainingPlan(trainingPlan);
        }

        User salvo = userRepository.save(user);

        return converterParaDTO(salvo);
    }

    public UserResponseDTO atualizar(Long id, UserRequestDTO dto) {
        User user = userRepository.findById(id).orElse(null);

        if (user == null) {
            return null;
        }

        user.setNome(dto.getNome());
        user.setEmail(dto.getEmail());
        user.setSenha(dto.getSenha());
        user.setRole(dto.getRole());

        if (dto.getTrainingPlanId() != null) {
            TrainingPlan trainingPlan = trainingPlanRepository
                    .findById(dto.getTrainingPlanId())
                    .orElse(null);

            user.setTrainingPlan(trainingPlan);
        } else {
            user.setTrainingPlan(null);
        }

        User atualizado = userRepository.save(user);

        return converterParaDTO(atualizado);
    }

    public void deletar(Long id) {
        userRepository.deleteById(id);
    }

    public List<WorkoutResponseDTO> listarWorkoutsDoUsuario(Long userId) {
        User user = userRepository.findById(userId).orElse(null);

        if (user == null || user.getTrainingPlan() == null) {
            return List.of();
        }

        Long trainingPlanId = user.getTrainingPlan().getId();

        return workoutRepository.findByTrainingPlanId(trainingPlanId)
                .stream()
                .map(this::converterWorkoutParaDTO)
                .toList();
    }

    private UserResponseDTO converterParaDTO(User user) {
        Long trainingPlanId = null;
        String trainingPlanNome = null;

        if (user.getTrainingPlan() != null) {
            trainingPlanId = user.getTrainingPlan().getId();
            trainingPlanNome = user.getTrainingPlan().getNome();
        }

        return new UserResponseDTO(
                user.getId(),
                user.getNome(),
                user.getEmail(),
                user.getRole(),
                trainingPlanId,
                trainingPlanNome
        );
    }

    private WorkoutResponseDTO converterWorkoutParaDTO(Workout workout) {
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
