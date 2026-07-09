package com.kaio.runtracker.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.kaio.runtracker.dto.GeneratePlanRequestDTO;
import com.kaio.runtracker.service.OpenAiTrainingPlanException;
import com.kaio.runtracker.service.OpenAiTrainingPlanService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/training-plans")
public class TrainingPlanAiController {

    private final OpenAiTrainingPlanService openAiTrainingPlanService;

    public TrainingPlanAiController(
            OpenAiTrainingPlanService openAiTrainingPlanService) {
        this.openAiTrainingPlanService = openAiTrainingPlanService;
    }

    @PostMapping("/generate-ai")
    public ResponseEntity<?> gerarPlanoComIa(
            @Valid @RequestBody GeneratePlanRequestDTO request) {
        try {
            JsonNode plano = openAiTrainingPlanService.gerarPlano(request);
            return ResponseEntity.ok(plano);
        } catch (OpenAiTrainingPlanException exception) {
            return ResponseEntity.internalServerError().body(
                    Map.of("erro", exception.getMessage())
            );
        }
    }
}
