package com.clinica.recordatorios.channel.gateway;

/**
 * Puerto / Gateway para el envío de mensajes SMS mediante proveedores externos.
 */
public interface NotificadorSmsGateway {
    void enviarSms(String telefonoDestino, String texto);
}
