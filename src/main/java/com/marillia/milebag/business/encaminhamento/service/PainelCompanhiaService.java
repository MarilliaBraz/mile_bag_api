package com.marillia.milebag.business.encaminhamento.service;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.bagagemencontrada.model.SituacaoBagagemEncontrada;
import com.marillia.milebag.business.bagagemencontrada.repository.BagagemEncontradaRepository;
import com.marillia.milebag.business.encaminhamento.model.EtapaEntrega;
import com.marillia.milebag.business.encaminhamento.model.PainelCompanhiaResponse;
import com.marillia.milebag.business.encaminhamento.repository.EncaminhamentoEntregaRepository;
import com.marillia.milebag.core.config.security.AuthenticatedUser;
import com.marillia.milebag.core.config.security.Papeis;

/**
 * Painel de atividades da companhia: rascunhos por concluir (o administrador ve os da companhia, o funcionario so os
 * seus), bagagens registradas ainda nao encaminhadas e o andamento das encaminhadas.
 */
@Service
public class PainelCompanhiaService {

    static final Set<EtapaEntrega> EM_ANDAMENTO =
            EnumSet.of(EtapaEntrega.AGUARDANDO_MOTORISTA, EtapaEntrega.MOTORISTA_DESIGNADO, EtapaEntrega.A_CAMINHO);

    private final BagagemEncontradaRepository bagagemRepository;
    private final EncaminhamentoEntregaRepository encaminhamentoRepository;

    public PainelCompanhiaService(BagagemEncontradaRepository bagagemRepository, EncaminhamentoEntregaRepository encaminhamentoRepository) {
        this.bagagemRepository = bagagemRepository;
        this.encaminhamentoRepository = encaminhamentoRepository;
    }

    @Transactional(readOnly = true)
    public PainelCompanhiaResponse painel(AuthenticatedUser usuario) {
        UUID companhiaId = usuario.tenantId();
        long rascunhos = Papeis.ADMINISTRADOR_COMPANHIA.equals(usuario.papel())
                ? bagagemRepository.countBySituacao(SituacaoBagagemEncontrada.RASCUNHO)
                : bagagemRepository.countByCriadoPorAndSituacao(usuario.userId(), SituacaoBagagemEncontrada.RASCUNHO);
        return new PainelCompanhiaResponse(
                rascunhos,
                encaminhamentoRepository.contarRegistradasSemEncaminhamento(companhiaId),
                encaminhamentoRepository.countByCompanhiaIdAndEtapaIn(companhiaId, EM_ANDAMENTO),
                encaminhamentoRepository.countByCompanhiaIdAndEtapaIn(companhiaId, EnumSet.of(EtapaEntrega.NAO_ENTREGUE)));
    }
}
