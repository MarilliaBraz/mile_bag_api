package com.marillia.milebag.business.acompanhamento.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** V65: pesquisa de satisfacao do passageiro depois da entrega (uma por entrega). Sem RLS, como o link publico. */
@Entity
@Table(name = "avaliacao_entrega")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AvaliacaoEntrega {

    @Id
    private UUID id;

    @Column(name = "entrega_id", nullable = false, updatable = false)
    private UUID entregaId;

    @Column(name = "bdo_id", nullable = false, updatable = false)
    private UUID bdoId;

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    @Column(nullable = false, updatable = false)
    private short nota;

    @Column(updatable = false)
    private Short cordialidade;

    @Column(name = "mala_em_bom_estado", updatable = false)
    private Boolean malaEmBomEstado;

    @Column(length = 500, updatable = false)
    private String comentario;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public AvaliacaoEntrega(UUID entregaId, UUID bdoId, UUID empresaId, short nota, Short cordialidade, Boolean malaEmBomEstado,
            String comentario, Instant agora) {
        this.id = UUID.randomUUID();
        this.entregaId = entregaId;
        this.bdoId = bdoId;
        this.empresaId = empresaId;
        this.nota = nota;
        this.cordialidade = cordialidade;
        this.malaEmBomEstado = malaEmBomEstado;
        this.comentario = comentario;
        this.criadoEm = agora;
    }
}
