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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Locale;

@Service
public class SolicitacaoPlanoService {
    private static final Logger logger = LoggerFactory.getLogger(SolicitacaoPlanoService.class);
    private static final char[] CODIGO_ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_TENTATIVAS_CODIGO = 10;
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
            solicitacao.setCodigoAtendimento(gerarCodigoAtendimento());
            solicitacao.setStatus(SolicitacaoPlanoStatus.PENDING);
            SolicitacaoPlano salva = repository.save(solicitacao);
            logger.info("Solicitação de plano criada: codigoAtendimento={}, solicitacaoPlanoId={}, status={}",
                    salva.getCodigoAtendimento(), salva.getId(), salva.getStatus());
            return new CriarSolicitacaoPlanoResponseDTO(salva.getId(), salva.getStatus());
        } catch (IllegalArgumentException exception) {
            throw new PagamentoException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (JsonProcessingException exception) {
            throw new PagamentoException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Não foi possível armazenar os dados do plano.", exception);
        }
    }

    String gerarCodigoAtendimento() {
        for (int tentativa = 0; tentativa < MAX_TENTATIVAS_CODIGO; tentativa++) {
            StringBuilder codigo = new StringBuilder("END-");
            for (int indice = 0; indice < 6; indice++) {
                codigo.append(CODIGO_ALFABETO[SECURE_RANDOM.nextInt(CODIGO_ALFABETO.length)]);
            }
            String valor = codigo.toString();
            if (!repository.existsByCodigoAtendimento(valor)) return valor;
        }
        throw new IllegalStateException("Não foi possível gerar um código de atendimento único.");
    }
}
