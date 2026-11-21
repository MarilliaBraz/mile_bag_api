package com.marillia.milebag.business.retirada.model;

import java.time.Instant;
import java.util.UUID;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Retirada da bagagem no aeroporto (V48), uma por tentativa: foto obrigatória (em foto_entrega), onde o motorista
 * estava e a observação das avarias. Fica "pendente" (sem trajeto) até a viagem iniciar, que a vincula.
 */
@Entity
@Table(name = "retirada_bagagem")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RetiradaBagagem extends BaseEntity {

    @Column(name = "entrega_id", nullable = false, updatable = false)
    private UUID entregaId;

    @Column(name = "motorista_id", nullable = false, updatable = false)
    private UUID motoristaId;

    @Column(name = "trajeto_id")
    private UUID trajetoId;

    @Column(updatable = false)
    private Double latitude;

    @Column(updatable = false)
    private Double longitude;

    @Column(name = "observacao_avaria", updatable = false)
    private String observacaoAvaria;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private Instant dataHora;

    @Column(name = "registrado_offline", nullable = false, updatable = false)
    private boolean registradoOffline;

    @Column(name = "id_local", updatable = false, length = 100)
    private String idLocal;

    @Column(name = "descartada_em")
    private Instant descartadaEm;

    public RetiradaBagagem(
            UUID entregaId, UUID motoristaId, Double latitude, Double longitude, String observacaoAvaria, Instant dataHora,
            boolean registradoOffline, String idLocal) {
        this.entregaId = entregaId;
        this.motoristaId = motoristaId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.observacaoAvaria = observacaoAvaria;
        this.dataHora = dataHora;
        this.registradoOffline = registradoOffline;
        this.idLocal = idLocal;
    }

    /** Dispensa ou cancelamento antes da viagem: a bagagem voltou, esta retirada nao vale mais para iniciar. */
    /** V51: correcao das avarias da retirada, com motivo registrado em correcao_entrega. */
    public void corrigirObservacaoAvaria(String observacao) {
        this.observacaoAvaria = observacao;
    }

    public void descartar(Instant quando) {
        this.descartadaEm = quando;
    }

    /** A viagem desta tentativa iniciou: a retirada passa a ser dela. */
    public void vincularAoTrajeto(UUID trajetoId) {
        this.trajetoId = trajetoId;
    }
}
