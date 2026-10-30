package com.marillia.milebag.business.bagagemencontrada.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Bagagem com etiqueta registrada, vista pela busca nacional (HU36, RD22): so os campos que qualquer companhia pode ver.
 * So leitura - quem escreve e o trigger de {@code bagagem_encontrada} (V44). Nao estende {@code BaseEntity}: sem RLS, e
 * a unica excecao ao isolamento entre companhias.
 */
@Entity
@Immutable
@Table(name = "bagagem_encontrada_nacional")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BagagemEncontradaNacional {

    @Id
    @Column(name = "bagagem_id")
    private UUID bagagemId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "numero_etiqueta", nullable = false)
    private String numeroEtiqueta;

    @Column(name = "aeroporto_codigo", nullable = false)
    private String aeroportoCodigo;

    @Column(name = "registrada_em", nullable = false)
    private Instant registradaEm;

    private Double latitude;

    private Double longitude;

    /** Para testes: a tabela e mantida pelo trigger. */
    public BagagemEncontradaNacional(
            UUID bagagemId, UUID tenantId, String numeroEtiqueta, String aeroportoCodigo, Instant registradaEm, Double latitude, Double longitude) {
        this.bagagemId = bagagemId;
        this.tenantId = tenantId;
        this.numeroEtiqueta = numeroEtiqueta;
        this.aeroportoCodigo = aeroportoCodigo;
        this.registradaEm = registradaEm;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
