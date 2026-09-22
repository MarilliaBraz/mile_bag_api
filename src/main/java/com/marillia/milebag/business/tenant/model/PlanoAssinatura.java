package com.marillia.milebag.business.tenant.model;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Entidade MINIMA (id, nome, ativo) - HU16 (gerenciar planos de assinatura)
 * ainda nao foi implementada nesta base de codigo. tasks.md desta HU
 * autoriza explicitamente este stub so para satisfazer a referencia
 * obrigatoria de {@code Tenant.planoId} (FR-002), sem as regras completas
 * de HU16 (limites por plano, precificacao, upgrade/downgrade). Substituir
 * quando HU16 for implementada - nao adicionar regra de negocio aqui.
 */
@Entity
@Table(name = "plano_assinatura")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanoAssinatura {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String nome;

    private boolean ativo;

    public PlanoAssinatura(String nome) {
        this.nome = nome;
        this.ativo = true;
    }
}
