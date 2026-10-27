package com.marillia.milebag.business.tenant.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.configuracao.service.MotivoOcorrenciaService;
import com.marillia.milebag.business.tenant.model.Tenant;
import com.marillia.milebag.business.tenant.repository.PlanoAssinaturaRepository;
import com.marillia.milebag.business.tenant.repository.TenantRepository;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/** Cadastro de empresas assinantes (HU14) - ver contracts/tenants-api.md. */
@Service
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    private final TenantRepository tenantRepository;
    private final PlanoAssinaturaRepository planoAssinaturaRepository;
    private final TenantPlanoService tenantPlanoService;
    private final ConviteAdministradorService conviteAdministradorService;
    private final MotivoOcorrenciaService motivoOcorrenciaService;

    public TenantService(
            TenantRepository tenantRepository,
            PlanoAssinaturaRepository planoAssinaturaRepository,
            TenantPlanoService tenantPlanoService,
            ConviteAdministradorService conviteAdministradorService,
            MotivoOcorrenciaService motivoOcorrenciaService) {
        this.tenantRepository = tenantRepository;
        this.planoAssinaturaRepository = planoAssinaturaRepository;
        this.tenantPlanoService = tenantPlanoService;
        this.conviteAdministradorService = conviteAdministradorService;
        this.motivoOcorrenciaService = motivoOcorrenciaService;
    }

    /**
     * HU15 (FR-001) dispara o convite do primeiro administrador dentro
     * desta MESMA transacao - um e-mail invalido reverte tambem a criacao
     * do tenant (ver javadoc de {@code ConviteAdministradorService.criarEEnviar}),
     * o que na pratica estende o "tudo ou nada" de FR-006 (HU14) para
     * cobrir tambem o convite, nao so o registro do tenant em si.
     */
    @Transactional
    public Tenant cadastrar(String identificadorEmpresa, String nome, UUID planoId, String emailAdministrador) {
        if (identificadorEmpresa == null || identificadorEmpresa.isBlank()) {
            throw new RegraNegocioException("identificadorEmpresa e obrigatorio.");
        }
        if (nome == null || nome.isBlank()) {
            throw new RegraNegocioException("nome e obrigatorio.");
        }
        if (planoId == null || !planoAssinaturaRepository.existsById(planoId)) {
            // 400 (RegraNegocioException), nao 404: contracts/tenants-api.md exige
            // "planoId ausente ou invalido" como Response 400 (FR-002) - diferente
            // de PlanoAssinaturaService.buscarOuFalhar (404), usado noutros fluxos.
            throw new RegraNegocioException("planoId invalido ou inexistente.");
        }

        if (tenantRepository.findByIdentificadorEmpresa(identificadorEmpresa).isPresent()) {
            // Mensagem nao expoe nome/id do tenant conflitante (T019 desta HU).
            throw new ConflitoException("Ja existe uma empresa cadastrada com este identificador.");
        }

        Tenant tenant = tenantRepository.save(new Tenant(identificadorEmpresa, nome));
        // HU16 (T016): a associacao ao plano vive exclusivamente em
        // HistoricoPlanoTenant, nunca num campo direto em Tenant.
        tenantPlanoService.associarInicial(tenant.getId(), planoId);
        conviteAdministradorService.criarEEnviar(tenant.getId(), emailAdministrador);
        // Por ultimo: define o tenant da conexao (RLS) para o resto desta transacao.
        motivoOcorrenciaService.criarPadroesSeAusentes(tenant.getId());
        log.info("Tenant {} cadastrado (plano {})", tenant.getId(), planoId);
        return tenant;
    }

    @Transactional(readOnly = true)
    public Tenant buscar(UUID id) {
        return tenantRepository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Tenant nao encontrado."));
    }

    /**
     * {@code tenant} nao tem Row Level Security (e o proprio registro de
     * tenants, lido fora do contexto de qualquer um deles) - ver
     * {@code TenantEmailBdo} (HU02) para o mesmo raciocinio. Usado por
     * processos em segundo plano sem tenant conhecido de antemao (ex.:
     * {@code EmailInboundListener}/{@code TelegramWebhookController}, HU18),
     * que tentam cada tenant ate encontrar o dono do recurso resolvido pelo
     * canal externo (endereco de e-mail, chat do Telegram).
     */
    @Transactional(readOnly = true)
    public List<UUID> listarTodosOsIds() {
        return tenantRepository.findAll().stream().map(Tenant::getId).toList();
    }
}
