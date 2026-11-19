package com.clinica.recordatorios.service;

import com.clinica.recordatorios.channel.ReminderChannel;
import com.clinica.recordatorios.channel.ReminderChannelFactory;
import com.clinica.recordatorios.domain.CanalNotificacion;
import com.clinica.recordatorios.domain.Cita;
import com.clinica.recordatorios.repository.CitaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Suite de pruebas unitarias para CitaReminderServiceImpl.
 * Parte 1: Pruebas de recordatorios pendientes con Strategy, Factory Method y Clock fijo como Fake.
 * Parte 2: Pruebas de recordatorios urgentes habilitadas tras el refactor DIP.
 */
@ExtendWith(MockitoExtension.class)
class CitaReminderServiceImplTest {

    @Mock
    private CitaRepository citaRepository;

    @Mock
    private ReminderChannelFactory channelFactory;

    @Mock
    private ReminderChannel emailChannel;

    @Mock
    private ReminderChannel smsChannel;

    @Mock
    private ReminderChannel canalUrgente;

    private Clock fixedClock;
    private CitaReminderServiceImpl citaReminderService;

    private final Instant fixedInstant = Instant.parse("2026-06-15T10:00:00Z");
    private final ZoneId zoneId = ZoneId.of("UTC");

    @BeforeEach
    void setUp() {
        // Test Double: Fake para Clock (determinismo temporal)
        fixedClock = Clock.fixed(fixedInstant, zoneId);
        citaReminderService = new CitaReminderServiceImpl(citaRepository, channelFactory, canalUrgente, fixedClock);
    }

    // =========================================================================
    // PARTE 1: Pruebas de enviarRecordatoriosPendientes
    // =========================================================================

    @Test
    @DisplayName("enviarRecordatoriosPendientes: cita en ventana obtiene el canal de la factory y envía el mensaje")
    void enviarRecordatoriosPendientes_citaEnVentana_usaCanalPreferido() {
        // Arrange
        LocalDateTime ahora = LocalDateTime.now(fixedClock);
        LocalDateTime finVentana = ahora.plusHours(24);

        Cita cita = new Cita(1L, "Carlos Gómez", "carlos@example.com", ahora.plusHours(4), CanalNotificacion.EMAIL, false);
        when(citaRepository.findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse(ahora, finVentana))
                .thenReturn(List.of(cita));
        when(channelFactory.obtenerCanal(CanalNotificacion.EMAIL)).thenReturn(emailChannel);

        // Act
        citaReminderService.enviarRecordatoriosPendientes();

        // Assert
        verify(channelFactory, times(1)).obtenerCanal(CanalNotificacion.EMAIL);
        verify(emailChannel, times(1)).enviar(eq(cita), contains("Carlos Gómez"));
        assertThat(cita.isRecordatorioEnviado()).isTrue();
        verify(citaRepository, times(1)).save(cita);
    }

    @Test
    @DisplayName("enviarRecordatoriosPendientes: ventana calculada de forma determinista según el Clock fijo")
    void enviarRecordatoriosPendientes_ventanaDeterministaSegunClockFijo_consultaLosLimitesExactos() {
        // Arrange
        when(citaRepository.findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse(any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        citaReminderService.enviarRecordatoriosPendientes();

        // Assert - capturar argumentos pasados al repositorio
        ArgumentCaptor<LocalDateTime> desdeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> hastaCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(citaRepository).findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse(desdeCaptor.capture(), hastaCaptor.capture());

        LocalDateTime esperadoDesde = LocalDateTime.of(2026, 6, 15, 10, 0, 0);
        LocalDateTime esperadoHasta = LocalDateTime.of(2026, 6, 16, 10, 0, 0);

        assertThat(desdeCaptor.getValue()).isEqualTo(esperadoDesde);
        assertThat(hastaCaptor.getValue()).isEqualTo(esperadoHasta);
    }

    @Test
    @DisplayName("enviarRecordatoriosPendientes: si no hay citas pendientes, no interactúa con la factory ni con canales")
    void enviarRecordatoriosPendientes_sinCitasPendientes_noConsultaLaFactory() {
        // Arrange
        when(citaRepository.findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse(any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        citaReminderService.enviarRecordatoriosPendientes();

        // Assert
        verify(channelFactory, never()).obtenerCanal(any());
        verify(emailChannel, never()).enviar(any(), any());
        verify(smsChannel, never()).enviar(any(), any());
        verify(citaRepository, never()).save(any());
    }

    // =========================================================================
    // PARTE 2: Pruebas de enviarRecordatorioUrgente habilitadas por refactor DIP
    // =========================================================================

    @Test
    @DisplayName("enviarRecordatorioUrgente: cita existente envía mensaje urgente vía canalUrgente inyectado y persiste")
    void enviarRecordatorioUrgente_citaExistente_usaCanalUrgenteInyectado() {
        // Arrange
        Cita cita = new Cita(5L, "Ana Torres", "+573001234567",
                LocalDateTime.now(fixedClock).plusHours(2), CanalNotificacion.SMS, false);
        when(citaRepository.findById(5L)).thenReturn(Optional.of(cita));

        // Act
        citaReminderService.enviarRecordatorioUrgente(5L);

        // Assert
        verify(canalUrgente, times(1)).enviar(eq(cita), contains("URGENTE"));
        assertThat(cita.isRecordatorioEnviado()).isTrue();
        verify(citaRepository, times(1)).save(cita);
    }

    @Test
    @DisplayName("enviarRecordatorioUrgente: si el canal/gateway falla, la excepción escala y la cita NO se guarda como enviada")
    void enviarRecordatorioUrgente_gatewayFalla_noMarcaComoEnviado() {
        // Arrange
        Cita cita = new Cita(5L, "Ana Torres", "+573001234567",
                LocalDateTime.now(fixedClock).plusHours(2), CanalNotificacion.SMS, false);
        when(citaRepository.findById(5L)).thenReturn(Optional.of(cita));
        doThrow(new RuntimeException("Falla en gateway SMS Twilio")).when(canalUrgente).enviar(any(), any());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> citaReminderService.enviarRecordatorioUrgente(5L));

        assertThat(cita.isRecordatorioEnviado()).isFalse();
        verify(citaRepository, never()).save(cita);
    }

    @Test
    @DisplayName("enviarRecordatorioUrgente: si la cita no existe, lanza CitaNoEncontradaException y no envía nada")
    void enviarRecordatorioUrgente_citaInexistente_lanzaCitaNoEncontradaException() {
        // Arrange
        when(citaRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(CitaNoEncontradaException.class, () -> citaReminderService.enviarRecordatorioUrgente(99L));

        verify(canalUrgente, never()).enviar(any(), any());
        verify(citaRepository, never()).save(any());
    }
}
