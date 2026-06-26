package com.algaworks.algasensors.temperature.monitoring.api.controller;

import com.algaworks.algasensors.temperature.monitoring.api.model.SensorAlertInput;
import com.algaworks.algasensors.temperature.monitoring.domain.model.SensorAlert;
import com.algaworks.algasensors.temperature.monitoring.domain.model.SensorId;
import com.algaworks.algasensors.temperature.monitoring.domain.repository.SensorAlertRepository;
import io.hypersistence.tsid.TSID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SensorAlertControllerIT {

    private static final String ALERT_PATH = "/api/sensors/{id}/alert";

    @LocalServerPort
    private int port;

    @Autowired
    private SensorAlertRepository sensorAlertRepository;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        sensorAlertRepository.deleteAll();
    }

    @Nested
    @DisplayName("GET /api/sensors/{id}/alert")
    class GetDetail {

        @Test
        @DisplayName("should return 200 with the alert configuration when it exists")
        void shouldReturnAlert() {
            SensorAlert alert = persistAlert(TSID.fast(), 50.0, 10.0);

            client.get().uri(ALERT_PATH, idOf(alert))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(idOf(alert))
                    .jsonPath("$.maxTemperature").isEqualTo(50.0)
                    .jsonPath("$.minTemperature").isEqualTo(10.0);
        }

        @Test
        @DisplayName("should return 404 when the alert configuration does not exist")
        void shouldReturnNotFound() {
            client.get().uri(ALERT_PATH, TSID.fast().toString())
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("PUT /api/sensors/{id}/alert")
    class CreateOrUpdate {

        @Test
        @DisplayName("should create the alert configuration and return 200 when it does not exist")
        void shouldCreateAlert() {
            TSID sensorId = TSID.fast();

            client.put().uri(ALERT_PATH, sensorId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(newInput(80.5, -5.0))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(sensorId.toString())
                    .jsonPath("$.maxTemperature").isEqualTo(80.5)
                    .jsonPath("$.minTemperature").isEqualTo(-5.0);

            assertEquals(1, sensorAlertRepository.count());
        }

        @Test
        @DisplayName("should update the alert configuration and return 200 when it already exists")
        void shouldUpdateAlert() {
            SensorAlert alert = persistAlert(TSID.fast(), 50.0, 10.0);

            client.put().uri(ALERT_PATH, idOf(alert))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(newInput(99.0, 1.0))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(idOf(alert))
                    .jsonPath("$.maxTemperature").isEqualTo(99.0)
                    .jsonPath("$.minTemperature").isEqualTo(1.0);

            assertEquals(1, sensorAlertRepository.count());
        }

        @Test
        @DisplayName("should accept null temperatures and return 200")
        void shouldAcceptNullTemperatures() {
            TSID sensorId = TSID.fast();

            client.put().uri(ALERT_PATH, sensorId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(newInput(null, null))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(sensorId.toString())
                    .jsonPath("$.maxTemperature").isEmpty()
                    .jsonPath("$.minTemperature").isEmpty();
        }
    }

    @Nested
    @DisplayName("DELETE /api/sensors/{id}/alert")
    class Delete {

        @Test
        @DisplayName("should delete the alert configuration, return 204 and make it unreachable")
        void shouldDeleteAlert() {
            SensorAlert alert = persistAlert(TSID.fast(), 50.0, 10.0);

            client.delete().uri(ALERT_PATH, idOf(alert))
                    .exchange()
                    .expectStatus().isNoContent()
                    .expectBody().isEmpty();

            assertEquals(0, sensorAlertRepository.count());

            client.get().uri(ALERT_PATH, idOf(alert))
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    @Nested
    @DisplayName("Not found scenarios")
    class NotFound {

        @ParameterizedTest(name = "{0} -> 404")
        @DisplayName("should return 404 when the alert configuration does not exist")
        @ValueSource(strings = {"GET", "DELETE"})
        void shouldReturnNotFound(String method) {
            client.method(HttpMethod.valueOf(method)).uri(ALERT_PATH, TSID.fast().toString())
                    .exchange()
                    .expectStatus().isNotFound();
        }
    }

    private SensorAlertInput newInput(Double maxTemperature, Double minTemperature) {
        SensorAlertInput input = new SensorAlertInput();
        input.setMaxTemperature(maxTemperature);
        input.setMinTemperature(minTemperature);
        return input;
    }

    private SensorAlert persistAlert(TSID sensorId, Double maxTemperature, Double minTemperature) {
        return sensorAlertRepository.saveAndFlush(SensorAlert.builder()
                .id(new SensorId(sensorId))
                .maxTemperature(maxTemperature)
                .minTemperature(minTemperature)
                .build());
    }

    private String idOf(SensorAlert alert) {
        return alert.getId().getValue().toString();
    }
}
