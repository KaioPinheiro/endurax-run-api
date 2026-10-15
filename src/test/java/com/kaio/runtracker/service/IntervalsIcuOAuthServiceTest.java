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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntervalsIcuOAuthServiceTest {
    private PagamentoRepository pagamentoRepository;
    private IntervalsIcuOAuthStateRepository stateRepository;
    private IntervalsIcuConnectionRepository connectionRepository;
    private IntervalsIcuOAuthClient client;
    private IntervalsIcuTokenCipher cipher;
    private IntervalsIcuProperties properties;
    private IntervalsIcuOAuthService service;
    private Pagamento pagamento;

    @BeforeEach
    void setUp() {
        pagamentoRepository = mock(PagamentoRepository.class);
        stateRepository = mock(IntervalsIcuOAuthStateRepository.class);
        connectionRepository = mock(IntervalsIcuConnectionRepository.class);
        client = mock(IntervalsIcuOAuthClient.class);
        cipher = mock(IntervalsIcuTokenCipher.class);
        properties = properties();
        pagamento = new Pagamento();
        pagamento.setId(42L);
        service = new IntervalsIcuOAuthService(pagamentoRepository, stateRepository,
                connectionRepository, client, cipher, properties,
                Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC),
                new java.security.SecureRandom());
    }

    @Test
    void inicioResolvePlanoNoBackendEPersisteApenasHashDoState() {
        when(pagamentoRepository.findByValidPlanAccessToken("plan-token-opaco"))
                .thenReturn(Optional.of(pagamento));
        ArgumentCaptor<IntervalsIcuOAuthState> captor = ArgumentCaptor.forClass(IntervalsIcuOAuthState.class);

        URI authorization = service.beginAuthorization("plan-token-opaco");

        verify(stateRepository).save(captor.capture());
        IntervalsIcuOAuthState saved = captor.getValue();
        assertEquals(64, saved.getStateHash().length());
        assertEquals(pagamento, saved.getPagamento());
        assertEquals(LocalDateTime.of(2026, 10, 10, 12, 10), saved.getExpiresAt());
        assertTrue(authorization.toString().contains("scope=CALENDAR:WRITE"));
        assertTrue(authorization.toString().contains("client_id=client-id"));
        assertFalse(authorization.toString().contains("plan-token-opaco"));
        assertFalse(authorization.toString().contains("client-secret"));
    }

    @Test
    void callbackConsomeStateEArmazenaSomenteTokenCifrado() {
        IntervalsIcuOAuthState state = validState();
        when(stateRepository.findByStateHashForUpdate(anyString())).thenReturn(Optional.of(state));
        when(client.exchangeCode("authorization-code")).thenReturn(new IntervalsIcuTokenResponse(
                "Bearer", "access-token-secreto", "CALENDAR:WRITE",
                new IntervalsIcuTokenResponse.Athlete("i123", "Atleta")));
        when(cipher.encrypt("access-token-secreto")).thenReturn("token-cifrado");
        when(connectionRepository.findByPagamentoId(42L)).thenReturn(Optional.empty());
        ArgumentCaptor<IntervalsIcuConnection> captor = ArgumentCaptor.forClass(IntervalsIcuConnection.class);

        IntervalsIcuConnectionStatusDTO result = service.completeAuthorization(
                "authorization-code", "state-original", null);

        verify(connectionRepository).save(captor.capture());
        assertEquals("token-cifrado", captor.getValue().getAccessTokenEncrypted());
        assertFalse(captor.getValue().getAccessTokenEncrypted().contains("access-token-secreto"));
        assertNotNull(state.getConsumedAt());
        assertTrue(result.connected());
        assertEquals("Atleta", result.athleteName());
    }

    @Test
    void callbackRejeitaStateReutilizadoAntesDeTrocarCode() {
        IntervalsIcuOAuthState state = validState();
        state.setConsumedAt(LocalDateTime.of(2026, 10, 10, 11, 59));
        when(stateRepository.findByStateHashForUpdate(anyString())).thenReturn(Optional.of(state));

        assertThrows(IntervalsIcuOAuthException.class,
                () -> service.completeAuthorization("code", "state", null));
    }

    @Test
    void callbackRejeitaTokenSemEscopoDeCalendario() {
        when(stateRepository.findByStateHashForUpdate(anyString()))
                .thenReturn(Optional.of(validState()));
        when(client.exchangeCode(anyString())).thenReturn(new IntervalsIcuTokenResponse(
                "Bearer", "token", "ACTIVITY:READ",
                new IntervalsIcuTokenResponse.Athlete("i123", "Atleta")));

        assertThrows(IntervalsIcuOAuthException.class,
                () -> service.completeAuthorization("code", "state", null));
    }

    private IntervalsIcuOAuthState validState() {
        IntervalsIcuOAuthState state = new IntervalsIcuOAuthState();
        state.setPagamento(pagamento);
        state.setExpiresAt(LocalDateTime.of(2026, 10, 10, 12, 10));
        state.setCreatedAt(LocalDateTime.of(2026, 10, 10, 12, 0));
        return state;
    }

    private IntervalsIcuProperties properties() {
        IntervalsIcuProperties value = new IntervalsIcuProperties();
        value.setClientId("client-id");
        value.setClientSecret("client-secret");
        value.setRedirectUri("https://api.example.com/api/integrations/intervals-icu/callback");
        value.setTokenEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        return value;
    }
}
