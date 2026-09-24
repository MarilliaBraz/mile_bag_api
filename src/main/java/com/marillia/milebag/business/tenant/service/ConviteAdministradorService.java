package com.marillia.milebag.business.tenant.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.tenant.model.ConviteAdministrador;
import com.marillia.milebag.business.tenant.model.ConviteConsultaResponse;
import com.marillia.milebag.business.tenant.model.ConviteStatus;
import com.marillia.milebag.business.tenant.model.Tenant;
import com.marillia.milebag.business.tenant.model.Usuario;
import com.marillia.milebag.business.tenant.repository.ConviteAdministradorRepository;
import com.marillia.milebag.business.tenant.repository.TenantRepository;
import com.marillia.milebag.business.tenant.repository.UsuarioRepository;
import com.marillia.milebag.core.config.ConviteProperties;
import com.marillia.milebag.core.config.security.Papeis;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.ConviteExpiradoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.core.mail.EmailSender;

import jakarta.mail.MessagingException;

/**
 * Convite do primeiro administrador do tenant (HU15) - ver
 * contracts/convite-api.md. Depende de {@link TenantRepository} e {@link
 * UsuarioRepository} diretamente (nao de {@code TenantService}) para
 * evitar dependencia circular com {@code TenantService}, que chama {@link
 * #criarEEnviar} ao final do cadastro do tenant (HU14).
 */
@Service
public class ConviteAdministradorService {

    private static final Logger log = LoggerFactory.getLogger(ConviteAdministradorService.class);
    private static final Pattern EMAIL_REGEX = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final ConviteAdministradorRepository conviteAdministradorRepository;
    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final TokenConviteGenerator tokenConviteGenerator;
    private final EmailSender emailSender;
    private final PasswordEncoder passwordEncoder;
    private final ConviteProperties conviteProperties;

    public ConviteAdministradorService(
            ConviteAdministradorRepository conviteAdministradorRepository,
            TenantRepository tenantRepository,
            UsuarioRepository usuarioRepository,
            TokenConviteGenerator tokenConviteGenerator,
            EmailSender emailSender,
            PasswordEncoder passwordEncoder,
            ConviteProperties conviteProperties) {
        this.conviteAdministradorRepository = conviteAdministradorRepository;
        this.tenantRepository = tenantRepository;
        this.usuarioRepository = usuarioRepository;
        this.tokenConviteGenerator = tokenConviteGenerator;
        this.emailSender = emailSender;
        this.passwordEncoder = passwordEncoder;
        this.conviteProperties = conviteProperties;
    }

    /**
     * Chamado por {@code TenantService.cadastrar(...)} dentro da MESMA
     * transacao (HU14, FR-006) - um e-mail malformado (RegraNegocioException,
     * 400) reverte tambem a criacao do tenant: mais simples do que inventar
     * um estado de "pendencia" nao previsto em nenhum data-model, e ainda
     * satisfaz o Edge Case 1 desta HU (nenhum tenant fica sem caminho de
     * ativacao, porque nenhum tenant chega a existir com esse defeito).
     *
     * <p>Falha no ENVIO (SMTP fora do ar, por exemplo) e tratada de forma
     * diferente: best-effort, nunca bloqueia - o convite/tenant ja validos
     * permanecem persistidos (PENDENTE), recuperaveis via {@link
     * #reenviar}, que existe exatamente para esse cenario.</p>
     */
    @Transactional
    public ConviteAdministrador criarEEnviar(UUID tenantId, String emailDestinatario) {
        if (emailDestinatario == null || !EMAIL_REGEX.matcher(emailDestinatario).matches()) {
            throw new RegraNegocioException("E-mail do administrador indicado invalido ou malformado.");
        }

        conviteAdministradorRepository.findByTenantIdAndStatus(tenantId, ConviteStatus.PENDENTE)
                .ifPresent(pendente -> {
                    pendente.invalidar();
                    conviteAdministradorRepository.save(pendente);
                });

        Instant expiraEm = Instant.now().plus(conviteProperties.prazoValidadeHoras(), ChronoUnit.HOURS);
        ConviteAdministrador convite = conviteAdministradorRepository.save(
                new ConviteAdministrador(tokenConviteGenerator.gerar(), tenantId, emailDestinatario, expiraEm));

        enviarEmailBestEffort(convite);
        log.info("Convite {} criado para o tenant {} (expira em {})", convite.getToken(), tenantId, expiraEm);
        return convite;
    }

    /** FR-006/Edge Case 2/3: reenvio so se aplica a um tenant sem administrador ativo; convite PENDENTE anterior e invalidado por {@link #criarEEnviar}. */
    @Transactional
    public ConviteAdministrador reenviar(UUID tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new RecursoNaoEncontradoException("Tenant nao encontrado.");
        }
        if (possuiAdministradorAtivo(tenantId)) {
            throw new ConflitoException("Tenant ja possui administrador ativo; reenvio nao se aplica.");
        }
        ConviteAdministrador ultimo = conviteAdministradorRepository.findFirstByTenantIdOrderByCriadoEmDesc(tenantId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Nenhum convite anterior encontrado para este tenant."));

        return criarEEnviar(tenantId, ultimo.getEmailDestinatario());
    }

    @Transactional
    public ConviteConsultaResponse consultar(UUID token) {
        ConviteAdministrador convite = buscarAtivo(token);
        String tenantNome = tenantRepository.findById(convite.getTenantId()).map(Tenant::getNome).orElse(null);
        return new ConviteConsultaResponse(tenantNome, convite.getEmailDestinatario(), convite.getStatus().name());
    }

    /**
     * Revalida o estado do convite no momento do envio (nao confia na
     * checagem previa do GET - contracts/convite-api.md). O bridging manual
     * de {@link TenantContext} abaixo segue o mesmo padrao ja usado por
     * {@code BdoEmailListener} (HU02) para outro fluxo tambem sem JWT: a
     * ativacao e um endpoint publico, entao nao ha claim de tenant a
     * propagar automaticamente para o {@code @PrePersist} de {@link
     * Usuario} (que estende {@code BaseEntity}) - sem isso, a policy de RLS
     * de {@code usuario} (V23) rejeitaria o INSERT.
     */
    @Transactional
    public void ativar(UUID token, String senha) {
        if (senha == null || senha.length() < 8) {
            throw new RegraNegocioException("senha e obrigatoria e deve ter ao menos 8 caracteres.");
        }
        ConviteAdministrador convite = buscarAtivo(token);

        TenantContext.set(convite.getTenantId());
        try {
            try {
                // saveAndFlush: o indice unico de login (V29) tem que disparar aqui, dentro do try, e nao so no commit.
                usuarioRepository.saveAndFlush(new Usuario(convite.getEmailDestinatario(), passwordEncoder.encode(senha), Papeis.ADMINISTRADOR_TENANT));
            } catch (DataIntegrityViolationException e) {
                throw new ConflitoException("Ja existe uma conta com este e-mail.");
            }
        } finally {
            TenantContext.clear();
        }

        convite.marcarUsado();
        conviteAdministradorRepository.save(convite);
        log.info("Convite {} ativado - administrador criado para o tenant {}", token, convite.getTenantId());
    }

    /** PENDENTE (nao expirado) e o unico status que segue em frente; qualquer outro lanca a excecao correspondente (404/410/409). */
    private ConviteAdministrador buscarAtivo(UUID token) {
        ConviteAdministrador convite = conviteAdministradorRepository.findById(token)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Convite nao encontrado."));

        if (convite.getStatus() == ConviteStatus.PENDENTE && convite.expirado()) {
            convite.marcarExpirado();
            conviteAdministradorRepository.save(convite);
        }

        return switch (convite.getStatus()) {
            case PENDENTE -> convite;
            case EXPIRADO -> throw new ConviteExpiradoException("Convite expirado; solicite um novo.");
            case USADO, INVALIDADO -> throw new ConflitoException("Convite ja utilizado ou invalidado.");
        };
    }

    private boolean possuiAdministradorAtivo(UUID tenantId) {
        TenantContext.set(tenantId);
        try {
            return usuarioRepository.findAll().stream().anyMatch(u -> Papeis.ADMINISTRADOR_TENANT.equals(u.getPapel()));
        } finally {
            TenantContext.clear();
        }
    }

    private void enviarEmailBestEffort(ConviteAdministrador convite) {
        String link = conviteProperties.urlBase() + "/convites/" + convite.getToken();
        String corpo = "Voce foi indicado como administrador de um tenant no Milebag.\n\n"
                + "Para ativar seu acesso, acesse: " + link + "\n\n"
                + "Este link expira em " + convite.getExpiraEm() + ".";
        try {
            emailSender.enviar(convite.getEmailDestinatario(), "Milebag - Convite de administrador", corpo);
        } catch (MessagingException e) {
            log.error(
                    "Falha ao enviar e-mail de convite para o tenant {} - convite persistido como PENDENTE, recuperavel via reenvio.",
                    convite.getTenantId(),
                    e);
        }
    }
}
