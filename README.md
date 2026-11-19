# Sistema de Recordatorio de Citas Médicas (`recordatorios-citas-service`)

[![CI](https://github.com/CamiloGarcia11/garcia-post1-u9/actions/workflows/ci.yml/badge.svg)](https://github.com/CamiloGarcia11/garcia-post1-u9/actions/workflows/ci.yml)

**Estudiante:** Edwin Camilo García  
**Materia:** Patrones de Diseño de Software  
**Unidad 9:** Probar el Diseño — JUnit 5, Mockito, Test Doubles e Integración Continua  
**Repositorio GitHub:** [https://github.com/CamiloGarcia11/garcia-post1-u9](https://github.com/CamiloGarcia11/garcia-post1-u9)

---

## 1. Contexto y Objetivos

El servicio `recordatorios-citas-service` es responsable de notificar a pacientes sobre citas médicas programadas dentro de una ventana de 24 horas a través de su canal de preferencia (EMAIL o SMS).

El diseño inicial integra dos patrones creacionales/estructurales/de comportamiento:
1. **Patrón Strategy (`ReminderChannel`):** Permite desacoplar el mecanismo de transporte y notificación (correo electrónico o SMS) de la lógica de negocio central.
2. **Patrón Factory Method (`ReminderChannelFactory`):** Centraliza la resolución de la estrategia adecuada según el `CanalNotificacion` configurado para cada paciente.
3. **Inyección de `java.time.Clock`:** Desacopla el servicio del reloj del sistema (`LocalDateTime.now()`), garantizando pruebas deterministas.

---

## 2. Parte 1 — Elección Justificada de Test Doubles

### 2.1 Justificación por Componente

| Dependencia | Rol en Producción | Test Double Seleccionado | Justificación |
|---|---|---|---|
| `CitaRepository` | Acceso a base de datos persistente (Spring Data JPA) | **Mock** | Evita la necesidad de levantar una base de datos o esquema relacional en pruebas unitarias del servicio. Permite stubear los retornos de consulta y verificar con exactitud que el método `save()` sea invocado con la entidad modificada (`recordatorioEnviado = true`). |
| `ReminderChannelFactory` | Resolución polimórfica del canal según el enum | **Mock** | Permite verificar la interacción directa: se comprueba que el servicio consulta la fábrica exactamente con el canal configurado (`verify(channelFactory).obtenerCanal(EMAIL)`). |
| `ReminderChannel` (Email/SMS) | Estrategia de envío de mensajes hacia el exterior | **Mock** | Aísla la capa de red/infraestructura (SMTP, pasarelas SMS). Permite verificar que el método `enviar(...)` se invoque con la cita y el mensaje esperado sin realizar I/O real. |
| `java.time.Clock` | Proveedor de la hora actual del sistema | **Fake** (`Clock.fixed(...)`) | El método estático `Clock.fixed(instant, zone)` proporciona una implementación funcional y real del contrato de la JDK que devuelve un instante inmutable. Es superior a mockear `clock.instant()` y `clock.getZone()` porque no requiere stubbing de bajo nivel y garantiza determinismo absoluto en el cálculo de ventanas temporales. |

### 2.2 Cobertura de Pruebas Unitarias de `CitaReminderServiceImplTest`

- **`enviarRecordatoriosPendientes_citaEnVentana_usaCanalPreferido`**: Comprueba que una cita dentro de la ventana de 24 horas obtiene su canal vía `ReminderChannelFactory`, delega el envío en la estrategia `ReminderChannel`, marca la cita como enviada y persiste los cambios en `CitaRepository`.
- **`enviarRecordatoriosPendientes_ventanaDeterministaSegunClockFijo_consultaLosLimitesExactos`**: Verifica mediante `ArgumentCaptor` que los instantes de inicio y fin enviados a la consulta del repositorio correspondan exactamente a `[T, T + 24h]` calculados desde el `Clock` fijo inyectado.
- **`enviarRecordatoriosPendientes_sinCitasPendientes_noConsultaLaFactory`**: Verifica que en ausencia de citas pendientes, el flujo termina limpiamente sin consultar la fábrica ni ejecutar canales de envío ni invocar guardado.
