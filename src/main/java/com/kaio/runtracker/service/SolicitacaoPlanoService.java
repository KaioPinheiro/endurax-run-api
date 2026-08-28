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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class SolicitacaoPlanoService {
    private final SolicitacaoPlanoRepository repository;
    private final ObjectMapper objectMapper;
    private final PlanoTreinoRegrasDeterministicasValidator regrasValidator;

    @Autowired
    public SolicitacaoPlanoService(
            SolicitacaoPlanoRepository repository,
            ObjectMapper objectMapper,
            PlanoTreinoRegrasDeterministicasValidator regrasValidator) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.regrasValidator = regrasValidator;
    }

    SolicitacaoPlanoService(SolicitacaoPlanoRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, new PlanoTreinoRegrasDeterministicasValidator());
    }

    public CriarSolicitacaoPlanoResponseDTO criar(CriarSolicitacaoPlanoRequestDTO request) {
        try {
            regrasValidator.prepararNovaSolicitacaoPublicaV1(request.formulario());
            SolicitacaoPlano solicitacao = new SolicitacaoPlano();
            solicitacao.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
            solicitacao.setDadosFormularioJson(objectMapper.writeValueAsString(request.formulario()));
            solicitacao.setStatus(SolicitacaoPlanoStatus.PENDING);
            SolicitacaoPlano salva = repository.save(solicitacao);
            return new CriarSolicitacaoPlanoResponseDTO(salva.getId(), salva.getStatus());
        } catch (IllegalArgumentException exception) {
            throw new PagamentoException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (JsonProcessingException exception) {
            throw new PagamentoException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Não foi possível armazenar os dados do plano.", exception);
        }
    }
}
