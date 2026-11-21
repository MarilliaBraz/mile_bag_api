package com.marillia.milebag.business.segundavia.service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.core.pdf.TextoPdf;

/**
 * A segunda via do BDO em PDF (V50), uma pagina A4 por BDO: a marca de segunda via, o texto do telex como chegou (ou
 * os campos no mesmo formato, para o BDO digitado ou da companhia aerea), um codigo de barras por mala - como no papel
 * original - e o espaco para quem recebe assinar. A foto desse papel assinado e a do comprovante da entrega.
 */
final class SegundaViaBdoPdf {

    private static final float MARGEM = 40;
    private static final float LARGURA = PDRectangle.A4.getWidth() - 2 * MARGEM;
    private static final float TOPO = PDRectangle.A4.getHeight() - MARGEM;
    private static final float TAMANHO_TELEX = 9;
    private static final float ALTURA_LINHA_TELEX = 11;
    /** Courier: todo caractere tem 0,6 do tamanho da fonte. */
    private static final int COLUNAS_TELEX = (int) (LARGURA / (TAMANHO_TELEX * 0.6f));
    private static final int MAXIMO_CODIGOS = 4;
    private static final float ALTURA_CODIGO = 42;
    private static final float ALTURA_ASSINATURA = 150;
    private static final Color CINZA = new Color(0x5F, 0x63, 0x68);
    private static final PDType1Font NORMAL = PDType1Font.HELVETICA;
    private static final PDType1Font NEGRITO = PDType1Font.HELVETICA_BOLD;
    private static final PDType1Font TELEX = PDType1Font.COURIER;
    private static final PDType1Font CODIGO = PDType1Font.COURIER_BOLD;
    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Sao_Paulo"));

    private SegundaViaBdoPdf() {
    }

    static byte[] gerar(List<Bdo> bdos, String empresa, Instant emitidaEm) {
        try (PDDocument documento = new PDDocument()) {
            for (Bdo bdo : bdos) {
                pagina(documento, bdo, empresa, emitidaEm);
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            documento.save(saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar a segunda via do BDO em PDF.", e);
        }
    }

    private static void pagina(PDDocument documento, Bdo bdo, String empresa, Instant emitidaEm) throws IOException {
        PDPage pagina = new PDPage(PDRectangle.A4);
        documento.addPage(pagina);
        try (PDPageContentStream conteudo = new PDPageContentStream(documento, pagina)) {
            float y = TOPO - 10;
            texto(conteudo, NORMAL, 9, CINZA, MARGEM, y, empresa);
            y -= 22;
            texto(conteudo, NEGRITO, 16, Color.BLACK, MARGEM, y, "Segunda via do BDO");
            y -= 16;
            texto(conteudo, NORMAL, 9, CINZA, MARGEM, y, "Emitida pelo Milebag em " + DATA_HORA.format(emitidaEm)
                    + " (horário de Brasília) - cópia do documento recebido.");
            y -= 22;
            String identificacao = (bdo.getTipoProcesso() == null ? "" : bdo.getTipoProcesso() + " ")
                    + (bdo.getNumero() == null ? "BDO sem número" : bdo.getNumero());
            texto(conteudo, CODIGO, 14, Color.BLACK, MARGEM, y, identificacao);
            y -= 10;
            linha(conteudo, y);
            y -= 16;

            List<String> codigos = codigosDeBarras(bdo);
            float limiteTelex = MARGEM + ALTURA_ASSINATURA + (codigos.isEmpty() ? 0 : ALTURA_CODIGO + 24);
            for (String linhaTelex : linhasDoTelex(bdo)) {
                if (y < limiteTelex) {
                    texto(conteudo, NORMAL, 8, CINZA, MARGEM, y, "(texto cortado - o restante está no BDO original)");
                    break;
                }
                texto(conteudo, TELEX, TAMANHO_TELEX, Color.BLACK, MARGEM, y, linhaTelex);
                y -= ALTURA_LINHA_TELEX;
            }

            if (!codigos.isEmpty()) {
                float largura = (LARGURA - (codigos.size() - 1) * 12) / codigos.size();
                float base = MARGEM + ALTURA_ASSINATURA + 14;
                for (int i = 0; i < codigos.size(); i++) {
                    float x = MARGEM + i * (largura + 12);
                    try {
                        BitMatrix barras = new Code128Writer().encode(codigos.get(i), BarcodeFormat.CODE_128, (int) largura * 2,
                                (int) ALTURA_CODIGO * 2, Map.of(EncodeHintType.MARGIN, 0));
                        BufferedImage imagem = MatrixToImageWriter.toBufferedImage(barras);
                        conteudo.drawImage(LosslessFactory.createFromImage(documento, imagem), x, base, largura, ALTURA_CODIGO);
                    } catch (IllegalArgumentException e) {
                        // Sem o desenho, o numero escrito embaixo ainda identifica a mala.
                    }
                    texto(conteudo, CODIGO, 9, Color.BLACK, x, base - 11, codigos.get(i));
                }
            }

            assinatura(conteudo);
        }
    }

    /** O telex como chegou, quebrado na largura da pagina; sem ele, os campos no mesmo formato do telex. */
    static List<String> linhasDoTelex(Bdo bdo) {
        List<String> origem = new ArrayList<>();
        if (bdo.getTextoOriginal() != null && !bdo.getTextoOriginal().isBlank()) {
            for (String linha : bdo.getTextoOriginal().strip().split("\\R")) {
                origem.add(linha.stripTrailing());
            }
        } else {
            adicionar(origem, "NOME", bdo.getPassageiroNome());
            adicionar(origem, "ENDERECO", bdo.getEnderecoEntrega());
            adicionar(origem, "TELEFONE", bdo.getTelefonePassageiro());
            adicionar(origem, "E-MAIL", bdo.getEmailPassageiro());
            adicionar(origem, "COMPANHIA", bdo.getCompanhiaAerea());
            adicionar(origem, "ETIQUETAS", bdo.getEtiquetaBagagem());
            adicionar(origem, "MALAS", bdo.getQuantidadeMalas() == null ? null : bdo.getQuantidadeMalas().toString());
            adicionar(origem, "COR/TIPO", bdo.getCoresBagagem());
            adicionar(origem, "RETIRADA", bdo.getAeroportoRetirada());
            adicionar(origem, "VOO", bdo.getVooChegada());
            adicionar(origem, "INSTRUCOES", bdo.getInstrucoesEntrega());
        }
        List<String> quebradas = new ArrayList<>();
        for (String linha : origem) {
            if (linha.isEmpty()) {
                quebradas.add("");
            }
            for (int inicio = 0; inicio < linha.length(); inicio += COLUNAS_TELEX) {
                quebradas.add(linha.substring(inicio, Math.min(linha.length(), inicio + COLUNAS_TELEX)));
            }
        }
        return quebradas;
    }

    /** Um codigo por mala, como no papel original: processo + numero da mala ("AHLGYNLA24713-01"). */
    static List<String> codigosDeBarras(Bdo bdo) {
        if (bdo.getNumero() == null || !bdo.getNumero().matches("[A-Z0-9]{4,30}")) {
            return List.of();
        }
        // So letras ASCII no prefixo: o Code128 recusa acento, e um BDO digitado nao pode derrubar a impressao.
        String tipo = bdo.getTipoProcesso() == null ? "" : bdo.getTipoProcesso().replaceAll("[^A-Z]", "");
        String processo = tipo + bdo.getNumero();
        int malas = bdo.getQuantidadeMalas() == null ? 1 : Math.min(bdo.getQuantidadeMalas(), MAXIMO_CODIGOS);
        List<String> codigos = new ArrayList<>();
        for (int mala = 1; mala <= malas; mala++) {
            codigos.add(processo + "-" + String.format("%02d", mala));
        }
        return codigos;
    }

    private static void assinatura(PDPageContentStream conteudo) throws IOException {
        float y = MARGEM + ALTURA_ASSINATURA - 20;
        linha(conteudo, y + 10);
        texto(conteudo, NEGRITO, 11, Color.BLACK, MARGEM, y - 6, "Recebi a(s) bagagem(ns) descrita(s) acima.");
        y -= 34;
        for (String campo : List.of("Nome:", "Documento:", "Assinatura:")) {
            texto(conteudo, NORMAL, 10, Color.BLACK, MARGEM, y, campo);
            conteudo.setStrokingColor(CINZA);
            conteudo.moveTo(MARGEM + 70, y - 2);
            conteudo.lineTo(MARGEM + LARGURA, y - 2);
            conteudo.stroke();
            y -= 28;
        }
        texto(conteudo, NORMAL, 10, Color.BLACK, MARGEM, y, "Data e hora:  ____/____/________   ____:____");
    }

    private static void adicionar(List<String> linhas, String rotulo, String valor) {
        if (valor != null && !valor.isBlank()) {
            linhas.add(rotulo + ": " + valor.strip());
        }
    }

    private static void linha(PDPageContentStream conteudo, float y) throws IOException {
        conteudo.setStrokingColor(CINZA);
        conteudo.moveTo(MARGEM, y);
        conteudo.lineTo(MARGEM + LARGURA, y);
        conteudo.stroke();
    }

    private static void texto(PDPageContentStream conteudo, PDType1Font fonte, float tamanho, Color cor, float x, float y, String texto)
            throws IOException {
        conteudo.beginText();
        conteudo.setNonStrokingColor(cor);
        conteudo.setFont(fonte, tamanho);
        conteudo.newLineAtOffset(x, y);
        conteudo.showText(TextoPdf.somenteCaracteresDaFonte(fonte, texto == null ? "" : texto));
        conteudo.endText();
    }
}
