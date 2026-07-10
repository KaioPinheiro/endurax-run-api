package com.kaio.runtracker.controller;

import com.kaio.runtracker.dto.GerarPlanoSemanalRequestDTO;
import com.kaio.runtracker.dto.PlanoSemanalIAResponseDTO;
import com.kaio.runtracker.service.GerarPlanoSemanalIAService;
import com.kaio.runtracker.service.GerarTreinoIAException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiPlanoSemanalController {

    private final GerarPlanoSemanalIAService service;

    public AiPlanoSemanalController(GerarPlanoSemanalIAService service) {
        this.service = service;
    }

    @PostMapping("/gerar-plano-semanal")
    public ResponseEntity<?> gerarPlano(
            @Valid @RequestBody GerarPlanoSemanalRequestDTO request) {
        if (Boolean.TRUE.equals(request.getPossuiLesao())
                && !StringUtils.hasText(request.getDescricaoLesao())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "descricaoLesao",
                    "Descreva a lesão ou limitação informada"
            ));
        }

        try {
            PlanoSemanalIAResponseDTO plano = service.gerarPlano(request);
            return ResponseEntity.ok(plano);
        } catch (GerarTreinoIAException exception) {
            return ResponseEntity.status(exception.getStatus())
                    .body(Map.of("erro", exception.getMessage()));
        }
    }
}
