package com.marillia.milebag.business.tarifa.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.tarifa.model.AtualizarTarifaRequest;
import com.marillia.milebag.business.tarifa.model.CriarTarifaRequest;
import com.marillia.milebag.business.tarifa.model.EscopoTarifa;
import com.marillia.milebag.business.tarifa.model.TabelaTarifa;
import com.marillia.milebag.business.tarifa.repository.TabelaTarifaRepository;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * CRUD de tarifas e resolucao de especificidade (US1 desta HU) - ver
 * contracts/tarifas-api.md.
 */
@Service
public class TabelaTarifaService {

    private static final Logger log = LoggerFactory.getLogger(TabelaTarifaService.class);

    private final TabelaTarifaRepository tabelaTarifaRepository;

    public TabelaTarifaService(TabelaTarifaRepository tabelaTarifaRepository) {
        this.tabelaTarifaRepository = tabelaTarifaRepository;
    }

    @Transactional
    public TabelaTarifa criar(CriarTarifaRequest request) {
        validarCampos(request);
        verificarConflito(request.escopo(), request.motoristaId(), request.regiao());

        TabelaTarifa tarifa = tabelaTarifaRepository.save(new TabelaTarifa(
                request.escopo(), request.motoristaId(), request.regiao(), request.valorFixo(), request.valorPorKm(), request.vigenteDesde()));

        log.info("Tarifa {} criada (escopo {})", tarifa.getId(), tarifa.getEscopo());
        return tarifa;
    }

    @Transactional(readOnly = true)
    public List<TabelaTarifa> listar(EscopoTarifa escopo, UUID motoristaId, String regiao) {
        return tabelaTarifaRepository.findAll().stream()
                .filter(tarifa -> escopo == null || tarifa.getEscopo() == escopo)
                .filter(tarifa -> motoristaId == null || motoristaId.equals(tarifa.getMotoristaId()))
                .filter(tarifa -> regiao == null || regiao.equals(tarifa.getRegiao()))
                .toList();
    }

    @Transactional(readOnly = true)
    public TabelaTarifa buscar(UUID id) {
        return tabelaTarifaRepository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Tarifa nao encontrada."));
    }

    @Transactional
    public TabelaTarifa atualizar(UUID id, AtualizarTarifaRequest request) {
        TabelaTarifa tarifa = buscar(id);
        validarValores(request.valorFixo(), request.valorPorKm());
        if (request.vigenteDesde() == null) {
            throw new RegraNegocioException("vigenteDesde e obrigatorio.");
        }

        tarifa.atualizarValores(request.valorFixo(), request.valorPorKm(), request.vigenteDesde());
        TabelaTarifa salva = tabelaTarifaRepository.save(tarifa);
        log.info("Tarifa {} atualizada", salva.getId());
        return salva;
    }

    @Transactional
    public void remover(UUID id) {
        TabelaTarifa tarifa = buscar(id);
        // contracts/tarifas-api.md: 409 quando referenciada por um
        // fechamento ja emitido - HU12 ainda nao existe, entao nao ha nada
        // a checar por enquanto; remover a tempo de HU12 nascer com essa
        // validacao.
        tabelaTarifaRepository.delete(tarifa);
        log.info("Tarifa {} removida", id);
    }

    /**
     * FR-004: motorista > regiao > padrao. Ainda sem chamador nesta HU (o
     * uso real e no fechamento, HU12, que ainda nao existe) - a propria
     * spec desta HU pede que a resolucao seja testavel independente disso
     * (Independent Test do spec.md).
     */
    @Transactional(readOnly = true)
    public Optional<TabelaTarifa> resolverTarifaAplicavel(UUID motoristaId, String regiao) {
        if (motoristaId != null) {
            Optional<TabelaTarifa> porMotorista = tabelaTarifaRepository.findByEscopoAndMotoristaId(EscopoTarifa.MOTORISTA, motoristaId);
            if (porMotorista.isPresent()) {
                return porMotorista;
            }
        }
        if (regiao != null) {
            Optional<TabelaTarifa> porRegiao = tabelaTarifaRepository.findByEscopoAndRegiao(EscopoTarifa.REGIAO, regiao);
            if (porRegiao.isPresent()) {
                return porRegiao;
            }
        }
        return tabelaTarifaRepository.findByEscopo(EscopoTarifa.PADRAO);
    }

    private void validarCampos(CriarTarifaRequest request) {
        if (request.escopo() == null) {
            throw new RegraNegocioException("escopo e obrigatorio.");
        }
        if (request.vigenteDesde() == null) {
            throw new RegraNegocioException("vigenteDesde e obrigatorio.");
        }
        validarValores(request.valorFixo(), request.valorPorKm());

        if (request.escopo() == EscopoTarifa.MOTORISTA && request.motoristaId() == null) {
            throw new RegraNegocioException("motoristaId e obrigatorio para escopo MOTORISTA.");
        }
        if (request.escopo() == EscopoTarifa.REGIAO && (request.regiao() == null || request.regiao().isBlank())) {
            throw new RegraNegocioException("regiao e obrigatoria para escopo REGIAO.");
        }
    }

    private void validarValores(BigDecimal valorFixo, BigDecimal valorPorKm) {
        if (valorFixo == null && valorPorKm == null) {
            throw new RegraNegocioException("Informe ao menos valorFixo ou valorPorKm.");
        }
    }

    private void verificarConflito(EscopoTarifa escopo, UUID motoristaId, String regiao) {
        boolean existe = switch (escopo) {
            case PADRAO -> tabelaTarifaRepository.findByEscopo(EscopoTarifa.PADRAO).isPresent();
            case MOTORISTA -> tabelaTarifaRepository.findByEscopoAndMotoristaId(EscopoTarifa.MOTORISTA, motoristaId).isPresent();
            case REGIAO -> tabelaTarifaRepository.findByEscopoAndRegiao(EscopoTarifa.REGIAO, regiao).isPresent();
        };
        if (existe) {
            throw new ConflitoException("Ja existe uma tarifa vigente para este escopo/motorista/regiao.");
        }
    }
}
