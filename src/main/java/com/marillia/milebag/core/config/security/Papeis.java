package com.marillia.milebag.core.config.security;

/**
 * Nomes de perfil (claim {@code papel} do JWT, RD12 em
 * requisitos/04-regras-dominio.md) usados em restricoes de autorizacao por
 * URL em {@link SecurityConfig}. So os perfis efetivamente exigidos por
 * alguma HU ja implementada - RD12 cita "no minimo" administrador do
 * tenant, operador de back-office e motorista; os demais entram quando uma
 * HU concreta passar a exigi-los.
 */
public final class Papeis {

    public static final String MOTORISTA = "MOTORISTA";
    public static final String ADMINISTRADOR_TENANT = "ADMINISTRADOR_TENANT";
    // HU14: perfil do provedor da plataforma (administra tenants - nao
    // confundir com ADMINISTRADOR_TENANT, que administra UM tenant). Token
    // deste perfil nao carrega claim de tenant (JwtService) - ver Tenant.java.
    public static final String ADMINISTRADOR_PLATAFORMA = "ADMINISTRADOR_PLATAFORMA";
    // HU33 (RD19): perfis de uma companhia aerea - o administrador cadastra os
    // funcionarios; o funcionario registra as bagagens encontradas.
    public static final String ADMINISTRADOR_COMPANHIA = "ADMINISTRADOR_COMPANHIA";
    public static final String FUNCIONARIO_COMPANHIA = "FUNCIONARIO_COMPANHIA";

    private Papeis() {
    }
}
