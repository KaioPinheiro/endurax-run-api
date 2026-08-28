package com.kaio.runtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.dto.CriarSolicitacaoPlanoRequestDTO;
import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.entity.SolicitacaoPlano;
import com.kaio.runtracker.entity.SolicitacaoPlanoStatus;
import com.kaio.runtracker.repository.SolicitacaoPlanoRepository;
import org.junit.jupiter.api.Test;
import com.kaio.runtracker.exception.PagamentoException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SolicitacaoPlanoServiceTest {

    @Test
    void persisteFormularioAntesDaCriacaoDoPix() {
        SolicitacaoPlanoRepository repository = mock(SolicitacaoPlanoRepository.class);
        SolicitacaoPlanoService service = new SolicitacaoPlanoService(repository, new ObjectMapper());
        GerarPlanoTreinoRequestDTO formulario = new GerarPlanoTreinoRequestDTO();
        formulario.setObjetivo("Primeira meia maratona");
        formulario.setDuracaoSemanas(4);
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

    @Test
    void novaSolicitacaoV1NeutralizaDadosDeProvaAntesDePersistir() throws Exception {
        SolicitacaoPlanoRepository repository = mock(SolicitacaoPlanoRepository.class);
        SolicitacaoPlanoService service = serviceComDataFixa(repository);
        GerarPlanoTreinoRequestDTO formulario = formularioBase();
        formulario.setPossuiProva(true);
        formulario.setDataProva(LocalDate.of(2026, 8, 10));
        formulario.setDistanciaProva("10 km");
        formulario.setObjetivoProva("Completar a prova");
        formulario.setImportanciaProva("Prova importante");
        when(repository.save(any(SolicitacaoPlano.class))).thenAnswer(invocation -> {
            SolicitacaoPlano solicitacao = invocation.getArgument(0);
            solicitacao.setId(7L);
            return solicitacao;
        });

        service.criar(new CriarSolicitacaoPlanoRequestDTO("cliente@email.com", formulario));

        assertEquals(false, formulario.getPossuiProva());
        assertEquals(null, formulario.getDataProva());
        assertEquals(null, formulario.getDistanciaProva());
        assertEquals(null, formulario.getObjetivoProva());
        assertEquals(null, formulario.getImportanciaProva());
        verify(repository).save(any());
    }

    @Test
    void rejeitaMaratonaInvalidaAntesDePersistirSolicitacao() {
        SolicitacaoPlanoRepository repository = mock(SolicitacaoPlanoRepository.class);
        SolicitacaoPlanoService service = serviceComDataFixa(repository);
        GerarPlanoTreinoRequestDTO formulario = formularioBase();
        formulario.setObjetivo("Melhorar tempo na Maratona");
        formulario.setDistanciaAlvo("42 km");
        formulario.setIdade(17);
        formulario.setExperienciaCorrida("Mais de 3 anos");
        formulario.setVolumeSemanalAtual("40-60 km");
        formulario.setDiasDisponiveis(List.of(
                "terça-feira", "quinta-feira", "sábado", "domingo"));

        assertThrows(PagamentoException.class, () -> service.criar(
                new CriarSolicitacaoPlanoRequestDTO("cliente@email.com", formulario)));
        verify(repository, never()).save(any());
    }

    private SolicitacaoPlanoService serviceComDataFixa(SolicitacaoPlanoRepository repository) {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-20T15:00:00Z"),
                ZoneId.of("America/Sao_Paulo"));
        return new SolicitacaoPlanoService(
                repository,
                new ObjectMapper().findAndRegisterModules(),
                new PlanoTreinoRegrasDeterministicasValidator(clock));
    }

    private GerarPlanoTreinoRequestDTO formularioBase() {
        GerarPlanoTreinoRequestDTO formulario = new GerarPlanoTreinoRequestDTO();
        formulario.setObjetivo("Melhorar tempo nos 10 km");
        formulario.setDistanciaAlvo("10 km");
        formulario.setPossuiProva(false);
        formulario.setIdade(30);
        formulario.setExperienciaCorrida("1 a 3 anos");
        formulario.setVolumeSemanalAtual("20-40 km");
        formulario.setDiasDisponiveis(List.of("terça-feira", "quinta-feira", "sábado"));
        formulario.setDuracaoSemanas(4);
        return formulario;
    }
}
