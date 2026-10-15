package com.kaio.runtracker.client;

import com.kaio.runtracker.config.IntervalsIcuProperties;
import com.kaio.runtracker.exception.IntervalsIcuOAuthException;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Component
public class IntervalsIcuOAuthClient {
    private final IntervalsIcuProperties properties;
    private final RestClient restClient;

    @Autowired
    public IntervalsIcuOAuthClient(IntervalsIcuProperties properties) {
        this(properties, createRestClient(properties));
    }

    IntervalsIcuOAuthClient(IntervalsIcuProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public IntervalsIcuTokenResponse exchangeCode(String code) {
        properties.requireConfigured();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("code", code);

        try {
            IntervalsIcuTokenResponse response = restClient.post()
                    .uri(properties.getTokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(IntervalsIcuTokenResponse.class);
            if (response == null || !StringUtils.hasText(response.accessToken())
                    || response.athlete() == null || !StringUtils.hasText(response.athlete().id())) {
                throw new IntervalsIcuOAuthException(BAD_GATEWAY,
                        "O Intervals.icu retornou uma resposta de autorização inválida.");
            }
            return response;
        } catch (IntervalsIcuOAuthException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw new IntervalsIcuOAuthException(BAD_GATEWAY,
                    "O Intervals.icu recusou a autorização.", exception);
        } catch (RestClientException exception) {
            throw new IntervalsIcuOAuthException(SERVICE_UNAVAILABLE,
                    "O Intervals.icu está temporariamente indisponível.", exception);
        }
    }

    private static RestClient createRestClient(IntervalsIcuProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs())).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return RestClient.builder().requestFactory(factory).build();
    }
}
