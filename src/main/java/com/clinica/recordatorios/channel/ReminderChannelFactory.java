package com.clinica.recordatorios.channel;

import com.clinica.recordatorios.domain.CanalNotificacion;

/**
 * Patrón Factory Method: Contrato para instanciar u obtener la estrategia ReminderChannel correspondiente.
 */
public interface ReminderChannelFactory {
    ReminderChannel obtenerCanal(CanalNotificacion canal);
}
