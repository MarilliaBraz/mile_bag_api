package com.marillia.milebag.business.tenant.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.marillia.milebag.business.configuracao.service.MotivoOcorrenciaService;
import com.marillia.milebag.business.tenant.model.Tenant;
import com.marillia.milebag.business.tenant.model.Usuario;
import com.marillia.milebag.business.tenant.model.UsuarioPlataforma;
import com.marillia.milebag.business.tenant.repository.TenantRepository;
import com.marillia.milebag.business.tenant.repository.UsuarioPlataformaRepository;
import com.marillia.milebag.business.tenant.repository.UsuarioRepository;
import com.marillia.milebag.core.config.security.JwtProperties;
import com.marillia.milebag.core.config.security.Papeis;
import com.marillia.milebag.core.config.security.TenantContext;

/**
 * Contas PADRAO de DESENVOLVIMENTO, com senha conhecida ({@value #SENHA_DEV}) -
 * so existem quando {@code milebag.dev.seed-usuarios=true}, propriedade ligada
 * apenas em {@code application-dev.yml}. Nunca ligar fora de desenvolvimento:
 * uma senha fixa e publica e uma porta aberta. Como o perfil {@code dev} e o
 * padrao, ha uma trava extra: o seed so roda se o segredo do JWT for o de
 * desenvolvimento - um ambiente real tem que sobrescrever esse segredo, e ai
 * as contas nao sao criadas mesmo que o perfil tenha sido esquecido.
 *
 * <p>Cria (se ainda nao existirem): o administrador da plataforma {@code admin},
 * a empresa {@code dev} (no plano de desenvolvimento) e, nela, o administrador
 * {@code empresa} e o {@code motorista} - este ultimo porque nenhuma HU cria
 * motoristas ainda, e sem ele o app do motorista nao teria como entrar.</p>
 */
@Component
@ConditionalOnProperty(name = "milebag.dev.seed-usuarios", havingValue = "true")
public class DevUsuariosSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevUsuariosSeeder.class);

    static final String SENHA_DEV = "12345678";
    static final String IDENTIFICADOR_EMPRESA_DEV = "dev";
    /** Plano criado por V22__seed_dev_plano_assinatura.sql. */
    /** Igual ao de application-dev.yml. */
    static final String SEGREDO_JWT_DEV = "dev-only-secret-nao-usar-em-producao-0123456789abcdef";
    private static final UUID PLANO_DEV = UUID.fromString("00000000-0000-0000-0000-000000000201");

    private final UsuarioPlataformaRepository usuarioPlataformaRepository;
    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final TenantPlanoService tenantPlanoService;
    private final MotivoOcorrenciaService motivoOcorrenciaService;
    private final PasswordEncoder passwordEncoder;
    private final JwtProperties jwtProperties;
    private final TransactionTemplate transacao;

    public DevUsuariosSeeder(
            UsuarioPlataformaRepository usuarioPlataformaRepository, TenantRepository tenantRepository, UsuarioRepository usuarioRepository,
            TenantPlanoService tenantPlanoService, MotivoOcorrenciaService motivoOcorrenciaService, PasswordEncoder passwordEncoder, JwtProperties jwtProperties,
            PlatformTransactionManager transactionManager) {
        this.usuarioPlataformaRepository = usuarioPlataformaRepository;
        this.tenantRepository = tenantRepository;
        this.usuarioRepository = usuarioRepository;
        this.tenantPlanoService = tenantPlanoService;
        this.motivoOcorrenciaService = motivoOcorrenciaService;
        this.passwordEncoder = passwordEncoder;
        this.jwtProperties = jwtProperties;
        this.transacao = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!SEGREDO_JWT_DEV.equals(jwtProperties.secret())) {
            log.error("milebag.dev.seed-usuarios=true, mas o segredo do JWT nao e o de desenvolvimento: contas padrao NAO criadas.");
            return;
        }
        log.warn("*** milebag.dev.seed-usuarios=true: contas de DESENVOLVIMENTO com senha conhecida ({}). Nunca use fora do desenvolvimento. ***", SENHA_DEV);

        transacao.executeWithoutResult(status -> {
            if (usuarioPlataformaRepository.findByLogin("admin").isEmpty()) {
                usuarioPlataformaRepository.save(new UsuarioPlataforma("admin", passwordEncoder.encode(SENHA_DEV)));
            }
        });

        UUID tenantId = transacao.execute(status -> tenantRepository.findByIdentificadorEmpresa(IDENTIFICADOR_EMPRESA_DEV).map(Tenant::getId).orElse(null));
        if (tenantId == null) {
            UUID novoId = transacao.execute(status -> tenantRepository.save(new Tenant(IDENTIFICADOR_EMPRESA_DEV, "Empresa Dev")).getId());
            // A associacao ao plano e tenant-scoped (RLS): precisa do tenant no contexto.
            criarNoTenant(novoId, () -> tenantPlanoService.associarInicial(novoId, PLANO_DEV));
            tenantId = novoId;
        }
        motivoOcorrenciaService.criarPadroesSeAusentes(tenantId);
        criarUsuarioSeAusente(tenantId, "empresa", Papeis.ADMINISTRADOR_TENANT, "Administrador Dev");
        criarUsuarioSeAusente(tenantId, "motorista", Papeis.MOTORISTA, "Motorista Dev");
    }

    private void criarUsuarioSeAusente(UUID tenantId, String login, String papel, String nome) {
        criarNoTenant(tenantId, () -> {
            if (usuarioRepository.findByEmailIgnoreCase(login).isEmpty()) {
                usuarioRepository.save(new Usuario(login, passwordEncoder.encode(SENHA_DEV), papel, nome));
            }
        });
    }

    private void criarNoTenant(UUID tenantId, Runnable acao) {
        TenantContext.set(tenantId);
        try {
            transacao.executeWithoutResult(status -> acao.run());
        } finally {
            TenantContext.clear();
        }
    }
}
