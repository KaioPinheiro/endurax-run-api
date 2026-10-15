package com.kaio.runtracker.service;

import com.kaio.runtracker.client.IntervalsIcuOAuthClient;
import com.kaio.runtracker.client.IntervalsIcuTokenResponse;
import com.kaio.runtracker.config.IntervalsIcuProperties;
import com.kaio.runtracker.dto.IntervalsIcuConnectionStatusDTO;
import com.kaio.runtracker.entity.IntervalsIcuConnection;
import com.kaio.runtracker.entity.IntervalsIcuOAuthState;
import com.kaio.runtracker.entity.Pagamento;
import com.kaio.runtracker.exception.IntervalsIcuOAuthException;
import com.kaio.runtracker.repository.IntervalsIcuConnectionRepository;
import com.kaio.runtracker.repository.IntervalsIcuOAuthStateRepository;
import com.kaio.runtracker.repository.PagamentoRepository;
import com.kaio.runtracker.security.IntervalsIcuTokenCipher;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class IntervalsIcuOAuthService {
    static final String REQUIRED_SCOPE = "CALENDAR:WRITE";

    private final PagamentoRepository pagamentoRepository;
    private final IntervalsIcuOAuthStateRepository stateRepository;
    private final IntervalsIcuConnectionRepository connectionRepository;
    private final IntervalsIcuOAuthClient oauthClient;
    private final IntervalsIcuTokenCipher tokenCipher;
    private final IntervalsIcuProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public IntervalsIcuOAuthService(
            PagamentoRepository pagamentoRepository,
            IntervalsIcuOAuthStateRepository stateRepository,
            IntervalsIcuConnectionRepository connectionRepository,
            IntervalsIcuOAuthClient oauthClient,
            IntervalsIcuTokenCipher tokenCipher,
            IntervalsIcuProperties properties) {
        this(pagamentoRepository, stateRepository, connectionRepository, oauthClient,
                tokenCipher, properties, Clock.systemUTC(), new SecureRandom());
    }

    IntervalsIcuOAuthService(
            PagamentoRepository pagamentoRepository,
            IntervalsIcuOAuthStateRepository stateRepository,
            IntervalsIcuConnectionRepository connectionRepository,
            IntervalsIcuOAuthClient oauthClient,
            IntervalsIcuTokenCipher tokenCipher,
            IntervalsIcuProperties properties,
            Clock clock,
            SecureRandom secureRandom) {
        this.pagamentoRepository = pagamentoRepository;
        this.stateRepository = stateRepository;
        this.connectionRepository = connectionRepository;
        this.oauthClient = oauthClient;
        this.tokenCipher = tokenCipher;
        this.properties = properties;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    @Transactional
    public URI beginAuthorization(String planAccessToken) {
        requireConfigured();
        Pagamento pagamento = findPlanAccess(planAccessToken);
        String rawState = newState();
        LocalDateTime now = LocalDateTime.now(clock);

        IntervalsIcuOAuthState state = new IntervalsIcuOAuthState();
        state.setStateHash(hash(rawState));
        state.setPagamento(pagamento);
        state.setCreatedAt(now);
        state.setExpiresAt(now.plusMinutes(properties.getStateTtlMinutes()));
        stateRepository.save(state);

        return UriComponentsBuilder.fromUriString(properties.getAuthorizationUrl())
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("scope", REQUIRED_SCOPE)
                .queryParam("state", rawState)
                .build().encode().toUri();
    }

    @Transactional
    public IntervalsIcuConnectionStatusDTO completeAuthorization(
            String code,
            String rawState,
            String providerError) {
        requireConfigured();
        if (!StringUtils.hasText(rawState)) {
            throw new IntervalsIcuOAuthException(BAD_REQUEST, "State OAuth ausente ou inválido.");
        }

        IntervalsIcuOAuthState state = stateRepository.findByStateHashForUpdate(hash(rawState))
                .orElseThrow(() -> new IntervalsIcuOAuthException(BAD_REQUEST,
                        "State OAuth ausente ou inválido."));
        LocalDateTime now = LocalDateTime.now(clock);
        if (state.getConsumedAt() != null || !state.getExpiresAt().isAfter(now)) {
            throw new IntervalsIcuOAuthException(BAD_REQUEST, "State OAuth expirado ou já utilizado.");
        }
        if (StringUtils.hasText(providerError)) {
            state.setConsumedAt(now);
            stateRepository.save(state);
            throw new IntervalsIcuOAuthException(BAD_REQUEST,
                    "A autorização no Intervals.icu não foi concedida.");
        }
        if (!StringUtils.hasText(code)) {
            throw new IntervalsIcuOAuthException(BAD_REQUEST, "Código de autorização ausente.");
        }

        state.setConsumedAt(now);
        stateRepository.save(state);
        IntervalsIcuTokenResponse token = oauthClient.exchangeCode(code);
        if (!hasRequiredScope(token.scope())) {
            throw new IntervalsIcuOAuthException(BAD_REQUEST,
                    "O Intervals.icu não concedeu o escopo CALENDAR:WRITE.");
        }

        Pagamento pagamento = state.getPagamento();
        IntervalsIcuConnection connection = connectionRepository.findByPagamentoId(pagamento.getId())
                .orElseGet(IntervalsIcuConnection::new);
        connection.setPagamento(pagamento);
        connection.setAccessTokenEncrypted(tokenCipher.encrypt(token.accessToken()));
        connection.setAthleteId(token.athlete().id());
        connection.setAthleteName(token.athlete().name());
        connection.setGrantedScope(token.scope());
        if (connection.getConnectedAt() == null) connection.setConnectedAt(now);
        connection.setUpdatedAt(now);
        connectionRepository.save(connection);
        return toStatus(connection);
    }

    @Transactional(readOnly = true)
    public IntervalsIcuConnectionStatusDTO connectionStatus(String planAccessToken) {
        requireConfigured();
        Pagamento pagamento = findPlanAccess(planAccessToken);
        return connectionRepository.findByPagamentoId(pagamento.getId())
                .map(this::toStatus)
                .orElseGet(() -> new IntervalsIcuConnectionStatusDTO(false, null, null, null));
    }

    private Pagamento findPlanAccess(String token) {
        if (!StringUtils.hasText(token)) {
            throw new IntervalsIcuOAuthException(NOT_FOUND, "Plano não encontrado.");
        }
        return pagamentoRepository.findByValidPlanAccessToken(token)
                .orElseThrow(() -> new IntervalsIcuOAuthException(NOT_FOUND, "Plano não encontrado."));
    }

    private IntervalsIcuConnectionStatusDTO toStatus(IntervalsIcuConnection connection) {
        return new IntervalsIcuConnectionStatusDTO(true, connection.getAthleteName(),
                connection.getGrantedScope(), connection.getConnectedAt());
    }

    private boolean hasRequiredScope(String scope) {
        if (!StringUtils.hasText(scope)) return false;
        return java.util.Arrays.stream(scope.split("[,\\s]+"))
                .anyMatch(REQUIRED_SCOPE::equals);
    }

    private String newState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível.", exception);
        }
    }

    private void requireConfigured() {
        try {
            properties.requireConfigured();
        } catch (IllegalStateException exception) {
            throw new IntervalsIcuOAuthException(SERVICE_UNAVAILABLE,
                    "A conexão com o Intervals.icu não está disponível.", exception);
        }
    }
}
