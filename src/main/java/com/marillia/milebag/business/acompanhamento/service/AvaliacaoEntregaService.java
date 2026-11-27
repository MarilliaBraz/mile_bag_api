package com.marillia.milebag.business.acompanhamento.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.acompanhamento.model.AvaliacaoEntrega;
import com.marillia.milebag.business.acompanhamento.model.AvaliarEntregaRequest;
import com.marillia.milebag.business.acompanhamento.repository.AvaliacaoEntregaRepository;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.viagem.model.LinkAcompanhamento;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * V65: pesquisa de satisfacao do passageiro, respondida na pagina publica "Acompanhe sua bagagem" depois da entrega.
 * Uma por entrega; so com a entrega concluida. Roda com o tenant do link (definido pelo controller).
 */
@Service
public class AvaliacaoEntregaService {

    static final int COMENTARIO_MAX = 500;

    private final AvaliacaoEntregaRepository repository;
    private final EntregaService entregaService;

    public AvaliacaoEntregaService(AvaliacaoEntregaRepository repository, EntregaService entregaService) {
        this.repository = repository;
        this.entregaService = entregaService;
    }

    @Transactional(readOnly = true)
    public boolean avaliada(UUID entregaId) {
        return repository.existsByEntregaId(entregaId);
    }

    @Transactional
    public void registrar(LinkAcompanhamento link, AvaliarEntregaRequest pedido) {
        Entrega entrega = entregaService.buscarPorId(link.getEntregaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Link de acompanhamento invalido."));
        if (entrega.getStatus() != EntregaStatus.ENCERRADA_COM_SUCESSO) {
            throw new ConflitoException("A pesquisa abre depois que a bagagem for entregue.");
        }
        if (repository.existsByEntregaId(entrega.getId())) {
            throw new ConflitoException("A avaliacao desta entrega ja foi enviada. Obrigado!");
        }
        if (pedido == null || pedido.nota() == null || pedido.nota() < 0 || pedido.nota() > 10) {
            throw new RegraNegocioException("Escolha uma nota de 0 a 10.");
        }
        if (pedido.cordialidade() != null && (pedido.cordialidade() < 1 || pedido.cordialidade() > 5)) {
            throw new RegraNegocioException("A cordialidade vai de 1 a 5.");
        }
        String comentario = pedido.comentario() == null || pedido.comentario().isBlank() ? null : pedido.comentario().strip();
        if (comentario != null && comentario.length() > COMENTARIO_MAX) {
            throw new RegraNegocioException("O comentario pode ter ate " + COMENTARIO_MAX + " caracteres.");
        }
        repository.save(new AvaliacaoEntrega(entrega.getId(), entrega.getBdoId(), link.getTenantId(), pedido.nota().shortValue(),
                pedido.cordialidade() == null ? null : pedido.cordialidade().shortValue(), pedido.malaEmBomEstado(), comentario,
                Instant.now()));
    }
}
