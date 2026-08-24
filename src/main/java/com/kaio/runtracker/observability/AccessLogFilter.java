package com.kaio.runtracker.observability;

import com.kaio.runtracker.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class AccessLogFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    static final String MDC_REQUEST_ID = "requestId";
    private static final int MAX_REQUEST_ID_LENGTH = 64;
    private static final int MAX_USER_AGENT_LENGTH = 256;
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
                logger.info(
                        "access timestamp={} requestId={} method={} uri={} status={} durationMs={} ip={} userAgent={} userId={}",
                        Instant.now(), requestId, request.getMethod(),
                        textoSeguro(sanitizarUri(request.getRequestURI()), 1024), response.getStatus(),
                        (System.nanoTime() - inicio) / 1_000_000, request.getRemoteAddr(),
                        textoSeguro(request.getHeader("User-Agent"), MAX_USER_AGENT_LENGTH), userId());
            } finally {
                MDC.remove(MDC_REQUEST_ID);
            }
        }
    }

    private String requestId(String recebido) {
        return recebido != null
                && SAFE_REQUEST_ID.matcher(recebido).matches()
                && !SECRET_LIKE.matcher(recebido).find()
                ? recebido
                : UUID.randomUUID().toString();
    }

    private String userId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return "-";
        return authentication.getPrincipal() instanceof User user && user.getId() != null
                ? user.getId().toString()
                : "-";
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
