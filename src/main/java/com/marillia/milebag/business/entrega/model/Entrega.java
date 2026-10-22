package com.marillia.milebag.business.entrega.model;

import java.time.Instant;
import java.util.UUID;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Liga um BDO confirmado (de qualquer origem) a um motorista responsavel -
 * ver data-model.md desta HU.
 */
@Entity
@Table(name = "entrega")
@Getter
@Setter
@NoArgsConstructor
public class Entrega extends BaseEntity {

    @Column(name = "bdo_id", nullable = false, updatable = false)
    private UUID bdoId;

    @Column(name = "motorista_responsavel")
    private UUID motoristaResponsavel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EntregaStatus status = EntregaStatus.AGUARDANDO_ATRIBUICAO;

    /**
     * Janela combinada com o passageiro na ocorrencia mais recente (HU29,
     * RD07) - nula se ela nao foi de reagendamento. Mantida na nova
     * autoatribuicao, para orientar quem assumir a entrega.
     */
    @Column(name = "janela_reagendamento_inicio")
    private Instant janelaReagendamentoInicio;

    @Column(name = "janela_reagendamento_fim")
    private Instant janelaReagendamentoFim;

    public JanelaReagendamento janelaReagendamento() {
        return janelaReagendamentoInicio == null ? null : new JanelaReagendamento(janelaReagendamentoInicio, janelaReagendamentoFim);
    }

    public void definirJanelaReagendamento(JanelaReagendamento janela) {
        this.janelaReagendamentoInicio = janela == null ? null : janela.inicio();
        this.janelaReagendamentoFim = janela == null ? null : janela.fim();
    }
}
