package com.marillia.milebag.business.tenant.model;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Conta de acesso de um usuario do sistema. HU15 e o primeiro ponto do
 * codigo a criar uma linha aqui (o administrador de tenant ativado pelo
 * convite) - entidade tenant-scoped normal (estende {@link BaseEntity}: RLS
 * padrao). {@code papel} guarda a mesma string usada em {@code Papeis} (nao
 * um enum) - consistente com a claim {@code papel} do JWT.
 */
@Entity
@Table(name = "usuario")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Usuario extends BaseEntity {

    @Column(updatable = false, length = 320)
    private String email;

    @Column(name = "senha_hash", updatable = false, length = 100)
    private String senhaHash;

    @Column(updatable = false, length = 30)
    private String papel;

    public Usuario(String email, String senhaHash, String papel) {
        this.email = email;
        this.senhaHash = senhaHash;
        this.papel = papel;
    }
}
