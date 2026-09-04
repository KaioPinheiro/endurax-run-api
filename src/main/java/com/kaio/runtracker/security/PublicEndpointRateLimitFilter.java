package com.kaio.runtracker.security;

import com.kaio.runtracker.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 5)
public class PublicEndpointRateLimitFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(PublicEndpointRateLimitFilter.class);
    private static final String RESPONSE_BODY = "{\"erro\":\"Muitas requisições. Tente novamente mais tarde.\"}";
    private static final long CLEANUP_INTERVAL = 256;

    private final RateLimitProperties properties;
    private final Clock clock;
    private final Map<ClientKey, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong requestsSinceCleanup = new AtomicLong();

    @Autowired
    public PublicEndpointRateLimitFilter(RateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    PublicEndpointRateLimitFilter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Rule rule = ruleFor(request.getMethod(), request.getRequestURI());
        if (!properties.isEnabled() || rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = clock.millis();
        cleanupIfNeeded(now);
        ClientKey key = new ClientKey(rule.category(), request.getRemoteAddr());
        Decision decision = acquire(key, rule.limit(), now);
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfter = Math.max(1, (decision.resetAtMillis() - now + 999) / 1_000);
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retryAfter));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(RESPONSE_BODY);
        logger.warn("Rate limit excedido: categoria={} requestId={} uri={}",
                rule.category(), MDC.get("requestId"), rule.safeLogPath());
    }

    private synchronized Decision acquire(ClientKey key, RateLimitProperties.Limit limit, long now) {
        if (!windows.containsKey(key) && windows.size() >= properties.getMaxEntries()) {
            cleanup(now);
            if (windows.size() >= properties.getMaxEntries()) {
                return new Decision(false, now + limit.getWindowSeconds() * 1_000);
            }
        }

        DecisionHolder holder = new DecisionHolder();
        windows.compute(key, (ignored, current) -> {
            long windowMillis = limit.getWindowSeconds() * 1_000;
            if (current == null || now >= current.resetAtMillis()) {
                holder.decision = new Decision(true, now + windowMillis);
                return new Window(1, now + windowMillis);
            }
            if (current.count() < limit.getRequests()) {
                holder.decision = new Decision(true, current.resetAtMillis());
                return new Window(current.count() + 1, current.resetAtMillis());
            }
            holder.decision = new Decision(false, current.resetAtMillis());
            return current;
        });
        return holder.decision;
    }

    private void cleanupIfNeeded(long now) {
        if (requestsSinceCleanup.incrementAndGet() >= CLEANUP_INTERVAL) {
            requestsSinceCleanup.set(0);
            cleanup(now);
        }
    }

    private void cleanup(long now) {
        windows.entrySet().removeIf(entry -> now >= entry.getValue().resetAtMillis());
    }

    private Rule ruleFor(String method, String uri) {
        if ("POST".equals(method) && "/api/solicitacoes-plano".equals(uri)) {
            return new Rule("CRIACAO_SOLICITACAO", properties.getSolicitacao(), uri);
        }
        if ("POST".equals(method) && "/api/pagamentos/pix".equals(uri)) {
            return new Rule("CRIACAO_PIX", properties.getPix(), uri);
        }
        if ("POST".equals(method)
                && uri.matches("^/api/pagamentos/public/[^/]+/cancelar$")) {
            return new Rule("CANCELAMENTO_PIX", properties.getPix(),
                    "/api/pagamentos/public/[REDACTED]/cancelar");
        }
        if ("POST".equals(method) && ("/api/ai/gerar-plano".equals(uri)
                || "/api/ai/gerar-treino".equals(uri))) {
            return new Rule("GERACAO_IA", properties.getGeracao(), uri);
        }
        if ("POST".equals(method)
                && uri.matches("^/api/pagamentos/public/[^/]+/geracao/tentar-novamente$")) {
            return new Rule("GERACAO_IA", properties.getGeracao(),
                    "/api/pagamentos/public/[REDACTED]/geracao/tentar-novamente");
        }
        if ("GET".equals(method) && uri.matches("^/api/pagamentos/public/[^/]+/(status|resultado)$")) {
            String suffix = uri.endsWith("/status") ? "status" : "resultado";
            return new Rule("CONSULTA", properties.getConsulta(),
                    "/api/pagamentos/public/[REDACTED]/" + suffix);
        }
        if ("GET".equals(method) && uri.matches("^/training-plans/public/[^/]+$")) {
            return new Rule("CONSULTA", properties.getConsulta(),
                    "/training-plans/public/[REDACTED]");
        }
        return null;
    }

    private record ClientKey(String category, String clientAddress) {
    }

    private record Window(int count, long resetAtMillis) {
    }

    private record Rule(String category, RateLimitProperties.Limit limit, String safeLogPath) {
    }

    private record Decision(boolean allowed, long resetAtMillis) {
    }

    private static final class DecisionHolder {
        private Decision decision;
    }
}
