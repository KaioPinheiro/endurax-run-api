package com.kaio.runtracker.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.kaio.runtracker.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PublicEndpointRateLimitFilterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-26T12:00:00Z"));
    private final RateLimitProperties properties = properties();
    private final PublicEndpointRateLimitFilter filter =
            new PublicEndpointRateLimitFilter(properties, clock);
    private final Logger logger =
            (Logger) LoggerFactory.getLogger(PublicEndpointRateLimitFilter.class);
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void captureLogs() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        MDC.put("requestId", "req-rate-limit");
    }

    @AfterEach
    void cleanup() {
        logger.detachAppender(appender);
        MDC.clear();
    }

    @Test
    void allowsRequestsWithinLimitAndRejectsNextWith429AndRetryAfter() throws Exception {
        assertThat(execute("POST", "/api/pagamentos/pix", "203.0.113.10").getStatus()).isEqualTo(204);
        assertThat(execute("POST", "/api/pagamentos/pix", "203.0.113.10").getStatus()).isEqualTo(204);

        MockHttpServletResponse rejected =
                execute("POST", "/api/pagamentos/pix", "203.0.113.10");

        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("60");
        assertThat(rejected.getContentAsString())
                .contains("Muitas requisições")
                .doesNotContain("203.0.113.10", "token", "contador");
    }

    @Test
    void categoriesAreIndependent() throws Exception {
        execute("POST", "/api/pagamentos/pix", "203.0.113.10");
        execute("POST", "/api/pagamentos/pix", "203.0.113.10");

        assertThat(execute("POST", "/api/solicitacoes-plano", "203.0.113.10").getStatus())
                .isEqualTo(204);
        assertThat(execute("POST", "/api/ai/gerar-plano", "203.0.113.10").getStatus())
                .isEqualTo(204);
    }

    @Test
    void resetsCounterAfterWindowExpires() throws Exception {
        execute("POST", "/api/pagamentos/pix", "203.0.113.10");
        execute("POST", "/api/pagamentos/pix", "203.0.113.10");
        assertThat(execute("POST", "/api/pagamentos/pix", "203.0.113.10").getStatus())
                .isEqualTo(429);

        clock.advanceSeconds(60);

        assertThat(execute("POST", "/api/pagamentos/pix", "203.0.113.10").getStatus())
                .isEqualTo(204);
    }

    @Test
    void basicConcurrencyNeverAllowsMoreThanConfiguredLimit() throws Exception {
        int workers = 20;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();

        try {
            for (int i = 0; i < workers; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    if (execute("POST", "/api/ai/gerar-treino", "198.51.100.20").getStatus() == 204) {
                        allowed.incrementAndGet();
                    }
                    return null;
                });
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(allowed).hasValue(2);
    }

    @Test
    void unprotectedEndpointAndWebhookRemainUnchanged() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(execute("GET", "/api/config/publica", "203.0.113.10").getStatus())
                    .isEqualTo(204);
            assertThat(execute("POST", "/api/webhooks/mercado-pago", "203.0.113.10").getStatus())
                    .isEqualTo(204);
        }
    }

    @Test
    void usesRemoteAddressAndDoesNotTrustForwardedHeaderDirectly() throws Exception {
        MockHttpServletRequest first = request("POST", "/api/pagamentos/pix", "203.0.113.10");
        first.addHeader("X-Forwarded-For", "198.51.100.1");
        execute(first);
        MockHttpServletRequest second = request("POST", "/api/pagamentos/pix", "203.0.113.10");
        second.addHeader("X-Forwarded-For", "198.51.100.2");
        execute(second);
        MockHttpServletRequest third = request("POST", "/api/pagamentos/pix", "203.0.113.10");
        third.addHeader("X-Forwarded-For", "198.51.100.3");

        assertThat(execute(third).getStatus()).isEqualTo(429);
    }

    @Test
    void logUsesSanitizedRouteAndNeverIncludesOpaqueToken() throws Exception {
        String token = "550e8400-e29b-41ce-98fd-7fd50b809672";
        execute("POST", "/api/pagamentos/public/" + token + "/geracao/tentar-novamente",
                "203.0.113.10");
        execute("POST", "/api/pagamentos/public/" + token + "/geracao/tentar-novamente",
                "203.0.113.10");
        execute("POST", "/api/pagamentos/public/" + token + "/geracao/tentar-novamente",
                "203.0.113.10");

        String logs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + right);
        assertThat(logs)
                .contains("categoria=GERACAO_IA", "requestId=req-rate-limit",
                        "/api/pagamentos/public/[REDACTED]/geracao/tentar-novamente")
                .doesNotContain(token);
    }

    @Test
    void limitaCancelamentoPublicoSemRegistrarToken() throws Exception {
        String token = "550e8400-e29b-41ce-98fd-7fd50b809672";
        String rota = "/api/pagamentos/public/" + token + "/cancelar";

        assertThat(execute("POST", rota, "203.0.113.20").getStatus()).isEqualTo(204);
        assertThat(execute("POST", rota, "203.0.113.20").getStatus()).isEqualTo(204);
        assertThat(execute("POST", rota, "203.0.113.20").getStatus()).isEqualTo(429);

        String logs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + right);
        assertThat(logs)
                .contains("categoria=CANCELAMENTO_PIX",
                        "/api/pagamentos/public/[REDACTED]/cancelar")
                .doesNotContain(token);
    }

    private MockHttpServletResponse execute(String method, String uri, String remoteAddress)
            throws Exception {
        return execute(request(method, uri, remoteAddress));
    }

    private MockHttpServletResponse execute(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(204);
        filter.doFilter(request, response, chain);
        return response;
    }

    private MockHttpServletRequest request(String method, String uri, String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    private RateLimitProperties properties() {
        RateLimitProperties result = new RateLimitProperties();
        result.setSolicitacao(new RateLimitProperties.Limit(2, 60));
        result.setPix(new RateLimitProperties.Limit(2, 60));
        result.setGeracao(new RateLimitProperties.Limit(2, 60));
        result.setConsulta(new RateLimitProperties.Limit(2, 60));
        return result;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
