package com.marillia.milebag.business.correcao.model;

import java.time.Instant;
import java.util.UUID;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Uma correcao de etapa da entrega, com motivo, antes e depois (V51) - auditoria; nunca editada nem apagada. */
@Entity
@Table(name = "correcao_entrega")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CorrecaoEntrega extends BaseEntity {

    @Column(name = "entrega_id", nullable = false, updatable = false)
    private UUID entregaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private EtapaCorrecao etapa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private MomentoCorrecao momento;

    @Column(name = "motivo_ocorrencia_id", nullable = false, updatable = false)
    private UUID motivoId;

    @Column(updatable = false, columnDefinition = "TEXT")
    private String complemento;

    @Column(updatable = false, length = 300)
    private String antes;

    @Column(updatable = false, length = 300)
    private String depois;

    @Column(name = "corrigido_por", nullable = false, updatable = false)
    private UUID corrigidoPor;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private Instant dataHora;

    public CorrecaoEntrega(UUID entregaId, EtapaCorrecao etapa, MomentoCorrecao momento, UUID motivoId, String complemento, String antes,
            String depois, UUID corrigidoPor, Instant dataHora) {
        this.entregaId = entregaId;
        this.etapa = etapa;
        this.momento = momento;
        this.motivoId = motivoId;
        this.complemento = complemento;
        this.antes = antes;
        this.depois = depois;
        this.corrigidoPor = corrigidoPor;
        this.dataHora = dataHora;
    }
}
