package com.marillia.milebag.business.configuracao.model;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Sobrescreve, por tenant, a obrigatoriedade padrao de um {@link CampoBaixa} (RI10). */
@Entity
@Table(name = "campo_baixa_config")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampoBaixaConfig extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 50)
    private CampoBaixa campo;

    @Column(nullable = false)
    private boolean obrigatorio;

    public CampoBaixaConfig(CampoBaixa campo, boolean obrigatorio) {
        this.campo = campo;
        this.obrigatorio = obrigatorio;
    }

    public void definirObrigatorio(boolean obrigatorio) {
        this.obrigatorio = obrigatorio;
    }
}
