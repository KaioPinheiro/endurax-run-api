package com.kaio.runtracker.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class AccessLogFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    static final String MDC_REQUEST_ID = "requestId";
    private static final int MAX_REQUEST_ID_LENGTH = 64;
    private static final Pattern SAFE_REQUEST_ID =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0," + (MAX_REQUEST_ID_LENGTH - 1) + "}");
    private static final Pattern SECRET_LIKE = Pattern.compile(
            "(?i)(?:Bearer\\s+\\S+|sk-[A-Za-z0-9_-]{10,}|(?:api[_-]?key|password|senha)=\\S+|[A-Za-z0-9_-]{16,}\\.[A-Za-z0-9_-]{16,}\\.[A-Za-z0-9_-]{16,})");
    private static final Pattern PLANO_TOKEN_PATH =
            Pattern.compile("^(/training-plans/public/)[^/]+");
    private static final Pattern PAGAMENTO_TOKEN_PATH =
            Pattern.compile("^(/api/pagamentos/public/)[^/]+");
    private static final Logger logger = LoggerFactory.getLogger(AccessLogFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
        long inicio = System.nanoTime();
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            try {
                registrarAcesso(requestId, request, response, inicio);
            } finally {
                MDC.remove(MDC_REQUEST_ID);
            }
        }
    }

    private void registrarAcesso(
            String requestId,
            HttpServletRequest request,
            HttpServletResponse response,
            long inicio) {
        String uri = textoSeguro(sanitizarUri(request.getRequestURI()), 1024);
        long duracaoMs = (System.nanoTime() - inicio) / 1_000_000;
        int status = response.getStatus();
        String formato = "access requestId={} method={} uri={} status={} duracaoMs={}";

        if (status >= 500) {
            logger.error(formato, requestId, request.getMethod(), uri, status, duracaoMs);
        } else if (status >= 400) {
            logger.warn(formato, requestId, request.getMethod(), uri, status, duracaoMs);
        } else {
            logger.debug(formato, requestId, request.getMethod(), uri, status, duracaoMs);
        }
    }

    private String requestId(String recebido) {
        return recebido != null
                && SAFE_REQUEST_ID.matcher(recebido).matches()
                && !SECRET_LIKE.matcher(recebido).find()
                ? recebido
                : UUID.randomUUID().toString();
    }

    private String textoSeguro(String valor, int tamanhoMaximo) {
        if (valor == null || valor.isBlank()) return "-";
        String seguro = valor.replaceAll("[\\r\\n\\t]", " ");
        seguro = SECRET_LIKE.matcher(seguro).replaceAll("[REDACTED]");
        return seguro.length() <= tamanhoMaximo ? seguro : seguro.substring(0, tamanhoMaximo);
    }

    private String sanitizarUri(String uri) {
        if (uri == null) return null;
        String sanitizada = PLANO_TOKEN_PATH.matcher(uri).replaceFirst("$1[REDACTED]");
        return PAGAMENTO_TOKEN_PATH.matcher(sanitizada).replaceFirst("$1[REDACTED]");
    }
}
