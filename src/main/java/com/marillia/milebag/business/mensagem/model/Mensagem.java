package com.marillia.milebag.business.mensagem.model;

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

/**
 * Uma mensagem (manual ou automatica) da conversa de uma entrega (HU18,
 * RD14) - registro append-only: nunca atualizada nem apagada, sem
 * setters, mesmo principio de {@code AjusteViagem} (HU17) e {@code
 * CheckpointGps} (HU04).
 */
@Entity
@Table(name = "mensagem")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Mensagem extends BaseEntity {

    @Column(name = "entrega_id", nullable = false, updatable = false)
    private UUID entregaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private AutorMensagem autor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private CanalMensagem canal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private TipoMensagem tipo;

    @Column(nullable = false, updatable = false)
    private String conteudo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_envio", nullable = false, updatable = false, length = 20)
    private StatusEnvioMensagem statusEnvio;

    /** Nulo quando {@code tipo = MANUAL} (data-model.md). */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", updatable = false, length = 30)
    private TipoEventoNotificacao tipoEvento;

    public Mensagem(
            UUID entregaId,
            AutorMensagem autor,
            CanalMensagem canal,
            TipoMensagem tipo,
            String conteudo,
            StatusEnvioMensagem statusEnvio,
            TipoEventoNotificacao tipoEvento) {
        this.entregaId = entregaId;
        this.autor = autor;
        this.canal = canal;
        this.tipo = tipo;
        this.conteudo = conteudo;
        this.statusEnvio = statusEnvio;
        this.tipoEvento = tipoEvento;
    }
}
