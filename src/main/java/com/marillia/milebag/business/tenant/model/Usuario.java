package com.marillia.milebag.business.tenant.model;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Conta de acesso de um usuario do sistema. HU15 criou as primeiras linhas
 * (o administrador de tenant ativado pelo convite) e HU23 as de motorista -
 * entidade tenant-scoped normal (estende {@link BaseEntity}: RLS padrao).
 * {@code papel} guarda a mesma string usada em {@code Papeis} (nao um enum) -
 * consistente com a claim {@code papel} do JWT. {@code email} e o login e nao
 * muda; senha, nome e {@code ativo} sim (HU23).
 */
@Entity
@Table(name = "usuario")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Usuario extends BaseEntity {

    @Column(updatable = false, length = 320)
    private String email;

    @Column(name = "senha_hash", length = 100)
    private String senhaHash;

    @Column(updatable = false, length = 30)
    private String papel;

    /** Nulo em contas anteriores a HU23 (administradores criados por convite): a tela mostra o login no lugar. */
    @Column(length = 200)
    private String nome;

    /** Conta desativada nao entra (login recusado); o historico dela (entregas, ajustes) continua apontando para o id. */
    @Column(nullable = false)
    private boolean ativo = true;

    public Usuario(String email, String senhaHash, String papel) {
        this(email, senhaHash, papel, null);
    }

    public Usuario(String email, String senhaHash, String papel, String nome) {
        this.email = email;
        this.senhaHash = senhaHash;
        this.papel = papel;
        this.nome = nome;
    }

    public void definirSenhaHash(String novoHash) {
        this.senhaHash = novoHash;
    }

    public void definirAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
