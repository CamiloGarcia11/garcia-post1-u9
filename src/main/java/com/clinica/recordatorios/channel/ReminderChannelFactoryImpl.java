package com.clinica.recordatorios.channel;

import com.clinica.recordatorios.domain.CanalNotificacion;
import org.springframework.stereotype.Component;

/**
 * Implementación de ReminderChannelFactory que resuelve la estrategia de envío.
 */
@Component
public class ReminderChannelFactoryImpl implements ReminderChannelFactory {

    private final ReminderChannel emailReminderChannel;
    private final ReminderChannel smsReminderChannel;

    public ReminderChannelFactoryImpl(ReminderChannel emailReminderChannel,
                                      ReminderChannel smsReminderChannel) {
        this.emailReminderChannel = emailReminderChannel;
        this.smsReminderChannel = smsReminderChannel;
    }

    @Override
    public ReminderChannel obtenerCanal(CanalNotificacion canal) {
        if (canal == null) {
            throw new IllegalArgumentException("El canal de notificación no puede ser nulo");
        }
        return switch (canal) {
            case EMAIL -> emailReminderChannel;
            case SMS -> smsReminderChannel;
        };
    }
}
