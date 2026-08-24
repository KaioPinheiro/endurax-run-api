package com.kaio.runtracker.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.kaio.runtracker.entity.User;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AccessLogFilterTest {

    private final AccessLogFilter filter = new AccessLogFilter();
    private final Logger logger = (Logger) LoggerFactory.getLogger(AccessLogFilter.class);
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void capturarLogs() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void limpar() {
        logger.detachAppender(appender);
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void geraRequestIdCapturaStatusIpELimpaMdc() throws Exception {
        MockHttpServletRequest request = requisicao();
        MockHttpServletResponse response = new MockHttpServletResponse();

        executar(request, response, 404);

        assertThat(response.getHeader(AccessLogFilter.REQUEST_ID_HEADER))
                .matches("[0-9a-f-]{36}");
        assertThat(log()).contains("status=404", "ip=203.0.113.10", "method=GET", "uri=/api/teste");
        assertThat(MDC.get(AccessLogFilter.MDC_REQUEST_ID)).isNull();
    }

    @Test
    void preservaRequestIdValido() throws Exception {
        MockHttpServletRequest request = requisicao();
        request.addHeader(AccessLogFilter.REQUEST_ID_HEADER, "web-abc_123.9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        executar(request, response, 200);

        assertThat(response.getHeader(AccessLogFilter.REQUEST_ID_HEADER)).isEqualTo("web-abc_123.9");
        assertThat(log()).contains("requestId=web-abc_123.9");
    }

    @Test
    void substituiRequestIdInvalidoOuExcessivo() throws Exception {
        for (String invalido : new String[]{"id com espaço", "x".repeat(65), "sk-12345678901234567890"}) {
            appender.list.clear();
            MockHttpServletRequest request = requisicao();
            request.addHeader(AccessLogFilter.REQUEST_ID_HEADER, invalido);
            MockHttpServletResponse response = new MockHttpServletResponse();
            executar(request, response, 200);
            assertThat(response.getHeader(AccessLogFilter.REQUEST_ID_HEADER)).isNotEqualTo(invalido);
        }
    }

    @Test
    void naoRegistraAuthorizationSenhaOuApiKey() throws Exception {
        MockHttpServletRequest request = requisicao();
        request.addHeader("Authorization", "Bearer jwt-secreto");
        request.addHeader("X-API-Key", "sk-12345678901234567890");
        request.removeHeader("User-Agent");
        request.addHeader("User-Agent", "cliente password=segredo sk-12345678901234567890");
        executar(request, new MockHttpServletResponse(), 200);

        assertThat(log())
                .doesNotContain("jwt-secreto", "segredo", "sk-12345678901234567890")
                .contains("[REDACTED]");
    }

    @Test
    void ignoraXForwardedForNaoConfiavel() throws Exception {
        MockHttpServletRequest request = requisicao();
        request.addHeader("X-Forwarded-For", "198.51.100.99");
        executar(request, new MockHttpServletResponse(), 200);

        assertThat(log()).contains("ip=203.0.113.10").doesNotContain("198.51.100.99");
    }

    @Test
    void configuracaoMantemForwardedHeadersDesabilitadosPorPadrao() throws Exception {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(
                Path.of("src/main/resources/application.properties"))) {
            properties.load(input);
        }

        assertThat(properties.getProperty("server.forward-headers-strategy"))
                .isEqualTo("${SERVER_FORWARD_HEADERS_STRATEGY:NONE}");
        assertThat(properties.getProperty("server.tomcat.remoteip.remote-ip-header"))
                .isEqualTo("x-real-ip");
        assertThat(properties.getProperty("server.tomcat.remoteip.protocol-header"))
                .isEqualTo("x-forwarded-proto");
        assertThat(properties).doesNotContainKey("server.tomcat.remoteip.internal-proxies");
    }

    @Test
    void registraSomenteIdDoUsuarioAutenticado() throws Exception {
        User user = new User();
        user.setId(42L);
        user.setEmail("nao-logar@example.com");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));

        executar(requisicao(), new MockHttpServletResponse(), 200);

        assertThat(log()).contains("userId=42").doesNotContain("nao-logar@example.com");
    }

    @Test
    void ocultaPlanoTokenNoLogSemAlterarUriEntregueAoEndpoint() throws Exception {
        String token = "550e8400-e29b-41ce-98fd-7fd50b809672";
        MockHttpServletRequest request = requisicao("/training-plans/public/" + token);
        AtomicReference<String> uriRecebida = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> uriRecebida.set(((HttpServletRequest) req).getRequestURI()));

        assertThat(log())
                .contains("uri=/training-plans/public/[REDACTED]")
                .doesNotContain(token);
        assertThat(uriRecebida).hasValue("/training-plans/public/" + token);
    }

    @Test
    void ocultaPagamentoTokenEPreservaSufixoDaRota() throws Exception {
        String token = "550e8400-e29b-41ce-98fd-7fd50b809672";

        executar(requisicao("/api/pagamentos/public/" + token + "/resultado"),
                new MockHttpServletResponse(), 200);

        assertThat(log())
                .contains("uri=/api/pagamentos/public/[REDACTED]/resultado")
                .doesNotContain(token);
    }

    @Test
    void mantemRotaComumInalterada() throws Exception {
        executar(requisicao("/api/teste/123"), new MockHttpServletResponse(), 200);

        assertThat(log()).contains("uri=/api/teste/123");
    }

    private MockHttpServletRequest requisicao() {
        return requisicao("/api/teste");
    }

    private MockHttpServletRequest requisicao(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("User-Agent", "agent-test/1.0");
        return request;
    }

    private void executar(MockHttpServletRequest request, MockHttpServletResponse response, int status)
            throws ServletException, IOException {
        filter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(status));
    }

    private String log() {
        return appender.list.get(appender.list.size() - 1).getFormattedMessage();
    }
}
