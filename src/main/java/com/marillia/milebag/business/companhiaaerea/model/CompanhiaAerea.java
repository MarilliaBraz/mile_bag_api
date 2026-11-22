package com.marillia.milebag.business.companhiaaerea.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Companhia aerea da lista de referencia (V53) - sem tenant nem RLS, so leitura. */
@Entity
@Table(name = "companhia_aerea")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanhiaAerea {

    @Id
    @Column(name = "codigo_iata", length = 2)
    private String codigoIata;

    @Column(name = "codigo_icao", length = 3)
    private String codigoIcao;

    private String nome;

    @Column(length = 2)
    private String pais;

    /** Para testes: a tabela e carregada pela migracao. */
    public CompanhiaAerea(String codigoIata, String codigoIcao, String nome, String pais) {
        this.codigoIata = codigoIata;
        this.codigoIcao = codigoIcao;
        this.nome = nome;
        this.pais = pais;
    }
}
