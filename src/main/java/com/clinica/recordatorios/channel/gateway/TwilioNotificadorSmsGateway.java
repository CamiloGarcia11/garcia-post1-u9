package com.clinica.recordatorios.channel.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adaptador / Implementación de NotificadorSmsGateway simulando la integración con Twilio.
 */
@Component
public class TwilioNotificadorSmsGateway implements NotificadorSmsGateway {

    private static final Logger log = LoggerFactory.getLogger(TwilioNotificadorSmsGateway.class);

    @Override
    public void enviarSms(String telefonoDestino, String texto) {
        log.info("Twilio SMS Gateway -> Enviando SMS a {}: {}", telefonoDestino, texto);
    }
}
