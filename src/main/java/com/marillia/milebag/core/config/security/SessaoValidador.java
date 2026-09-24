package com.marillia.milebag.core.config.security;

/**
 * Decide se um token de assinatura valida ainda representa uma sessao aceita (HU23): a conta pode ter
 * sido desativada ou a senha redefinida depois da emissao. Implementado no dominio dono das contas
 * ({@code business.tenant}); o filtro so conhece esta interface (inversao de dependencia, o core nao
 * depende de dominio de negocio). Roda com o {@link TenantContext} do token ja definido.
 */
public interface SessaoValidador {

    boolean sessaoValida(AuthenticatedUser usuario);
}
