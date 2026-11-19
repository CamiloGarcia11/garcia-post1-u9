package com.clinica.recordatorios;

import com.clinica.recordatorios.channel.gateway.NotificadorSmsGateway;
import com.clinica.recordatorios.domain.CanalNotificacion;
import com.clinica.recordatorios.domain.Cita;
import com.clinica.recordatorios.repository.CitaRepository;
import com.clinica.recordatorios.service.CitaReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
class CitaReminderServiceIntegrationTest {

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        public Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneId.of("UTC"));
        }
    }

    @Autowired
    private CitaReminderService citaReminderService;

    @Autowired
    private CitaRepository citaRepository;

    @Autowired
    private Clock clock;

    @MockBean
    private NotificadorSmsGateway smsGateway;

    @BeforeEach
    void setUp() {
        citaRepository.deleteAll();
    }

    @Test
    @DisplayName("Integración: enviarRecordatoriosPendientes procesa citas en ventana, ejecuta canales y persiste en H2")
    void flujoCompletoRecordatoriosPendientes_procesaCitasYActualizaEstadoEnBaseDeDatos() {
        LocalDateTime ahora = LocalDateTime.now(clock);

        // Cita 1: dentro de ventana (EMAIL)
        Cita citaEmail = new Cita(null, "Diana Uribe", "diana@example.com",
                ahora.plusHours(8), CanalNotificacion.EMAIL, false);

        // Cita 2: dentro de ventana (SMS)
        Cita citaSms = new Cita(null, "Gabriel García", "+573100000000",
                ahora.plusHours(14), CanalNotificacion.SMS, false);

        // Cita 3: fuera de ventana (EMAIL, +30 horas)
        Cita citaLejana = new Cita(null, "Jorge Velosa", "jorge@example.com",
                ahora.plusHours(30), CanalNotificacion.EMAIL, false);

        citaRepository.saveAll(List.of(citaEmail, citaSms, citaLejana));

        // Act
        citaReminderService.enviarRecordatoriosPendientes();

        // Assert
        List<Cita> todas = citaRepository.findAll();
        assertThat(todas).hasSize(3);

        Cita guardadaEmail = todas.stream().filter(c -> c.getPacienteNombre().equals("Diana Uribe")).findFirst().orElseThrow();
        Cita guardadaSms = todas.stream().filter(c -> c.getPacienteNombre().equals("Gabriel García")).findFirst().orElseThrow();
        Cita guardadaLejana = todas.stream().filter(c -> c.getPacienteNombre().equals("Jorge Velosa")).findFirst().orElseThrow();

        assertThat(guardadaEmail.isRecordatorioEnviado()).isTrue();
        assertThat(guardadaSms.isRecordatorioEnviado()).isTrue();
        assertThat(guardadaLejana.isRecordatorioEnviado()).isFalse();

        // Verificar que el gateway de SMS fue invocado para la cita SMS
        verify(smsGateway).enviarSms(eq("+573100000000"), anyString());
    }

    @Test
    @DisplayName("Integración: enviarRecordatorioUrgente usa canal urgente con gateway mockeado y actualiza base de datos")
    void flujoRecordatorioUrgente_conSmsGatewayMockeado_actualizaEstado() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        Cita cita = new Cita(null, "Andrés Cepeda", "+573201112233",
                ahora.plusHours(1), CanalNotificacion.EMAIL, false);
        Cita guardada = citaRepository.save(cita);

        // Act
        citaReminderService.enviarRecordatorioUrgente(guardada.getId());

        // Assert
        Cita actualizada = citaRepository.findById(guardada.getId()).orElseThrow();
        assertThat(actualizada.isRecordatorioEnviado()).isTrue();

        // Verificar delegación en el gateway SMS
        verify(smsGateway).enviarSms(eq("+573201112233"), anyString());
    }
}
