Implemente a funcionalidade: Configuração de Alerta de Sensores
Você precisa desenvolver a funcionalidade para gerenciar alertas de temperatura dos sensores. Cada sensor pode ter limites configuráveis de temperatura (máxima e mínima), que serão usadas para gerar alertas, e essas configurações devem ser armazenadas e recuperadas de um banco de dados.

O sistema deve permitir:

Consultar os detalhes da configuração de alerta de um sensor.
Criar ou atualizar a configuração de alerta de um sensor.
Remover a configuração de alerta de um sensor.
Requisitos
1. Entity SensorAlert
   Deve representar a configuração de alerta sistema com os seguintes campos:

id: identificador único(SensorId)
maxTemperature: Temperatura máxima para um alerta (Double)
minTemperature: Temperatura mínima para um alerta (Double)
2. Repository SensorAlertRepository
   Deve permitir operações básicas de CRUD (criar, ler, atualizar e deletar) usando Spring Data JPA.

3. DTOs de Entrada e Saída
   Cada um deve conter as propriedades:

SensorAlertInput:

maxTemperature: Double
minTemperature: Double
SensorAlertOutput:

id: TSID
maxTemperature: Double
minTemperature: Double
4. Controller SensorAlertController
   Deve expor endpoints REST para:

GET /api/sensors/{sensorId}/alert:

Retorna SensorAlertOutput.
Retorna 200 OK em caso de sucesso.
Se a configuração não existir, retorna 404 Not Found.
PUT /api/sensors/{sensorId}/alert:

Cria ou atualiza a configuração de alerta de um sensor.
Retorna SensorAlertOutput.
Retorna 200 OK em caso de sucesso.
Se a configuração não existir, cria um novo registro.
DELETE /api/sensors/{sensorId}/alert:

Remove a configuração de alerta de um sensor.
Retorna 204 No Content em caso de sucesso.
Se a configuração não existir, retorna 404 Not Found.
Regras de validação
O ID do sensor é obrigatório em todas as operações.
As temperaturas (máxima e mínima) podem ser nulas.
Tarefas do Desafio
Implemente a entidade SensorAlert com JPA.
Crie o repositório para persistência dos dados.
Defina os DTOs de entrada (SensorAlertInput) e saída (SensorAlertOutput).
Implemente o controlador REST com os endpoints especificados.
Garanta que as respostas HTTP estejam corretas (200, 404, 204).
Dicas
Use @Builder para facilitar a criação de objetos.
Utilize ResponseStatusException para tratamento de erros.
Considere que SensorId é um objeto que encapsula um TSID.
Armazene o valor do TSID como BigInt.