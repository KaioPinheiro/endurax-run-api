package com.kaio.runtracker.client;

import com.kaio.runtracker.config.IntervalsIcuProperties;
import com.kaio.runtracker.exception.IntervalsIcuWorkoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Component
public class IntervalsIcuCalendarClient {
    private final IntervalsIcuProperties properties;
    private final RestClient restClient;

    @Autowired
    public IntervalsIcuCalendarClient(IntervalsIcuProperties properties) {
        this(properties, RestClient.create());
    }

    IntervalsIcuCalendarClient(IntervalsIcuProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public IntervalsIcuEventResponse upsertWorkout(
            String accessToken, IntervalsIcuEventRequest event) {
        try {
            IntervalsIcuEventResponse[] response = restClient.post()
                    .uri(properties.getApiBaseUrl()
                            + "/athlete/0/events/bulk?upsert=true")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(List.of(event))
                    .retrieve()
                    .body(IntervalsIcuEventResponse[].class);
            if (response == null || response.length != 1 || response[0].id() == null) {
                throw new IntervalsIcuWorkoutException(BAD_GATEWAY,
                        "O Intervals.icu retornou uma resposta invalida ao criar o treino.");
            }
            return response[0];
        } catch (IntervalsIcuWorkoutException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw new IntervalsIcuWorkoutException(BAD_GATEWAY,
                    "O Intervals.icu recusou o envio do treino.", exception);
        } catch (RestClientException exception) {
            throw new IntervalsIcuWorkoutException(SERVICE_UNAVAILABLE,
                    "O Intervals.icu esta temporariamente indisponivel.", exception);
        }
    }
}
