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

    public PainelGestaoConfig(int prazoEntregaHoras, int horasSemAtualizacao) {
        atualizar(prazoEntregaHoras, horasSemAtualizacao);
    }

    public void atualizar(int prazoEntregaHoras, int horasSemAtualizacao) {
        this.prazoEntregaHoras = prazoEntregaHoras;
        this.horasSemAtualizacao = horasSemAtualizacao;
    }
}
