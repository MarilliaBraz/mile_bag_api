package com.marillia.milebag.business.encaminhamento.service;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.encaminhamento.model.EncaminhamentoEntrega;
import com.marillia.milebag.business.encaminhamento.model.EntregaParceiroResponse;
import com.marillia.milebag.business.encaminhamento.model.EtapaEntrega;
import com.marillia.milebag.business.encaminhamento.repository.EncaminhamentoEntregaRepository;
import com.marillia.milebag.business.encaminhamento.repository.EntregaParceiroRepository;
import com.marillia.milebag.business.fotoentrega.service.FotoEntregaService;
import com.marillia.milebag.business.rastreabilidade.service.RastreabilidadeEntregaService;
import com.marillia.milebag.business.tenant.model.Tenant;
import com.marillia.milebag.business.tenant.repository.TenantRepository;
import com.marillia.milebag.business.viagem.service.AcompanhamentoViagem;
import com.marillia.milebag.core.base.LinhaNativa;
import com.marillia.milebag.core.config.security.TenantContext;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;

/**
 * Painel da companhia, fase 2: o que a companhia ve da entrega do parceiro de uma bagagem ENCAMINHADA por ela. A
 * leitura acontece com o tenant da empresa de entrega (RLS dela), so para o BDO desse encaminhamento; nunca expoe o
 * motorista (RD23).
 */
@Service
public class EntregaParceiroService {

    private final EncaminhamentoEntregaRepository encaminhamentoRepository;
    private final EntregaParceiroRepository repository;
    private final TenantRepository tenantRepository;
    private final RastreabilidadeEntregaService rastreabilidade;
    private final AcompanhamentoViagem acompanhamento;

    public EntregaParceiroService(EncaminhamentoEntregaRepository encaminhamentoRepository, EntregaParceiroRepository repository,
            TenantRepository tenantRepository, RastreabilidadeEntregaService rastreabilidade, AcompanhamentoViagem acompanhamento) {
        this.encaminhamentoRepository = encaminhamentoRepository;
        this.repository = repository;
        this.tenantRepository = tenantRepository;
        this.rastreabilidade = rastreabilidade;
        this.acompanhamento = acompanhamento;
    }

    @Transactional(readOnly = true)
    public EntregaParceiroResponse consultar(UUID bagagemId) {
        EncaminhamentoEntrega encaminhamento = encaminhamentoAtivo(bagagemId);
        String empresa = tenantRepository.findById(encaminhamento.getEmpresaId()).map(Tenant::getNome).orElse(null);
        if (encaminhamento.getBdoId() == null) {
            return resposta(encaminhamento, empresa, null, null, null, null, null, List.of());
        }
        return naEmpresa(encaminhamento.getEmpresaId(), () -> {
            List<Object[]> contato = repository.contato(encaminhamento.getBdoId());
            String telefone = contato.isEmpty() ? null : new LinhaNativa(contato.get(0)).texto(0);
            String email = contato.isEmpty() ? null : new LinhaNativa(contato.get(0)).texto(1);
            UUID entregaId = repository.entregaDoBdo(encaminhamento.getBdoId()).stream().findFirst().orElse(null);
            if (entregaId == null) {
                return resposta(encaminhamento, empresa, null, null, telefone, email, null, List.of());
            }
            EntregaParceiroResponse.Comprovante comprovante = repository.comprovante(entregaId).stream().map(LinhaNativa::new)
                    .map(l -> new EntregaParceiroResponse.Comprovante(l.instante(0), l.texto(1), l.texto(2), l.decimal(3), l.decimal(4)))
                    .findFirst().orElse(null);
            EntregaParceiroResponse.Tentativa tentativa = repository.ultimaOcorrencia(entregaId).stream().map(LinhaNativa::new)
                    .map(l -> new EntregaParceiroResponse.Tentativa(l.instante(0), l.texto(1), l.texto(2)))
                    .findFirst().orElse(null);
            String link = repository.linkAcompanhamento(entregaId).stream().findFirst().map(acompanhamento::url).orElse(null);
            List<EntregaParceiroResponse.Foto> fotos = rastreabilidade.fotos(entregaId).stream()
                    .filter(foto -> !foto.substituida())
                    .map(foto -> new EntregaParceiroResponse.Foto(foto.id(), foto.tipo(), foto.dataHora()))
                    .toList();
            return resposta(encaminhamento, empresa, comprovante, tentativa, telefone, email, link, fotos);
        });
    }

    /** O arquivo de uma foto da entrega desta bagagem (404 se for de outra entrega). */
    @Transactional(readOnly = true)
    public FotoEntregaService.Arquivo foto(UUID bagagemId, UUID fotoId) {
        EncaminhamentoEntrega encaminhamento = encaminhamentoAtivo(bagagemId);
        if (encaminhamento.getBdoId() == null) {
            throw new RecursoNaoEncontradoException("Foto nao encontrada.");
        }
        return naEmpresa(encaminhamento.getEmpresaId(), () -> {
            UUID entregaId = repository.entregaDoBdo(encaminhamento.getBdoId()).stream().findFirst()
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Foto nao encontrada."));
            return rastreabilidade.foto(entregaId, fotoId);
        });
    }

    private EncaminhamentoEntrega encaminhamentoAtivo(UUID bagagemId) {
        return encaminhamentoRepository.findByBagagemIdAndCompanhiaIdAndEtapaNot(bagagemId, TenantContext.get(), EtapaEntrega.CANCELADO)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Esta bagagem nao foi encaminhada para entrega."));
    }

    private static EntregaParceiroResponse resposta(EncaminhamentoEntrega e, String empresa, EntregaParceiroResponse.Comprovante comprovante,
            EntregaParceiroResponse.Tentativa tentativa, String telefone, String email, String link, List<EntregaParceiroResponse.Foto> fotos) {
        return new EntregaParceiroResponse(e.getEtapa(), empresa, e.getEncaminhadoEm(), e.getColetadoEm(), e.getEntregueEm(),
                e.getNaoEntregueEm(), comprovante, tentativa, telefone, email, link, fotos);
    }

    /** Roda a leitura com o tenant da empresa e volta ao da companhia, mesmo se falhar. */
    private static <T> T naEmpresa(UUID empresaId, Supplier<T> acao) {
        UUID companhiaId = TenantContext.get();
        TenantContext.set(empresaId);
        try {
            return acao.get();
        } finally {
            TenantContext.set(companhiaId);
        }
    }
}
