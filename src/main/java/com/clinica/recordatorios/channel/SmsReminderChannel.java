package com.clinica.recordatorios.channel;

import com.clinica.recordatorios.channel.gateway.NotificadorSmsGateway;
import com.clinica.recordatorios.domain.Cita;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Patrón Strategy: Implementación concreta de recordatorio vía SMS que delega en NotificadorSmsGateway.
 */
@Component("smsReminderChannel")
public class SmsReminderChannel implements ReminderChannel {

    private static final Logger log = LoggerFactory.getLogger(SmsReminderChannel.class);
    private final NotificadorSmsGateway smsGateway;

    public SmsReminderChannel(NotificadorSmsGateway smsGateway) {
        this.smsGateway = smsGateway;
    }

    @Override
    public void enviar(Cita cita, String mensaje) {
        log.info("Preparando envío de recordatorio por SMS para cita #{} a {}",
                cita.getId(), cita.getPacienteTelefono());
        smsGateway.enviarSms(cita.getPacienteTelefono(), mensaje);
    }

    public NotificadorSmsGateway getSmsGateway() {
        return smsGateway;
    }
}
