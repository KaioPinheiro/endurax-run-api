package com.kaio.runtracker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.client.IntervalsIcuCalendarClient;
import com.kaio.runtracker.client.IntervalsIcuEventRequest;
import com.kaio.runtracker.dto.IntervalsIcuWorkoutSendRequest;
import com.kaio.runtracker.dto.IntervalsIcuWorkoutSendResponse;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import com.kaio.runtracker.dto.SemanaPlanoIAResponseDTO;
import com.kaio.runtracker.dto.TreinoPlanoIAResponseDTO;
import com.kaio.runtracker.entity.IntervalsIcuConnection;
import com.kaio.runtracker.entity.IntervalsIcuWorkoutExport;
import com.kaio.runtracker.entity.Pagamento;
import com.kaio.runtracker.exception.IntervalsIcuWorkoutException;
import com.kaio.runtracker.repository.IntervalsIcuConnectionRepository;
import com.kaio.runtracker.repository.IntervalsIcuWorkoutExportRepository;
import com.kaio.runtracker.repository.PagamentoRepository;
import com.kaio.runtracker.security.IntervalsIcuTokenCipher;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class IntervalsIcuWorkoutService {
    private final PagamentoRepository pagamentoRepository;
    private final IntervalsIcuConnectionRepository connectionRepository;
    private final IntervalsIcuWorkoutExportRepository exportRepository;
    private final IntervalsIcuCalendarClient calendarClient;
    private final IntervalsIcuTokenCipher tokenCipher;
    private final IntervalsIcuWorkoutDescriptionConverter converter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public IntervalsIcuWorkoutService(
            PagamentoRepository pagamentoRepository,
            IntervalsIcuConnectionRepository connectionRepository,
            IntervalsIcuWorkoutExportRepository exportRepository,
            IntervalsIcuCalendarClient calendarClient,
            IntervalsIcuTokenCipher tokenCipher,
            IntervalsIcuWorkoutDescriptionConverter converter,
            ObjectMapper objectMapper) {
        this(pagamentoRepository, connectionRepository, exportRepository, calendarClient,
                tokenCipher, converter, objectMapper, Clock.systemUTC());
    }

    IntervalsIcuWorkoutService(
            PagamentoRepository pagamentoRepository,
            IntervalsIcuConnectionRepository connectionRepository,
            IntervalsIcuWorkoutExportRepository exportRepository,
            IntervalsIcuCalendarClient calendarClient,
            IntervalsIcuTokenCipher tokenCipher,
            IntervalsIcuWorkoutDescriptionConverter converter,
            ObjectMapper objectMapper,
            Clock clock) {
        this.pagamentoRepository = pagamentoRepository;
        this.connectionRepository = connectionRepository;
        this.exportRepository = exportRepository;
        this.calendarClient = calendarClient;
        this.tokenCipher = tokenCipher;
        this.converter = converter;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public IntervalsIcuWorkoutSendResponse sendOne(
            String planAccessToken, IntervalsIcuWorkoutSendRequest request) {
        Pagamento payment = pagamentoRepository.findByValidPlanAccessTokenForUpdate(planAccessToken)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Plano nao encontrado."));
        IntervalsIcuConnection connection = connectionRepository.findByPagamentoId(payment.getId())
                .filter(this::hasCalendarWriteScope)
                .orElseThrow(() -> error(HttpStatus.CONFLICT,
                        "O plano nao possui uma conexao ativa com o Intervals.icu."));

        String workoutKey = request.weekNumber() + ":" + normalize(request.dayOfWeek())
                + ":" + request.scheduledDate();
        var previous = exportRepository.findByPagamentoIdAndWorkoutKey(
                payment.getId(), workoutKey);
        if (previous.isPresent()) return response(previous.get(), true);

        TreinoPlanoIAResponseDTO workout = findWorkout(payment, request);
        if (isRest(workout)) {
            throw error(HttpStatus.UNPROCESSABLE_ENTITY,
                    "O item selecionado e um dia de descanso.");
        }
        if (!StringUtils.hasText(workout.getTitulo())) {
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "O treino selecionado nao possui titulo.");
        }

        String externalId = "endurax-" + payment.getTrainingPlan().getId() + "-w"
                + request.weekNumber() + "-" + normalize(request.dayOfWeek()) + "-"
                + request.scheduledDate();
        String description = converter.convert(workout);
        String accessToken = tokenCipher.decrypt(connection.getAccessTokenEncrypted());
        var event = calendarClient.upsertWorkout(accessToken, new IntervalsIcuEventRequest(
                "WORKOUT",
                request.scheduledDate() + "T00:00:00",
                "Run",
                workout.getTitulo(),
                description,
                externalId));

        IntervalsIcuWorkoutExport export = new IntervalsIcuWorkoutExport();
        export.setPagamento(payment);
        export.setWorkoutKey(workoutKey);
        export.setExternalId(externalId);
        export.setIntervalsEventId(event.id());
        export.setWorkoutName(StringUtils.hasText(event.name())
                ? event.name() : workout.getTitulo());
        export.setScheduledDate(request.scheduledDate());
        export.setCreatedAt(LocalDateTime.now(clock));
        return response(exportRepository.save(export), false);
    }

    private TreinoPlanoIAResponseDTO findWorkout(
            Pagamento payment, IntervalsIcuWorkoutSendRequest request) {
        try {
            PlanoTreinoIAResponseDTO plan = objectMapper.readValue(
                    payment.getTrainingPlan().getDescricao(), PlanoTreinoIAResponseDTO.class);
            SemanaPlanoIAResponseDTO week = plan.getSemanas().stream()
                    .filter(candidate -> request.weekNumber().equals(candidate.getNumeroSemana()))
                    .findFirst()
                    .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Semana nao encontrada no plano."));
            return week.getTreinos().stream()
                    .filter(candidate -> normalize(request.dayOfWeek())
                            .equals(normalize(candidate.getDiaSemana())))
                    .findFirst()
                    .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Treino nao encontrado no plano."));
        } catch (JsonProcessingException | NullPointerException exception) {
            throw new IntervalsIcuWorkoutException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "O plano salvo nao pode ser convertido para o Intervals.icu.", exception);
        }
    }

    private boolean hasCalendarWriteScope(IntervalsIcuConnection connection) {
        return StringUtils.hasText(connection.getAccessTokenEncrypted())
                && StringUtils.hasText(connection.getGrantedScope())
                && java.util.Arrays.stream(connection.getGrantedScope().split("[,\\s]+"))
                .anyMatch("CALENDAR:WRITE"::equals);
    }

    private boolean isRest(TreinoPlanoIAResponseDTO workout) {
        return normalize(workout.getTipo()).contains("descanso")
                || normalize(workout.getTitulo()).contains("descanso");
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private IntervalsIcuWorkoutSendResponse response(
            IntervalsIcuWorkoutExport export, boolean alreadySent) {
        return new IntervalsIcuWorkoutSendResponse(export.getIntervalsEventId(),
                export.getExternalId(), export.getWorkoutName(), export.getScheduledDate(),
                alreadySent);
    }

    private IntervalsIcuWorkoutException error(HttpStatus status, String message) {
        return new IntervalsIcuWorkoutException(status, message);
    }
}
