package com.clinica.recordatorios.channel;

import com.clinica.recordatorios.domain.Cita;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Patrón Strategy: Implementación concreta de recordatorio vía correo electrónico.
 */
@Component("emailReminderChannel")
public class EmailReminderChannel implements ReminderChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailReminderChannel.class);

    @Override
    public void enviar(Cita cita, String mensaje) {
        log.info("Enviando recordatorio por EMAIL a {} ({}) para cita #{}: {}",
                cita.getPacienteNombre(), cita.getPacienteEmail(), cita.getId(), mensaje);
    }
}
