package com.marillia.milebag.business.tenant.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.tenant.model.PlanoAssinatura;
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
    private final ConviteAdministradorService conviteAdministradorService;

    public TenantService(
            TenantRepository tenantRepository,
            PlanoAssinaturaRepository planoAssinaturaRepository,
            ConviteAdministradorService conviteAdministradorService) {
        this.tenantRepository = tenantRepository;
        this.planoAssinaturaRepository = planoAssinaturaRepository;
        this.conviteAdministradorService = conviteAdministradorService;
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
        if (planoId == null) {
            throw new RegraNegocioException("planoId e obrigatorio.");
        }
        PlanoAssinatura plano = planoAssinaturaRepository.findById(planoId)
                .filter(PlanoAssinatura::isAtivo)
                .orElseThrow(() -> new RegraNegocioException("planoId invalido ou inexistente."));

        if (tenantRepository.findByIdentificadorEmpresa(identificadorEmpresa).isPresent()) {
            // Mensagem nao expoe nome/id do tenant conflitante (T019 desta HU).
            throw new ConflitoException("Ja existe uma empresa cadastrada com este identificador.");
        }

        Tenant tenant = tenantRepository.save(new Tenant(identificadorEmpresa, nome, plano.getId()));
        conviteAdministradorService.criarEEnviar(tenant.getId(), emailAdministrador);
        log.info("Tenant {} cadastrado (plano {})", tenant.getId(), plano.getId());
        return tenant;
    }

    @Transactional(readOnly = true)
    public Tenant buscar(UUID id) {
        return tenantRepository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Tenant nao encontrado."));
    }
}
