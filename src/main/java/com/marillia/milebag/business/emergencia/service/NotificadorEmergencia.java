package com.marillia.milebag.business.emergencia.service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.marillia.milebag.business.emergencia.model.AlertaEmergencia;
import com.marillia.milebag.business.tenant.service.UsuarioConsultaService;
import com.marillia.milebag.core.mail.EmailSender;

/**
 * Avisa por e-mail os administradores do tenant de um alerta novo (HU30).
 * Nunca lanca: uma falha de envio nao pode impedir o registro do alerta,
 * que continua visivel no back-office.
 */
@Component
public class NotificadorEmergencia {

    private static final Logger log = LoggerFactory.getLogger(NotificadorEmergencia.class);
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'as' HH:mm");

    private final EmailSender emailSender;
    private final UsuarioConsultaService usuarioConsultaService;
    private final ZoneId fusoHorario;

    public NotificadorEmergencia(
            EmailSender emailSender,
            UsuarioConsultaService usuarioConsultaService,
            @Value("${milebag.fuso-horario:America/Sao_Paulo}") String fusoHorario) {
        this.emailSender = emailSender;
        this.usuarioConsultaService = usuarioConsultaService;
        this.fusoHorario = ZoneId.of(fusoHorario);
    }

    public void notificarAdministradores(AlertaEmergencia alerta, String motoristaNome) {
        List<String> destinatarios = usuarioConsultaService.emailsDosAdministradoresAtivos();
        if (destinatarios.isEmpty()) {
            log.warn("Alerta de emergencia {} sem administrador com e-mail para avisar - fica so no back-office.", alerta.getId());
            return;
        }
        String assunto = "[Milebag] EMERGENCIA: " + motoristaNome + " acionou o alerta";
        String corpo = corpo(alerta, motoristaNome);
        for (String destinatario : destinatarios) {
            try {
                emailSender.enviar(destinatario, assunto, corpo);
            } catch (Exception e) {
                log.error("Falha ao avisar {} do alerta de emergencia {} - alerta ja registrado.", destinatario, alerta.getId(), e);
            }
        }
        log.info("Alerta de emergencia {} avisado a {} administrador(es).", alerta.getId(), destinatarios.size());
    }

    String corpo(AlertaEmergencia alerta, String motoristaNome) {
        String quando = DATA_HORA.format(alerta.getDataHora().atZone(fusoHorario));
        String onde = alerta.hasPosicao()
                ? "Posicao no momento do alerta: " + linkMapa(alerta.getLatitude(), alerta.getLongitude())
                : "O aparelho nao informou a posicao (GPS indisponivel).";
        return "O motorista " + motoristaNome + " acionou o alerta de emergencia em " + quando + ".\n\n"
                + onde + "\n\n"
                + "Entre em contato com o motorista agora. Se houver risco a vida, ligue 190.\n"
                + "Depois de atender, marque o alerta como atendido no Milebag (Administracao > Emergencias).";
    }

    private static String linkMapa(double latitude, double longitude) {
        String lat = String.format(Locale.ROOT, "%.6f", latitude);
        String lon = String.format(Locale.ROOT, "%.6f", longitude);
        return "https://www.openstreetmap.org/?mlat=" + lat + "&mlon=" + lon + "#map=17/" + lat + "/" + lon;
    }
}
