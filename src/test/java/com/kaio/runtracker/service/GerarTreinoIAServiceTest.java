package com.kaio.runtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.dto.GerarTreinoRequestDTO;
import com.kaio.runtracker.dto.GerarTreinoResponseDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GerarTreinoIAServiceTest {

    @Test
    void treinoUnitarioUsaClienteOpenAiCentral() {
        OpenAIService clienteCentral = mock(OpenAIService.class);
        when(clienteCentral.enviarPromptTreino(anyString(), anyString()))
                .thenReturn("""
                        {
                          "titulo":"Rodagem leve",
                          "tipo":"Leve",
                          "descricao":"Treino confortável"
                        }
                        """);
        GerarTreinoIAService service =
                new GerarTreinoIAService(clienteCentral, new ObjectMapper());

        GerarTreinoResponseDTO resposta =
                service.gerarTreino(new GerarTreinoRequestDTO());

        assertThat(resposta.getTitulo()).isEqualTo("Rodagem leve");
        verify(clienteCentral).enviarPromptTreino(anyString(), anyString());
    }
}
