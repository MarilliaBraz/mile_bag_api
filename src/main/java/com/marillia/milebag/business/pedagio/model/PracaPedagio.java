package com.marillia.milebag.business.pedagio.model;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Praca de pedagio (RD08, data-model.md desta HU). Sem endpoint de
 * renomeacao no contrato - so o historico de valores muda depois da
 * criacao ({@link PracaPedagioValor}), por isso {@code nome} nao tem
 * setter. A localizacao (HU24) e opcional e pode ser corrigida: sem ela a
 * praca nao e detectada nas rotas.
 */
@Entity
@Table(name = "praca_pedagio")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PracaPedagio extends BaseEntity {

    @Column(nullable = false, updatable = false, length = 200)
    private String nome;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    public PracaPedagio(String nome) {
        this.nome = nome;
    }

    public PracaPedagio(String nome, Double latitude, Double longitude) {
        this.nome = nome;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void definirLocalizacao(Double novaLatitude, Double novaLongitude) {
        this.latitude = novaLatitude;
        this.longitude = novaLongitude;
    }

    public boolean temLocalizacao() {
        return latitude != null && longitude != null;
    }
}
