package com.marillia.milebag.business.sincronizacao.model;

import java.time.Instant;
import java.util.UUID;

import com.marillia.milebag.core.config.security.TenantContext;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Registro de deduplicacao/auditoria de um evento offline ja processado
 * (data-model.md desta HU) - a chave primaria e o proprio {@code idLocal}
 * (UUID gerado no dispositivo, ja naturalmente unico), por isso nao
 * extende {@link com.marillia.milebag.core.base.BaseEntity}. So gravado
 * apos sucesso (resultado sempre {@code ACEITO}); um evento com erro nao e
 * persistido aqui, para permitir reenvio integral numa proxima tentativa.
 */
@Entity
@Table(name = "evento_sincronizado")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventoSincronizado {

    @Id
    @Column(name = "id_local")
    private UUID idLocal;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, updatable = false, length = 40)
    private TipoEventoSincronizacao tipoEvento;

    @Column(name = "processado_em", nullable = false, updatable = false)
    private Instant processadoEm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private ResultadoSincronizacao resultado;

    @Column(name = "id_servidor", updatable = false)
    private UUID idServidor;

    public EventoSincronizado(UUID idLocal, TipoEventoSincronizacao tipoEvento, UUID idServidor) {
        this.idLocal = idLocal;
        this.tipoEvento = tipoEvento;
        this.processadoEm = Instant.now();
        this.resultado = ResultadoSincronizacao.ACEITO;
        this.idServidor = idServidor;
    }

    @PrePersist
    private void preencherTenantAntesDePersistir() {
        if (tenantId == null) {
            tenantId = TenantContext.get();
        }
    }
}
