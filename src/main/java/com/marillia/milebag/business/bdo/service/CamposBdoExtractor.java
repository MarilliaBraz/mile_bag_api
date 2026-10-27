package com.marillia.milebag.business.bdo.service;

import com.marillia.milebag.business.bdo.model.CamposExtraidosBdo;
import com.marillia.milebag.core.util.CampoRotuladoExtractor;

/**
 * Rotulos aceitos para cada campo do BDO, compartilhados pela leitura por
 * foto ({@link BdoOcrService}, HU01) e pelo telex do e-mail
 * ({@code BdoTelexParser}, HU02).
 *
 * <p>Aceita os rotulos curtos do WorldTracer ("PAX:", "ADDR:") e os dos
 * formularios em portugues das companhias ("Nome do passageiro:",
 * "Endereço de entrega:"), sem diferenciar maiusculas e acentos (ver
 * {@link CampoRotuladoExtractor}). Em cada campo, os rotulos mais longos vem
 * primeiro.</p>
 */
public final class CamposBdoExtractor {

    private static final String[] NUMERO = {
        "NÚMERO DO BDO", "Nº DO BDO", "N° DO BDO", "Nº BDO", "N° BDO", "BDO", "NÚMERO", "NUMBER", "NBR"};
    private static final String[] PASSAGEIRO = {
        "NOME DO PASSAGEIRO", "NOME DA PASSAGEIRA", "PASSAGEIRO", "PASSAGEIRA", "PAX", "NOME", "NAME"};
    private static final String[] ENDERECO = {
        "ENDEREÇO DE ENTREGA", "ENDEREÇO PARA ENTREGA", "DELIVERY ADDRESS", "ENDEREÇO", "ADDRESS", "ADDR"};
    private static final String[] COMPANHIA = {
        "COMPANHIA AÉREA", "CIA. AÉREA", "CIA AÉREA", "CIA", "AIRLINE", "CARRIER", "VOO", "FLT"};
    private static final String[] TELEFONE = {
        "TELEFONE DO PASSAGEIRO", "TELEFONE", "CELULAR", "FONE", "PHONE", "TEL"};
    private static final String[] EMAIL = {"E-MAIL DO PASSAGEIRO", "E-MAIL", "EMAIL"};
    private static final String[] ETIQUETA = {"ETIQUETA DA BAGAGEM", "ETIQUETA", "BAG TAG", "BAGGAGE", "TAG"};

    private CamposBdoExtractor() {
    }

    public static CamposExtraidosBdo extrair(String texto) {
        return new CamposExtraidosBdo(
                CampoRotuladoExtractor.extrair(texto, NUMERO),
                CampoRotuladoExtractor.extrair(texto, PASSAGEIRO),
                CampoRotuladoExtractor.extrair(texto, ENDERECO),
                CampoRotuladoExtractor.extrair(texto, COMPANHIA),
                CampoRotuladoExtractor.extrair(texto, TELEFONE),
                CampoRotuladoExtractor.extrair(texto, EMAIL),
                CampoRotuladoExtractor.extrair(texto, ETIQUETA));
    }
}
