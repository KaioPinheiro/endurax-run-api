package com.kaio.runtracker.service;


import com.kaio.runtracker.dto.TrainingPlanRequestDTO;
import com.kaio.runtracker.dto.TrainingPlanResponseDTO;
import com.kaio.runtracker.entity.TrainingPlan;
import com.kaio.runtracker.repository.TrainingPlanRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TrainingPlanService {

    private final TrainingPlanRepository repository;

    public TrainingPlanService(TrainingPlanRepository repository) {
        this.repository = repository;
    }

    public List<TrainingPlanResponseDTO> listarTodos() {
        return repository.findAll()
                .stream()
                .map(this::converterParaDTO)
                .toList();
    }

    public TrainingPlanResponseDTO buscarPorId(Long id) {
        return repository.findById(id)
                .map(this::converterParaDTO)
                .orElse(null);
    }

    public TrainingPlanResponseDTO salvar(TrainingPlanRequestDTO dto) {

        TrainingPlan trainingPlan = new TrainingPlan();

        trainingPlan.setNome(dto.getNome());
        trainingPlan.setObjetivo(dto.getObjetivo());
        trainingPlan.setNivel(dto.getNivel());
        trainingPlan.setDescricao(dto.getDescricao());

        TrainingPlan salvo = repository.save(trainingPlan);

        return converterParaDTO(salvo);
    }

    public TrainingPlanResponseDTO atualizar(Long id, TrainingPlanRequestDTO dto) {

        TrainingPlan trainingPlan = repository.findById(id).orElse(null);

        if (trainingPlan == null) {
            return null;
        }

        trainingPlan.setNome(dto.getNome());
        trainingPlan.setObjetivo(dto.getObjetivo());
        trainingPlan.setNivel(dto.getNivel());
        trainingPlan.setDescricao(dto.getDescricao());

        TrainingPlan atualizado = repository.save(trainingPlan);

        return converterParaDTO(atualizado);
    }

    public void deletar(Long id) {
        repository.deleteById(id);
    }

    private TrainingPlanResponseDTO converterParaDTO(TrainingPlan trainingPlan) {
        return new TrainingPlanResponseDTO(
                trainingPlan.getId(),
                trainingPlan.getNome(),
                trainingPlan.getObjetivo(),
                trainingPlan.getNivel(),
                trainingPlan.getDescricao()
        );
    }
}

