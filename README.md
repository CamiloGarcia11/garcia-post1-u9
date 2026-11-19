# Sistema de Recordatorio de Citas Médicas (`recordatorios-citas-service`)

[![CI](https://github.com/CamiloGarcia11/garcia-post1-u9/actions/workflows/ci.yml/badge.svg)](https://github.com/CamiloGarcia11/garcia-post1-u9/actions/workflows/ci.yml)

**Estudiante:** Edwin Camilo García  
**Materia:** Patrones de Diseño de Software  
**Unidad 9:** Probar el Diseño — JUnit 5, Mockito, Test Doubles e Integración Continua  
**Repositorio GitHub:** [https://github.com/CamiloGarcia11/garcia-post1-u9](https://github.com/CamiloGarcia11/garcia-post1-u9)

---

## 1. Contexto y Arquitectura del Sistema

El proyecto **`recordatorios-citas-service`** gestiona la notificación periódica y urgente a pacientes sobre citas médicas programadas en la clínica.

El diseño del servicio central `CitaReminderServiceImpl` fue concebido para maximizar la testabilidad y la separación de responsabilidades:
- **Patrón Strategy (`ReminderChannel`):** Permite intercambiar canales de notificación (`EmailReminderChannel`, `SmsReminderChannel`) sin alterar la lógica de recordatorios.
- **Patrón Factory Method (`ReminderChannelFactory`):** Encapsula la selección polimórfica del canal en función de la preferencia del paciente (`CanalNotificacion.EMAIL` o `CanalNotificacion.SMS`).
- **Inyección de `java.time.Clock`:** Desacopla el servicio del reloj del sistema operativo (`LocalDateTime.now()`), permitiendo fijar instantes temporales exactos durante las pruebas y evitar tests intermitentes o "flaky".

---

## 2. Parte 1 — Elección Justificada de Test Doubles

### 2.1 Taxonomía y Justificación de Test Doubles Utilizados

En las pruebas unitarias de `CitaReminderServiceImplTest` se seleccionó minuciosamente el tipo de doble de prueba (Test Double) según las necesidades de aislamiento y verificación:

| Dependencia | Rol en Producción | Tipo de Test Double | Justificación Técnica y de Diseño |
|---|---|---|---|
| `CitaRepository` | Persistencia en base de datos relacional (Spring Data JPA) | **Mock** | Aísla completamente la capa de persistencia e I/O de disco. Permite stubear los retornos de consulta (`findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse`) y verificar que el método `save(cita)` sea invocado con el estado actualizado (`recordatorioEnviado = true`). |
| `ReminderChannelFactory` | Resolución de estrategias de notificación según el enum | **Mock** | Permite verificar la interacción directa mediante `verify(channelFactory).obtenerCanal(EMAIL)` sin necesidad de mantener un mapa o lógica de creación interna. |
| `ReminderChannel` (Email/SMS) | Estrategia de entrega de mensajes hacia transportes externos | **Mock** | Previene llamadas de red hacia servidores SMTP o gateways SMS de terceros. Facilita la verificación del mensaje entregado (`verify(channel).enviar(...)`) o la simulación de fallas de infraestructura. |
| `java.time.Clock` | Proveedor del tiempo actual del sistema | **Fake** (`Clock.fixed(...)`) | El método estándar `Clock.fixed(instant, zone)` proporciona una implementación funcional de la JDK que devuelve un instante inmutable. Es superior a mockear llamadas de bajo nivel (`instant()`, `getZone()`) porque provee un comportamiento completo, canónico y 100% determinista sin ruido de stubbing. |

### 2.2 Suite Unitaria de `CitaReminderServiceImplTest` (Parte 1)

1. **`enviarRecordatoriosPendientes_citaEnVentana_usaCanalPreferido`**:
   - Comprueba que una cita con fecha/hora dentro de las próximas 24 horas obtiene su estrategia adecuada vía `ReminderChannelFactory.obtenerCanal(...)`.
   - Verifica que se invoca `ReminderChannel.enviar(...)` con los datos del paciente y el mensaje esperado.
   - Constata que la entidad se marca como enviada (`isRecordatorioEnviado() == true`) y se persiste mediante `citaRepository.save(cita)`.
2. **`enviarRecordatoriosPendientes_ventanaDeterministaSegunClockFijo_consultaLosLimitesExactos`**:
   - Valida mediante `ArgumentCaptor<LocalDateTime>` que los límites inferior y superior pasados a la consulta del repositorio coincidan al segundo con `[T, T + 24h]` definidos por el `Clock` fijo.
3. **`enviarRecordatoriosPendientes_sinCitasPendientes_noConsultaLaFactory`**:
   - Valida que cuando el repositorio retorna una lista vacía, el servicio finaliza de inmediato sin invocar la fábrica de canales ni ejecutar transportes ni realizar operaciones de escritura.

---

## 3. Parte 2 — Diagnóstico de Violación DIP, Refactorización y Pruebas Habilitadas

### 3.1 Identificación de la Violación del Principio de Inversión de Dependencias (DIP)

Al incorporar el caso de uso `enviarRecordatorioUrgente(Long citaId)`, una implementación inicial ingenua incurrió en la siguiente violación:

```java
// CitaReminderServiceImpl.java (ANTES del refactor)
@Override
public void enviarRecordatorioUrgente(Long citaId) {
    Cita cita = citaRepository.findById(citaId)
            .orElseThrow(() -> new CitaNoEncontradaException("No se encontro la cita con ID: " + citaId));

    // VIOLACIÓN DE DIP: Instanciación directa con "new" dentro de la capa de servicio
    ReminderChannel canalUrgente = new SmsReminderChannel(new TwilioNotificadorSmsGateway());
    
    canalUrgente.enviar(cita, "URGENTE...");
    cita.setRecordatorioEnviado(true);
    citaRepository.save(cita);
}
```

#### Problemas de Diseño y Testing detectados:
1. **Acoplamiento Directo a Implementaciones Concretas:** El módulo de alto nivel (`CitaReminderServiceImpl`) dependía directamente de módulos y detalles de bajo nivel (`SmsReminderChannel` y `TwilioNotificadorSmsGateway`).
2. **Imposibilidad de Testear Fallas de Infraestructura:** No era posible verificar el comportamiento del servicio si la pasarela de Twilio fallaba (e.g., timeout, error 500, credenciales inválidas) sin intentar conexiones de red reales en los tests.
3. **Imposibilidad de Probar la Integridad del Estado:** Sin poder simular una excepción en el canal, no se podía probar de forma aislada que la cita **no** se marcara como enviada ante un fallo en el gateway.

### 3.2 Refactorización Aplicada

Se extrajo la construcción y ensamblaje del canal urgente hacia la capa de configuración (`CanalUrgenteConfig`), inyectando la abstracción `ReminderChannel` en el servicio a través del constructor:

```java
// CanalUrgenteConfig.java
@Configuration
public class CanalUrgenteConfig {

    @Bean(name = "canalUrgente")
    public ReminderChannel canalUrgente(NotificadorSmsGateway smsGateway) {
        return new SmsReminderChannel(smsGateway);
    }
}
```

```java
// CitaReminderServiceImpl.java (DESPUÉS del refactor)
@Service
public class CitaReminderServiceImpl implements CitaReminderService {

    private final CitaRepository citaRepository;
    private final ReminderChannelFactory channelFactory;
    private final ReminderChannel canalUrgente;
    private final Clock clock;

    public CitaReminderServiceImpl(CitaRepository citaRepository,
                                  ReminderChannelFactory channelFactory,
                                  @Qualifier("canalUrgente") ReminderChannel canalUrgente,
                                  Clock clock) {
        this.citaRepository = citaRepository;
        this.channelFactory = channelFactory;
        this.canalUrgente = canalUrgente;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void enviarRecordatorioUrgente(Long citaId) {
        Cita cita = citaRepository.findById(citaId)
                .orElseThrow(() -> new CitaNoEncontradaException("No se encontro la cita con ID: " + citaId));

        String mensajeUrgente = String.format("URGENTE: Estimado(a) %s, confirme su cita medica inmediata programada para %s.",
                cita.getPacienteNombre(), cita.getFechaHoraCita());

        canalUrgente.enviar(cita, mensajeUrgente);
        cita.setRecordatorioEnviado(true);
        citaRepository.save(cita);
    }
}
```

### 3.3 Pruebas Unitarias Habilitadas tras el Refactor

- **`enviarRecordatorioUrgente_citaExistente_usaCanalUrgenteInyectado`**: Valida que una cita existente sea notificada a través del canal urgente mockeado y marcada como enviada.
- **`enviarRecordatorioUrgente_gatewayFalla_noMarcaComoEnviado`**: Configura el mock del canal para simular una falla (`doThrow(new RuntimeException(...)).when(canalUrgente).enviar(...)`), verificando que la excepción se propaga y que la cita **no** es persistida como enviada (`verify(citaRepository, never()).save(...)`).
- **`enviarRecordatorioUrgente_citaInexistente_lanzaCitaNoEncontradaException`**: Valida que un ID inexistente dispare `CitaNoEncontradaException` y no interactúe con el canal urgente ni con la base de datos.

---

## 4. Pruebas de Integración

### 4.1 `CitaRepositoryTest` (`@DataJpaTest`)
- Levanta una base de datos relacional H2 en memoria.
- Valida la semántica de la consulta derivada:
  ```java
  List<Cita> findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse(LocalDateTime desde, LocalDateTime hasta);
  ```
- Comprueba que únicamente se recuperan las citas comprendidas entre `desde` y `hasta` con `recordatorioEnviado == false`, excluyendo citas ya enviadas o fuera del rango temporal.

### 4.2 `CitaReminderServiceIntegrationTest` (`@SpringBootTest`)
- Levanta el contexto completo de Spring Boot.
- Configura un bean `@Primary Clock` fijo mediante `@TestConfiguration` para proveer un marco temporal consistente.
- Utiliza `@MockBean NotificadorSmsGateway` para aislar el extremo del proveedor SMS externo sin tocar credenciales ni redes reales.
- Valida el flujo end-to-end de `enviarRecordatoriosPendientes()` y `enviarRecordatorioUrgente()`, confirmando las actualizaciones de estado en la base de datos H2.

---

## 5. Pipeline de Integración Continua (GitHub Actions)

El archivo de flujo de trabajo `.github/workflows/ci.yml` asegura que cada cambio cumpla con los estándares de calidad:

```yaml
name: CI

on:
  push:
    branches: [ "main" ]
  pull_request:
    branches: [ "main" ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - name: Checkout del repositorio
      uses: actions/checkout@v4

    - name: Configurar Java 21 (Eclipse Temurin)
      uses: actions/setup-java@v4
      with:
        java-version: '21'
        distribution: 'temurin'
        cache: 'maven'

    - name: Compilar y ejecutar pruebas con Maven
      run: mvn -B verify
```

---

## 6. Ejecución del Proyecto

### Requisitos
- **JDK 21** o superior
- **Apache Maven 3.9+**

### Comandos de Compilación y Prueba
```bash
# Compilar el proyecto y ejecutar toda la suite de pruebas (unitarias e integración)
mvn clean verify

# Ejecutar únicamente pruebas unitarias
mvn test -Dtest=CitaReminderServiceImplTest

# Ejecutar únicamente pruebas de integración
mvn test -Dtest=CitaRepositoryTest,CitaReminderServiceIntegrationTest
```

---

## 7. Estructura del Repositorio

```text
garcia-post1-u9/
├── .github/
│   └── workflows/
│       └── ci.yml
├── src/
│   ├── main/
│   │   ├── java/com/clinica/recordatorios/
│   │   │   ├── RecordatoriosCitasServiceApplication.java
│   │   │   ├── channel/
│   │   │   │   ├── EmailReminderChannel.java
│   │   │   │   ├── ReminderChannel.java
│   │   │   │   ├── ReminderChannelFactory.java
│   │   │   │   ├── ReminderChannelFactoryImpl.java
│   │   │   │   ├── SmsReminderChannel.java
│   │   │   │   └── gateway/
│   │   │   │       ├── NotificadorSmsGateway.java
│   │   │   │       └── TwilioNotificadorSmsGateway.java
│   │   │   ├── config/
│   │   │   │   ├── CanalUrgenteConfig.java
│   │   │   │   └── ClockConfig.java
│   │   │   ├── domain/
│   │   │   │   ├── CanalNotificacion.java
│   │   │   │   └── Cita.java
│   │   │   ├── repository/
│   │   │   │   └── CitaRepository.java
│   │   │   └── service/
│   │   │       ├── CitaNoEncontradaException.java
│   │   │       ├── CitaReminderService.java
│   │   │       └── CitaReminderServiceImpl.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/com/clinica/recordatorios/
│           ├── CitaReminderServiceIntegrationTest.java
│           ├── repository/
│           │   └── CitaRepositoryTest.java
│           └── service/
│               └── CitaReminderServiceImplTest.java
├── pom.xml
└── README.md
```
