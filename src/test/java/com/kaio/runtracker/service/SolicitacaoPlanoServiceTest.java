package com.kaio.runtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.dto.CriarSolicitacaoPlanoRequestDTO;
import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.entity.SolicitacaoPlano;
import com.kaio.runtracker.entity.SolicitacaoPlanoStatus;
import com.kaio.runtracker.repository.SolicitacaoPlanoRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SolicitacaoPlanoServiceTest {

    @Test
    void persisteFormularioAntesDaCriacaoDoPix() {
        SolicitacaoPlanoRepository repository = mock(SolicitacaoPlanoRepository.class);
        SolicitacaoPlanoService service = new SolicitacaoPlanoService(repository, new ObjectMapper());
        GerarPlanoTreinoRequestDTO formulario = new GerarPlanoTreinoRequestDTO();
        formulario.setObjetivo("Primeira meia maratona");
        when(repository.save(any(SolicitacaoPlano.class))).thenAnswer(invocation -> {
            SolicitacaoPlano solicitacao = invocation.getArgument(0);
            solicitacao.setId(7L);
            return solicitacao;
        });

        var response = service.criar(new CriarSolicitacaoPlanoRequestDTO(
                " Cliente@Email.com ", formulario));

        assertEquals(7L, response.solicitacaoPlanoId());
        assertEquals(SolicitacaoPlanoStatus.PENDING, response.status());
        assertTrue(formulario.getObjetivo().equals("Primeira meia maratona"));
    }
}
