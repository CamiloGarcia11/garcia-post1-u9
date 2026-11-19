package com.clinica.recordatorios.channel;

import com.clinica.recordatorios.domain.Cita;

/**
 * Patrón Strategy: Interfaz común para los canales de recordatorio de citas.
 */
public interface ReminderChannel {
    void enviar(Cita cita, String mensaje);
}
