package com.kaio.runtracker.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import com.kaio.runtracker.dto.TrainingPlanRequestDTO;
import com.kaio.runtracker.dto.TrainingPlanResponseDTO;
import com.kaio.runtracker.entity.TrainingPlan;
import com.kaio.runtracker.repository.TrainingPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TrainingPlanService {

    private final TrainingPlanRepository repository;
    private final ObjectMapper objectMapper;

    public TrainingPlanService(TrainingPlanRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
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

    @Transactional
    public TrainingPlan salvarPlanoGerado(
            GerarPlanoTreinoRequestDTO formulario,
            PlanoTreinoIAResponseDTO planoGerado) {
        try {
            TrainingPlan trainingPlan = new TrainingPlan();
            trainingPlan.setNome(planoGerado.getTitulo());
            trainingPlan.setObjetivo(formulario.getObjetivo());
            trainingPlan.setNivel(formulario.getExperienciaCorrida());
            trainingPlan.setDescricao(objectMapper.writeValueAsString(planoGerado));
            return repository.save(trainingPlan);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível persistir o plano gerado.", exception);
        }
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

