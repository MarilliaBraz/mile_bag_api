package com.marillia.milebag.business.pedagio.model;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Praca de pedagio (RD08, data-model.md desta HU). O valor muda pelo
 * historico ({@link PracaPedagioValor}); o nome pode ser corrigido
 * (ajustes de 2026-11-22) - as viagens ja encerradas guardam o nome da
 * epoca, no trecho de pedagio delas. A localizacao (HU24) e opcional e pode ser corrigida: sem ela a
 * praca nao e detectada nas rotas.
 */
@Entity
@Table(name = "praca_pedagio")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PracaPedagio extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String nome;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    /** V57: "Cidade/UF" descoberta pela localizacao - so informativa; nula sem localizacao ou se o mapa nao achar. */
    @Column(length = 200)
    private String cidade;

    public PracaPedagio(String nome) {
        this.nome = nome;
    }

    public void definirLocalizacao(Double novaLatitude, Double novaLongitude, String novaCidade) {
        this.latitude = novaLatitude;
        this.longitude = novaLongitude;
        this.cidade = novaCidade;
    }

    /** Ajustes de 2026-11-22: a empresa corrige o nome. Viagens ja encerradas guardam o nome da epoca. */
    public void renomear(String novoNome) {
        this.nome = novoNome;
    }

    public boolean temLocalizacao() {
        return latitude != null && longitude != null;
    }
}
