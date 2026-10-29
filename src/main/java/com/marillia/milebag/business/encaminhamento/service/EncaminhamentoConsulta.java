package com.marillia.milebag.business.encaminhamento.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontradaResponse;
import com.marillia.milebag.business.bagagemencontrada.service.EncaminhamentosDaBagagem;
import com.marillia.milebag.business.encaminhamento.model.EncaminhamentoEntrega;
import com.marillia.milebag.business.encaminhamento.model.EtapaEntrega;
import com.marillia.milebag.business.encaminhamento.repository.EncaminhamentoEntregaRepository;
import com.marillia.milebag.business.tenant.model.Tenant;
import com.marillia.milebag.business.tenant.repository.TenantRepository;
import com.marillia.milebag.business.tenant.service.UsuarioConsultaService;
import com.marillia.milebag.core.config.security.TenantContext;

/** O encaminhamento ativo na ficha e na consulta da companhia (HU37): filtrado pela companhia do token (a tabela nao tem RLS). */
@Component
public class EncaminhamentoConsulta implements EncaminhamentosDaBagagem {

    private final EncaminhamentoEntregaRepository repository;
    private final TenantRepository tenantRepository;
    private final UsuarioConsultaService usuarioConsultaService;

    public EncaminhamentoConsulta(
            EncaminhamentoEntregaRepository repository, TenantRepository tenantRepository, UsuarioConsultaService usuarioConsultaService) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
        this.usuarioConsultaService = usuarioConsultaService;
    }

    @Override
    public Optional<BagagemEncontradaResponse.Encaminhamento> ativo(UUID bagagemId) {
        return repository.findByBagagemIdAndCompanhiaIdAndEtapaNot(bagagemId, TenantContext.get(), EtapaEntrega.CANCELADO).map(this::resposta);
    }

    private BagagemEncontradaResponse.Encaminhamento resposta(EncaminhamentoEntrega e) {
        return new BagagemEncontradaResponse.Encaminhamento(
                tenantRepository.findById(e.getEmpresaId()).map(Tenant::getNome).orElse(null),
                e.getNumeroProcesso(), e.getPassageiroNome(), e.getEndereco(), e.getEtapa().name(),
                usuarioConsultaService.nomeParaExibicao(e.getEncaminhadoPor()).orElse(null),
                e.getEncaminhadoEm(), e.getAtualizadoEm());
    }
}
