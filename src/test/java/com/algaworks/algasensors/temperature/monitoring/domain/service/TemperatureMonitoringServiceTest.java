package com.algaworks.algasensors.temperature.monitoring.domain.service;

import com.algaworks.algasensors.temperature.monitoring.api.model.TemperatureLogData;
import com.algaworks.algasensors.temperature.monitoring.domain.model.SensorId;
import com.algaworks.algasensors.temperature.monitoring.domain.model.SensorMonitoring;
import com.algaworks.algasensors.temperature.monitoring.domain.model.TemperatureLog;
import com.algaworks.algasensors.temperature.monitoring.domain.repository.SensorMonitoringRepository;
import com.algaworks.algasensors.temperature.monitoring.domain.repository.TemperatureLogRepository;
import io.hypersistence.tsid.TSID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemperatureMonitoringServiceTest {

    @Mock
    private SensorMonitoringRepository sensorMonitoringRepository;

    @Mock
    private TemperatureLogRepository temperatureLogRepository;

    @InjectMocks
    private TemperatureMonitoringService service;

    @Test
    @DisplayName("should ignore the reading when there is no monitoring for the sensor")
    void shouldIgnoreWhenMonitoringDoesNotExist() {
        TemperatureLogData data = newData(TSID.fast(), 25.0);
        when(sensorMonitoringRepository.findById(new SensorId(data.getSensorId())))
                .thenReturn(Optional.empty());

        service.processTemperatureReading(data);

        verify(sensorMonitoringRepository, never()).save(any());
        verifyNoInteractions(temperatureLogRepository);
    }

    @Test
    @DisplayName("should ignore the reading when monitoring is disabled")
    void shouldIgnoreWhenMonitoringDisabled() {
        TemperatureLogData data = newData(TSID.fast(), 25.0);
        SensorMonitoring monitoring = SensorMonitoring.builder()
                .id(new SensorId(data.getSensorId()))
                .enabled(false)
                .build();
        when(sensorMonitoringRepository.findById(new SensorId(data.getSensorId())))
                .thenReturn(Optional.of(monitoring));

        service.processTemperatureReading(data);

        verify(sensorMonitoringRepository, never()).save(any());
        verifyNoInteractions(temperatureLogRepository);
    }

    @Test
    @DisplayName("should update the monitoring and persist a temperature log when monitoring is enabled")
    void shouldUpdateAndLogWhenMonitoringEnabled() {
        TSID sensorId = TSID.fast();
        UUID logId = UUID.randomUUID();
        OffsetDateTime registeredAt = OffsetDateTime.now();
        TemperatureLogData data = TemperatureLogData.builder()
                .id(logId)
                .sensorId(sensorId)
                .value(42.0)
                .registeredAt(registeredAt)
                .build();
        SensorMonitoring monitoring = SensorMonitoring.builder()
                .id(new SensorId(sensorId))
                .enabled(true)
                .lastTemperature(10.0)
                .build();
        when(sensorMonitoringRepository.findById(new SensorId(sensorId)))
                .thenReturn(Optional.of(monitoring));

        service.processTemperatureReading(data);

        ArgumentCaptor<SensorMonitoring> monitoringCaptor = ArgumentCaptor.forClass(SensorMonitoring.class);
        verify(sensorMonitoringRepository).save(monitoringCaptor.capture());
        SensorMonitoring saved = monitoringCaptor.getValue();
        assertThat(saved.getLastTemperature()).isEqualTo(42.0);
        assertThat(saved.getUpdatedAt()).isNotNull();

        ArgumentCaptor<TemperatureLog> logCaptor = ArgumentCaptor.forClass(TemperatureLog.class);
        verify(temperatureLogRepository).save(logCaptor.capture());
        TemperatureLog log = logCaptor.getValue();
        assertThat(log.getId().getValue()).isEqualTo(logId);
        assertThat(log.getValue()).isEqualTo(42.0);
        assertThat(log.getRegisteredAt()).isEqualTo(registeredAt);
        assertThat(log.getSensorId().getValue()).isEqualTo(sensorId);
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
