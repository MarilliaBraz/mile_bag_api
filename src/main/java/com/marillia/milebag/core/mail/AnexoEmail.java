package com.marillia.milebag.core.mail;

/** Arquivo anexado a um e-mail (segunda via do BDO em PDF, V50). */
public record AnexoEmail(String nomeArquivo, String tipoConteudo, byte[] conteudo) {
}
