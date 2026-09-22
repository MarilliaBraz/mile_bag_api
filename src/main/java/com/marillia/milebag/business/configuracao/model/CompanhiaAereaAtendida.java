package com.marillia.milebag.business.configuracao.model;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Companhia aerea atendida pelo tenant (data-model.md desta HU) - so criar/listar/remover no contrato, sem update. */
@Entity
@Table(name = "companhia_aerea_atendida")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanhiaAereaAtendida extends BaseEntity {

    @Column(name = "codigo_iata", nullable = false, updatable = false, length = 10)
    private String codigoIata;

    @Column(updatable = false, length = 200)
    private String nome;

    public CompanhiaAereaAtendida(String codigoIata, String nome) {
        this.codigoIata = codigoIata;
        this.nome = nome;
    }
}
