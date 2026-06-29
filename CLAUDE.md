# CLAUDE.md — temperature-monitoring

Microsserviço de **monitoramento de temperatura** do projeto AlgaSensors. Mantém o estado de
monitoramento de cada sensor, o histórico de leituras (temperature logs) e os alertas.

## Stack

- Java 25 (toolchain Gradle) · Spring Boot 4.1.0 · Gradle (use o wrapper `./gradlew`)
- Spring Web (MVC) · Spring Data JPA · H2 (arquivo em `~/algasensors-temperature-monitoring-db`)
- **Spring AMQP / RabbitMQ** (`spring-boot-starter-amqp`) — consome as leituras publicadas pelo
  `temperature-processing`
- Lombok · hypersistence-tsid (IDs no formato TSID)
- **Jackson 3** (vem com o Spring Boot 4 — ver Convenções)

## Comandos

```bash
./gradlew bootRun   # sobe a aplicação na porta 8082
./gradlew test      # roda os testes
./gradlew build     # compila + testa + empacota
```

H2 console: `http://localhost:8082/h2-console` (URL/credenciais em `src/main/resources/application.yml`).

## Arquitetura

- Porta **8082**. Pacote base: `com.algaworks.algasensors.temperature.monitoring`.
- Camadas: `api` (controller/model/config) · `domain` (model/repository/**service**) ·
  `infrastructure/rabbitmq` (consumo de mensagens).
- Controllers (todos sob `/api/sensors/{sensorId}`):
  - `SensorMonitoringController` (`/monitoring`): `GET` detalhe, `PUT|DELETE /enable`
    liga/desliga o monitoramento.
  - `SensorAlertController` (`/alert`): `GET`/`PUT`/`DELETE` da configuração de alerta.
  - `TemperatureLogController` (`/temperatures`): `GET` do histórico de leituras.
- **Consumidor a montante**: o `device-management` (porta 8080) chama os endpoints de
  `/monitoring` deste serviço para refletir o enable/disable dos sensores.

## Mensageria (RabbitMQ) — lado consumidor

- Config em `infrastructure/rabbitmq`:
  - `RabbitMQConfig` declara duas filas ligadas (binding) ao fanout exchange do
    `temperature-processing` (`temperature-processing.temperature-received.v1.e`):
    - `QUEUE_PROCESS_TEMPERATURE` (`...process-temperature.v1.q`) — com **DLQ**
      (`...process-temperature.v1.dlq`) via `x-dead-letter-exchange`/`-routing-key`.
    - `QUEUE_ALERTING` (`temperature-monitoring.alerting.v1.q`).
  - `RabbitMQInitializer` chama `rabbitAdmin.initialize()` num `@PostConstruct` (**força conexão
    com o broker no startup**).
- `RabbitMQListener` (`@RabbitListener`, `concurrency = "2-3"`) recebe o payload desserializado
  como `TemperatureLogData` e delega aos services:
  - fila de processamento → `TemperatureMonitoringService#processTemperatureReading`
  - fila de alerta → `SensorAlertService#handleAlert` (e dorme 5s — comportamento do exercício).
- Domain services (regra de negócio, cobertos por testes unitários Mockito):
  - `TemperatureMonitoringService`: ignora se não há monitoramento ou está desabilitado; se
    habilitado, atualiza `lastTemperature`/`updatedAt` e persiste um `TemperatureLog`.
  - `SensorAlertService`: read-only (só loga) — compara o valor com `max`/`min` configurados.

## Convenções

- **IDs são TSID** (`io.hypersistence.tsid.TSID`), persistidos como `Long`. Reutilize as peças
  de conversão dedicadas ao criar novas entidades/endpoints:
  - Web (path variable `String`→TSID): `api/config/web/StringToTSIDWebConverter`
  - JSON: `api/config/jackson/TSIDToStringSerializer` (config em `TSIDJacksonConfig`)
  - JPA: `api/config/jpa/TSIDToLongJPAAttributeConverter`
  - Value objects de identidade: `domain/model/SensorId`, `TemperatureLogId`.
- **Jackson 3**: o pacote é `tools.jackson.*` (não `com.fasterxml.jackson.*`, exceto as
  anotações). `JsonDeserializer`→`ValueDeserializer`, `JsonSerializer`→`ValueSerializer`,
  `parser.getText()`→`getString()`, e as exceções Jackson agora são unchecked.
- Lombok em uso: `@RequiredArgsConstructor` (injeção por construtor), `@Builder`, `@Getter/@Setter`.
- Erros de domínio via `ResponseStatusException`.

## Testes

- Padrão e2e: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `RestTestClient` apontando para
  `http://localhost:{port}`. Cada teste limpa o repositório no `@BeforeEach`.
- `src/test/resources/application.yml` sobrescreve o datasource para **H2 em memória**
  (`create-drop`), isolando os testes do banco em arquivo.
- **Isolamento do RabbitMQ (sem broker nos testes)** — dois ajustes em conjunto:
  - `application.yml` de teste define `spring.rabbitmq.listener.simple.auto-startup: false`,
    para os containers do `@RabbitListener` **não** subirem nem tentarem conectar (senão eles
    consomem mensagens reais do broker durante os testes).
  - cada `@SpringBootTest` declara `@MockitoBean RabbitAdmin`, tornando
    `RabbitMQInitializer#init()` um no-op (não tenta declarar topologia/conectar no startup).
- **Services de domínio** são testados com Mockito puro (`@ExtendWith(MockitoExtension.class)`,
  sem Spring): `TemperatureMonitoringServiceTest` e `SensorAlertServiceTest` cobrem todos os
  ramos (sensor inexistente/desabilitado/habilitado; limiares max/min/dentro da faixa/nulos).

## Pegadinhas

- `SensorMonitoringController#disable` faz `Thread.sleep(10s)` quando o monitoramento já está
  desligado — comportamento intencional do exercício; cuidado ao cronometrar testes/chamadas.
- Migração Spring Boot 4: se for adicionar consumo HTTP via `RestClient`, é preciso a
  dependência `org.springframework.boot:spring-boot-starter-restclient` (o bean
  `RestClient.Builder` não vem mais no starter web). E `@MockBean` foi removido — use
  `@MockitoBean` nos testes.

## Contexto do repositório

Este módulo é um submódulo Git do meta-repo `ems-algasensors-meta`. Para a ordem de commit/push
entre submódulos e meta-repo, ver o `README.md` da raiz.
