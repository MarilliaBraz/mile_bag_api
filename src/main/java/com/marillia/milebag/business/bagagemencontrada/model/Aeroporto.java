package com.marillia.milebag.business.bagagemencontrada.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Aeroporto brasileiro com codigo IATA e voos regulares (HU34) - dado de referencia nacional, sem tenant nem RLS
 * (V41, base OurAirports). So leitura.
 */
@Entity
@Table(name = "aeroporto")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Aeroporto {

    @Id
    @Column(length = 3)
    private String codigo;

    private String nome;

    private String cidade;

    @Column(length = 2)
    private String uf;

    private double latitude;

    private double longitude;

    /** Para testes: a tabela e carregada pela migracao. */
    public Aeroporto(String codigo, String nome, String cidade, String uf, double latitude, double longitude) {
        this.codigo = codigo;
        this.nome = nome;
        this.cidade = cidade;
        this.uf = uf;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
