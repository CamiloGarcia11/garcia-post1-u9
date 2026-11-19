package com.clinica.recordatorios.service;

import com.clinica.recordatorios.channel.ReminderChannel;
import com.clinica.recordatorios.channel.ReminderChannelFactory;
import com.clinica.recordatorios.domain.Cita;
import com.clinica.recordatorios.repository.CitaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CitaReminderServiceImpl implements CitaReminderService {

    private static final Logger log = LoggerFactory.getLogger(CitaReminderServiceImpl.class);

    private final CitaRepository citaRepository;
    private final ReminderChannelFactory channelFactory;
    private final ReminderChannel canalUrgente;
    private final Clock clock;

    public CitaReminderServiceImpl(CitaRepository citaRepository,
                                  ReminderChannelFactory channelFactory,
                                  @Qualifier("canalUrgente") ReminderChannel canalUrgente,
                                  Clock clock) {
        this.citaRepository = citaRepository;
        this.channelFactory = channelFactory;
        this.canalUrgente = canalUrgente;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void enviarRecordatoriosPendientes() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        LocalDateTime finVentana = ahora.plusHours(24);

        log.info("Buscando citas pendientes entre {} y {}", ahora, finVentana);
        List<Cita> citasPendientes = citaRepository.findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse(ahora, finVentana);

        for (Cita cita : citasPendientes) {
            ReminderChannel channel = channelFactory.obtenerCanal(cita.getCanalPreferido());
            String mensaje = String.format("Estimado(a) %s, le recordamos su cita medica programada para %s.",
                    cita.getPacienteNombre(), cita.getFechaHoraCita());
            channel.enviar(cita, mensaje);
            cita.setRecordatorioEnviado(true);
            citaRepository.save(cita);
            log.info("Recordatorio enviado y actualizado para la cita #{}", cita.getId());
        }
    }

    @Override
    @Transactional
    public void enviarRecordatorioUrgente(Long citaId) {
        Cita cita = citaRepository.findById(citaId)
                .orElseThrow(() -> new CitaNoEncontradaException("No se encontro la cita con ID: " + citaId));

        // DIP Aplicado: Se utiliza la abstracción inyectada por constructor sin acoplamiento a clases concretas
        String mensajeUrgente = String.format("URGENTE: Estimado(a) %s, confirme su cita medica inmediata programada para %s.",
                cita.getPacienteNombre(), cita.getFechaHoraCita());

        canalUrgente.enviar(cita, mensajeUrgente);
        cita.setRecordatorioEnviado(true);
        citaRepository.save(cita);
        log.info("Recordatorio urgente enviado para cita #{}", citaId);
    }
}
