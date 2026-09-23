package com.marillia.milebag.business.tenant.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.tenant.model.PlanoAssinatura;
import com.marillia.milebag.business.tenant.model.PlanoAssinaturaListItemResponse;
import com.marillia.milebag.business.tenant.repository.HistoricoPlanoTenantRepository;
import com.marillia.milebag.business.tenant.repository.PlanoAssinaturaRepository;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/** CRUD de planos de assinatura (HU16) - ver contracts/planos-api.md. */
@Service
public class PlanoAssinaturaService {

    private static final Logger log = LoggerFactory.getLogger(PlanoAssinaturaService.class);

    private final PlanoAssinaturaRepository planoAssinaturaRepository;
    private final HistoricoPlanoTenantRepository historicoPlanoTenantRepository;

    public PlanoAssinaturaService(
            PlanoAssinaturaRepository planoAssinaturaRepository, HistoricoPlanoTenantRepository historicoPlanoTenantRepository) {
        this.planoAssinaturaRepository = planoAssinaturaRepository;
        this.historicoPlanoTenantRepository = historicoPlanoTenantRepository;
    }

    @Transactional
    public PlanoAssinatura criar(String nome, int limiteMotoristasAtivos, int limiteVolumeMensalBdos) {
        if (nome == null || nome.isBlank()) {
            throw new RegraNegocioException("nome e obrigatorio.");
        }
        // Edge Case 3 do spec.md: limite zero ou negativo e recusado.
        if (limiteMotoristasAtivos <= 0) {
            throw new RegraNegocioException("limiteMotoristasAtivos deve ser maior que zero.");
        }
        if (limiteVolumeMensalBdos <= 0) {
            throw new RegraNegocioException("limiteVolumeMensalBdos deve ser maior que zero.");
        }
        PlanoAssinatura plano = planoAssinaturaRepository.save(new PlanoAssinatura(nome, limiteMotoristasAtivos, limiteVolumeMensalBdos));
        log.info("Plano de assinatura {} criado ({} motoristas, {} BDOs/mes)", plano.getId(), limiteMotoristasAtivos, limiteVolumeMensalBdos);
        return plano;
    }

    @Transactional(readOnly = true)
    public List<PlanoAssinaturaListItemResponse> listar() {
        return planoAssinaturaRepository.findAll().stream()
                .map(plano -> PlanoAssinaturaListItemResponse.de(
                        plano, historicoPlanoTenantRepository.countByPlanoIdAndVigenteAteIsNull(plano.getId())))
                .toList();
    }

    /** Edge Case 3 do Acceptance Scenario 3: recusa enquanto houver tenant vigente associado (FR-005). */
    @Transactional
    public void remover(UUID id) {
        PlanoAssinatura plano = buscarOuFalhar(id);
        long tenantsAssociados = historicoPlanoTenantRepository.countByPlanoIdAndVigenteAteIsNull(id);
        if (tenantsAssociados > 0) {
            throw new ConflitoException(
                    "Existem " + tenantsAssociados + " tenant(s) associado(s) a este plano; migre-os para outro plano antes de remover.");
        }
        planoAssinaturaRepository.delete(plano);
        log.info("Plano de assinatura {} removido", id);
    }

    /** Ponto de leitura publico para outros dominios/services deste mesmo dominio (ex.: TenantPlanoService). */
    @Transactional(readOnly = true)
    public PlanoAssinatura buscarOuFalhar(UUID id) {
        return planoAssinaturaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Plano de assinatura nao encontrado."));
    }
}
