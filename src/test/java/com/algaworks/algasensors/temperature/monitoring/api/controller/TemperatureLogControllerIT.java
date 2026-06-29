package com.algaworks.algasensors.temperature.monitoring.api.controller;

import com.algaworks.algasensors.temperature.monitoring.domain.model.SensorId;
import com.algaworks.algasensors.temperature.monitoring.domain.model.TemperatureLog;
import com.algaworks.algasensors.temperature.monitoring.domain.model.TemperatureLogId;
import com.algaworks.algasensors.temperature.monitoring.domain.repository.TemperatureLogRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.OffsetDateTime;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TemperatureLogControllerIT {

    private static final String TEMPERATURES_PATH = "/api/sensors/{id}/temperatures";

    @LocalServerPort
    private int port;

    // No broker in tests: mock the RabbitAdmin so RabbitMQInitializer#init() is a no-op.
    @MockitoBean
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private TemperatureLogRepository temperatureLogRepository;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        temperatureLogRepository.deleteAll();
    }

    @Nested
    @DisplayName("GET /api/sensors/{id}/temperatures")
    class Search {

        @Test
        @DisplayName("should return an empty page when the sensor has no logs")
        void shouldReturnEmptyPage() {
            client.get().uri(TEMPERATURES_PATH, TSID.fast().toString())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").isEmpty()
                    .jsonPath("$.totalElements").isEqualTo(0)
                    .jsonPath("$.totalPages").isEqualTo(0);
        }

        @Test
        @DisplayName("should return the logs of the sensor with their fields")
        void shouldReturnSensorLogs() {
            TSID sensorId = TSID.fast();
            TemperatureLog log = persistLog(sensorId, 30.1, OffsetDateTime.now());

            client.get().uri(TEMPERATURES_PATH, sensorId.toString())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(1)
                    .jsonPath("$.totalElements").isEqualTo(1)
                    .jsonPath("$.content[0].id").isEqualTo(log.getId().getValue().toString())
                    .jsonPath("$.content[0].sensorId").isEqualTo(sensorId.toString())
                    .jsonPath("$.content[0].value").isEqualTo(30.1)
                    .jsonPath("$.content[0].registeredAt").exists();
        }

        @Test
        @DisplayName("should return only the logs that belong to the requested sensor")
        void shouldFilterBySensor() {
            TSID sensorId = TSID.fast();
            TSID otherSensorId = TSID.fast();
            persistLog(sensorId, 10.0, OffsetDateTime.now());
            persistLog(sensorId, 11.0, OffsetDateTime.now());
            persistLog(otherSensorId, 99.0, OffsetDateTime.now());

            client.get().uri(TEMPERATURES_PATH, sensorId.toString())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(2)
                    .jsonPath("$.totalElements").isEqualTo(2)
                    .jsonPath("$.content[0].sensorId").isEqualTo(sensorId.toString())
                    .jsonPath("$.content[1].sensorId").isEqualTo(sensorId.toString());
        }

        @ParameterizedTest(name = "size={0} -> content={1}, totalPages={2}")
        @DisplayName("should paginate the logs according to the page size")
        @CsvSource({
                "2, 2, 3",
                "3, 3, 2",
                "5, 5, 1"
        })
        void shouldPaginateLogs(int size, int expectedContentLength, int expectedTotalPages) {
            TSID sensorId = TSID.fast();
            for (int i = 0; i < 5; i++) {
                persistLog(sensorId, 20.0 + i, OffsetDateTime.now());
            }

            client.get().uri(TEMPERATURES_PATH + "?page=0&size={size}", sensorId.toString(), size)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(expectedContentLength)
                    .jsonPath("$.totalElements").isEqualTo(5)
                    .jsonPath("$.totalPages").isEqualTo(expectedTotalPages)
                    .jsonPath("$.size").isEqualTo(size)
                    .jsonPath("$.number").isEqualTo(0);
        }
    }

    private TemperatureLog persistLog(TSID sensorId, Double value, OffsetDateTime registeredAt) {
        return temperatureLogRepository.saveAndFlush(TemperatureLog.builder()
                .id(new TemperatureLogId(UUID.randomUUID()))
                .sensorId(new SensorId(sensorId))
                .value(value)
                .registeredAt(registeredAt)
                .build());
    }
}