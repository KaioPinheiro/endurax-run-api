package com.kaio.runtracker.controller;


import com.kaio.runtracker.dto.PublicTrainingPlanResponseDTO;
import com.kaio.runtracker.service.TrainingPlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/training-plans")
public class TrainingPlanController {

    private final TrainingPlanService trainingPlanService;

    public TrainingPlanController(TrainingPlanService trainingPlanService) {
        this.trainingPlanService = trainingPlanService;
    }

    @GetMapping("/public/{token}")
    public ResponseEntity<PublicTrainingPlanResponseDTO> buscarPlanoPago(@PathVariable String token) {
        PublicTrainingPlanResponseDTO plano = trainingPlanService.buscarPlanoPagoPorToken(token);

        if (plano == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(plano);
    }
}
