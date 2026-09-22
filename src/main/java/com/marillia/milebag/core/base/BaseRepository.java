package com.marillia.milebag.core.base;

import java.io.Serializable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Repositorio base de toda entidade de negocio. Hoje so reexporta {@link
 * JpaRepository}; existe como ponto unico de extensao para quando um
 * segundo dominio precisar de um metodo comum (ex: uma query padrao
 * respeitando o tenant) - evita alterar a assinatura de todo repositorio ja
 * existente naquele momento (CONVENTIONS.md secao 2.2).
 */
@NoRepositoryBean
public interface BaseRepository<T extends BaseEntity, ID extends Serializable> extends JpaRepository<T, ID> {
}
