package com.marillia.milebag.business.basebdo.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.basebdo.model.TrocaBaseBdo;
import com.marillia.milebag.business.basebdo.repository.TrocaBaseBdoRepository;
import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.model.BdoMudouDeBase;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.configuracao.model.BaseAeroportuaria;
import com.marillia.milebag.business.configuracao.service.BaseAeroportuariaService;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCaronaService;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;
import com.marillia.milebag.core.util.TextoLivre;

/**
 * Troca da base de um BDO pela empresa (V52, AG) - ex.: o texto veio com o aeroporto errado. So antes de alguem
 * assumir, sempre com motivo; fica no historico da mala e os motoristas da base nova sao avisados.
 */
@Service
public class TrocaBaseBdoService {

    private static final Logger log = LoggerFactory.getLogger(TrocaBaseBdoService.class);
    private static final int TAMANHO_MAXIMO_MOTIVO = 500;

    private final TrocaBaseBdoRepository repository;
    private final BdoService bdoService;
    private final EntregaService entregaService;
    private final BagagemCaronaService bagagemCaronaService;
    private final BaseAeroportuariaService baseAeroportuariaService;
    private final ApplicationEventPublisher eventos;

    public TrocaBaseBdoService(TrocaBaseBdoRepository repository, BdoService bdoService, EntregaService entregaService,
            BagagemCaronaService bagagemCaronaService, BaseAeroportuariaService baseAeroportuariaService, ApplicationEventPublisher eventos) {
        this.repository = repository;
        this.bdoService = bdoService;
        this.entregaService = entregaService;
        this.bagagemCaronaService = bagagemCaronaService;
        this.baseAeroportuariaService = baseAeroportuariaService;
        this.eventos = eventos;
    }

    @Transactional
    public TrocaBaseBdo trocar(UUID bdoId, UUID baseId, String motivo, UUID administradorId) {
        String motivoLimpo = TextoLivre.opcional(motivo, TAMANHO_MAXIMO_MOTIVO, "motivo da troca de base");
        if (motivoLimpo == null) {
            throw new RegraNegocioException("Informe o motivo da troca de base.");
        }
        BaseAeroportuaria nova = baseAeroportuariaService.buscar(baseId)
                .orElseThrow(() -> new RegraNegocioException("Base nao encontrada."));
        // Travado: um autoatribuir do mesmo BDO ao mesmo tempo espera (ou e esperado), nunca passa com a base antiga.
        Bdo bdo = bdoService.travar(bdoId).orElseThrow(() -> new RecursoNaoEncontradoException("BDO nao encontrado."));
        if (bdo.getStatusConferencia() != com.marillia.milebag.business.bdo.model.StatusConferencia.CONFIRMADO) {
            throw new ConflitoException("So da para trocar a base de um BDO confirmado.");
        }
        // So antes de alguem assumir: a entrega (se ja existe) tem de estar sem motorista e disponivel.
        entregaService.buscarPorBdo(bdoId).ifPresent(entrega -> {
            boolean disponivel = entrega.getMotoristaResponsavel() == null
                    && (entrega.getStatus() == EntregaStatus.AGUARDANDO_ATRIBUICAO || entrega.getStatus() == EntregaStatus.REABERTA);
            if (!disponivel) {
                throw new ConflitoException("So da para trocar a base de um BDO que ninguem assumiu.");
            }
        });
        if (bagagemCaronaService.entregaPrincipalDe(bdoId).isPresent()) {
            throw new ConflitoException("Este BDO viaja como bagagem carona; troque a base da entrega principal.");
        }
        if (nova.getId().equals(bdo.getBaseId())) {
            throw new RegraNegocioException("O BDO ja e desta base.");
        }
        UUID anterior = bdo.getBaseId();
        bdoService.trocarBase(bdoId, nova.getId());
        TrocaBaseBdo troca = repository.save(new TrocaBaseBdo(bdoId, anterior, nova.getId(), motivoLimpo, administradorId, Instant.now()));
        log.info("BDO {} passou da base {} para {} por {}.", bdoId, anterior, nova.getCodigoIata(), administradorId);
        eventos.publishEvent(new BdoMudouDeBase(bdoId, bdo.getNumero()));
        return troca;
    }

    @Transactional(readOnly = true)
    public List<TrocaBaseBdo> listarPorBdo(UUID bdoId) {
        return repository.findByBdoIdOrderByDataHoraAsc(bdoId);
    }
}
