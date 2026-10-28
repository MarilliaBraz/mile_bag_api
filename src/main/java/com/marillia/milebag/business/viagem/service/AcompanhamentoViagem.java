package com.marillia.milebag.business.viagem.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.tenant.service.UsuarioConsultaService;
import com.marillia.milebag.business.viagem.model.LinkAcompanhamento;
import com.marillia.milebag.business.viagem.model.Trajeto;
import com.marillia.milebag.business.viagem.repository.LinkAcompanhamentoRepository;
import com.marillia.milebag.core.config.ConviteProperties;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.mail.EmailSender;

/**
 * Link "Acompanhe sua bagagem" da tentativa (HU31): cria o link ao iniciar a viagem e avisa os administradores
 * do tenant por e-mail. O aviso ao passageiro sai pela mensageria (HU18), no texto de viagem iniciada. A URL usa
 * a mesma base publica dos e-mails de convite e de senha ({@code milebag.convite.url-base}).
 */
@Component
public class AcompanhamentoViagem {

    private static final Logger log = LoggerFactory.getLogger(AcompanhamentoViagem.class);

    private final LinkAcompanhamentoRepository linkAcompanhamentoRepository;
    private final ConviteProperties conviteProperties;
    private final EmailSender emailSender;
    private final UsuarioConsultaService usuarioConsultaService;
    private final EntregaService entregaService;
    private final BdoService bdoService;

    public AcompanhamentoViagem(
            LinkAcompanhamentoRepository linkAcompanhamentoRepository,
            ConviteProperties conviteProperties,
            EmailSender emailSender,
            UsuarioConsultaService usuarioConsultaService,
            EntregaService entregaService,
            BdoService bdoService) {
        this.linkAcompanhamentoRepository = linkAcompanhamentoRepository;
        this.conviteProperties = conviteProperties;
        this.emailSender = emailSender;
        this.usuarioConsultaService = usuarioConsultaService;
        this.entregaService = entregaService;
        this.bdoService = bdoService;
    }

    /** Cria o link da tentativa e devolve a URL publica dele. */
    public String criarLink(Trajeto trajeto) {
        LinkAcompanhamento link = linkAcompanhamentoRepository.save(new LinkAcompanhamento(TenantContext.get(), trajeto));
        return url(link.getCodigo());
    }

    /** Leitura publica (sem tenant): a tabela nao tem RLS, o link e o que revela o tenant. */
    public Optional<LinkAcompanhamento> buscarLink(UUID codigo) {
        return linkAcompanhamentoRepository.findById(codigo);
    }

    public String url(UUID codigo) {
        return conviteProperties.urlBase() + "/acompanhar/" + codigo;
    }

    /** Nunca lanca: falha de envio nao pode impedir o inicio da viagem (mesma regra de NotificadorEmergencia, HU30). */
    public void avisarAdministradores(UUID entregaId, String url) {
        List<String> destinatarios = usuarioConsultaService.emailsDosAdministradoresAtivos();
        if (destinatarios.isEmpty()) {
            return;
        }
        String bdo = numeroDoBdo(entregaId).orElse("sem numero");
        String assunto = "[Milebag] Viagem iniciada: BDO " + bdo;
        String corpo = "O motorista iniciou a viagem de entrega do BDO " + bdo + ".\n\n"
                + "Acompanhe a bagagem em tempo real: " + url + "\n\n"
                + "O mesmo link foi enviado ao passageiro. Ele mostra a posicao do motorista so ate a entrega ser concluida.";
        for (String destinatario : destinatarios) {
            try {
                emailSender.enviar(destinatario, assunto, corpo);
            } catch (Exception e) {
                log.error("Falha ao enviar o link de acompanhamento da entrega {} a {} - viagem ja iniciada.", entregaId, destinatario, e);
            }
        }
    }

    private Optional<String> numeroDoBdo(UUID entregaId) {
        return entregaService.buscarPorId(entregaId)
                .map(Entrega::getBdoId)
                .flatMap(bdoService::buscarPorId)
                .map(Bdo::getNumero);
    }
}
