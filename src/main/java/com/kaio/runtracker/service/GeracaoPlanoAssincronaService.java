package com.kaio.runtracker.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class GeracaoPlanoAssincronaService {
    private final GeracaoPlanoService geracaoPlanoService;

    public GeracaoPlanoAssincronaService(GeracaoPlanoService geracaoPlanoService) {
        this.geracaoPlanoService = geracaoPlanoService;
    }

    @Async
    public void iniciar(Long pagamentoId) {
        geracaoPlanoService.gerar(pagamentoId);
    }
}
