package com.clinica.recordatorios.config;

import com.clinica.recordatorios.channel.ReminderChannel;
import com.clinica.recordatorios.channel.SmsReminderChannel;
import com.clinica.recordatorios.channel.gateway.NotificadorSmsGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de infraestructura para resolver la dependencia del canal urgente (DIP).
 */
@Configuration
public class CanalUrgenteConfig {

    @Bean(name = "canalUrgente")
    public ReminderChannel canalUrgente(NotificadorSmsGateway smsGateway) {
        return new SmsReminderChannel(smsGateway);
    }
}
