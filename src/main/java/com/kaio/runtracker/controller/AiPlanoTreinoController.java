package com.kaio.runtracker.controller;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import com.kaio.runtracker.service.GerarPlanoTreinoIAService;
import com.kaio.runtracker.service.GerarTreinoIAException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiPlanoTreinoController {

    private final GerarPlanoTreinoIAService service;

    public AiPlanoTreinoController(GerarPlanoTreinoIAService service) {
        this.service = service;
    }

    @PostMapping("/gerar-plano")
    public ResponseEntity<?> gerarPlano(
            @Valid @RequestBody GerarPlanoTreinoRequestDTO request) {
        try {
            PlanoTreinoIAResponseDTO plano = service.gerarPlano(request);
            return ResponseEntity.ok(plano);
        } catch (GerarTreinoIAException exception) {
            return ResponseEntity.status(exception.getStatus())
                    .body(Map.of("erro", exception.getMessage()));
        }
    }
}
