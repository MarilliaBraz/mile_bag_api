package com.marillia.milebag.business.tenant.service;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.tenant.model.HistoricoPlanoTenant;
import com.marillia.milebag.business.tenant.model.PlanoAssinatura;
import com.marillia.milebag.business.tenant.model.PlanoVigenteConsulta;
import com.marillia.milebag.business.tenant.model.TrocaPlanoResultado;
import com.marillia.milebag.business.tenant.model.UsoAtual;
import com.marillia.milebag.business.tenant.repository.HistoricoPlanoTenantRepository;
import com.marillia.milebag.business.tenant.repository.TenantRepository;
import com.marillia.milebag.business.tenant.repository.UsuarioRepository;
import com.marillia.milebag.core.config.security.Papeis;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;

/**
 * Associacao/troca de plano de um tenant (HU16) - {@link
 * HistoricoPlanoTenant} e a UNICA fonte de verdade do plano vigente
 * (tasks.md T016; {@code Tenant} nao guarda {@code planoId}).
 */
@Service
public class TenantPlanoService {

    private static final Logger log = LoggerFactory.getLogger(TenantPlanoService.class);

    private final HistoricoPlanoTenantRepository historicoPlanoTenantRepository;
    private final PlanoAssinaturaService planoAssinaturaService;
    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final BdoService bdoService;

    public TenantPlanoService(
            HistoricoPlanoTenantRepository historicoPlanoTenantRepository,
            PlanoAssinaturaService planoAssinaturaService,
            TenantRepository tenantRepository,
            UsuarioRepository usuarioRepository,
            BdoService bdoService) {
        this.historicoPlanoTenantRepository = historicoPlanoTenantRepository;
        this.planoAssinaturaService = planoAssinaturaService;
        this.tenantRepository = tenantRepository;
        this.usuarioRepository = usuarioRepository;
        this.bdoService = bdoService;
    }

    /**
     * Primeira associacao de um tenant recem-cadastrado (chamado por {@code
     * TenantService.cadastrar}, HU14) - sem checagem de excedente: um
     * tenant novo sempre comeca com uso zero.
     */
    @Transactional
    public void associarInicial(UUID tenantId, UUID planoId) {
        historicoPlanoTenantRepository.save(new HistoricoPlanoTenant(tenantId, planoId, Instant.now(), false));
    }

    /** Troca o plano vigente de um tenant, em tempo real, sem reimplantacao (FR-002/FR-003/FR-004). */
    @Transactional
    public TrocaPlanoResultado trocarPlano(UUID tenantId, UUID novoPlanoId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new RecursoNaoEncontradoException("Tenant nao encontrado.");
        }
        PlanoAssinatura novoPlano = planoAssinaturaService.buscarOuFalhar(novoPlanoId);

        Instant agora = Instant.now();
        historicoPlanoTenantRepository.findByTenantIdAndVigenteAteIsNull(tenantId).ifPresent(vigente -> {
            vigente.encerrar(agora);
            // saveAndFlush (nao save): o Hibernate ordena INSERTs antes de
            // UPDATEs no mesmo flush, entao sem isto o registro novo entraria
            // enquanto o antigo ainda tem vigente_ate nulo e violaria o
            // indice unico parcial uk_historico_plano_tenant_vigente (no
            // maximo um plano vigente por tenant).
            historicoPlanoTenantRepository.saveAndFlush(vigente);
        });

        // FR-006/Edge Case 1: a troca e sempre permitida, mesmo com excedente
        // (research.md) - so o indicador e sinalizado, nunca bloqueia.
        boolean usoExcedeu = usoExcedeLimite(tenantId, novoPlano);
        historicoPlanoTenantRepository.save(new HistoricoPlanoTenant(tenantId, novoPlano.getId(), agora, usoExcedeu));

        log.info("Tenant {} trocado para o plano {} (uso excedeu limite: {})", tenantId, novoPlano.getId(), usoExcedeu);
        return new TrocaPlanoResultado(novoPlano, usoExcedeu);
    }

    /** Consulta o plano vigente e o uso atual de um tenant (HU16; tambem consumido por FR-017 do spec-mae). */
    @Transactional(readOnly = true)
    public PlanoVigenteConsulta consultarVigente(UUID tenantId) {
        HistoricoPlanoTenant vigente = historicoPlanoTenantRepository.findByTenantIdAndVigenteAteIsNull(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant " + tenantId + " nao tem plano vigente."));
        PlanoAssinatura plano = planoAssinaturaService.buscarOuFalhar(vigente.getPlanoId());
        UsoAtual uso = calcularUso(tenantId);
        boolean dentroDoLimite = uso.motoristasAtivos() <= plano.getLimiteMotoristasAtivos()
                && uso.bdosNoMesCorrente() <= plano.getLimiteVolumeMensalBdos();
        return new PlanoVigenteConsulta(plano, uso, dentroDoLimite);
    }

    /** {@code planoId} vigente de um tenant - usado por {@code TenantController} para montar {@code TenantResponse}. */
    @Transactional(readOnly = true)
    public UUID planoIdVigente(UUID tenantId) {
        return historicoPlanoTenantRepository.findByTenantIdAndVigenteAteIsNull(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant " + tenantId + " nao tem plano vigente."))
                .getPlanoId();
    }

    private boolean usoExcedeLimite(UUID tenantId, PlanoAssinatura plano) {
        UsoAtual uso = calcularUso(tenantId);
        return uso.motoristasAtivos() > plano.getLimiteMotoristasAtivos() || uso.bdosNoMesCorrente() > plano.getLimiteVolumeMensalBdos();
    }

    /**
     * Bridging manual de {@link TenantContext} (mesmo padrao ja usado em
     * {@code ConviteAdministradorService.possuiAdministradorAtivo}): esta
     * consulta roda no contexto do administrador da PLATAFORMA (sem claim
     * de tenant no JWT), mas precisa ler dado de um tenant especifico via
     * os Services de outros dominios (que dependem de RLS + TenantContext
     * ambiente, nao de um parametro tenantId explicito).
     */
    private UsoAtual calcularUso(UUID tenantId) {
        TenantContext.set(tenantId);
        try {
            return new UsoAtual(usuarioRepository.countByPapelAndAtivoTrue(Papeis.MOTORISTA), bdoService.contarNoMesCorrente());
        } finally {
            TenantContext.clear();
        }
    }
}
