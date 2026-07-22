package com.kaio.runtracker.service;

import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class GeracaoPlanoService {
    private static final Logger logger = LoggerFactory.getLogger(GeracaoPlanoService.class);

    private final GeracaoPlanoTransacaoService transacaoService;
    private final GerarPlanoTreinoIAService geradorIA;

    public GeracaoPlanoService(
            GeracaoPlanoTransacaoService transacaoService,
            GerarPlanoTreinoIAService geradorIA) {
        this.transacaoService = transacaoService;
        this.geradorIA = geradorIA;
    }

    public void gerar(Long pagamentoId) {
        Optional<GeracaoPlanoTransacaoService.GeracaoContexto> reserva =
                transacaoService.reservar(pagamentoId);
        if (reserva.isEmpty()) {
            logger.info("Geração de plano ignorada por duplicidade ou estado incompatível: pagamentoId={}",
                    pagamentoId);
            return;
        }

        GeracaoPlanoTransacaoService.GeracaoContexto contexto = reserva.get();
        logger.info("Iniciando geração automática do plano: pagamentoId={}", pagamentoId);
        try {
            PlanoTreinoIAResponseDTO plano = geradorIA.gerarPlanoAutomatico(contexto.formulario());
            Long planoId = transacaoService.concluir(contexto, plano);
            logger.info("Plano gerado automaticamente: pagamentoId={}, planoId={}", pagamentoId, planoId);
        } catch (Exception exception) {
            transacaoService.falhar(pagamentoId);
            logger.error("Falha na geração automática do plano: pagamentoId={}, tipoErro={}",
                    pagamentoId, exception.getClass().getSimpleName());
        }
    }
}
