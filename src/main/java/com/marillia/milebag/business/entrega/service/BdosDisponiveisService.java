package com.marillia.milebag.business.entrega.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.bdo.repository.BdoRepository;
import com.marillia.milebag.business.entrega.mapper.PainelEntregasMapper;
import com.marillia.milebag.business.entrega.model.BdosDisponiveisResponse;
import com.marillia.milebag.business.entrega.repository.EntregaRepository;

/**
 * Lista de BDOs confirmados sem motorista responsavel (HU19, FR-007) - a
 * unica via de descoberta, na interface, de um BDO recebido automaticamente
 * por e-mail (RF21/HU02). Injeta {@code BdoRepository} direto (nao
 * BdoService): esta consulta e sobre BDO do proprio dominio de business/bdo,
 * nao um acesso cross-domain (CONVENTIONS.md 2.1) - diferente de
 * PainelEntregasService, que consulta Bdo a partir de business/entrega.
 */
@Service
public class BdosDisponiveisService {

    private static final Logger log = LoggerFactory.getLogger(BdosDisponiveisService.class);

    private final BdoRepository bdoRepository;
    private final EntregaRepository entregaRepository;
    private final PainelEntregasMapper mapper;

    public BdosDisponiveisService(BdoRepository bdoRepository, EntregaRepository entregaRepository, PainelEntregasMapper mapper) {
        this.bdoRepository = bdoRepository;
        this.entregaRepository = entregaRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public BdosDisponiveisResponse consultarDisponiveis() {
        // A entrega reaberta (RN05) traz a janela de reagendamento (HU29); a consulta so devolve BDO sem entrega ou com entrega reaberta.
        var bdos = bdoRepository.findDisponiveisParaAutoatribuicao().stream()
                .map(bdo -> mapper.toItemDisponivel(bdo, entregaRepository.findByBdoId(bdo.getId()).orElse(null)))
                .toList();
        log.info("Consulta de BDOs disponiveis: {} encontrado(s)", bdos.size());
        return new BdosDisponiveisResponse(bdos);
    }
}
