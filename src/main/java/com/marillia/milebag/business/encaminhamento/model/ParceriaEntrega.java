package com.marillia.milebag.business.encaminhamento.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Empresa de entrega parceira de uma companhia aerea (HU37, RD23): so para ela a companhia encaminha bagagens. Cadastro
 * da plataforma, sem RLS - nao estende {@code BaseEntity}.
 */
@Entity
@IdClass(ParceriaEntrega.Chave.class)
@Table(name = "parceria_entrega")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParceriaEntrega {

    @Id
    @Column(name = "companhia_id")
    private UUID companhiaId;

    @Id
    @Column(name = "empresa_id")
    private UUID empresaId;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    public ParceriaEntrega(UUID companhiaId, UUID empresaId) {
        this.companhiaId = companhiaId;
        this.empresaId = empresaId;
        this.criadoEm = Instant.now();
    }

    public record Chave(UUID companhiaId, UUID empresaId) implements Serializable {
        public Chave {
        }

        /** O JPA exige um construtor sem argumentos na classe da chave. */
        public Chave() {
            this(null, null);
        }
    }
}
