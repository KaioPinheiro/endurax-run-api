package com.kaio.runtracker.controller;

import com.kaio.runtracker.dto.CriarSolicitacaoPlanoRequestDTO;
import com.kaio.runtracker.dto.CriarSolicitacaoPlanoResponseDTO;
import com.kaio.runtracker.service.SolicitacaoPlanoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/solicitacoes-plano")
public class SolicitacaoPlanoController {
    private final SolicitacaoPlanoService service;

    public SolicitacaoPlanoController(SolicitacaoPlanoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CriarSolicitacaoPlanoResponseDTO> criar(
            @Valid @RequestBody CriarSolicitacaoPlanoRequestDTO request) {
        CriarSolicitacaoPlanoResponseDTO response = service.criar(request);
        return ResponseEntity.created(URI.create("/api/solicitacoes-plano/" + response.solicitacaoPlanoId()))
                .body(response);
    }
}
