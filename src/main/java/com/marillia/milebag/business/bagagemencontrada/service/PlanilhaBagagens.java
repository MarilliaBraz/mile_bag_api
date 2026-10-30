package com.marillia.milebag.business.bagagemencontrada.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.marillia.milebag.business.bagagemencontrada.model.BagagemEncontradaResponse;

/**
 * A busca de bagagens encontradas em CSV (HU36, RF29), no formato que o Excel em portugues abre direto: UTF-8 com BOM,
 * separador {@code ;} e decimais com virgula.
 */
final class PlanilhaBagagens {

    private static final String BOM = "﻿";
    private static final List<String> COLUNAS = List.of(
            "Tipo", "Situação", "Número da etiqueta", "Código SID", "Companhia", "Destino", "Voo", "Passageiro", "Tipo de volume", "Tamanho",
            "Cor", "Sinais particulares", "Marca", "Observações", "Aeroporto", "Distância do aeroporto", "Latitude", "Longitude",
            "Origem da localização", "Registrada em", "Fotografada em", "Registrado por");

    private PlanilhaBagagens() {
    }

    static byte[] gerar(List<BagagemEncontradaResponse> bagagens) {
        StringBuilder csv = new StringBuilder(BOM).append(linha(COLUNAS));
        for (BagagemEncontradaResponse b : bagagens) {
            BagagemEncontradaResponse.Etiqueta etiqueta = b.etiqueta();
            BagagemEncontradaResponse.Descricao descricao = b.descricao();
            List<String> celulas = new ArrayList<>(COLUNAS.size());
            celulas.add(RotulosBagagem.tipo(b.tipo()));
            celulas.add(RotulosBagagem.situacao(b.situacao()));
            // O rascunho ainda nao foi conferido: mostra o numero lido da foto.
            celulas.add(etiqueta != null ? etiqueta.numeroEtiqueta() : b.lido().numeroEtiqueta());
            celulas.add(descricao == null ? null : descricao.codigoSid());
            celulas.add(etiqueta == null ? null : etiqueta.companhia());
            celulas.add(etiqueta == null ? null : etiqueta.destino());
            celulas.add(etiqueta == null ? null : etiqueta.voo());
            celulas.add(etiqueta == null ? null : etiqueta.nomePassageiro());
            celulas.add(descricao == null ? null : RotulosBagagem.tipoVolume(descricao.tipoVolume()));
            celulas.add(descricao == null ? null : RotulosBagagem.tamanho(descricao.tamanho()));
            celulas.add(descricao == null ? null : RotulosBagagem.cor(descricao.cor()));
            celulas.add(descricao == null ? null : RotulosBagagem.sinais(descricao.sinais()));
            celulas.add(descricao == null ? null : descricao.marca());
            celulas.add(descricao == null ? null : descricao.observacoes());
            celulas.add(b.aeroporto() == null ? null : b.aeroporto().codigo());
            celulas.add(b.aeroporto() == null ? null : RotulosBagagem.distancia(b.aeroporto().distanciaMetros()));
            celulas.add(coordenada(b.localizacao().latitude()));
            celulas.add(coordenada(b.localizacao().longitude()));
            celulas.add(RotulosBagagem.origemLocalizacao(b.localizacao().origem()));
            celulas.add(RotulosBagagem.dataHora(b.registradaEm()));
            celulas.add(RotulosBagagem.dataHora(b.criadoEm()));
            celulas.add(b.registradoPor());
            csv.append(linha(celulas));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String coordenada(Double valor) {
        return valor == null ? null : String.format(Locale.ROOT, "%.6f", valor).replace('.', ',');
    }

    private static String linha(List<String> celulas) {
        return celulas.stream().map(PlanilhaBagagens::celula).collect(Collectors.joining(";")) + "\r\n";
    }

    /**
     * Entre aspas quando tem separador, aspas ou quebra de linha. Texto digitado que comeca com {@code = + - @} ganha um
     * apostrofo, para a planilha nao o executar como formula (injecao de CSV); as coordenadas negativas sao numeros.
     */
    static String celula(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "";
        }
        String seguro = "=+-@\t\r".indexOf(valor.charAt(0)) >= 0 && !valor.matches("-?[0-9]+(,[0-9]+)?") ? "'" + valor : valor;
        return seguro.matches("(?s).*[;\"\r\n].*") ? "\"" + seguro.replace("\"", "\"\"") + "\"" : seguro;
    }
}
