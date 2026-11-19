package com.clinica.recordatorios.repository;

import com.clinica.recordatorios.domain.CanalNotificacion;
import com.clinica.recordatorios.domain.Cita;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CitaRepositoryTest {

    @Autowired
    private CitaRepository citaRepository;

    @Test
    @DisplayName("findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse recupera únicamente citas en ventana y no enviadas")
    void buscarCitasEnVentana_recuperaCitasNoEnviadasDentroDelRango() {
        LocalDateTime baseTime = LocalDateTime.of(2026, 6, 15, 10, 0);
        LocalDateTime finVentana = baseTime.plusHours(24);

        // Cita 1: Dentro de ventana y NO enviada -> Debe ser encontrada
        Cita citaValida = new Cita(null, "Mario Casas", "mario@example.com",
                baseTime.plusHours(6), CanalNotificacion.EMAIL, false);

        // Cita 2: Dentro de ventana pero YA enviada -> NO debe ser encontrada
        Cita citaYaEnviada = new Cita(null, "Lucía Méndez", "lucia@example.com",
                baseTime.plusHours(12), CanalNotificacion.EMAIL, true);

        // Cita 3: Fuera de ventana (a 48 horas) y NO enviada -> NO debe ser encontrada
        Cita citaFueraDeVentana = new Cita(null, "Pedro Infante", "pedro@example.com",
                baseTime.plusHours(48), CanalNotificacion.SMS, false);

        citaRepository.saveAll(List.of(citaValida, citaYaEnviada, citaFueraDeVentana));

        // Act
        List<Cita> resultado = citaRepository.findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse(baseTime, finVentana);

        // Assert
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getPacienteNombre()).isEqualTo("Mario Casas");
        assertThat(resultado.get(0).isRecordatorioEnviado()).isFalse();
    }

    @Test
    @DisplayName("save y findById persisten y recuperan correctamente los datos de la cita")
    void guardarYRecuperarCita_funcionaCorrectamente() {
        Cita cita = new Cita(null, "Elena Poniatowska", "3119876543",
                LocalDateTime.of(2026, 7, 1, 9, 30), CanalNotificacion.SMS, false);

        Cita guardada = citaRepository.save(cita);
        assertThat(guardada.getId()).isNotNull();

        Cita encontrada = citaRepository.findById(guardada.getId()).orElse(null);
        assertThat(encontrada).isNotNull();
        assertThat(encontrada.getPacienteNombre()).isEqualTo("Elena Poniatowska");
        assertThat(encontrada.getCanalPreferido()).isEqualTo(CanalNotificacion.SMS);
    }
}
