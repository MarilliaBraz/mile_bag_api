package com.marillia.milebag.business.configuracao.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.configuracao.model.ExibicaoTarifaMotorista;
import com.marillia.milebag.business.configuracao.repository.ExibicaoTarifaMotoristaRepository;

/**
 * V56: a empresa pode ocultar do motorista a tarifa paga por viagem. Linha unica do tenant atual (a RLS ja filtra),
 * mesmo padrao de {@code IdentidadeVisualTenantService}. Sem linha, a tarifa aparece.
 */
@Service
public class ExibicaoTarifaMotoristaService {

    private static final Logger log = LoggerFactory.getLogger(ExibicaoTarifaMotoristaService.class);

    private final ExibicaoTarifaMotoristaRepository repository;

    public ExibicaoTarifaMotoristaService(ExibicaoTarifaMotoristaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean ocultarTarifa() {
        return repository.findAll().stream().findFirst().map(ExibicaoTarifaMotorista::isOcultarTarifa).orElse(false);
    }

    @Transactional
    public boolean definir(boolean ocultarTarifa) {
        ExibicaoTarifaMotorista config = repository.findAll().stream().findFirst()
                .orElseGet(() -> new ExibicaoTarifaMotorista(ocultarTarifa));
        config.definir(ocultarTarifa);
        repository.save(config);
        log.info("Tarifa por viagem {} para os motoristas do tenant.", ocultarTarifa ? "oculta" : "visivel");
        return ocultarTarifa;
    }
}
