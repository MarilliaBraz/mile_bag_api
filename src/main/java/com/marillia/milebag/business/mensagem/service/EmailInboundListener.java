package com.marillia.milebag.business.mensagem.service;

import java.io.IOException;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.tenant.service.TenantService;
import com.marillia.milebag.core.config.MensageriaProperties;
import com.marillia.milebag.core.config.security.TenantContext;

import jakarta.mail.Address;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.search.FlagTerm;

/**
 * Monitora, via IMAP, a caixa de entrada da conversa com o passageiro
 * (HU18) e associa cada e-mail recebido a uma entrega pelo endereco de
 * destino ({@code entrega-{entregaId}@<dominio>}, {@code Reply-To} unico
 * por entrega - ver {@code EmailChannel}). Mesma estrutura de {@code
 * BdoEmailListener} (HU02), com uma caixa IMAP propria ({@link
 * MensageriaProperties}, nao {@code ImapProperties}) - ver seu javadoc
 * para o motivo de nao compartilhar a caixa.
 *
 * <p>Sem JWT nesta via de entrada: o {@code entregaId} ja vem no proprio
 * endereco, mas o tenant dono dele so e descoberto tentando cada tenant
 * conhecido ({@link TenantService#listarTodosOsIds}) ate a entrega ser
 * encontrada - mesmo raciocinio do {@code TelegramWebhookController}.</p>
 */
@Component
public class EmailInboundListener {

    private static final Logger log = LoggerFactory.getLogger(EmailInboundListener.class);
    // find() (nao matches()): o endereco de e-mail costuma vir embutido em
    // "Nome <entrega-xxx@dominio>", nao sozinho na string (mesma observacao
    // que motiva o uso de contains() em BdoEmailListener).
    private static final Pattern PADRAO_ENDERECO_ENTREGA = Pattern.compile("entrega-([0-9a-fA-F-]{36})@", Pattern.CASE_INSENSITIVE);

    private final MensageriaProperties mensageriaProperties;
    private final TenantService tenantService;
    private final EntregaService entregaService;
    private final MensagemService mensagemService;

    public EmailInboundListener(
            MensageriaProperties mensageriaProperties, TenantService tenantService, EntregaService entregaService,
            MensagemService mensagemService) {
        this.mensageriaProperties = mensageriaProperties;
        this.tenantService = tenantService;
        this.entregaService = entregaService;
        this.mensagemService = mensagemService;
    }

    @Scheduled(fixedDelayString = "${milebag.mensageria.imap-intervalo-consulta-ms:60000}")
    public void consultarCaixaDeEntrada() {
        if (!mensageriaProperties.imapHabilitado()) {
            return;
        }

        Properties propriedadesSessao = new Properties();
        propriedadesSessao.put("mail.store.protocol", "imaps");
        Session sessao = Session.getInstance(propriedadesSessao);

        try {
            Store store = sessao.getStore("imaps");
            try {
                store.connect(
                        mensageriaProperties.imapHost(), mensageriaProperties.imapPort(), mensageriaProperties.imapUsuario(),
                        mensageriaProperties.imapSenha());
                consultarInbox(store);
            } finally {
                store.close();
            }
        } catch (MessagingException e) {
            log.error("Falha ao consultar a caixa de entrada da conversa com o passageiro.", e);
        }
    }

    private void consultarInbox(Store store) throws MessagingException {
        Folder inbox = store.getFolder("INBOX");
        try {
            inbox.open(Folder.READ_WRITE);
            Message[] mensagensNaoLidas = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
            for (Message mensagem : mensagensNaoLidas) {
                processarMensagem(mensagem);
            }
        } finally {
            // Se open() falhou a pasta segue fechada e close() lancaria IllegalStateException, escondendo o erro real.
            if (inbox.isOpen()) {
                inbox.close(false);
            }
        }
    }

    private void processarMensagem(Message mensagem) {
        try {
            UUID entregaId = resolverEntregaId(mensagem);
            if (entregaId == null) {
                log.warn("E-mail recebido nao corresponde a nenhum endereco de entrega conhecido - ignorado.");
                mensagem.setFlag(Flags.Flag.SEEN, true);
                return;
            }

            String corpo = TextoRespostaEmail.extrair(mensagem, MensagemService.PREFIXO_ASSUNTO_CONVERSA);
            if (corpo.isEmpty()) {
                log.warn("E-mail recebido para a entrega {} sem texto nem assunto - ignorado.", entregaId);
            } else if (!tentarRegistrarEmTodosOsTenants(entregaId, corpo)) {
                log.warn("E-mail recebido para a entrega {} nao encontrada em nenhum tenant - ignorado.", entregaId);
            }

            mensagem.setFlag(Flags.Flag.SEEN, true);
        } catch (MessagingException | IOException e) {
            log.error("Falha ao processar e-mail da conversa com o passageiro - mantido para nova tentativa.", e);
        }
    }

    private boolean tentarRegistrarEmTodosOsTenants(UUID entregaId, String corpo) {
        for (UUID tenantId : tenantService.listarTodosOsIds()) {
            TenantContext.set(tenantId);
            try {
                if (entregaService.buscarPorId(entregaId).isPresent()) {
                    mensagemService.registrarRecebidaEmail(entregaId, corpo);
                    return true;
                }
            } finally {
                TenantContext.clear();
            }
        }
        return false;
    }

    private UUID resolverEntregaId(Message mensagem) throws MessagingException {
        Address[] destinatarios = mensagem.getAllRecipients();
        if (destinatarios == null) {
            return null;
        }
        for (Address destinatario : destinatarios) {
            Matcher matcher = PADRAO_ENDERECO_ENTREGA.matcher(destinatario.toString());
            if (matcher.find()) {
                try {
                    return UUID.fromString(matcher.group(1));
                } catch (IllegalArgumentException e) {
                    log.warn("Endereco de entrega malformado no destinatario '{}'.", destinatario);
                }
            }
        }
        return null;
    }
}
