package com.marillia.milebag.business.painelgestao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.marillia.milebag.core.base.BaseEntity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** V61: prazo padrao da mala e horas sem atualizacao para virar excecao, por empresa. */
@Entity
@Table(name = "painel_gestao_config")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PainelGestaoConfig extends BaseEntity {

    @Column(name = "prazo_entrega_horas", nullable = false)
    private int prazoEntregaHoras;

    @Column(name = "horas_sem_atualizacao", nullable = false)
    private int horasSemAtualizacao;

    /** V63: meta de entregas no prazo (OTD), em %. */
    @Column(name = "meta_otd", nullable = false)
    private int metaOtd;

    public PainelGestaoConfig(int prazoEntregaHoras, int horasSemAtualizacao, int metaOtd) {
        atualizar(prazoEntregaHoras, horasSemAtualizacao, metaOtd);
    }

    public void atualizar(int prazoEntregaHoras, int horasSemAtualizacao, int metaOtd) {
        this.prazoEntregaHoras = prazoEntregaHoras;
        this.horasSemAtualizacao = horasSemAtualizacao;
        this.metaOtd = metaOtd;
    }
}
