package com.kaio.runtracker.service;

import com.kaio.runtracker.entity.GeracaoPlanoStatus;
import com.kaio.runtracker.entity.Pagamento;
import com.kaio.runtracker.entity.PagamentoStatus;
import com.kaio.runtracker.entity.SolicitacaoPlano;
import com.kaio.runtracker.entity.SolicitacaoPlanoStatus;
import com.kaio.runtracker.repository.PagamentoRepository;
import com.kaio.runtracker.repository.SolicitacaoPlanoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class GeracaoPlanoTransacaoServiceConcurrencyTest {

    @Autowired
    private GeracaoPlanoTransacaoService service;
    @Autowired
    private PagamentoRepository pagamentoRepository;
    @Autowired
    private SolicitacaoPlanoRepository solicitacaoPlanoRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long pagamentoId;
    private Long solicitacaoId;

    @AfterEach
    void limparRegistrosDoTeste() {
        if (pagamentoId != null) pagamentoRepository.deleteById(pagamentoId);
        if (solicitacaoId != null) solicitacaoPlanoRepository.deleteById(solicitacaoId);
    }

    @Test
    void duasTentativasConcorrentesRetomamProcessingStaleUmaUnicaVez() throws Exception {
        SolicitacaoPlano solicitacao = new SolicitacaoPlano();
        solicitacao.setEmail("concorrencia-" + UUID.randomUUID() + "@example.com");
        solicitacao.setDadosFormularioJson("{}");
        solicitacao.setStatus(SolicitacaoPlanoStatus.PAYMENT_PENDING);
        solicitacao = solicitacaoPlanoRepository.saveAndFlush(solicitacao);
        solicitacaoId = solicitacao.getId();

        Pagamento pagamento = new Pagamento();
        String referencia = UUID.randomUUID().toString();
        pagamento.setOrderExternalId("order-" + referencia);
        pagamento.setExternalReference(referencia);
        pagamento.setIdempotencyKey(UUID.randomUUID().toString());
        pagamento.setStatus(PagamentoStatus.APPROVED);
        pagamento.setStatusDetail("accredited");
        pagamento.setValor(new BigDecimal("12.90"));
        pagamento.setEmailPagador(solicitacao.getEmail());
        pagamento.setPixCopiaCola("pix-teste");
        pagamento.setQrCodeBase64("qr-teste");
        pagamento.setDataExpiracao(LocalDateTime.now().plusMinutes(15));
        pagamento.setGeracaoStatus(GeracaoPlanoStatus.PROCESSING);
        pagamento.setSolicitacaoPlano(solicitacao);
        pagamento = pagamentoRepository.saveAndFlush(pagamento);
        pagamentoId = pagamento.getId();
        jdbcTemplate.update(
                "update pagamentos set atualizado_em = ? where id = ?",
                LocalDateTime.now().minusHours(1), pagamentoId);

        CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<GeracaoPlanoTransacaoService.GeracaoContexto>> primeira =
                    executor.submit(() -> {
                        inicio.await();
                        return service.reservar(pagamentoId);
                    });
            Future<Optional<GeracaoPlanoTransacaoService.GeracaoContexto>> segunda =
                    executor.submit(() -> {
                        inicio.await();
                        return service.reservar(pagamentoId);
                    });

            inicio.countDown();
            long reservas = java.util.stream.Stream.of(primeira.get(), segunda.get())
                    .filter(Optional::isPresent)
                    .count();

            assertEquals(1, reservas);
        } finally {
            executor.shutdownNow();
        }
    }
}
