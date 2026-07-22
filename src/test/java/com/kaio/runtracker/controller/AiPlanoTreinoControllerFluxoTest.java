package com.kaio.runtracker.controller;

import com.kaio.runtracker.config.FluxoPlanoModo;
import com.kaio.runtracker.config.FluxoPlanoProperties;
import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import com.kaio.runtracker.exception.PagamentoException;
import com.kaio.runtracker.service.GerarPlanoTreinoIAService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiPlanoTreinoControllerFluxoTest {

    @Test
    void geracaoDiretaPermitidaEmDesenvolvimento() {
        GerarPlanoTreinoIAService service = mock(GerarPlanoTreinoIAService.class);
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        PlanoTreinoIAResponseDTO plano = new PlanoTreinoIAResponseDTO();
        when(service.gerarPlano(request)).thenReturn(plano);
        AiPlanoTreinoController controller = new AiPlanoTreinoController(
                service, fluxo(FluxoPlanoModo.DESENVOLVIMENTO));

        var response = controller.gerarPlano(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(plano, response.getBody());
    }

    @Test
    void jornadaSemPagamentoImpedidaEmTeste() {
        verificarBloqueio(FluxoPlanoModo.TESTE);
    }

    @Test
    void jornadaSemPagamentoImpedidaEmProducao() {
        verificarBloqueio(FluxoPlanoModo.PRODUCAO);
    }

    private void verificarBloqueio(FluxoPlanoModo modo) {
        GerarPlanoTreinoIAService service = mock(GerarPlanoTreinoIAService.class);
        AiPlanoTreinoController controller = new AiPlanoTreinoController(service, fluxo(modo));

        PagamentoException exception = assertThrows(PagamentoException.class,
                () -> controller.gerarPlano(new GerarPlanoTreinoRequestDTO()));

        assertEquals(402, exception.getStatus().value());
        verify(service, never()).gerarPlano(org.mockito.ArgumentMatchers.any());
    }

    private FluxoPlanoProperties fluxo(FluxoPlanoModo modo) {
        FluxoPlanoProperties properties = new FluxoPlanoProperties();
        properties.setModo(modo);
        return properties;
    }
}
