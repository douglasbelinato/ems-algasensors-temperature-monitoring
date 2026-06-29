package com.algaworks.algasensors.temperature.monitoring.api.controller;

import com.algaworks.algasensors.temperature.monitoring.domain.model.SensorId;
import com.algaworks.algasensors.temperature.monitoring.domain.model.SensorMonitoring;
import com.algaworks.algasensors.temperature.monitoring.domain.repository.SensorMonitoringRepository;
import io.hypersistence.tsid.TSID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SensorMonitoringControllerIT {

    private static final String DETAIL_PATH = "/api/sensors/{id}/monitoring";
    private static final String ENABLE_PATH = "/api/sensors/{id}/monitoring/enable";

    @LocalServerPort
    private int port;

    // No broker in tests: mock the RabbitAdmin so RabbitMQInitializer#init() is a no-op.
    @MockitoBean
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private SensorMonitoringRepository sensorMonitoringRepository;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        sensorMonitoringRepository.deleteAll();
    }

    @Nested
    @DisplayName("GET /api/sensors/{id}/monitoring")
    class GetDetail {

        @Test
        @DisplayName("should return a disabled default detail when the monitoring does not exist")
        void shouldReturnDefaultWhenMonitoringDoesNotExist() {
            TSID sensorId = TSID.fast();

            client.get().uri(DETAIL_PATH, sensorId.toString())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(sensorId.toString())
                    .jsonPath("$.enabled").isEqualTo(false)
                    .jsonPath("$.lastTemperature").isEmpty()
                    .jsonPath("$.updatedAt").isEmpty();

            assertEquals(0, sensorMonitoringRepository.count());
        }

        @Test
        @DisplayName("should return the stored detail when the monitoring exists")
        void shouldReturnStoredMonitoring() {
            SensorMonitoring monitoring = persistMonitoring(TSID.fast(), true, 25.5, OffsetDateTime.now());

            client.get().uri(DETAIL_PATH, idOf(monitoring))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(idOf(monitoring))
                    .jsonPath("$.enabled").isEqualTo(true)
                    .jsonPath("$.lastTemperature").isEqualTo(25.5)
                    .jsonPath("$.updatedAt").exists();
        }
    }

    @Nested
    @DisplayName("PUT/DELETE /api/sensors/{id}/monitoring/enable")
    class EnableDisable {

        @ParameterizedTest(name = "{0} /enable: {1} -> {2}")
        @DisplayName("should toggle the enabled state of an existing monitoring and return 204")
        @CsvSource({
                "PUT, false, true",
                "DELETE, true, false"
        })
        void shouldToggleEnabledState(String method, boolean initialState, boolean expectedState) {
            SensorMonitoring monitoring = persistMonitoring(TSID.fast(), initialState, null, null);

            client.method(HttpMethod.valueOf(method)).uri(ENABLE_PATH, idOf(monitoring))
                    .exchange()
                    .expectStatus().isNoContent()
                    .expectBody().isEmpty();

            client.get().uri(DETAIL_PATH, idOf(monitoring))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.enabled").isEqualTo(expectedState);
        }

        @Test
        @DisplayName("should return 422 when enabling a monitoring that is already enabled")
        void shouldReturnUnprocessableWhenAlreadyEnabled() {
            SensorMonitoring monitoring = persistMonitoring(TSID.fast(), true, null, null);

            client.put().uri(ENABLE_PATH, idOf(monitoring))
                    .exchange()
                    .expectStatus().isEqualTo(422);

            client.get().uri(DETAIL_PATH, idOf(monitoring))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.enabled").isEqualTo(true);
        }

        @Test
        @DisplayName("should create and enable the monitoring when it does not exist yet")
        void shouldCreateAndEnableWhenAbsent() {
            TSID sensorId = TSID.fast();

            client.put().uri(ENABLE_PATH, sensorId.toString())
                    .exchange()
                    .expectStatus().isNoContent()
                    .expectBody().isEmpty();

            assertEquals(1, sensorMonitoringRepository.count());
            assertTrue(sensorMonitoringRepository.findById(new SensorId(sensorId)).orElseThrow().getEnabled());

            client.get().uri(DETAIL_PATH, sensorId.toString())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.enabled").isEqualTo(true);
        }

        @Test
        @DisplayName("should still return 204 and keep it disabled when disabling an already disabled monitoring")
        void shouldHandleDisableWhenAlreadyDisabled() {
            // Intentional design: disable() sleeps 10s when the monitoring is already off
            // (see CLAUDE.md). This test documents that the request still succeeds.
            SensorMonitoring monitoring = persistMonitoring(TSID.fast(), false, null, null);

            client.delete().uri(ENABLE_PATH, idOf(monitoring))
                    .exchange()
                    .expectStatus().isNoContent()
                    .expectBody().isEmpty();

            assertEquals(Boolean.FALSE, sensorMonitoringRepository.findById(monitoring.getId())
                    .orElseThrow().getEnabled());
        }
    }

    private SensorMonitoring persistMonitoring(TSID sensorId, boolean enabled,
                                               Double lastTemperature, OffsetDateTime updatedAt) {
        return sensorMonitoringRepository.saveAndFlush(SensorMonitoring.builder()
                .id(new SensorId(sensorId))
                .enabled(enabled)
                .lastTemperature(lastTemperature)
                .updatedAt(updatedAt)
                .build());
    }

    private String idOf(SensorMonitoring monitoring) {
        return monitoring.getId().getValue().toString();
    }
}