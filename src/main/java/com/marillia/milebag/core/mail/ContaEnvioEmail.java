package com.marillia.milebag.core.mail;

/**
 * Conta SMTP propria de um envio, no lugar da conta padrao de {@code milebag.smtp} (mesmo servidor). Provedores como o
 * Google Workspace so aceitam como remetente a conta autenticada - por isso a mensageria (HU18) manda com a sua
 * propria caixa.
 */
public record ContaEnvioEmail(String usuario, String senha, String remetente) {
}
