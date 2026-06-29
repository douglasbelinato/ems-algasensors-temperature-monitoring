package com.algaworks.algasensors.temperature.monitoring;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class TemperatureMonitoringApplicationTests {

	// No broker in tests: mock the RabbitAdmin so RabbitMQInitializer#init() is a no-op.
	@MockitoBean
	private RabbitAdmin rabbitAdmin;

	@Test
	void contextLoads() {
	}

}
