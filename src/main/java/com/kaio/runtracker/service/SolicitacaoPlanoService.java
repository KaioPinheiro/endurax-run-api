package com.kaio.runtracker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.dto.CriarSolicitacaoPlanoRequestDTO;
import com.kaio.runtracker.dto.CriarSolicitacaoPlanoResponseDTO;
import com.kaio.runtracker.entity.SolicitacaoPlano;
import com.kaio.runtracker.entity.SolicitacaoPlanoStatus;
import com.kaio.runtracker.exception.PagamentoException;
import com.kaio.runtracker.repository.SolicitacaoPlanoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class SolicitacaoPlanoService {
    private final SolicitacaoPlanoRepository repository;
    private final ObjectMapper objectMapper;

    public SolicitacaoPlanoService(SolicitacaoPlanoRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public CriarSolicitacaoPlanoResponseDTO criar(CriarSolicitacaoPlanoRequestDTO request) {
        try {
            SolicitacaoPlano solicitacao = new SolicitacaoPlano();
            solicitacao.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
            solicitacao.setDadosFormularioJson(objectMapper.writeValueAsString(request.formulario()));
            solicitacao.setStatus(SolicitacaoPlanoStatus.PENDING);
            SolicitacaoPlano salva = repository.save(solicitacao);
            return new CriarSolicitacaoPlanoResponseDTO(salva.getId(), salva.getStatus());
        } catch (JsonProcessingException exception) {
            throw new PagamentoException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Não foi possível armazenar os dados do plano.", exception);
        }
    }
}
