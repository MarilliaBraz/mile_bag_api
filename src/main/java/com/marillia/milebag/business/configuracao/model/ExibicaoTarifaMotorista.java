package com.marillia.milebag.business.configuracao.model;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** V56: se a empresa oculta do motorista a tarifa paga por viagem - no maximo uma linha por tenant; sem linha, aparece. */
@Entity
@Table(name = "exibicao_tarifa_motorista")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExibicaoTarifaMotorista extends BaseEntity {

    @Column(name = "ocultar_tarifa", nullable = false)
    private boolean ocultarTarifa;

    public ExibicaoTarifaMotorista(boolean ocultarTarifa) {
        this.ocultarTarifa = ocultarTarifa;
    }

    public void definir(boolean novoValor) {
        this.ocultarTarifa = novoValor;
    }
}
