package com.marillia.milebag.business.configuracao.service;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.configuracao.model.CampoBaixa;
import com.marillia.milebag.business.configuracao.model.CampoBaixaConfig;
import com.marillia.milebag.business.configuracao.model.CampoObrigatorioRequest;
import com.marillia.milebag.business.configuracao.model.CampoObrigatorioResponse;
import com.marillia.milebag.business.configuracao.repository.CampoBaixaConfigRepository;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * Obrigatoriedade por tenant dos campos do formulario de baixa (US1 desta
 * HU, sub-area "Campos obrigatorios") - ver contracts/configuracoes-tenant-api.md.
 */
@Service
public class CampoBaixaConfigService {

    private static final Logger log = LoggerFactory.getLogger(CampoBaixaConfigService.class);

    private final CampoBaixaConfigRepository campoBaixaConfigRepository;

    public CampoBaixaConfigService(CampoBaixaConfigRepository campoBaixaConfigRepository) {
        this.campoBaixaConfigRepository = campoBaixaConfigRepository;
    }

    /** Todo {@link CampoBaixa} do catalogo, com a obrigatoriedade configurada pelo tenant ou o padrao do sistema quando nao configurada. */
    @Transactional(readOnly = true)
    public List<CampoObrigatorioResponse> listar() {
        return Arrays.stream(CampoBaixa.values())
                .map(campo -> new CampoObrigatorioResponse(
                        campo, campoBaixaConfigRepository.findByCampo(campo).map(CampoBaixaConfig::isObrigatorio).orElse(campo.isObrigatorioPadrao())))
                .toList();
    }

    @Transactional
    public void atualizar(List<CampoObrigatorioRequest> itens) {
        for (CampoObrigatorioRequest item : itens) {
            CampoBaixa campo = interpretarCampo(item.campo());
            CampoBaixaConfig config = campoBaixaConfigRepository.findByCampo(campo).orElseGet(() -> new CampoBaixaConfig(campo, item.obrigatorio()));
            config.definirObrigatorio(item.obrigatorio());
            campoBaixaConfigRepository.save(config);
        }
        log.info("Configuracao de campos de baixa atualizada ({} campo(s))", itens.size());
    }

    private CampoBaixa interpretarCampo(String campo) {
        if (campo == null) {
            throw new RegraNegocioException("campo e obrigatorio.");
        }
        try {
            return CampoBaixa.valueOf(campo);
        } catch (IllegalArgumentException e) {
            throw new RegraNegocioException("campo '" + campo + "' nao pertence ao catalogo fixo do sistema.");
        }
    }
}
