package com.mac.audit.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mac.audit.config.properties.ErrorAlertProperties;
import com.mac.audit.entities.model.ErrorAlert;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CentralizedAlertClientTest {

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void sendsAlertWithAuthorizationHeader() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(202);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        CentralizedAlertClient client = new CentralizedAlertClient(
                httpClient, new ObjectMapper(), properties(true, List.of("ops@example.com"), "Bearer token"));

        client.send(ErrorAlert.failure("trace-1", "kafka", "consume"));

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().headers().firstValue("Authorization")).contains("Bearer token");
        assertThat(captor.getValue().headers().firstValue("X-Correlation-Id")).contains("trace-1");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void skipsDisabledConfigurationAndHandlesDeliveryFailures() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        ErrorAlert alert = ErrorAlert.failure("trace", "http", "request");
        new CentralizedAlertClient(httpClient, new ObjectMapper(), properties(false, List.of("ops@example.com"), ""))
                .send(alert);
        new CentralizedAlertClient(httpClient, new ObjectMapper(), properties(true, List.of(), ""))
                .send(alert);
        verify(httpClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(503);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response)
                .thenThrow(new IOException("down"))
                .thenThrow(new InterruptedException("stop"));
        CentralizedAlertClient client = new CentralizedAlertClient(
                httpClient, new ObjectMapper(), properties(true, List.of("ops@example.com"), ""));
        client.send(alert);
        client.send(alert);
        client.send(alert);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    private static ErrorAlertProperties properties(boolean enabled, List<String> recipients, String auth) {
        return new ErrorAlertProperties(enabled, URI.create("https://alert.example.com/api/v1/alert"),
                "audit@example.com", "Audit", recipients, auth, Duration.ofSeconds(2));
    }
}
