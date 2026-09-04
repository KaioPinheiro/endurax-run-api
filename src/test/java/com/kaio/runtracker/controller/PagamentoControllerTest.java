package com.kaio.runtracker.controller;

import com.kaio.runtracker.dto.PagamentoStatusResponseDTO;
import com.kaio.runtracker.entity.PagamentoStatus;
import com.kaio.runtracker.service.GeracaoPlanoAssincronaService;
import com.kaio.runtracker.service.PagamentoService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PagamentoControllerTest {

    private final PagamentoService service = mock(PagamentoService.class);
    private final GeracaoPlanoAssincronaService geracaoAssincronaService =
            mock(GeracaoPlanoAssincronaService.class);
    private final PagamentoController controller =
            new PagamentoController(service, geracaoAssincronaService);

    @Test
    void reconciliacaoQueDetectaAprovacaoIniciaGeracaoNoBackend() {
        PagamentoStatusResponseDTO status = new PagamentoStatusResponseDTO(
                PagamentoStatus.APPROVED, "accredited", true, false);
        when(service.consultarStatusPorToken("TOKEN")).thenReturn(status);
        when(service.pagamentoPendenteDeGeracaoPorToken("TOKEN")).thenReturn(1L);

        PagamentoStatusResponseDTO resposta = controller.consultarStatus("TOKEN");

        assertEquals(PagamentoStatus.APPROVED, resposta.status());
        verify(geracaoAssincronaService).iniciar(1L);
    }

    @Test
    void reconciliacaoNaoIniciaGeracaoQuandoBackendNaoSinaliza() {
        PagamentoStatusResponseDTO status = new PagamentoStatusResponseDTO(
                PagamentoStatus.PENDING, "waiting_transfer", false, false);
        when(service.consultarStatusPorToken("TOKEN")).thenReturn(status);
        when(service.pagamentoPendenteDeGeracaoPorToken("TOKEN")).thenReturn(null);

        controller.consultarStatus("TOKEN");

        verify(geracaoAssincronaService, never()).iniciar(any());
    }

    @Test
    void cancelamentoPorTokenRetornaSemConteudo() {
        var resposta = controller.cancelar("TOKEN");

        assertTrue(resposta.getStatusCode().is2xxSuccessful());
        verify(service).cancelarPorToken("TOKEN");
    }
}
