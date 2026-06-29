package com.algaworks.algasensors.temperature.monitoring.domain.service;

import com.algaworks.algasensors.temperature.monitoring.api.model.TemperatureLogData;
import com.algaworks.algasensors.temperature.monitoring.domain.model.SensorAlert;
import com.algaworks.algasensors.temperature.monitoring.domain.model.SensorId;
import com.algaworks.algasensors.temperature.monitoring.domain.repository.SensorAlertRepository;
import io.hypersistence.tsid.TSID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensorAlertServiceTest {

    @Mock
    private SensorAlertRepository sensorAlertRepository;

    @InjectMocks
    private SensorAlertService service;

    @Test
    @DisplayName("should only look up the alert configuration and never mutate state")
    void shouldNeverPersist() {
        TemperatureLogData data = newData(TSID.fast(), 25.0);
        when(sensorAlertRepository.findById(new SensorId(data.getSensorId())))
                .thenReturn(Optional.empty());

        service.handleAlert(data);

        // handleAlert is read-only: it only logs, it must never write to the repository.
        verify(sensorAlertRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("should run without error when no alert configuration exists for the sensor")
    void shouldHandleMissingAlert() {
        TemperatureLogData data = newData(TSID.fast(), 25.0);
        when(sensorAlertRepository.findById(new SensorId(data.getSensorId())))
                .thenReturn(Optional.empty());

        service.handleAlert(data);

        verify(sensorAlertRepository).findById(new SensorId(data.getSensorId()));
    }

    @Test
    @DisplayName("should run without error when the value is at or above the configured maximum")
    void shouldHandleMaxThreshold() {
        runWithAlert(50.0, 10.0, 50.0);
        runWithAlert(50.0, 10.0, 75.0);
    }

    @Test
    @DisplayName("should run without error when the value is at or below the configured minimum")
    void shouldHandleMinThreshold() {
        runWithAlert(50.0, 10.0, 10.0);
        runWithAlert(50.0, 10.0, 5.0);
    }

    @Test
    @DisplayName("should run without error when the value is within the configured range")
    void shouldHandleWithinRange() {
        runWithAlert(50.0, 10.0, 30.0);
    }

    @Test
    @DisplayName("should run without error when the configured thresholds are null")
    void shouldHandleNullThresholds() {
        runWithAlert(null, null, 30.0);
    }

    private void runWithAlert(Double max, Double min, Double value) {
        TSID sensorId = TSID.fast();
        TemperatureLogData data = newData(sensorId, value);
        SensorAlert alert = SensorAlert.builder()
                .id(new SensorId(sensorId))
                .maxTemperature(max)
                .minTemperature(min)
                .build();
        when(sensorAlertRepository.findById(new SensorId(sensorId)))
                .thenReturn(Optional.of(alert));

        service.handleAlert(data);

        verify(sensorAlertRepository).findById(new SensorId(sensorId));
    }

    private TemperatureLogData newData(TSID sensorId, Double value) {
        return TemperatureLogData.builder()
                .id(UUID.randomUUID())
                .sensorId(sensorId)
                .value(value)
                .registeredAt(OffsetDateTime.now())
                .build();
    }
}
