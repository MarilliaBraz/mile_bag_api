package com.marillia.milebag.business.bdo.mail;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.marillia.milebag.business.bdo.model.CamposExtraidosBdo;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.core.config.ImapProperties;
import com.marillia.milebag.core.config.security.TenantContext;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.search.FlagTerm;

/**
 * Monitora, via IMAP, a caixa de entrada compartilhada de recebimento de
 * BDO (RF21) e cria BDOs automaticamente a partir de e-mails reconhecidos.
 *
 * <p>Um unico endereco IMAP recebe todas as caixas {@code bdo-{tenantId}@...}
 * de cada tenant (roteadas pelo servidor de e-mail self-hosted de HU18); o
 * tenant e resolvido aqui comparando o destinatario do e-mail com
 * {@link TenantEmailBdoRepository} - nao ha JWT nesta via de entrada, entao
 * {@link TenantContext} e definido manualmente antes de persistir (mesmo
 * papel que {@code JwtAuthenticationFilter} cumpre para requisicoes HTTP).
 * </p>
 */
@Component
public class BdoEmailListener {

    private static final Logger log = LoggerFactory.getLogger(BdoEmailListener.class);

    private final ImapProperties imapProperties;
    private final TenantEmailBdoRepository tenantEmailBdoRepository;
    private final BdoTelexParser bdoTelexParser;
    private final BdoService bdoService;

    public BdoEmailListener(
            ImapProperties imapProperties,
            TenantEmailBdoRepository tenantEmailBdoRepository,
            BdoTelexParser bdoTelexParser,
            BdoService bdoService) {
        this.imapProperties = imapProperties;
        this.tenantEmailBdoRepository = tenantEmailBdoRepository;
        this.bdoTelexParser = bdoTelexParser;
        this.bdoService = bdoService;
    }

    @Scheduled(fixedDelayString = "${milebag.imap.intervalo-consulta-ms:60000}")
    public void consultarCaixaDeEntrada() {
        if (!imapProperties.habilitado()) {
            return;
        }

        Map<String, UUID> tenantPorEndereco = carregarEnderecosPorTenant();
        if (tenantPorEndereco.isEmpty()) {
            return;
        }

        Properties propriedadesSessao = new Properties();
        propriedadesSessao.put("mail.store.protocol", "imaps");
        Session sessao = Session.getInstance(propriedadesSessao);

        try {
            Store store = sessao.getStore("imaps");
            try {
                store.connect(imapProperties.host(), imapProperties.port(), imapProperties.usuario(), imapProperties.senha());
                consultarInbox(store, tenantPorEndereco);
            } finally {
                store.close();
            }
        } catch (MessagingException e) {
            log.error("Falha ao consultar a caixa de entrada de recebimento de BDO.", e);
        }
    }

    private void consultarInbox(Store store, Map<String, UUID> tenantPorEndereco) throws MessagingException {
        Folder inbox = store.getFolder("INBOX");
        try {
            inbox.open(Folder.READ_WRITE);
            Message[] mensagensNaoLidas = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
            for (Message mensagem : mensagensNaoLidas) {
                processarMensagem(mensagem, tenantPorEndereco);
            }
        } finally {
            inbox.close(false);
        }
    }

    private void processarMensagem(Message mensagem, Map<String, UUID> tenantPorEndereco) {
        try {
            String enderecoDestino = resolverEnderecoConhecido(mensagem, tenantPorEndereco.keySet());
            if (enderecoDestino == null) {
                log.warn("E-mail recebido nao corresponde a nenhuma caixa de tenant provisionada - ignorado.");
                mensagem.setFlag(Flags.Flag.SEEN, true);
                return;
            }

            UUID tenantId = tenantPorEndereco.get(enderecoDestino);
            String corpo = extrairCorpoTexto(mensagem);
            processarParaTenant(tenantId, enderecoDestino, corpo);

            mensagem.setFlag(Flags.Flag.SEEN, true);
        } catch (MessagingException | IOException e) {
            log.error("Falha ao processar e-mail de recebimento de BDO - mantido para nova tentativa.", e);
        }
    }

    private void processarParaTenant(UUID tenantId, String enderecoDestino, String corpo) {
        TenantContext.set(tenantId);
        try {
            Optional<CamposExtraidosBdo> campos = bdoTelexParser.parsear(corpo);
            if (campos.isPresent()) {
                bdoService.criarAutomatico(campos.get());
            } else {
                // FR-007: sem tela de conferencia nesta via, um registro parcial
                // nao poderia ser corrigido depois - nenhum BDO e criado.
                log.warn(
                        "E-mail para {} nao reconhecido como telex WorldTracer valido "
                                + "(campos obrigatorios ausentes) - tratamento manual necessario.",
                        enderecoDestino);
            }
        } finally {
            TenantContext.clear();
        }
    }

    private String resolverEnderecoConhecido(Message mensagem, Set<String> enderecosConhecidos) throws MessagingException {
        Address[] destinatarios = mensagem.getAllRecipients();
        if (destinatarios == null) {
            return null;
        }
        for (Address destinatario : destinatarios) {
            String endereco = destinatario.toString().toLowerCase();
            for (String conhecido : enderecosConhecidos) {
                if (endereco.contains(conhecido.toLowerCase())) {
                    return conhecido;
                }
            }
        }
        return null;
    }

    private String extrairCorpoTexto(Message mensagem) throws MessagingException, IOException {
        Object conteudo = mensagem.getContent();
        if (conteudo instanceof String texto) {
            return texto;
        }
        if (conteudo instanceof Multipart multipart) {
            StringBuilder textoCompleto = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart parte = multipart.getBodyPart(i);
                if (parte.isMimeType("text/plain")) {
                    textoCompleto.append(parte.getContent());
                }
            }
            return textoCompleto.toString();
        }
        return "";
    }

    private Map<String, UUID> carregarEnderecosPorTenant() {
        return tenantEmailBdoRepository.findAll().stream()
                .collect(Collectors.toMap(TenantEmailBdo::getEndereco, TenantEmailBdo::getTenantId));
    }
}
