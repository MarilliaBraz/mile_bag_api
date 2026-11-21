package com.marillia.milebag.business.configuracao.model;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Base aeroportuaria do tenant (data-model.md desta HU). V52: uma delas e a principal - a que vale para o calculo da
 * viagem quando o BDO nao traz o aeroporto de retirada, ou traz um que nao e base da empresa.
 */
@Entity
@Table(name = "base_aeroportuaria")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BaseAeroportuaria extends BaseEntity {

    @Column(name = "codigo_iata", nullable = false, updatable = false, length = 10)
    private String codigoIata;

    @Column(updatable = false, length = 200)
    private String nome;

    @Column(nullable = false)
    private boolean principal;

    public BaseAeroportuaria(String codigoIata, String nome) {
        this.codigoIata = codigoIata;
        this.nome = nome;
    }

    public void definirPrincipal(boolean principal) {
        this.principal = principal;
    }
}
