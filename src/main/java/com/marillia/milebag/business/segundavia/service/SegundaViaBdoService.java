package com.marillia.milebag.business.segundavia.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCarona;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCaronaService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.segundavia.model.EnvioSegundaViaBdo;
import com.marillia.milebag.business.segundavia.repository.EnvioSegundaViaBdoRepository;
import com.marillia.milebag.business.tenant.service.LimitadorSolicitacoes;
import com.marillia.milebag.business.tenant.service.TenantService;
import com.marillia.milebag.business.tenant.service.UsuarioConsultaService;
import com.marillia.milebag.core.config.SmtpProperties;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.AcessoNegadoException;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.EnvioIndisponivelException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.core.mail.AnexoEmail;
import com.marillia.milebag.core.mail.EmailSender;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

/**
 * Segunda via do BDO (V50): o PDF para imprimir, se o papel se perder no caminho, e o envio desse PDF por e-mail para
 * imprimir onde houver impressora. O PDF e gerado na hora e nunca guardado (dados pessoais do passageiro).
 *
 * <p>O motorista so emite a da entrega que esta com ele (assumida ou em viagem), com as bagagens carona juntas; a
 * empresa emite a de qualquer BDO. Cada envio por e-mail fica registrado e aparece no historico da mala; o mesmo
 * limitador da recuperacao de senha segura o envio em serie para enderecos de fora.</p>
 */
@Service
public class SegundaViaBdoService {

    private static final Logger log = LoggerFactory.getLogger(SegundaViaBdoService.class);
    private static final Set<EntregaStatus> COM_O_MOTORISTA = Set.of(EntregaStatus.ATRIBUIDA, EntregaStatus.EM_ANDAMENTO);
    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(ZoneId.of("America/Sao_Paulo"));

    private final BdoService bdoService;
    private final EntregaService entregaService;
    private final BagagemCaronaService bagagemCaronaService;
    private final EnvioSegundaViaBdoRepository envioRepository;
    private final EmailSender emailSender;
    private final SmtpProperties smtpProperties;
    private final LimitadorSolicitacoes limitador;
    private final TenantService tenantService;
    private final UsuarioConsultaService usuarioConsultaService;

    public SegundaViaBdoService(BdoService bdoService, EntregaService entregaService, BagagemCaronaService bagagemCaronaService,
            EnvioSegundaViaBdoRepository envioRepository, EmailSender emailSender, SmtpProperties smtpProperties,
            LimitadorSolicitacoes limitador, TenantService tenantService, UsuarioConsultaService usuarioConsultaService) {
        this.bdoService = bdoService;
        this.entregaService = entregaService;
        this.bagagemCaronaService = bagagemCaronaService;
        this.envioRepository = envioRepository;
        this.emailSender = emailSender;
        this.smtpProperties = smtpProperties;
        this.limitador = limitador;
        this.tenantService = tenantService;
        this.usuarioConsultaService = usuarioConsultaService;
    }

    /** O arquivo pronto para baixar ou anexar. */
    public record ArquivoPdf(String nomeArquivo, byte[] conteudo) {
    }

    @Transactional(readOnly = true)
    public ArquivoPdf pdfDoMotorista(UUID entregaId, UUID motoristaId) {
        List<Bdo> bdos = bdosDaEntregaDoMotorista(entregaId, motoristaId);
        return gerar(bdos);
    }

    @Transactional(readOnly = true)
    public ArquivoPdf pdfDaEmpresa(UUID bdoId) {
        return gerar(List.of(bdoDaEmpresa(bdoId)));
    }

    @Transactional
    public void enviarDoMotorista(UUID entregaId, UUID motoristaId, String email) {
        String destinatario = validarEmail(email);
        List<Bdo> bdos = bdosDaEntregaDoMotorista(entregaId, motoristaId);
        enviar(bdos, destinatario, motoristaId, "segunda-via:" + motoristaId);
    }

    @Transactional
    public void enviarDaEmpresa(UUID bdoId, UUID administradorId, String email) {
        String destinatario = validarEmail(email);
        Bdo bdo = bdoDaEmpresa(bdoId);
        enviar(List.of(bdo), destinatario, administradorId, "segunda-via:" + administradorId + ":" + bdoId);
    }

    /** Os envios por e-mail do BDO, para o historico da mala. */
    @Transactional(readOnly = true)
    public List<EnvioSegundaViaBdo> listarEnvios(UUID bdoId) {
        return envioRepository.findByBdoIdOrderByDataHoraAsc(bdoId);
    }

    /** A principal e as caronas que viajam com ela: o motorista leva todas, e qualquer papel pode se perder. */
    private List<Bdo> bdosDaEntregaDoMotorista(UUID entregaId, UUID motoristaId) {
        Entrega entrega = entregaService.buscarPorId(entregaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Entrega nao encontrada."));
        EntregaService.exigirNaoCancelada(entrega);
        if (!motoristaId.equals(entrega.getMotoristaResponsavel())) {
            throw new AcessoNegadoException("So o motorista responsavel pela entrega pode emitir a segunda via do BDO.");
        }
        if (!COM_O_MOTORISTA.contains(entrega.getStatus())) {
            throw new ConflitoException("A segunda via so pode ser emitida enquanto a entrega estiver com voce.");
        }
        List<Bdo> bdos = new ArrayList<>();
        bdos.add(bdoService.buscarPorId(entrega.getBdoId()).orElseThrow(() -> new RecursoNaoEncontradoException("BDO nao encontrado.")));
        for (BagagemCarona carona : bagagemCaronaService.listarPorEntrega(entregaId)) {
            bdoService.buscarPorId(carona.getBdoId()).ifPresent(bdos::add);
        }
        return bdos;
    }

    private Bdo bdoDaEmpresa(UUID bdoId) {
        return bdoService.buscarPorId(bdoId).orElseThrow(() -> new RecursoNaoEncontradoException("BDO nao encontrado."));
    }

    private ArquivoPdf gerar(List<Bdo> bdos) {
        String empresa = tenantService.buscar(TenantContext.get()).getNome();
        byte[] pdf = SegundaViaBdoPdf.gerar(bdos, empresa, Instant.now());
        Bdo principal = bdos.get(0);
        String numero = principal.getNumero() == null ? principal.getId().toString().substring(0, 8) : principal.getNumero();
        return new ArquivoPdf("BDO-" + numero.replaceAll("[^A-Za-z0-9]", "") + "-segunda-via.pdf", pdf);
    }

    private void enviar(List<Bdo> bdos, String destinatario, UUID enviadoPor, String chaveLimite) {
        // Com o SMTP desligado o envio "daria certo" sem sair nada: melhor dizer que nao esta disponivel.
        if (!smtpProperties.habilitado()) {
            throw new EnvioIndisponivelException("O envio de e-mail esta desativado neste ambiente. Use o botao Imprimir.");
        }
        if (!limitador.permitir(chaveLimite)) {
            throw new RegraNegocioException("Muitos envios em pouco tempo. Tente de novo daqui a uma hora ou use o botao Imprimir.");
        }
        ArquivoPdf arquivo = gerar(bdos);
        Instant agora = Instant.now();
        Bdo principal = bdos.get(0);
        String numero = principal.getNumero() == null ? "" : " " + principal.getNumero();
        String remetente = usuarioConsultaService.nomeParaExibicao(enviadoPor).orElse("Milebag");
        String corpo = "Segue em anexo a segunda via do BDO" + numero + " para impressão.\n\n"
                + "Enviado por " + remetente + " pelo Milebag em " + DATA_HORA.format(agora) + " (horário de Brasília).\n\n"
                + "Este documento contém dados pessoais do passageiro: use somente para a entrega e descarte depois.";
        try {
            emailSender.enviarComAnexo(destinatario, "BDO" + numero + " - segunda via para impressão", corpo,
                    new AnexoEmail(arquivo.nomeArquivo(), "application/pdf", arquivo.conteudo()));
        } catch (Exception e) {
            log.error("Falha ao enviar a segunda via do BDO {}.", principal.getId(), e);
            throw new EnvioIndisponivelException("Nao foi possivel enviar o e-mail agora. Tente de novo em alguns minutos ou use o botao Imprimir.");
        }
        for (Bdo bdo : bdos) {
            envioRepository.save(new EnvioSegundaViaBdo(bdo.getId(), enviadoPor, destinatario, agora));
        }
        log.info("Segunda via do BDO {} enviada por e-mail por {} (tenant {}).", principal.getId(), enviadoPor, TenantContext.get());
    }

    private static String validarEmail(String email) {
        String limpo = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        if (limpo.isEmpty()) {
            throw new RegraNegocioException("Informe o e-mail para onde enviar o BDO.");
        }
        // Um endereco so, sem lista nem nome ("a@b.com, c@d.com" ou "<a@b.com>"): o PDF vai para quem foi digitado.
        if (limpo.length() > 200 || !limpo.matches("[^@\\s,;<>\"()]+@[^@\\s,;<>\"()]+\\.[^@\\s,;<>\"()]+")) {
            throw new RegraNegocioException("O e-mail informado nao parece valido.");
        }
        try {
            new InternetAddress(limpo, true).validate();
        } catch (AddressException e) {
            throw new RegraNegocioException("O e-mail informado nao parece valido.");
        }
        return limpo;
    }
}
