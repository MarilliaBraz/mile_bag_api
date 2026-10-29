package com.marillia.milebag.business.encaminhamento.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.encaminhamento.model.ParceriaEntrega;
import com.marillia.milebag.business.encaminhamento.repository.ParceriaEntregaRepository;
import com.marillia.milebag.business.tenant.model.Tenant;
import com.marillia.milebag.business.tenant.model.TipoTenant;
import com.marillia.milebag.business.tenant.repository.TenantRepository;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/** Empresas de entrega parceiras de cada companhia aerea (HU37, RD23): a plataforma define, a companhia consulta. */
@Service
public class ParceriaEntregaService {

    private static final Logger log = LoggerFactory.getLogger(ParceriaEntregaService.class);

    private final ParceriaEntregaRepository repository;
    private final TenantRepository tenantRepository;

    public ParceriaEntregaService(ParceriaEntregaRepository repository, TenantRepository tenantRepository) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
    }

    public record EmpresaParceira(UUID id, String nome, String identificador, boolean parceira) {
    }

    public record EmpresaEntrega(UUID id, String nome) {
    }

    /** Plataforma: todas as empresas de entrega, por nome, marcando as parceiras da companhia. */
    @Transactional(readOnly = true)
    public List<EmpresaParceira> listarParaCompanhia(UUID companhiaId) {
        companhiaOuFalhar(companhiaId);
        Set<UUID> parceiras = idsParceiras(companhiaId);
        return tenantRepository.findByTipoOrderByNomeAsc(TipoTenant.EMPRESA_ENTREGA).stream()
                .map(e -> new EmpresaParceira(e.getId(), e.getNome(), e.getIdentificadorEmpresa(), parceiras.contains(e.getId())))
                .toList();
    }

    /** Plataforma: troca o conjunto inteiro. Retirar uma parceira nao afeta o que ja foi encaminhado (RD23). */
    @Transactional
    public List<EmpresaParceira> definir(UUID companhiaId, Collection<UUID> empresas) {
        Tenant companhia = companhiaOuFalhar(companhiaId);
        Set<UUID> novas = new LinkedHashSet<>(empresas == null ? List.of() : empresas);
        novas.remove(null);
        for (UUID empresaId : novas) {
            boolean deEntrega = tenantRepository.findById(empresaId).map(t -> t.getTipo() == TipoTenant.EMPRESA_ENTREGA).orElse(false);
            if (!deEntrega) {
                throw new RegraNegocioException("Empresa de entrega invalida: " + empresaId + ".");
            }
        }
        repository.deleteByCompanhiaId(companhiaId);
        repository.flush();
        repository.saveAll(novas.stream().map(empresaId -> new ParceriaEntrega(companhiaId, empresaId)).toList());
        log.info("Companhia {} com {} empresa(s) de entrega parceira(s).", companhia.getCodigoIata(), novas.size());
        return listarParaCompanhia(companhiaId);
    }

    /** Companhia: so as proprias parceiras, para escolher no encaminhamento. */
    @Transactional(readOnly = true)
    public List<EmpresaEntrega> parceirasDa(UUID companhiaId) {
        return tenantRepository.findAllById(idsParceiras(companhiaId)).stream()
                .map(e -> new EmpresaEntrega(e.getId(), e.getNome()))
                .sorted((a, b) -> a.nome().compareToIgnoreCase(b.nome()))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean saoParceiras(UUID companhiaId, UUID empresaId) {
        return empresaId != null && repository.existsByCompanhiaIdAndEmpresaId(companhiaId, empresaId);
    }

    private Set<UUID> idsParceiras(UUID companhiaId) {
        return repository.findByCompanhiaId(companhiaId).stream().map(ParceriaEntrega::getEmpresaId).collect(Collectors.toSet());
    }

    private Tenant companhiaOuFalhar(UUID companhiaId) {
        return tenantRepository.findById(companhiaId)
                .filter(t -> t.getTipo() == TipoTenant.COMPANHIA_AEREA)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Companhia aerea nao encontrada."));
    }
}
