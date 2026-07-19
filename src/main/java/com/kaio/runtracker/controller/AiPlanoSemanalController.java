package com.kaio.runtracker.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.kaio.runtracker.dto.GerarPlanoSemanalRequestDTO;
import com.kaio.runtracker.dto.PlanoSemanalIAResponseDTO;
import com.kaio.runtracker.service.GerarPlanoSemanalIAService;
import com.kaio.runtracker.service.GerarTreinoIAException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger =
            LoggerFactory.getLogger(AiPlanoSemanalController.class);

    private final GerarPlanoSemanalIAService service;
    private final ObjectMapper objectMapper;

    public AiPlanoSemanalController(
            GerarPlanoSemanalIAService service,
            ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/gerar-plano-semanal")
    public ResponseEntity<?> gerarPlano(
            @Valid @RequestBody GerarPlanoSemanalRequestDTO request) {
        logger.info("Formulário do plano semanal recebido: {}", paraJson(request));

        if (Boolean.TRUE.equals(request.getPossuiLesao())
                && !StringUtils.hasText(request.getDescricaoLesao())) {
            Map<String, String> erro = Map.of(
                    "descricaoLesao",
                    "Descreva a lesão ou limitação informada"
            );
            logger.warn("API respondeu ao formulário do plano semanal: status=400, body={}",
                    paraJson(erro));
            return ResponseEntity.badRequest().body(erro);
        }

        try {
            PlanoSemanalIAResponseDTO plano = service.gerarPlano(request);
            logger.info("API respondeu ao formulário do plano semanal: status=200, body={}",
                    paraJson(plano));
            return ResponseEntity.ok(plano);
        } catch (GerarTreinoIAException exception) {
            Map<String, String> erro = Map.of("erro", exception.getMessage());
            logger.warn("API respondeu ao formulário do plano semanal: status={}, body={}",
                    exception.getStatus().value(), paraJson(erro));
            return ResponseEntity.status(exception.getStatus())
                    .body(erro);
        }
    }

    private String paraJson(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (JsonProcessingException exception) {
            logger.warn("Não foi possível serializar o conteúdo do log do plano semanal: {}",
                    exception.getMessage());
            return "<conteúdo indisponível>";
        }
    }
}
