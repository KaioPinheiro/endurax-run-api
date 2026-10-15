package com.kaio.runtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.client.IntervalsIcuCalendarClient;
import com.kaio.runtracker.client.IntervalsIcuEventRequest;
import com.kaio.runtracker.client.IntervalsIcuEventResponse;
import com.kaio.runtracker.dto.IntervalsIcuWorkoutSendRequest;
import com.kaio.runtracker.entity.IntervalsIcuConnection;
import com.kaio.runtracker.entity.IntervalsIcuWorkoutExport;
import com.kaio.runtracker.entity.Pagamento;
import com.kaio.runtracker.entity.TrainingPlan;
import com.kaio.runtracker.exception.IntervalsIcuWorkoutException;
import com.kaio.runtracker.repository.IntervalsIcuConnectionRepository;
import com.kaio.runtracker.repository.IntervalsIcuWorkoutExportRepository;
import com.kaio.runtracker.repository.PagamentoRepository;
import com.kaio.runtracker.security.IntervalsIcuTokenCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntervalsIcuWorkoutServiceTest {
    private PagamentoRepository paymentRepository;
    private IntervalsIcuConnectionRepository connectionRepository;
    private IntervalsIcuWorkoutExportRepository exportRepository;
    private IntervalsIcuCalendarClient calendarClient;
    private IntervalsIcuTokenCipher tokenCipher;
    private IntervalsIcuWorkoutService service;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PagamentoRepository.class);
        connectionRepository = mock(IntervalsIcuConnectionRepository.class);
        exportRepository = mock(IntervalsIcuWorkoutExportRepository.class);
        calendarClient = mock(IntervalsIcuCalendarClient.class);
        tokenCipher = mock(IntervalsIcuTokenCipher.class);
        service = new IntervalsIcuWorkoutService(paymentRepository, connectionRepository,
                exportRepository, calendarClient, tokenCipher,
                new IntervalsIcuWorkoutDescriptionConverter(), new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void enviaUmTreinoComTokenDecifradoERegistraIdExterno() {
        Pagamento payment = validPayment();
        IntervalsIcuConnection connection = connection(payment);
        var request = new IntervalsIcuWorkoutSendRequest(
                1, "terca-feira", LocalDate.of(2026, 10, 13));
        when(paymentRepository.findByValidPlanAccessTokenForUpdate("opaque-plan-token"))
                .thenReturn(Optional.of(payment));
        when(connectionRepository.findByPagamentoId(10L)).thenReturn(Optional.of(connection));
        when(exportRepository.findByPagamentoIdAndWorkoutKey(10L,
                "1:terca-feira:2026-10-13")).thenReturn(Optional.empty());
        when(tokenCipher.decrypt("encrypted-token")).thenReturn("provider-token");
        when(calendarClient.upsertWorkout(any(), any())).thenReturn(
                new IntervalsIcuEventResponse(987L, "Intervalado", "external"));
        when(exportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.sendOne("opaque-plan-token", request);

        ArgumentCaptor<IntervalsIcuEventRequest> event =
                ArgumentCaptor.forClass(IntervalsIcuEventRequest.class);
        verify(calendarClient).upsertWorkout(
                org.mockito.ArgumentMatchers.eq("provider-token"), event.capture());
        assertThat(event.getValue().startDateLocal()).isEqualTo("2026-10-13T00:00:00");
        assertThat(event.getValue().type()).isEqualTo("Run");
        assertThat(event.getValue().description()).contains("3x", "intensity=warmup",
                "intensity=interval", "intensity=recovery", "intensity=cooldown");
        assertThat(response.eventId()).isEqualTo(987L);
        assertThat(response.externalId()).startsWith("endurax-20-w1-terca-feira-");
        assertThat(response.alreadySent()).isFalse();
    }

    @Test
    void repeticaoRetornaRegistroExistenteSemNovoEnvio() {
        Pagamento payment = validPayment();
        var request = new IntervalsIcuWorkoutSendRequest(
                1, "terca-feira", LocalDate.of(2026, 10, 13));
        IntervalsIcuWorkoutExport existing = new IntervalsIcuWorkoutExport();
        existing.setIntervalsEventId(987L);
        existing.setExternalId("endurax-existing");
        existing.setWorkoutName("Intervalado");
        existing.setScheduledDate(request.scheduledDate());
        when(paymentRepository.findByValidPlanAccessTokenForUpdate("opaque-plan-token"))
                .thenReturn(Optional.of(payment));
        when(connectionRepository.findByPagamentoId(10L))
                .thenReturn(Optional.of(connection(payment)));
        when(exportRepository.findByPagamentoIdAndWorkoutKey(10L,
                "1:terca-feira:2026-10-13")).thenReturn(Optional.of(existing));

        var response = service.sendOne("opaque-plan-token", request);

        assertThat(response.alreadySent()).isTrue();
        verify(calendarClient, never()).upsertWorkout(any(), any());
        verify(tokenCipher, never()).decrypt(any());
    }

    @Test
    void exigeConexaoComEscopoDeCalendario() {
        Pagamento payment = validPayment();
        IntervalsIcuConnection connection = connection(payment);
        connection.setGrantedScope("ACTIVITY:READ");
        when(paymentRepository.findByValidPlanAccessTokenForUpdate("opaque-plan-token"))
                .thenReturn(Optional.of(payment));
        when(connectionRepository.findByPagamentoId(10L)).thenReturn(Optional.of(connection));

        assertThatThrownBy(() -> service.sendOne("opaque-plan-token",
                new IntervalsIcuWorkoutSendRequest(
                        1, "terca-feira", LocalDate.of(2026, 10, 13))))
                .isInstanceOf(IntervalsIcuWorkoutException.class)
                .hasMessageContaining("conexao ativa");
        verify(calendarClient, never()).upsertWorkout(any(), any());
    }

    private Pagamento validPayment() {
        TrainingPlan plan = new TrainingPlan();
        plan.setId(20L);
        plan.setDescricao("""
                {"semanas":[{"numeroSemana":1,"treinos":[{
                  "diaSemana":"terca-feira","titulo":"Intervalado","tipo":"Intervalado",
                  "descricao":"Aquecimento: 10 min leve a 6:20 min/km | Principal: 3 x (5 min forte a 4:30 min/km + 2 min de recuperacao a 6:30 min/km) | Desaquecimento: 10 min leve a 7:00 min/km",
                  "duracaoEstimada":"41 min","paceSugerido":"4:30 min/km"
                }]}]}""");
        Pagamento payment = new Pagamento();
        payment.setId(10L);
        payment.setTrainingPlan(plan);
        return payment;
    }

    private IntervalsIcuConnection connection(Pagamento payment) {
        IntervalsIcuConnection connection = new IntervalsIcuConnection();
        connection.setPagamento(payment);
        connection.setAccessTokenEncrypted("encrypted-token");
        connection.setGrantedScope("CALENDAR:WRITE");
        return connection;
    }
}
