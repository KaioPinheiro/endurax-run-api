package com.kaio.runtracker.controller;

import com.kaio.runtracker.dto.GerarTreinoRequestDTO;
import com.kaio.runtracker.dto.GerarTreinoResponseDTO;
import com.kaio.runtracker.service.GerarTreinoIAException;
import com.kaio.runtracker.service.GerarTreinoIAService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "http://localhost:5173")
public class AiTreinoController {

    private final GerarTreinoIAService gerarTreinoIAService;

    public AiTreinoController(GerarTreinoIAService gerarTreinoIAService) {
        this.gerarTreinoIAService = gerarTreinoIAService;
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("AI Controller OK");
    }

    @PostMapping("/gerar-treino")
    public ResponseEntity<?> gerarTreino(
            @Valid @RequestBody GerarTreinoRequestDTO request) {
        if (Boolean.TRUE.equals(request.getPossuiLesao())
                && !StringUtils.hasText(request.getDescricaoLesao())) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "descricaoLesao",
                            "Descreva a lesão ou limitação informada"
                    )
            );
        }

        try {
            GerarTreinoResponseDTO treino =
                    gerarTreinoIAService.gerarTreino(request);
            return ResponseEntity.ok(treino);
        } catch (GerarTreinoIAException exception) {
            return ResponseEntity.status(exception.getStatus()).body(
                    Map.of("erro", exception.getMessage())
            );
        }
    }
}
