package com.marillia.milebag.business.demonstracao.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.marillia.milebag.business.demonstracao.model.PedidoDemonstracaoRequest;
import com.marillia.milebag.core.config.DemonstracaoProperties;
import com.marillia.milebag.core.config.SmtpProperties;
import com.marillia.milebag.core.exception.EnvioIndisponivelException;
import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.core.mail.EmailSender;

/**
 * Pedido de demonstracao da pagina institucional (HU38, RF31): valida, filtra robo e manda o pedido por e-mail para a
 * equipe, com o interessado como endereco de resposta. Nada e gravado no banco - o registro e o proprio e-mail.
 */
@Service
public class DemonstracaoService {

    private static final Logger log = LoggerFactory.getLogger(DemonstracaoService.class);

    static final long TEMPO_MINIMO_MS = 3_000;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern TELEFONE = Pattern.compile("^[0-9+()\\- ]+$");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final EmailSender emailSender;
    private final SmtpProperties smtpProperties;
    private final DemonstracaoProperties demonstracaoProperties;
    private final LimitadorPedidosDemonstracao limitador;
    private final ZoneId fusoHorario;

    public DemonstracaoService(EmailSender emailSender, SmtpProperties smtpProperties, DemonstracaoProperties demonstracaoProperties,
            LimitadorPedidosDemonstracao limitador, @Value("${milebag.fuso-horario:America/Sao_Paulo}") String fusoHorario) {
        this.emailSender = emailSender;
        this.smtpProperties = smtpProperties;
        this.demonstracaoProperties = demonstracaoProperties;
        this.limitador = limitador;
        this.fusoHorario = ZoneId.of(fusoHorario);
    }

    public void pedir(PedidoDemonstracaoRequest pedido, String ip) {
        // Robo: responde como se tivesse enviado, para nao ensinar a contornar o filtro.
        if (!vazio(pedido.site()) || pedido.preenchidoEmMs() == null || pedido.preenchidoEmMs() < TEMPO_MINIMO_MS) {
            log.info("Pedido de demonstracao descartado como automatico (ip {}).", ip);
            return;
        }

        String nome = obrigatorio(pedido.nome(), 2, 100, "Informe o nome (2 a 100 caracteres).");
        String empresa = obrigatorio(pedido.empresa(), 2, 120, "Informe a empresa (2 a 120 caracteres).");
        if (pedido.perfil() == null) {
            throw new RegraNegocioException("Informe se e empresa de entrega ou companhia aerea.");
        }
        String email = obrigatorio(pedido.email(), 3, 254, "Informe um e-mail valido.");
        if (!EMAIL.matcher(email).matches()) {
            throw new RegraNegocioException("Informe um e-mail valido.");
        }
        String telefone = aparado(pedido.telefone());
        if (telefone != null && (telefone.length() > 30 || !TELEFONE.matcher(telefone).matches())) {
            throw new RegraNegocioException("Telefone invalido.");
        }
        String mensagem = aparado(pedido.mensagem());
        if (mensagem != null && mensagem.length() > 2000) {
            throw new RegraNegocioException("A mensagem pode ter ate 2000 caracteres.");
        }
        if (!Boolean.TRUE.equals(pedido.consentimento())) {
            throw new RegraNegocioException("E preciso autorizar o contato sobre a demonstracao.");
        }

        limitador.registrar(ip);

        // EmailSenderSmtp so registra um aviso com o SMTP desligado: sem esta verificacao a pagina diria "enviado".
        if (!smtpProperties.habilitado()) {
            log.warn("Pedido de demonstracao de {} nao enviado: SMTP desabilitado.", empresa);
            throw new EnvioIndisponivelException();
        }
        String assunto = "Pedido de demonstração - " + empresa + " (" + pedido.perfil().rotulo() + ")";
        String corpo = "Novo pedido de demonstração pela página do Milebag.\n\n"
                + "Nome: " + nome + "\n"
                + "Empresa: " + empresa + "\n"
                + "Perfil: " + pedido.perfil().rotulo() + "\n"
                + "E-mail: " + email + "\n"
                + "Telefone: " + (telefone == null ? "não informado" : telefone) + "\n"
                + "Mensagem: " + (mensagem == null ? "-" : mensagem) + "\n\n"
                + "Recebido em " + ZonedDateTime.now(fusoHorario).format(DATA_HORA) + ". Responda este e-mail para falar com o interessado.";
        try {
            emailSender.enviar(demonstracaoProperties.destino(), assunto, corpo, email);
            log.info("Pedido de demonstracao de {} enviado.", empresa);
        } catch (Exception e) {
            log.error("Falha ao enviar o pedido de demonstracao de {}.", empresa, e);
            throw new EnvioIndisponivelException();
        }
    }

    private static String obrigatorio(String valor, int minimo, int maximo, String mensagem) {
        String texto = aparado(valor);
        if (texto == null || texto.length() < minimo || texto.length() > maximo) {
            throw new RegraNegocioException(mensagem);
        }
        return texto;
    }

    private static String aparado(String valor) {
        return vazio(valor) ? null : valor.trim();
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
